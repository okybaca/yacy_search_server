/**
 *  JsonListImporter
 *  Copyright 23.10.2022 by Michael Peter Christen, @orbiterlab
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.document.importer;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;

import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.SolrInputField;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import net.yacy.cora.date.AbstractFormatter;
import net.yacy.cora.date.ISO8601Formatter;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.solr.SolrType;
import net.yacy.cora.language.synonyms.SynonymLibrary;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.document.Document;
import net.yacy.document.LibraryProvider;
import net.yacy.document.Tokenizer;
import net.yacy.document.VocabularyScraper;
import net.yacy.search.Switchboard;
import net.yacy.search.index.Fulltext;
import net.yacy.search.schema.CollectionConfiguration;
import net.yacy.search.schema.CollectionSchema;

public class JsonListImporter extends Thread implements Importer {

    private static final ConcurrentLog log = new ConcurrentLog("JsonListImporter");
    public static volatile JsonListImporter job;

    public enum ImportState {
        NEW, RUNNING, DRAINING, STOPPING, COMPLETED, COMPLETED_WITH_ERRORS, ABORTED, FAILED;

        public boolean isActive() {
            return this == RUNNING || this == DRAINING || this == STOPPING;
        }
    }

    private record Entry(long line, SolrInputDocument document) { }
    private record Sample(long time, long documents) { }

    private static final class ImportError {
        private long line;
        private String document;
        private final String message;
        private long occurrences = 1;

        private ImportError(final long line, final String document, final String message) {
            this.line = line;
            this.document = document;
            this.message = message;
        }
    }

    private final File inputFile;
    private final String name;
    private final String jobId = UUID.randomUUID().toString();
    private final long sourceSize;
    private final boolean gzip;
    private final boolean deletewhendone;
    private final Fulltext index;
    private final AtomicLong readCount = new AtomicLong();
    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong skipped = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong errorCount = new AtomicLong();
    private final AtomicLong ignoredFields = new AtomicLong();
    private final AtomicInteger activeWorkers = new AtomicInteger();
    private final AtomicInteger liveWorkers = new AtomicInteger();
    private final Deque<ImportError> errors = new ArrayDeque<>();
    private final Deque<Sample> samples = new ArrayDeque<>();
    private volatile ImportState state = ImportState.NEW;
    private volatile long consumed;
    private volatile long startTime;
    private volatile long finishTime;
    private volatile long lastProgress;
    private volatile boolean abort;
    private volatile boolean readerFinished;
    private volatile boolean workerFailed;
    private volatile BlockingQueue<Entry> queue;
    private volatile int workerCount;
    private boolean processingStarted;

    public JsonListImporter(final File inputFile, final boolean gz, final boolean deletewhendone) throws IOException {
        this(inputFile, gz, deletewhendone, Switchboard.getSwitchboard().index.fulltext());
    }

    public JsonListImporter(final File inputFile, final boolean gz, final boolean deletewhendone, final Fulltext index)
            throws IOException {
        super("JsonListImporter - from file " + inputFile.getName());
        if (!inputFile.isFile() || !inputFile.canRead()) throw new IOException("Cannot read import file: " + inputFile);
        this.inputFile = inputFile;
        this.name = inputFile.getName();
        this.sourceSize = inputFile.length();
        this.gzip = gz || this.name.endsWith(".gz");
        this.deletewhendone = deletewhendone;
        this.index = index;
    }

    /** Called under the class lock for both asynchronous and synchronous imports. */
    private void reserve() {
        if (this.state != ImportState.NEW) throw new IllegalStateException("Import has already been started");
        if (job != null && job.isActive()) throw new IllegalStateException("Another JSON import is already running");
        this.startTime = System.nanoTime();
        this.lastProgress = this.startTime;
        this.state = ImportState.RUNNING;
        job = this;
    }

    @Override
    public synchronized void start() {
        synchronized (JsonListImporter.class) {
            reserve();
            try {
                super.start();
            } catch (final RuntimeException | Error e) {
                this.finishTime = System.nanoTime();
                this.state = ImportState.FAILED;
                throw e;
            }
        }
    }

    @Override
    public void run() {
        try {
            proceessPackJson();
        } catch (final IOException e) {
            // Details are retained in the bounded error list; do not log the full JSON input again.
            log.warn("JSON import failed; see import status for details");
        } catch (final IllegalStateException e) {
            log.warn(e.getMessage());
        }
    }

    public void proceessPackJson() throws IOException {
        synchronized (JsonListImporter.class) {
            if (this.state == ImportState.NEW) reserve();
            if (job != this || this.processingStarted) throw new IllegalStateException("Import has already been started");
            this.processingStarted = true;
        }
        final CollectionConfiguration configuration = this.index.getDefaultConfiguration();
        this.workerCount = Runtime.getRuntime().availableProcessors();
        this.queue = new ArrayBlockingQueue<>(this.workerCount * 2);
        final Thread[] workers = new Thread[this.workerCount];
        IOException failure = null;
        long lineNumber = 0;
        long currentLine = 0;
        String currentDocument = "";
        boolean interrupted = false;
        try {
            for (int t = 0; t < workers.length; t++) {
                workers[t] = new Thread(() -> {
                    final VocabularyScraper scraper = new VocabularyScraper();
                    try {
                        while (true) {
                            final Entry entry = this.queue.poll(200, TimeUnit.MILLISECONDS);
                            if (entry == null) {
                                if (this.readerFinished && this.queue.isEmpty()) break;
                                continue;
                            }
                            this.activeWorkers.incrementAndGet();
                            final SolrInputDocument document = entry.document();
                            try {
                                final String id = (String) document.getFieldValue(CollectionSchema.id.getSolrFieldName());
                                final String text = (String) document.getFieldValue(CollectionSchema.text_t.getSolrFieldName());
                                if (text == null || text.isEmpty() || id == null) {
                                    this.skipped.incrementAndGet();
                                    continue;
                                }
                                addMissingAttributes(document, configuration);
                                if (SynonymLibrary.size() > 0 || !LibraryProvider.autotagging.isEmpty()) {
                                    final DigestURL url = new DigestURL((String) document.getFieldValue(CollectionSchema.sku.getSolrFieldName()), ASCII.getBytes(id));
                                    final Tokenizer tokenizer = new Tokenizer(url, text, LibraryProvider.dymLib, true, scraper);
                                    final Map<String, Set<String>> facets = Document.computeGenericFacets(tokenizer.tags());
                                    configuration.enrich(document, tokenizer.synonyms(), facets);
                                }
                                // Use the throwing write path: Segment.putDocument() hides IO failures.
                                this.index.putDocument(document);
                                this.submitted.incrementAndGet();
                            } catch (final IOException | RuntimeException e) {
                                recordError(entry.line(), String.valueOf(document.getFieldValue(CollectionSchema.sku.getSolrFieldName())), e);
                            } finally {
                                this.lastProgress = System.nanoTime();
                                this.activeWorkers.decrementAndGet();
                            }
                        }
                    } catch (final InterruptedException e) {
                        this.workerFailed = true;
                        recordError(0, "", e);
                        Thread.currentThread().interrupt();
                    } finally {
                        this.liveWorkers.decrementAndGet();
                    }
                }, "JsonListImporter-indexer-" + t);
                workers[t].setUncaughtExceptionHandler((thread, error) -> {
                    this.workerFailed = true;
                    recordError(0, "", error);
                });
                this.liveWorkers.incrementAndGet();
                try {
                    workers[t].start();
                } catch (final RuntimeException | Error e) {
                    this.liveWorkers.decrementAndGet();
                    throw e;
                }
            }
            final Set<String> unknownFields = new HashSet<>();
            // Count physical file bytes, before decompression; never compare UTF-16 characters with gzip bytes.
            try (final InputStream file = new FilterInputStream(new FileInputStream(this.inputFile)) {
                @Override
                public int read() throws IOException {
                    final int value = this.in.read();
                    if (value >= 0) {
                        JsonListImporter.this.consumed++;
                        JsonListImporter.this.lastProgress = System.nanoTime();
                    }
                    return value;
                }

                @Override
                public int read(final byte[] buffer, final int offset, final int length) throws IOException {
                    final int count = this.in.read(buffer, offset, length);
                    if (count > 0) {
                        JsonListImporter.this.consumed += count;
                        JsonListImporter.this.lastProgress = System.nanoTime();
                    }
                    return count;
                }
            }) {
                final InputStream decoded = this.gzip ? new GZIPInputStream(file, 65536) : file;
                try (final BufferedReader reader = new BufferedReader(new InputStreamReader(
                        new BufferedInputStream(decoded, 65536), StandardCharsets.UTF_8))) {
                    String line;
                    while (!this.abort && (line = reader.readLine()) != null) {
                        currentLine = ++lineNumber;
                        this.readCount.incrementAndGet();
                        final JSONObject json = new JSONObject(new JSONTokener(line));
                        if ((json.opt("index") != null && json.length() == 1) || json.length() == 0) {
                            this.readCount.decrementAndGet();
                            currentLine = 0;
                            continue;
                        }
                        currentDocument = json.optString("sku", json.optString("url", json.optString("url_s", "")));
                        final SolrInputDocument pack = new SolrInputDocument();
                        // set default values which act as constraints for a proper search
                        CollectionSchema.httpstatus_i.add(pack, 200);

                        // get fields for json object
                        jsonreader: for (final String key: json.keySet()) {
                            final Object o = json.opt(key);
                            if (o == null) continue;
                            if (o instanceof JSONArray) {
                                // transform this into a list
                                final JSONArray a = (JSONArray) o;
                                // patch altered yacy grid schema (yacy grid does not split url lists into protocol and urlstub)
                                if (key.equals("inboundlinks_sxt")) {
                                    // compute inboundlinks_urlstub_sxt and inboundlinks_protocol_sxt
                                    final List<Object> urlstub = new ArrayList<>();
                                    final List<Object> protocol = new ArrayList<>();
                                    for (int i = 0; i < a.length(); i++) {
                                        final AnchorURL b = new AnchorURL((String) a.opt(i));
                                        urlstub.add(b.urlstub(true, true));
                                        protocol.add(b.getProtocol());
                                    }
                                    CollectionSchema.inboundlinks_urlstub_sxt.add(pack, urlstub);
                                    CollectionSchema.inboundlinks_protocol_sxt.add(pack, protocol);
                                    continue jsonreader;
                                }
                                if (key.equals("outboundlinks_sxt")) {
                                    // compute outboundlinks_urlstub_sxt and outboundlinks_protocol_sxt
                                    final List<Object> urlstub = new ArrayList<>();
                                    final List<Object> protocol = new ArrayList<>();
                                    for (int i = 0; i < a.length(); i++) {
                                        final AnchorURL b = new AnchorURL((String) a.opt(i));
                                        urlstub.add(b.urlstub(true, true));
                                        protocol.add(b.getProtocol());
                                    }
                                    CollectionSchema.outboundlinks_urlstub_sxt.add(pack, urlstub);
                                    CollectionSchema.outboundlinks_protocol_sxt.add(pack, protocol);
                                    continue jsonreader;
                                }
                                if (key.equals("images_sxt")) {
                                    // compute images_urlstub_sxt and images_protocol_sxt
                                    final List<Object> urlstub = new ArrayList<>();
                                    final List<Object> protocol = new ArrayList<>();
                                    for (int i = 0; i < a.length(); i++) {
                                        final AnchorURL b = new AnchorURL((String) a.opt(i));
                                        urlstub.add(b.urlstub(true, true));
                                        protocol.add(b.getProtocol());
                                    }
                                    CollectionSchema.images_urlstub_sxt.add(pack, urlstub);
                                    CollectionSchema.images_protocol_sxt.add(pack, protocol);
                                    continue jsonreader;
                                }

                                // prepare to read key type
                                CollectionSchema ctype = null;
                                try {ctype = CollectionSchema.valueOf(key);} catch (final IllegalArgumentException e) {
                                    this.ignoredFields.incrementAndGet();
                                    if (unknownFields.size() < 10 && unknownFields.add(key)) log.warn("unknown key for CollectionSchema: " + key);
                                    continue jsonreader;
                                }
                                final List<Object> list = new ArrayList<>();
                                for (int i = 0; i < a.length(); i++) list.add(a.opt(i));
                                ctype.add(pack, list);
                            } else {
                                // first handle exceptional keys / maybe patch for other systems + other names
                                if (key.equals("url") || key.equals("url_s") || key.equals("sku")) {
                                    // patch yacy grid altered schema (yacy grid does not have IDs any more, but they can be re-computed here)
                                    final DigestURL durl = new DigestURL(o.toString());
                                    final String id = ASCII.String(durl.hash());
                                    pack.setField(CollectionSchema.sku.getSolrFieldName(), durl.toNormalform(true));
                                    // Check the complete input: supplied metadata may be read after the URL.
                                    if (!json.has(CollectionSchema.id.name())) pack.setField(CollectionSchema.id.getSolrFieldName(), id);
                                    if (!json.has(CollectionSchema.host_s.name())) pack.setField(CollectionSchema.host_s.getSolrFieldName(), durl.getHost());
                                    if (!json.has(CollectionSchema.host_id_s.name())) pack.setField(CollectionSchema.host_id_s.getSolrFieldName(), id.substring(6));
                                    continue jsonreader;
                                }
                                if (key.equals("description")) {
                                    // in YaCy descriptions are full-text indexed and also multi-value fields
                                    final List<Object> descriptions = new ArrayList<>();
                                    descriptions.add(o.toString());
                                    CollectionSchema.description_txt.add(pack, descriptions);
                                    continue jsonreader;
                                }
                                if (key.equals("referrer_url_s")) {
                                    // same patch as for urls which require re-calculation of id's; in this case we store the id only!
                                    final DigestURL durl = new DigestURL(o.toString());
                                    final String id = ASCII.String(durl.hash());
                                    pack.setField(CollectionSchema.referrer_id_s.getSolrFieldName(), id);
                                    continue jsonreader;
                                }

                                // prepare to read key type
                                CollectionSchema ctype = null;
                                try {ctype = CollectionSchema.valueOf(key);} catch (final IllegalArgumentException e) {
                                    this.ignoredFields.incrementAndGet();
                                    if (unknownFields.size() < 10 && unknownFields.add(key)) log.warn("unknown key for CollectionSchema: " + key);
                                    continue jsonreader;
                                }
                                if (ctype.getType() == SolrType.date) {
                                    // patch date into something that Solr can understand
                                    final String d = o.toString(); // i.e. Wed Apr 01 02:00:00 CEST 2020
                                    final Date dd = d == null || d.length() == 0 ? null : AbstractFormatter.parseAny(d);
                                    if (dd != null) pack.setField(ctype.getSolrFieldName(), ISO8601Formatter.FORMATTER.format(dd)); // solr dateTime is ISO8601 format
                                    continue jsonreader;
                                }

                                // regular situation, just read content of field
                                pack.setField(key, o.toString());
                            }
                        }

                        final Entry entry = new Entry(currentLine, pack);
                        while (!this.queue.offer(entry, 200, TimeUnit.MILLISECONDS)) {
                            if (this.workerFailed || this.liveWorkers.get() == 0) throw new IOException("Import workers stopped");
                        }
                        currentLine = 0;
                        currentDocument = "";
                        this.lastProgress = System.nanoTime();
                        if (this.workerFailed) throw new IOException("Import worker failed");
                    }
                }
            }
        } catch (final InterruptedException e) {
            this.abort = true;
            if (currentLine > 0) this.skipped.incrementAndGet();
            interrupted = true;
        } catch (final IOException | JSONException | RuntimeException e) {
            failure = e instanceof IOException ? (IOException) e : new IOException(e.getMessage(), e);
            recordError(currentLine, currentDocument, e);
        } catch (final Error e) {
            this.workerFailed = true;
            recordError(currentLine, currentDocument, e);
            throw e;
        } finally {
            this.readerFinished = true;
            synchronized (JsonListImporter.class) {
                this.state = this.abort ? ImportState.STOPPING : ImportState.DRAINING;
            }
            // EOF is not completion. Keep the job active until every accepted document is processed.
            for (final Thread worker : workers) {
                if (worker == null) continue;
                while (worker.isAlive()) {
                    try {
                        worker.join();
                    } catch (final InterruptedException e) {
                        this.abort = true;
                        this.state = ImportState.STOPPING;
                        interrupted = true;
                    }
                }
            }
            synchronized (JsonListImporter.class) {
                this.finishTime = System.nanoTime();
                this.state = failure != null || this.workerFailed ? ImportState.FAILED
                        : this.abort ? ImportState.ABORTED
                        : this.errorCount.get() > 0 ? ImportState.COMPLETED_WITH_ERRORS : ImportState.COMPLETED;
            }
            if (this.deletewhendone && this.state == ImportState.COMPLETED && !this.inputFile.delete()) {
                log.warn("Could not delete imported temporary file: " + this.inputFile);
            }
            log.info("finished processing json pack: state=" + this.state + ", submitted=" + this.submitted.get()
                    + ", skipped=" + this.skipped.get() + ", errors=" + this.errorCount.get() + ", seconds=" + runningTime());
            if (interrupted) Thread.currentThread().interrupt();
        }
        if (failure != null) throw failure;
    }

    private void recordError(final long line, final String document, final Throwable error) {
        final String detail = error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
        final String message = detail.substring(0, Math.min(500, detail.length()));
        final String identifier = document.substring(0, Math.min(300, document.length()));
        final long count = this.errorCount.incrementAndGet();
        if (line > 0) this.failed.incrementAndGet();
        synchronized (this.errors) {
            ImportError repeated = null;
            for (final ImportError previous : this.errors) {
                if (previous.message.equals(message)) {
                    repeated = previous;
                    break;
                }
            }
            if (repeated != null) {
                this.errors.remove(repeated);
                repeated.occurrences++;
                repeated.line = line;
                repeated.document = identifier;
                this.errors.addLast(repeated);
            } else {
                if (this.errors.size() == 10) this.errors.removeFirst();
                this.errors.addLast(new ImportError(line, identifier, message));
            }
        }
        if (count <= 10) log.warn("Import error at line " + line + ": " + message);
    }

    /** Complete URL and content metadata using shared derivations, preserving imported fields. */
    static void addMissingAttributes(final SolrInputDocument document, final CollectionConfiguration configuration)
            throws MalformedURLException {
        final DigestURL url = new DigestURL((String) document.getFieldValue(CollectionSchema.sku.getSolrFieldName()));
        final SolrInputDocument attributes = document.deepCopy();
        configuration.addTextAttributes(attributes);
        configuration.addURIAttributes(attributes, configuration.isEmpty(), url);
        for (final SolrInputField field : attributes) {
            if (!document.containsKey(field.getName())) {
                document.put(field.getName(), field);
            }
        }
    }

    public void quit() {
        synchronized (JsonListImporter.class) {
            if (isActive()) {
                this.abort = true;
                this.state = ImportState.STOPPING;
            }
        }
    }

    public boolean isActive() {
        return this.state.isActive();
    }

    public ImportState getImportState() {
        return this.state;
    }

    public String getJobId() {
        return this.jobId;
    }

    /** Match the source path, so an unrelated import with the same filename is not attributed to a pack. */
    public boolean isSourceFile(final File file) {
        return this.inputFile.toPath().toAbsolutePath().normalize().equals(file.toPath().toAbsolutePath().normalize());
    }

    /** A small, non-consuming snapshot shared by the UI and the authenticated API. */
    public synchronized JSONObject snapshot() throws JSONException {
        final ImportState currentState = this.state;
        final long now = System.nanoTime();
        final long end = this.finishTime == 0 ? now : this.finishTime;
        final long done = this.submitted.get() + this.skipped.get() + this.failed.get();
        if (this.samples.isEmpty()) this.samples.addLast(new Sample(this.startTime == 0 ? now : this.startTime, 0));
        // Keep a bounded window; polling never causes work on the indexing threads.
        if (now - this.samples.getLast().time() >= TimeUnit.SECONDS.toNanos(1)) {
            this.samples.addLast(new Sample(end, done));
        }
        while (this.samples.size() > 2 && now - this.samples.getFirst().time() > TimeUnit.SECONDS.toNanos(15)) {
            this.samples.removeFirst();
        }
        final Sample oldest = this.samples.getFirst();
        final double seconds = Math.max(0.001, (end - oldest.time()) / 1_000_000_000.0);
        final double speed = currentState.isActive() ? Math.max(0, (done - oldest.documents()) / seconds)
                : this.startTime == 0 ? 0 : done / Math.max(0.001, (end - this.startTime) / 1_000_000_000.0);
        final long age = this.lastProgress == 0 ? 0 : TimeUnit.NANOSECONDS.toSeconds(now - this.lastProgress);
        final JSONArray recentErrors = new JSONArray();
        synchronized (this.errors) {
            for (final ImportError error : this.errors) {
                recentErrors.put(new JSONObject().put("line", error.line).put("document", error.document)
                        .put("message", error.message).put("occurrences", error.occurrences));
            }
        }
        return new JSONObject().put("id", this.jobId).put("source", this.name).put("state", currentState.name())
                .put("active", currentState.isActive()).put("bytesRead", this.consumed).put("totalBytes", this.sourceSize)
                .put("read", this.readCount.get()).put("submitted", this.submitted.get()).put("skipped", this.skipped.get())
                .put("failed", this.failed.get()).put("errors", this.errorCount.get()).put("ignoredFields", this.ignoredFields.get())
                .put("documentsPerSecond", speed).put("elapsedSeconds", runningTime())
                .put("remainingSeconds", remainingTime()).put("lastProgressSeconds", age)
                .put("stalled", currentState.isActive() && age >= 60)
                .put("queueSize", this.queue == null ? 0 : this.queue.size()).put("queueCapacity", this.workerCount * 2)
                .put("activeWorkers", this.activeWorkers.get()).put("workers", this.workerCount).put("recentErrors", recentErrors);
    }

    @Override
    public String source() {
        return this.name;
    }

    @Override
    public int count() {
        return (int) Math.min(Integer.MAX_VALUE, this.submitted.get());
    }

    @Override
    public int speed() {
        return (int) Math.min(Integer.MAX_VALUE, this.submitted.get() / Math.max(1L, runningTime()));
    }

    @Override
    public long runningTime() {
        if (this.startTime == 0) return 0;
        return TimeUnit.NANOSECONDS.toSeconds((this.finishTime == 0 ? System.nanoTime() : this.finishTime) - this.startTime);
    }

    @Override
    public long remainingTime() {
        if (!isActive()) return 0;
        final long bytes = this.consumed;
        final long seconds = runningTime();
        if (bytes == 0 || bytes >= this.sourceSize || seconds == 0 || this.abort) return -1;
        return (long) Math.ceil((this.sourceSize - bytes) * (double) seconds / bytes);
    }

    @Override
    public String status() {
        return this.state.name();
    }
}
