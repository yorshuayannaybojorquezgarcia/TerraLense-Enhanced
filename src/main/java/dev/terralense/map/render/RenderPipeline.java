package dev.terralense.map.render;

import dev.terralense.core.Log;
import dev.terralense.performance.Metrics;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded worker pool for CPU rendering. */
public final class RenderPipeline implements AutoCloseable {
    private final ThreadPoolExecutor executor;
    private final Metrics metrics;
    private final Log log;

    public RenderPipeline(int threads, int queueCapacity, Metrics metrics, Log log) {
        this.metrics = metrics;
        this.log = log;

        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "TerraLense-Render-" + counter.getAndIncrement());
                thread.setDaemon(true);
                // Below normal: gameplay threads must always win contention.
                thread.setPriority(Thread.NORM_PRIORITY - 1);
                return thread;
            }
        };

        this.executor = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(Math.max(8, queueCapacity)), factory,
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * Submits a render task.
     *
     * @return false when the queue is saturated and the frame was dropped
     */
    public boolean submit(Runnable task) {
        try {
            executor.execute(() -> {
                long started = System.nanoTime();
                try {
                    task.run();
                    metrics.recordRender(System.nanoTime() - started);
                } catch (RuntimeException | Error ex) {
                    // A failed minimap frame must never take down anything else.
                    metrics.recordFailure();
                    log.error("Minimap render failed.", ex);
                }
            });
            return true;
        } catch (RejectedExecutionException ex) {
            metrics.recordDropped();
            return false;
        }
    }

    public int queueSize() {
        return executor.getQueue().size();
    }

    public int queueCapacity() {
        return executor.getQueue().size() + executor.getQueue().remainingCapacity();
    }

    public int threadCount() {
        return executor.getCorePoolSize();
    }

    public long activeRenders() {
        return executor.getActiveCount();
    }

    @Override
    public void close() {
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(2L, TimeUnit.SECONDS)) {
                log.debug("render workers did not stop within 2 seconds");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
