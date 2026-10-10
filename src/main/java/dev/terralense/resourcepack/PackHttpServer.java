package dev.terralense.resourcepack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.terralense.core.Log;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Minimal HTTP server that serves the generated resource pack. */
public final class PackHttpServer implements AutoCloseable {
    /** Path the pack is served from. */
    private static final String PACK_PATH = "/terralense/pack.zip";

    /** Small pool: requests are rare (one per player join) and short. */
    private static final int WORKER_THREADS = 2;

    private static final int SOCKET_BACKLOG = 16;

    private final Log log;
    private final AtomicLong servedCount = new AtomicLong();

    private HttpServer server;
    private ThreadPoolExecutor executor;
    private volatile Path packFile;
    private volatile int boundPort;

    public PackHttpServer(Log log) {
        this.log = log;
    }

    /**
     * Starts the server.
     *
     * @param bindAddress address to bind to, or blank for all interfaces
     * @param port TCP port; 0 picks a free one
     * @param file the pack to serve
     * @return true if the server started
     */
    public boolean start(String bindAddress, int port, Path file) {
        if (server != null) {
            return true;
        }
        if (!Files.isRegularFile(file)) {
            log.error("Cannot start the pack HTTP server: " + file + " does not exist.");
            return false;
        }
        this.packFile = file;

        try {
            InetSocketAddress address = (bindAddress == null || bindAddress.isBlank())
                    ? new InetSocketAddress(port)
                    : new InetSocketAddress(bindAddress, port);

            server = HttpServer.create(address, SOCKET_BACKLOG);
            executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(WORKER_THREADS,
                    daemonFactory());
            server.setExecutor(executor);
            server.createContext(PACK_PATH, this::handlePackRequest);
            server.start();

            boundPort = server.getAddress().getPort();
            log.info("Pack HTTP server listening on port " + boundPort + PACK_PATH + ".");
            return true;
        } catch (IOException | RuntimeException ex) {
            // A port clash must not stop the plugin; the operator can host the pack themselves
            // instead.
            log.error("Could not start the pack HTTP server on port " + port
                    + ". Set resource-pack.url to host the pack yourself, or choose "
                    + "another port.", ex);
            closeQuietly();
            return false;
        }
    }

    private static ThreadFactory daemonFactory() {
        AtomicInteger counter = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, "TerraLense-PackHttp-" + counter.getAndIncrement());
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        };
    }

    /** Serves the pack. */
    private void handlePackRequest(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            boolean head = "HEAD".equalsIgnoreCase(method);
            if (!head && !"GET".equalsIgnoreCase(method)) {
                exchange.getResponseHeaders().add("Allow", "GET, HEAD");
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            Path file = packFile;
            if (file == null || !Files.isRegularFile(file)) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            byte[] body = Files.readAllBytes(file);
            exchange.getResponseHeaders().add("Content-Type", "application/zip");
            exchange.getResponseHeaders().add("Cache-Control", "public, max-age=86400");
            // Discourage MIME sniffing on an endpoint that only ever serves a zip.
            exchange.getResponseHeaders().add("X-Content-Type-Options", "nosniff");

            if (head) {
                exchange.getResponseHeaders().add("Content-Length", Long.toString(body.length));
                exchange.sendResponseHeaders(200, -1);
                return;
            }

            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
            long served = servedCount.incrementAndGet();
            log.debug(() -> "served the resource pack (" + served + " total)");
        } catch (IOException | RuntimeException ex) {
            log.debug(() -> "pack request failed: " + ex.getMessage());
            try {
                exchange.sendResponseHeaders(500, -1);
            } catch (IOException ignored) {
                // Client already gone; nothing useful left to do.
            }
        } finally {
            exchange.close();
        }
    }

    /** Builds the URL clients should download from. */
    public String urlFor(String publicHost) {
        return "http://" + publicHost + ":" + boundPort + PACK_PATH;
    }

    public boolean isRunning() {
        return server != null;
    }

    public int port() {
        return boundPort;
    }

    public long servedCount() {
        return servedCount.get();
    }

    /** Points the server at a regenerated pack without restarting it. */
    public void updateFile(Path file) {
        this.packFile = file;
    }

    @Override
    public void close() {
        closeQuietly();
    }

    private void closeQuietly() {
        if (server != null) {
            // Zero delay: outstanding requests are a few hundred KB at most.
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(1L, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            executor = null;
        }
    }
}
