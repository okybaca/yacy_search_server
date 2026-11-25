package net.yacy.kelondro.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.http.HttpHost;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import net.yacy.cora.protocol.http.ProxySettings;

public class HFClientMetadataTest {
    private HttpServer server;
    private HttpRoutePlanner previousRoutePlanner;
    private final AtomicInteger requests = new AtomicInteger();
    private String response;
    private int status = 200;
    private String query;

    @Before
    public void startServer() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.previousRoutePlanner = ProxySettings.RoutePlanner;
        final HttpHost local = new HttpHost("127.0.0.1", this.server.getAddress().getPort(), "http");
        ProxySettings.RoutePlanner = (target, request, context) -> new HttpRoute(local);
        this.server.createContext("/", exchange -> {
            this.requests.incrementAndGet();
            this.query = exchange.getRequestURI().getQuery();
            send(exchange, this.response, this.status);
        });
        this.server.start();
    }

    @After
    public void stopServer() {
        ProxySettings.RoutePlanner = this.previousRoutePlanner;
        this.server.stop(0);
    }

    @Test
    public void listsSizesBeyondTwoGiBAndFallsBackToLfsSize() throws Exception {
        this.response = "{\"siblings\":["
                + "{\"rfilename\":\"large.jsonl.gz\",\"size\":21474836480},"
                + "{\"rfilename\":\"lfs.jsonl.gz\",\"lfs\":{\"size\":4405664395}},"
                + "{\"rfilename\":\"empty.jsonl\",\"size\":0},"
                + "{\"rfilename\":\"unknown.jsonl\"},"
                + "{\"path\":\"fallback.jsonl\",\"size\":42}]}";

        final List<HFClient.FileMetadata> files = HFClient.listFilesWithMetadata("yacy/test", HFClient.RepoType.DATASET);

        assertEquals(Arrays.asList(new HFClient.FileMetadata("large.jsonl.gz", 21_474_836_480L),
                new HFClient.FileMetadata("lfs.jsonl.gz", 4_405_664_395L), new HFClient.FileMetadata("empty.jsonl", 0),
                new HFClient.FileMetadata("unknown.jsonl", -1), new HFClient.FileMetadata("fallback.jsonl", 42)), files);
        assertEquals("blobs=true", this.query);
        assertEquals(1, this.requests.get());
    }

    @Test
    public void invalidSizesStayUnknownInsteadOfBecomingZeroOrOverflowing() throws Exception {
        this.response = "{\"files\":[{\"path\":\"null\",\"size\":null},{\"path\":\"text\",\"size\":\"invalid\"},"
                + "{\"path\":\"negative\",\"size\":-2},{\"path\":\"fraction\",\"size\":1.5},"
                + "{\"path\":\"overflow\",\"size\":9223372036854775808},{\"size\":3}]}";
        final List<HFClient.FileMetadata> files = HFClient.listFilesWithMetadata("yacy/test", HFClient.RepoType.DATASET);
        assertEquals(5, files.size());
        for (final HFClient.FileMetadata file : files) assertEquals(-1, file.size());
    }

    @Test
    public void preservesLegacyFileNamesAndJsonlFilter() throws Exception {
        this.response = "{\"siblings\":[{\"rfilename\":\"README.md\"},{\"rfilename\":\"pack.jsonl.gz\"},"
                + "{\"rfilename\":\"pack.jsonlist\"}]}";
        assertEquals(Arrays.asList("pack.jsonl.gz", "pack.jsonlist"), HFClient.listFiles("yacy/test", true));
        assertEquals(Arrays.asList("README.md", "pack.jsonl.gz", "pack.jsonlist"), HFClient.listFiles("yacy/test", false));
    }

    @Test
    public void aggregatesSizesInOneRequestPerRepositoryWithoutDownloadingFiles() throws Exception {
        this.server.removeContext("/");
        this.server.createContext("/", exchange -> {
            this.requests.incrementAndGet();
            final String path = exchange.getRequestURI().getPath();
            final String data;
            if (path.equals("/api/datasets")) {
                data = "[{\"id\":\"yacy/first\"},{\"id\":\"yacy/second\"}]";
            } else {
                assertEquals("blobs=true", exchange.getRequestURI().getQuery());
                data = path.endsWith("first") ? "{\"siblings\":[{\"rfilename\":\"pack.jsonl.gz\",\"size\":21474836480}]}"
                        : "{\"siblings\":[{\"rfilename\":\"README.md\",\"size\":500}]}";
            }
            send(exchange, data, 200);
        });
        final Map<String, List<HFClient.FileMetadata>> packs = HFClient.getAllPacksWithMetadata(true);
        assertEquals(1, packs.size());
        assertEquals(21_474_836_480L, packs.get("yacy/first").get(0).size());
        assertEquals(3, this.requests.get());
        assertEquals(Map.of("yacy/first", List.of("pack.jsonl.gz")), HFClient.getAllPacks(true));
        assertEquals(6, this.requests.get());
    }

    @Test
    public void reportsRepositoryHttpFailures() {
        this.status = 404;
        this.response = "{\"error\":\"not found\"}";
        final IOException error = assertThrows(IOException.class,
                () -> HFClient.listFilesWithMetadata("yacy/test", HFClient.RepoType.DATASET));
        assertTrue(error.getMessage().contains("404"));
    }

    private static void send(final HttpExchange exchange, final String json, final int status) throws IOException {
        try {
            final byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        } finally {
            exchange.close();
        }
    }
}
