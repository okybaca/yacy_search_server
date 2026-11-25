package net.yacy.search.index;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import net.yacy.cora.federate.solr.connector.SolrConnector;
import net.yacy.cora.federate.solr.instance.InstanceMirror;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.search.schema.CollectionSchema;

/** Verify logging on the real write path without opening a Solr index. */
public class FulltextLoggingTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final Logger logger = Logger.getLogger("Fulltext");
    private final List<LogRecord> records = new ArrayList<>();
    private final Handler handler = new Handler() {
        @Override
        public void publish(final LogRecord record) {
            FulltextLoggingTest.this.records.add(record);
        }

        @Override
        public void flush() { }

        @Override
        public void close() { }
    };

    private Level previousLevel;
    private boolean previousParentHandlers;
    private boolean previousBackgroundRunner;
    private Fulltext fulltext;
    private SolrException writeFailure;
    private int writes;
    private boolean connectorClosed;

    @Before
    public void setUp() throws Exception {
        this.previousLevel = this.logger.getLevel();
        this.previousParentHandlers = this.logger.getUseParentHandlers();
        this.previousBackgroundRunner = ConcurrentLog.backgroundRunner;
        ConcurrentLog.backgroundRunner = false;
        this.logger.setLevel(Level.INFO);
        this.logger.setUseParentHandlers(false);
        this.handler.setLevel(Level.ALL);
        this.logger.addHandler(this.handler);

        final SolrConnector connector = (SolrConnector) Proxy.newProxyInstance(
                SolrConnector.class.getClassLoader(), new Class<?>[] {SolrConnector.class},
                (proxy, method, arguments) -> {
                    switch (method.getName()) {
                        case "isClosed": return this.connectorClosed;
                        case "clearCaches": return null;
                        case "add":
                            this.writes++;
                            if (this.writeFailure != null) throw this.writeFailure;
                            return null;
                        default: throw new AssertionError("Unexpected connector call: " + method.getName());
                    }
                });
        this.fulltext = new Fulltext(this.temporaryFolder.getRoot(), this.temporaryFolder.getRoot(), null, null);
        this.fulltext.setUseWebgraph(true);
        final Field instances = Fulltext.class.getDeclaredField("solrInstances");
        instances.setAccessible(true);
        instances.set(this.fulltext, new InstanceMirror() {
            @Override
            public SolrConnector getDefaultMirrorConnector() {
                return connector;
            }

            @Override
            public SolrConnector getGenericMirrorConnector(final String coreName) {
                return connector;
            }
        });
    }

    @After
    public void restoreLogging() {
        this.logger.removeHandler(this.handler);
        this.logger.setLevel(this.previousLevel);
        this.logger.setUseParentHandlers(this.previousParentHandlers);
        ConcurrentLog.backgroundRunner = this.previousBackgroundRunner;
    }

    @Test
    public void successfulWritesAtInfoDoNotFormatOrLogPerDocumentMessages() throws IOException {
        final SolrInputDocument document = document();
        document.setField(CollectionSchema.httpstatus_i.getSolrFieldName(), new Object() {
            @Override
            public String toString() {
                throw new AssertionError("Disabled success log must not format document values");
            }
        });

        this.fulltext.putDocument(document);
        this.fulltext.putEdges(Collections.singletonList(document));

        assertEquals(2, this.writes);
        assertTrue(this.records.isEmpty());
    }

    @Test
    public void fineEnablesSuccessfulDocumentAndWebgraphDiagnostics() throws IOException {
        this.logger.setLevel(Level.FINE);
        final SolrInputDocument document = document();

        this.fulltext.putDocument(document);
        this.fulltext.putEdges(Collections.singletonList(document));

        assertEquals(2, this.writes);
        assertEquals(2, this.records.size());
        assertEquals(Level.FINE, this.records.get(0).getLevel());
        assertTrue(this.records.get(0).getMessage().contains("event=index.document"));
        assertEquals(Level.FINE, this.records.get(1).getLevel());
        assertTrue(this.records.get(1).getMessage().contains("event=index.webgraph"));
    }

    @Test
    public void failedWritesStillLogWarningsAndPropagateTheirCause() {
        this.writeFailure = new SolrException(SolrException.ErrorCode.SERVER_ERROR, "write failed");
        final SolrInputDocument document = document();

        final IOException documentFailure = assertThrows(IOException.class, () -> this.fulltext.putDocument(document));
        final IOException graphFailure = assertThrows(IOException.class,
                () -> this.fulltext.putEdges(Collections.singletonList(document)));

        assertSame(this.writeFailure, documentFailure.getCause());
        assertSame(this.writeFailure, graphFailure.getCause());
        assertEquals(2, this.writes);
        assertEquals(2, this.records.size());
        for (final LogRecord record : this.records) {
            assertEquals(Level.WARNING, record.getLevel());
            assertTrue(record.getMessage().contains("result=failure"));
            assertTrue(record.getMessage().contains("write failed"));
        }
    }

    @Test
    public void closedConnectorDoesNotReportSuccessfulSubmission() {
        this.connectorClosed = true;
        assertThrows(IOException.class, () -> this.fulltext.putDocument(document()));
        assertEquals(0, this.writes);
    }

    private static SolrInputDocument document() {
        final SolrInputDocument document = new SolrInputDocument();
        document.setField(CollectionSchema.id.getSolrFieldName(), "000000000000");
        document.setField(CollectionSchema.sku.getSolrFieldName(), "https://example.com/");
        document.setField(CollectionSchema.host_s.getSolrFieldName(), "example.com");
        document.setField(CollectionSchema.httpstatus_i.getSolrFieldName(), 200);
        return document;
    }
}
