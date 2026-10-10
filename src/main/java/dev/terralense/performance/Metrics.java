package dev.terralense.performance;

import java.util.concurrent.atomic.AtomicLong;

/** Runtime counters and timings for {@code /terralense status} and {@code debug}. */
public final class Metrics {
    private static final int SAMPLE_WINDOW = 128;

    private final AtomicLong rendersCompleted = new AtomicLong();
    private final AtomicLong rendersFailed = new AtomicLong();
    private final AtomicLong rendersDropped = new AtomicLong();
    private final AtomicLong updatesSkipped = new AtomicLong();
    private final AtomicLong transformOnlyUpdates = new AtomicLong();
    private final AtomicLong terrainResamples = new AtomicLong();

    private final long[] renderNanos = new long[SAMPLE_WINDOW];
    private final AtomicLong sampleCursor = new AtomicLong();

    public void recordRender(long durationNanos) {
        rendersCompleted.incrementAndGet();
        // Ring buffer: a lost sample under contention is acceptable for a diagnostic average, and
        // it keeps this allocation-free.
        int slot = (int) (sampleCursor.getAndIncrement() % SAMPLE_WINDOW);
        renderNanos[slot] = durationNanos;
    }

    public void recordFailure() {
        rendersFailed.incrementAndGet();
    }

    public void recordDropped() {
        rendersDropped.incrementAndGet();
    }

    public void recordSkip() {
        updatesSkipped.incrementAndGet();
    }

    public void recordTransformOnly() {
        transformOnlyUpdates.incrementAndGet();
    }

    public void recordTerrainResample() {
        terrainResamples.incrementAndGet();
    }

    public long rendersCompleted() {
        return rendersCompleted.get();
    }

    public long rendersFailed() {
        return rendersFailed.get();
    }

    public long rendersDropped() {
        return rendersDropped.get();
    }

    public long updatesSkipped() {
        return updatesSkipped.get();
    }

    public long transformOnlyUpdates() {
        return transformOnlyUpdates.get();
    }

    public long terrainResamples() {
        return terrainResamples.get();
    }

    /** Mean render time in milliseconds over the recent window. */
    public double averageRenderMillis() {
        long total = 0L;
        int count = 0;
        long observed = Math.min(sampleCursor.get(), SAMPLE_WINDOW);
        for (int i = 0; i < observed; i++) {
            long value = renderNanos[i];
            if (value > 0L) {
                total += value;
                count++;
            }
        }
        return count == 0 ? 0.0D : total / (double) count / 1_000_000.0D;
    }

    /** Slowest render in the recent window, in milliseconds. */
    public double peakRenderMillis() {
        long peak = 0L;
        long observed = Math.min(sampleCursor.get(), SAMPLE_WINDOW);
        for (int i = 0; i < observed; i++) {
            peak = Math.max(peak, renderNanos[i]);
        }
        return peak / 1_000_000.0D;
    }

    /** Fraction of update ticks that did no rendering work. */
    public double skipRate() {
        long skipped = updatesSkipped.get();
        long total = skipped + transformOnlyUpdates.get() + terrainResamples.get();
        return total == 0L ? 0.0D : (double) skipped / total;
    }

    public void reset() {
        rendersCompleted.set(0L);
        rendersFailed.set(0L);
        rendersDropped.set(0L);
        updatesSkipped.set(0L);
        transformOnlyUpdates.set(0L);
        terrainResamples.set(0L);
        sampleCursor.set(0L);
        java.util.Arrays.fill(renderNanos, 0L);
    }
}
