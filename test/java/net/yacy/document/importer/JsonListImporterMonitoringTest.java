package net.yacy.document.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

import org.apache.solr.common.SolrInputDocument;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import net.yacy.cora.federate.solr.connector.SolrConnector;
import net.yacy.cora.federate.solr.instance.InstanceMirror;
import net.yacy.cora.storage.Configuration.Entry;
import net.yacy.htroot.api.importstatus_p;
import net.yacy.htroot.IndexImportJsonList_p;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.data.TransactionManager;
import net.yacy.server.serverObjects;
import net.yacy.search.index.Fulltext;
import net.yacy.search.schema.CollectionConfiguration;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.search.schema.WebgraphConfiguration;

/** Exercise the actual reader, worker queue, write boundary and monitoring endpoint. */
public class JsonListImporterMonitoringTest {
    @Rule
    public final TemporaryFolder files = new TemporaryFolder();

    private Fulltext index;
    private JsonListImporter previousJob;
    private final AtomicInteger writes = new AtomicInteger();
    private final CopyOnWriteArrayList<SolrInputDocument> documents = new CopyOnWriteArrayList<>();
    private volatile boolean failWrites;
    private volatile CountDownLatch entered;
    private volatile CountDownLatch release;
    private Thread driver;

    @Before
    public void setUp() throws Exception {
        this.previousJob = JsonListImporter.job;
        JsonListImporter.job = null;
        final CollectionConfiguration schema = new CollectionConfiguration(this.files.newFile("schema"), true);
        for (final CollectionSchema field : CollectionSchema.values()) {
            schema.put(field.name(), new Entry(field.name(), false));
        }
        schema.get(CollectionSchema.sku.name()).setEnable(true);
        final Constructor<Fulltext> constructor = Fulltext.class.getDeclaredConstructor(
                File.class, File.class, CollectionConfiguration.class, WebgraphConfiguration.class);
        constructor.setAccessible(true);
        this.index = constructor.newInstance(this.files.getRoot(), this.files.getRoot(), schema, null);
        final SolrConnector connector = (SolrConnector) Proxy.newProxyInstance(
                SolrConnector.class.getClassLoader(), new Class<?>[] {SolrConnector.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "isClosed": return false;
                        case "add":
                            this.writes.incrementAndGet();
                            if (this.entered != null) this.entered.countDown();
                            if (this.release != null && !this.release.await(10, TimeUnit.SECONDS)) throw new IOException("Timed out in test writer");
                            if (this.failWrites) throw new IOException("simulated write failure");
                            this.documents.add(((SolrInputDocument) args[0]).deepCopy());
                            return null;
                        case "clearCaches": return null;
                        default: throw new AssertionError("Unexpected connector call: " + method.getName());
                    }
                });
        final Field instances = Fulltext.class.getDeclaredField("solrInstances");
        instances.setAccessible(true);
        instances.set(this.index, new InstanceMirror() {
            @Override
            public SolrConnector getDefaultMirrorConnector() {
                return connector;
            }
        });
    }

    @After
    public void tearDown() throws Exception {
        if (this.release != null) this.release.countDown();
        if (this.driver != null) {
            this.driver.join(10000);
            assertFalse("Importer did not terminate", this.driver.isAlive());
        }
        JsonListImporter.job = this.previousJob;
    }

    @Test
    public void statusEndpointIsReadOnlyProtectedAndEmptyBeforeFirstImport() throws Exception {
        assertTrue(net.yacy.http.AdminSecurity.isProtectedPath("/api/importstatus_p.json", false, false, true));
        final JSONObject status = new JSONObject(importstatus_p.respond(null, null, null).get("status"));
        assertTrue(status.isNull("job"));
    }

    @Test
    public void sourceIdentityIncludesDirectoryAndNormalizesRelativeComponents() throws Exception {
        final File first = this.files.newFolder("first");
        final File second = this.files.newFolder("second");
        final File source = new File(first, "pack.jsonl");
        Files.writeString(source.toPath(), "");
        final JsonListImporter importer = new JsonListImporter(source, false, false, this.index);
        assertTrue(importer.isSourceFile(source));
        assertTrue(importer.isSourceFile(new File(first, "../first/pack.jsonl")));
        assertFalse(importer.isSourceFile(new File(second, "pack.jsonl")));
    }

    @Test
    public void gzipCountsPhysicalBytesAndKeepsFinalResult() throws Exception {
        final File file = this.files.newFile("pack.jsonl.gz");
        try (final GZIPOutputStream out = new GZIPOutputStream(Files.newOutputStream(file.toPath()))) {
            out.write(("{\"index\":{}}\n{}\n" + row(1) + "\n{\"url\":\"https://example.org/empty\"}\n").getBytes(StandardCharsets.UTF_8));
        }
        final JsonListImporter importer = new JsonListImporter(file, false, false, this.index);
        importer.run();
        final JSONObject snapshot = importer.snapshot();
        assertEquals("COMPLETED", snapshot.getString("state"));
        assertEquals(file.length(), snapshot.getLong("bytesRead"));
        assertEquals(2, snapshot.getLong("read"));
        assertEquals(1, snapshot.getLong("submitted"));
        assertEquals(1, snapshot.getLong("skipped"));
        assertEquals(0, snapshot.getLong("errors"));
        assertEquals(0, snapshot.getInt("queueSize"));
        final long elapsed = importer.runningTime();
        assertEquals(elapsed, importer.snapshot().getLong("elapsedSeconds"));
        final JSONObject api = new JSONObject(importstatus_p.respond(null, null, null).get("status"));
        assertEquals(importer.getJobId(), api.getJSONObject("job").getString("id"));
        assertSame(importer, JsonListImporter.job);
    }

    @Test
    public void synchronousPackRemainsActiveUntilWorkerFinishesAndRejectsAnotherImport() throws Exception {
        this.entered = new CountDownLatch(1);
        this.release = new CountDownLatch(1);
        final JsonListImporter importer = importer(row(1));
        this.driver = new Thread(importer);
        this.driver.start();
        assertTrue(this.entered.await(5, TimeUnit.SECONDS));
        awaitState(importer, JsonListImporter.ImportState.DRAINING);
        assertFalse(importer.isAlive());
        assertTrue(importer.isActive());
        assertEquals(0, importer.snapshot().getLong("submitted"));
        assertEquals(1, importer.snapshot().getInt("activeWorkers"));
        assertEquals(-1, importer.remainingTime());
        final Field progress = JsonListImporter.class.getDeclaredField("lastProgress");
        progress.setAccessible(true);
        progress.setLong(importer, System.nanoTime() - TimeUnit.SECONDS.toNanos(61));
        assertTrue(importer.snapshot().getBoolean("stalled"));
        final JsonListImporter other = importer(row(2));
        assertThrows(IllegalStateException.class, other::start);
        assertSame(importer, JsonListImporter.job);
        final RequestHeader request = new RequestHeader();
        request.put(HeaderFramework.CONNECTION_PROP_CLIENTIP, "127.0.0.1");
        request.put(HeaderFramework.CONNECTION_PROP_PATH, "/IndexImportJsonList_p.html");
        request.put(HeaderFramework.CONNECTION_PROP_METHOD, "POST");
        final serverObjects stop = new serverObjects();
        stop.put("abort", "1");
        stop.put("job", "old-job-id");
        stop.put("transactionToken", TransactionManager.getTransactionToken(request));
        IndexImportJsonList_p.respond(request, stop, null);
        assertEquals(JsonListImporter.ImportState.DRAINING, importer.getImportState());
        stop.put("job", importer.getJobId());
        IndexImportJsonList_p.respond(request, stop, null);
        assertEquals(JsonListImporter.ImportState.STOPPING, importer.getImportState());
        this.release.countDown();
        this.driver.join(5000);
        assertEquals(JsonListImporter.ImportState.ABORTED, importer.getImportState());
        assertEquals(1, importer.snapshot().getLong("submitted"));
        importer.quit();
        assertEquals(JsonListImporter.ImportState.ABORTED, importer.getImportState());
    }

    @Test
    public void stopDoesNotReadRestOfFileAndDrainsAcceptedQueue() throws Exception {
        this.entered = new CountDownLatch(1);
        this.release = new CountDownLatch(1);
        final StringBuilder rows = new StringBuilder();
        for (int i = 0; i < 1000; i++) rows.append(row(i)).append('\n');
        final JsonListImporter importer = importer(rows.toString());
        this.driver = importer;
        importer.start();
        assertTrue(this.entered.await(5, TimeUnit.SECONDS));
        importer.quit();
        assertTrue(importer.isActive());
        this.release.countDown();
        importer.join(5000);
        assertEquals(JsonListImporter.ImportState.ABORTED, importer.getImportState());
        final JSONObject status = importer.snapshot();
        assertTrue(status.getLong("read") < 1000);
        assertEquals(status.getLong("read"), status.getLong("submitted"));
        assertEquals(0, status.getInt("queueSize"));
    }

    @Test
    public void writeFailuresAreCountedAndGroupedWithoutClaimingSuccessOrDeletingSource() throws Exception {
        this.failWrites = true;
        final File file = this.files.newFile("fail.jsonl");
        Files.writeString(file.toPath(), row(1) + "\n" + row(2));
        final JsonListImporter importer = new JsonListImporter(file, false, true, this.index);
        importer.run();
        final JSONObject status = importer.snapshot();
        assertEquals("COMPLETED_WITH_ERRORS", status.getString("state"));
        assertEquals(0, status.getLong("submitted"));
        assertEquals(2, status.getLong("failed"));
        assertEquals(2, status.getLong("errors"));
        assertEquals(1, status.getJSONArray("recentErrors").length());
        assertEquals(2, status.getJSONArray("recentErrors").getJSONObject(0).getLong("occurrences"));
        assertTrue(file.exists());
    }

    @Test
    public void parseFailureStillWaitsForAlreadyAcceptedDocuments() throws Exception {
        this.entered = new CountDownLatch(1);
        this.release = new CountDownLatch(1);
        final JsonListImporter importer = importer(row(1) + "\n{broken");
        this.driver = new Thread(importer);
        this.driver.start();
        assertTrue(this.entered.await(5, TimeUnit.SECONDS));
        awaitState(importer, JsonListImporter.ImportState.DRAINING);
        assertTrue(importer.isActive());
        this.release.countDown();
        this.driver.join(5000);
        final JSONObject status = importer.snapshot();
        assertEquals("FAILED", status.getString("state"));
        assertEquals(1, status.getLong("submitted"));
        assertEquals(1, status.getLong("failed"));
        assertEquals(2, status.getJSONArray("recentErrors").getJSONObject(0).getLong("line"));
    }

    @Test
    public void corruptGzipAndEmptyInputProduceStableResults() throws Exception {
        final File corrupt = this.files.newFile("bad.gz");
        Files.writeString(corrupt.toPath(), "not gzip");
        final JsonListImporter failed = new JsonListImporter(corrupt, false, false, this.index);
        failed.run();
        assertEquals(JsonListImporter.ImportState.FAILED, failed.getImportState());
        assertEquals(1, failed.snapshot().getLong("errors"));
        assertFalse(failed.isActive());
        final JsonListImporter empty = importer("");
        empty.run();
        assertEquals(JsonListImporter.ImportState.COMPLETED, empty.getImportState());
        assertEquals(0, empty.count());
        assertEquals(0, empty.speed());
        assertEquals(0, empty.remainingTime());
    }

    @Test
    public void twentyGiBFileAndLongCountersDoNotOverflow() throws Exception {
        final File file = this.files.newFile("large.jsonl");
        try (final RandomAccessFile sparse = new RandomAccessFile(file, "rw")) {
            sparse.setLength(20L * 1024 * 1024 * 1024);
        }
        final JsonListImporter importer = new JsonListImporter(file, false, false, this.index);
        assertEquals(20L * 1024 * 1024 * 1024, importer.snapshot().getLong("totalBytes"));
        assertEquals(0, importer.speed());
        final Field submitted = JsonListImporter.class.getDeclaredField("submitted");
        submitted.setAccessible(true);
        ((java.util.concurrent.atomic.AtomicLong) submitted.get(importer)).set(3_000_000_000L);
        assertEquals(3_000_000_000L, importer.snapshot().getLong("submitted"));
        assertEquals(Integer.MAX_VALUE, importer.count());
    }

    @Test
    public void readerPreservesSuppliedMetadataRegardlessOfUrlAlias() throws Exception {
        for (final String alias : new String[] {"url", "url_s", "sku"}) {
            final JsonListImporter importer = importer("{\"" + alias + "\":\"https://example.org/a\",\"id\":\"supplied\","
                    + "\"host_s\":\"custom\",\"host_id_s\":\"\",\"text_t\":\"Some imported text\"}");
            importer.run();
        }
        assertEquals(3, this.documents.size());
        for (final SolrInputDocument document : this.documents) {
            assertEquals("supplied", document.getFieldValue("id"));
            assertEquals("custom", document.getFieldValue("host_s"));
            assertEquals("", document.getFieldValue("host_id_s"));
        }
    }

    private JsonListImporter importer(final String contents) throws IOException {
        final File file = this.files.newFile();
        Files.writeString(file.toPath(), contents);
        return new JsonListImporter(file, false, false, this.index);
    }

    private static String row(final int id) {
        return "{\"url\":\"https://example.org/" + id + "\",\"text_t\":\"Imported text äöü\"}";
    }

    private static void awaitState(final JsonListImporter importer, final JsonListImporter.ImportState state) throws Exception {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (importer.getImportState() != state && System.nanoTime() < deadline) Thread.sleep(10);
        assertEquals(state, importer.getImportState());
    }
}
