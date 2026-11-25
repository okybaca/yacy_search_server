package net.yacy.kelondro.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.http.HttpHost;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.sun.net.httpserver.HttpServer;

import net.yacy.cora.protocol.http.ProxySettings;

/** Local HTTP fixtures exercise the real download and publication path without external requests. */
public class HFClientTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private HttpServer server;
    private HttpRoutePlanner previousRoutePlanner;

    @Before
    public void startServer() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.previousRoutePlanner = ProxySettings.RoutePlanner;
        // Route the fixed Hugging Face origin to this local HTTP fixture, then restore global state.
        final HttpHost local = new HttpHost("127.0.0.1", this.server.getAddress().getPort(), "http");
        ProxySettings.RoutePlanner = (target, request, context) -> new HttpRoute(local);
        this.server.start();
    }

    @After
    public void stopServer() {
        ProxySettings.RoutePlanner = this.previousRoutePlanner;
        if (this.server != null) this.server.stop(0);
    }

    @Test
    public void publishesCompletedFileAndPreservesExistingFileUntilCompletion() throws Exception {
        final File destination = this.temporaryFolder.newFile("pack.jsonl.gz");
        final byte[] previous = "previous pack".getBytes(StandardCharsets.UTF_8);
        final byte[] contents = "new pack contents".getBytes(StandardCharsets.UTF_8);
        Files.write(destination.toPath(), previous);
        final AtomicReference<byte[]> observed = new AtomicReference<>();
        final AtomicReference<String> request = new AtomicReference<>();
        this.server.createContext("/", exchange -> {
            try {
                request.set(exchange.getRequestURI().toString());
                observed.set(Files.readAllBytes(destination.toPath()));
                exchange.sendResponseHeaders(200, contents.length);
                exchange.getResponseBody().write(contents);
            } finally {
                exchange.close();
            }
        });

        HFClient.downloadFile("yacy/test", "pack.jsonl.gz", destination);

        assertEquals("/datasets/yacy/test/resolve/main/pack.jsonl.gz?download=true", request.get());
        assertArrayEquals(previous, observed.get());
        assertArrayEquals(contents, Files.readAllBytes(destination.toPath()));
        assertArrayEquals(new String[] {"pack.jsonl.gz"}, this.temporaryFolder.getRoot().list());
    }

    @Test
    public void followsRedirectsAndCreatesParentDirectoriesForChunkedDownload() throws Exception {
        final byte[] contents = "chunked pack".getBytes(StandardCharsets.UTF_8);
        final File destination = new File(this.temporaryFolder.getRoot(), "nested/pack.jsonl.gz");
        this.server.createContext("/datasets/", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + this.server.getAddress().getPort() + "/pack");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        this.server.createContext("/pack", exchange -> {
            try {
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().write(contents);
            } finally {
                exchange.close();
            }
        });

        HFClient.downloadFile("yacy/test", "pack.jsonl.gz", destination);

        assertArrayEquals(contents, Files.readAllBytes(destination.toPath()));
        assertArrayEquals(new String[] {"pack.jsonl.gz"}, destination.getParentFile().list());
    }

    @Test
    public void rejectsHttpErrorWithoutReplacingExistingPack() throws Exception {
        final File destination = this.temporaryFolder.newFile("pack.jsonl.gz");
        final byte[] previous = "previous pack".getBytes(StandardCharsets.UTF_8);
        Files.write(destination.toPath(), previous);
        this.server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });

        final IOException failure = assertThrows(IOException.class,
                () -> HFClient.downloadFile("yacy/test", "pack.jsonl.gz", destination));

        assertTrue(failure.getMessage(), failure.getMessage().contains("HTTP 404"));
        assertArrayEquals(previous, Files.readAllBytes(destination.toPath()));
        assertArrayEquals(new String[] {"pack.jsonl.gz"}, this.temporaryFolder.getRoot().list());
    }

    @Test
    public void rejectsEmptyResponseAndRemovesTemporaryFile() throws Exception {
        final File destination = new File(this.temporaryFolder.getRoot(), "pack.jsonl.gz");
        this.server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        final IOException failure = assertThrows(IOException.class,
                () -> HFClient.downloadFile("yacy/test", "pack.jsonl.gz", destination));

        assertTrue(failure.getMessage(), failure.getMessage().contains("empty"));
        assertFalse(destination.exists());
        assertEquals(0, this.temporaryFolder.getRoot().list().length);
    }

    @Test
    public void rejectsInterruptedTwentyGiBResponseAndRemovesTemporaryFile() throws Exception {
        final File destination = new File(this.temporaryFolder.getRoot(), "pack.jsonl.gz");
        final long advertisedLength = 20L * 1024 * 1024 * 1024;
        this.server.createContext("/", exchange -> {
            try {
                exchange.sendResponseHeaders(200, advertisedLength);
                exchange.getResponseBody().write(new byte[1024]);
                exchange.getResponseBody().flush();
            } finally {
                exchange.close();
            }
        });

        final IOException failure = assertThrows(IOException.class,
                () -> HFClient.downloadFile("yacy/test", "pack.jsonl.gz", destination));

        assertTrue(failure.getMessage(), failure.getMessage().contains("Content-Length"));
        assertFalse(destination.exists());
        assertEquals(0, this.temporaryFolder.getRoot().list().length);
    }

    @Test
    public void removesTemporaryFileWhenDestinationCannotBeReplaced() throws Exception {
        final File destination = this.temporaryFolder.newFolder("pack.jsonl.gz");
        Files.write(new File(destination, "keep").toPath(), new byte[] {7});
        this.server.createContext("/", exchange -> {
            try {
                exchange.sendResponseHeaders(200, 1);
                exchange.getResponseBody().write(42);
            } finally {
                exchange.close();
            }
        });

        assertThrows(IOException.class, () -> HFClient.downloadFile("yacy/test", "pack.jsonl.gz", destination));

        assertTrue(new File(destination, "keep").isFile());
        assertArrayEquals(new String[] {"pack.jsonl.gz"}, this.temporaryFolder.getRoot().list());
    }

    @Test
    public void keepsByteArrayDownloadCompatible() throws Exception {
        final byte[] contents = "small pack".getBytes(StandardCharsets.UTF_8);
        this.server.createContext("/", exchange -> {
            try {
                exchange.sendResponseHeaders(200, contents.length);
                exchange.getResponseBody().write(contents);
            } finally {
                exchange.close();
            }
        });

        assertArrayEquals(contents, HFClient.downloadFile("yacy/test", "pack.jsonl.gz"));
    }

    @Test
    public void streamsBeyondByteArrayLimit() throws Exception {
        final long length = (long) Integer.MAX_VALUE + 65537;
        final byte[] block = new byte[64 * 1024];
        Arrays.fill(block, (byte) 0xa5);
        this.server.createContext("/", exchange -> {
            try {
                exchange.sendResponseHeaders(200, length);
                try (final OutputStream output = exchange.getResponseBody()) {
                    long remaining = length;
                    while (remaining > 0) {
                        final int count = (int) Math.min(block.length, remaining);
                        output.write(block, 0, count);
                        remaining -= count;
                    }
                }
            } finally {
                exchange.close();
            }
        });
        final File destination = new File(this.temporaryFolder.getRoot(), "pack.jsonl.gz");

        HFClient.downloadFile("yacy/test", "pack.jsonl.gz", destination);

        assertEquals(length, destination.length());
        try (final RandomAccessFile file = new RandomAccessFile(destination, "r")) {
            assertEquals(0xa5, file.read());
            file.seek(Integer.MAX_VALUE);
            assertEquals(0xa5, file.read());
            file.seek(length - 1);
            assertEquals(0xa5, file.read());
            assertEquals(-1, file.read());
        }
        assertArrayEquals(new String[] {"pack.jsonl.gz"}, this.temporaryFolder.getRoot().list());
    }
}
