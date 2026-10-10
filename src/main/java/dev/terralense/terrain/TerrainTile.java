package dev.terralense.terrain;

/** Immutable per-chunk terrain summary: the only terrain data the renderer needs. */
public final class TerrainTile {
    /** Chunk edge length in blocks. */
    public static final int SIZE = 16;

    private final ChunkKey key;
    private final byte[] categories;
    private final short[] heights;
    private final long capturedAtNanos;

    private TerrainTile(ChunkKey key, byte[] categories, short[] heights, long capturedAtNanos) {
        this.key = key;
        this.categories = categories;
        this.heights = heights;
        this.capturedAtNanos = capturedAtNanos;
    }

    /**
     * Creates a tile from raw column data.
     *
     * @param categories 256 ordinals of {@link TerrainCategory}, indexed {@code z * 16 + x}
     * @param heights 256 surface Y values in the same order
     */
    public static TerrainTile of(ChunkKey key, byte[] categories, short[] heights) {
        if (categories.length != SIZE * SIZE || heights.length != SIZE * SIZE) {
            throw new IllegalArgumentException("expected " + (SIZE * SIZE) + " entries per tile");
        }
        return new TerrainTile(key, categories.clone(), heights.clone(), System.nanoTime());
    }

    /** An all-unknown tile, used when a chunk is not available. */
    public static TerrainTile empty(ChunkKey key) {
        byte[] categories = new byte[SIZE * SIZE];
        java.util.Arrays.fill(categories, (byte) TerrainCategory.UNKNOWN.ordinal());
        return new TerrainTile(key, categories, new short[SIZE * SIZE], System.nanoTime());
    }

    public ChunkKey key() {
        return key;
    }

    public long capturedAtNanos() {
        return capturedAtNanos;
    }

    /**
     * Category at a chunk-local coordinate.
     *
     * @param localX 0..15
     * @param localZ 0..15
     */
    public TerrainCategory categoryAt(int localX, int localZ) {
        int ordinal = categories[index(localX, localZ)] & 0xFF;
        TerrainCategory[] values = CATEGORIES;
        return ordinal < values.length ? values[ordinal] : TerrainCategory.UNKNOWN;
    }

    /** Surface height at a chunk-local coordinate. */
    public int heightAt(int localX, int localZ) {
        return heights[index(localX, localZ)];
    }

    private static int index(int localX, int localZ) {
        return (localZ << 4) | localX;
    }

    /** Approximate retained size in bytes, used for cache accounting. */
    public int approximateBytes() {
        // 256 bytes + 256 shorts + object overhead.
        return categories.length + heights.length * 2 + 64;
    }

    private static final TerrainCategory[] CATEGORIES = TerrainCategory.values();
}
