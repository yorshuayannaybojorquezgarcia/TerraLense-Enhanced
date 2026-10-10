package dev.terralense.terrain;

import java.util.UUID;

/** Cache key identifying one chunk in one world. */
public record ChunkKey(UUID worldId, int chunkX, int chunkZ) {
    public ChunkKey {
        if (worldId == null) {
            throw new IllegalArgumentException("worldId must not be null");
        }
    }

    /** Builds a key from block coordinates. */
    public static ChunkKey ofBlock(UUID worldId, int blockX, int blockZ) {
        return new ChunkKey(worldId, blockX >> 4, blockZ >> 4);
    }

    public int originX() {
        return chunkX << 4;
    }

    public int originZ() {
        return chunkZ << 4;
    }
}
