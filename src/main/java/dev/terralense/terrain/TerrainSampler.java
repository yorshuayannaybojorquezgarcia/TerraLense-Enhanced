package dev.terralense.terrain;

import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;

/** Converts a {@link ChunkSnapshot} into a {@link TerrainTile}. */
public final class TerrainSampler {
    private final MaterialClassifier classifier;

    public TerrainSampler(MaterialClassifier classifier) {
        this.classifier = classifier;
    }

    /** Samples the surface of a chunk snapshot. */
    public TerrainTile sample(ChunkKey key, ChunkSnapshot snapshot, int minWorldY, int maxWorldY) {
        byte[] categories = new byte[TerrainTile.SIZE * TerrainTile.SIZE];
        short[] heights = new short[TerrainTile.SIZE * TerrainTile.SIZE];

        for (int z = 0; z < TerrainTile.SIZE; z++) {
            for (int x = 0; x < TerrainTile.SIZE; x++) {
                int index = (z << 4) | x;
                Column column = sampleColumn(snapshot, x, z, minWorldY, maxWorldY);
                categories[index] = (byte) column.category().ordinal();
                heights[index] = (short) column.height();
            }
        }
        return TerrainTile.of(key, categories, heights);
    }

    /** Finds the topmost meaningful block in one column. */
    private Column sampleColumn(ChunkSnapshot snapshot, int x, int z, int minWorldY, int maxWorldY) {
        int startY = Math.clamp(snapshot.getHighestBlockYAt(x, z), minWorldY, maxWorldY);

        TerrainCategory surfaceCategory = TerrainCategory.UNKNOWN;
        int surfaceHeight = startY;

        for (int y = startY; y >= minWorldY; y--) {
            Material material = snapshot.getBlockType(x, y, z);
            if (material.isAir()) {
                continue;
            }
            TerrainCategory category = classifier.classify(material);
            if (category == TerrainCategory.UNKNOWN) {
                // Non-air but not something we draw (barriers, light blocks).
                continue;
            }
            surfaceCategory = category;
            surfaceHeight = y;
            break;
        }
        return new Column(surfaceCategory, surfaceHeight);
    }

    private record Column(TerrainCategory category, int height) {
    }
}
