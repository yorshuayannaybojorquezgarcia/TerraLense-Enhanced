package dev.terralense.resourcepack;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.terralense.core.Log;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the built-in pack server over a real socket. */
class PackHttpServerTest {
    private static final Log LOG = quietLog();

    private PackHttpServer server;

    private static Log quietLog() {
        Logger logger = Logger.getLogger("PackHttpServerTest");
        // Keep expected error paths from cluttering test output.
        logger.setLevel(Level.OFF);
        return new Log(logger);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    private Path writePack(Path dir) throws IOException {
        Path file = dir.resolve("TerraLense-Pack.zip");
        Files.write(file, "PK\u0003\u0004fake-pack-bytes".getBytes(StandardCharsets.UTF_8));
        return file;
    }

    /** Starts on an OS-assigned port so tests never collide with a real service. */
    private PackHttpServer started(Path file) {
        server = new PackHttpServer(LOG);
        assertTrue(server.start("127.0.0.1", 0, file), "server should start");
        return server;
    }

    private HttpURLConnection open(String path, String method) throws IOException {
        URI uri = URI.create("http://127.0.0.1:" + server.port() + path);
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(5_000);
        return connection;
    }

    @Test
    @DisplayName("serves the pack bytes verbatim")
    void servesPackBytes(@TempDir Path dir) throws IOException {
        Path file = writePack(dir);
        started(file);

        HttpURLConnection connection = open("/terralense/pack.zip", "GET");
        assertEquals(200, connection.getResponseCode());
        assertEquals("application/zip", connection.getHeaderField("Content-Type"));

        byte[] served;
        try (var in = connection.getInputStream()) {
            served = in.readAllBytes();
        }
        // Byte-exactness matters: the client verifies the pack's SHA-1.
        assertArrayEquals(Files.readAllBytes(file), served);
    }

    @Test
    @DisplayName("picks a free port when asked for port 0")
    void bindsEphemeralPort(@TempDir Path dir) throws IOException {
        started(writePack(dir));
        assertTrue(server.port() > 0);
        assertTrue(server.isRunning());
    }

    @Test
    @DisplayName("HEAD returns metadata without a body")
    void headReturnsNoBody(@TempDir Path dir) throws IOException {
        Path file = writePack(dir);
        started(file);

        HttpURLConnection connection = open("/terralense/pack.zip", "HEAD");
        assertEquals(200, connection.getResponseCode());
        assertEquals(Long.toString(Files.size(file)),
                connection.getHeaderField("Content-Length"));
    }

    @Test
    @DisplayName("write methods are rejected, keeping the endpoint read-only")
    void rejectsWriteMethods(@TempDir Path dir) throws IOException {
        started(writePack(dir));
        for (String method : new String[] {"POST", "PUT", "DELETE"}) {
            HttpURLConnection connection = open("/terralense/pack.zip", method);
            connection.setDoOutput(!method.equals("DELETE"));
            if (connection.getDoOutput()) {
                connection.getOutputStream().write(new byte[] {1});
            }
            assertEquals(405, connection.getResponseCode(), method + " should be rejected");
        }
    }

    @Test
    @DisplayName("directory traversal cannot escape to other files")
    void rejectsTraversal(@TempDir Path dir) throws IOException {
        // The handler never derives a path from the request, so traversal is structurally
        // impossible.
        Files.writeString(dir.resolve("secret.txt"), "must-not-be-served");
        started(writePack(dir));

        for (String path : new String[] {
                "/terralense/pack.zip/../secret.txt",
                "/terralense/../../secret.txt",
                "/terralense/pack.zip/..%2fsecret.txt"}) {
            HttpURLConnection connection = open(path, "GET");
            int code = connection.getResponseCode();
            String body = readBodyQuietly(connection);
            assertFalse(body.contains("must-not-be-served"),
                    "traversal leaked a file via " + path);
            assertTrue(code == 200 || code == 404,
                    "unexpected status " + code + " for " + path);
        }
    }

    @Test
    @DisplayName("unrelated paths are not served")
    void unknownPathsAreNotServed(@TempDir Path dir) throws IOException {
        started(writePack(dir));
        HttpURLConnection connection = open("/", "GET");
        assertEquals(404, connection.getResponseCode());
    }

    @Test
    @DisplayName("a missing pack file yields 404 rather than an error")
    void missingFileIsNotFound(@TempDir Path dir) throws IOException {
        Path file = writePack(dir);
        started(file);
        Files.delete(file);

        HttpURLConnection connection = open("/terralense/pack.zip", "GET");
        assertEquals(404, connection.getResponseCode());
    }

    @Test
    @DisplayName("refuses to start when the pack does not exist")
    void refusesMissingPack(@TempDir Path dir) {
        server = new PackHttpServer(LOG);
        assertFalse(server.start("127.0.0.1", 0, dir.resolve("absent.zip")));
        assertFalse(server.isRunning());
    }

    @Test
    @DisplayName("a port clash is reported instead of throwing")
    void handlesPortClash(@TempDir Path dir) throws IOException {
        Path file = writePack(dir);
        started(file);

        PackHttpServer second = new PackHttpServer(LOG);
        try {
            assertFalse(second.start("127.0.0.1", server.port(), file),
                    "binding an occupied port should fail gracefully");
        } finally {
            second.close();
        }
    }

    @Test
    @DisplayName("download counter tracks served requests")
    void countsDownloads(@TempDir Path dir) throws IOException, InterruptedException {
        started(writePack(dir));
        assertEquals(0L, server.servedCount());

        for (int i = 0; i < 3; i++) {
            try (var in = open("/terralense/pack.zip", "GET").getInputStream()) {
                in.readAllBytes();
            }
        }

        // The counter is incremented on the server's worker thread after the response body is
        // written, so the client can finish reading before that happens.
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (server.servedCount() < 3L && System.nanoTime() < deadline) {
            Thread.sleep(20L);
        }
        assertEquals(3L, server.servedCount());
    }

    @Test
    @DisplayName("the URL uses the configured public host and bound port")
    void buildsPublicUrl(@TempDir Path dir) throws IOException {
        started(writePack(dir));
        assertEquals("http://play.example.com:" + server.port() + "/terralense/pack.zip",
                server.urlFor("play.example.com"));
    }

    @Test
    @DisplayName("updateFile serves a regenerated pack without a restart")
    void updateFileSwapsContent(@TempDir Path dir) throws IOException {
        Path original = writePack(dir);
        started(original);

        Path replacement = dir.resolve("new-pack.zip");
        Files.write(replacement, "PK\u0003\u0004replacement".getBytes(StandardCharsets.UTF_8));
        server.updateFile(replacement);

        try (var in = open("/terralense/pack.zip", "GET").getInputStream()) {
            assertArrayEquals(Files.readAllBytes(replacement), in.readAllBytes());
        }
    }

    @Test
    @DisplayName("close releases the port for rebinding")
    void closeReleasesPort(@TempDir Path dir) throws IOException {
        Path file = writePack(dir);
        started(file);
        int port = server.port();
        server.close();
        assertFalse(server.isRunning());

        // Rebinding the same port must now succeed, which is what makes a plugin reload safe.
        PackHttpServer rebound = new PackHttpServer(LOG);
        try {
            assertTrue(rebound.start("127.0.0.1", port, file), "port was not released");
        } finally {
            rebound.close();
        }
    }

    private static String readBodyQuietly(HttpURLConnection connection) {
        try (var in = connection.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        }
    }
}
