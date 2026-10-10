package dev.terralense.storage;

import dev.terralense.core.Log;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs all database work off the server thread. */
public final class StorageService implements AutoCloseable {
    private final Database database;
    private final Log log;
    private final ThreadPoolExecutor executor;

    public StorageService(Database database, Log log, int queueCapacity) {
        this.database = database;
        this.log = log;

        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "TerraLense-Storage-" + counter.getAndIncrement());
                // Daemon so a hung query cannot keep the JVM alive at shutdown.
                thread.setDaemon(true);
                thread.setPriority(Thread.NORM_PRIORITY - 1);
                return thread;
            }
        };
        this.executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(Math.max(16, queueCapacity)), factory,
                new ThreadPoolExecutor.AbortPolicy());
    }

    /** Submits read/write work. */
    public <T> CompletableFuture<T> submit(SqlSupplier<T> work) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    future.complete(work.getChecked());
                } catch (SQLException ex) {
                    log.error("Database operation failed.", ex);
                    future.completeExceptionally(ex);
                } catch (RuntimeException ex) {
                    log.error("Unexpected error during a database operation.", ex);
                    future.completeExceptionally(ex);
                }
            });
        } catch (RejectedExecutionException ex) {
            // Queue full or shutting down: fail this operation rather than block the caller or grow
            // memory.
            log.warn("Storage queue is saturated; a database operation was dropped.");
            future.completeExceptionally(ex);
        }
        return future;
    }

    /** Submits work that produces no result. */
    public CompletableFuture<Void> submitVoid(SqlRunnable work) {
        return submit(() -> {
            work.run();
            return null;
        });
    }

    /**
     * Convenience wrapper that logs and swallows failures, for fire-and-forget writes where the
     * caller has nothing useful to do with an error.
     */
    public void submitAndForget(SqlRunnable work, String description) {
        submitVoid(work).exceptionally(throwable -> {
            log.debug(() -> "storage task '" + description + "' failed: " + throwable.getMessage());
            return null;
        });
    }

    public int queuedTasks() {
        return executor.getQueue().size();
    }

    public Database database() {
        return database;
    }

    /** Drains outstanding work and stops the executor. */
    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5L, TimeUnit.SECONDS)) {
                log.warn("Storage tasks did not finish within 5 seconds; forcing shutdown.");
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        database.close();
    }

    /** Database work returning a value. */
    @FunctionalInterface
    public interface SqlSupplier<T> {
        T getChecked() throws SQLException;
    }

    /** Database work returning nothing. */
    @FunctionalInterface
    public interface SqlRunnable {
        void run() throws SQLException;
    }
}
