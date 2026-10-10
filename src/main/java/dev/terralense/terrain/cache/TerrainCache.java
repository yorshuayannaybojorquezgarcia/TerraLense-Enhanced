package dev.terralense.terrain.cache;

import dev.terralense.terrain.ChunkKey;
import dev.terralense.terrain.TerrainTile;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded LRU cache of {@link TerrainTile}s. */
public final class TerrainCache {
    private final int maxEntries;
    private final LinkedHashMap<ChunkKey, TerrainTile> tiles;

    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();

    public TerrainCache(int maxEntries) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        this.maxEntries = maxEntries;
        // accessOrder = true turns this into an LRU.
        this.tiles = new LinkedHashMap<>(Math.min(maxEntries, 1024), 0.75F, true);
    }

    /** Returns a cached tile, or {@code null} if absent. */
    public TerrainTile get(ChunkKey key) {
        synchronized (this) {
            TerrainTile tile = tiles.get(key);
            if (tile != null) {
                hits.incrementAndGet();
            } else {
                misses.incrementAndGet();
            }
            return tile;
        }
    }

    /** True if present, without affecting hit/miss statistics. */
    public boolean contains(ChunkKey key) {
        synchronized (this) {
            return tiles.containsKey(key);
        }
    }

    /** Stores a tile, evicting the least recently used entry if full. */
    public void put(TerrainTile tile) {
        synchronized (this) {
            tiles.put(tile.key(), tile);
            while (tiles.size() > maxEntries) {
                Iterator<Map.Entry<ChunkKey, TerrainTile>> it = tiles.entrySet().iterator();
                if (!it.hasNext()) {
                    break;
                }
                it.next();
                it.remove();
                evictions.incrementAndGet();
            }
        }
    }

    /** Invalidates one chunk, e.g. after a block change. */
    public void invalidate(ChunkKey key) {
        synchronized (this) {
            tiles.remove(key);
        }
    }

    /** Invalidates every tile belonging to a world, e.g. on unload. */
    public int invalidateWorld(UUID worldId) {
        synchronized (this) {
            int before = tiles.size();
            tiles.keySet().removeIf(key -> key.worldId().equals(worldId));
            return before - tiles.size();
        }
    }

    public void clear() {
        synchronized (this) {
            tiles.clear();
        }
    }

    public int size() {
        synchronized (this) {
            return tiles.size();
        }
    }

    public int maxEntries() {
        return maxEntries;
    }

    public long hits() {
        return hits.get();
    }

    public long misses() {
        return misses.get();
    }

    public long evictions() {
        return evictions.get();
    }

    /** Hit rate in the range 0..1; 0 when nothing has been requested yet. */
    public double hitRate() {
        long h = hits.get();
        long total = h + misses.get();
        return total == 0L ? 0.0D : (double) h / total;
    }

    /** Rough retained size in bytes, for the status command. */
    public long approximateBytes() {
        synchronized (this) {
            long total = 0L;
            for (TerrainTile tile : tiles.values()) {
                total += tile.approximateBytes();
            }
            return total;
        }
    }
}
