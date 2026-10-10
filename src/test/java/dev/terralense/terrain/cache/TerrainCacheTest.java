package dev.terralense.terrain.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.terralense.terrain.ChunkKey;
import dev.terralense.terrain.TerrainTile;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TerrainCacheTest {
    private static final UUID WORLD_A = UUID.randomUUID();
    private static final UUID WORLD_B = UUID.randomUUID();

    private static TerrainTile tile(UUID world, int x, int z) {
        return TerrainTile.empty(new ChunkKey(world, x, z));
    }

    @Test
    @DisplayName("stores and retrieves tiles")
    void storesAndRetrieves() {
        TerrainCache cache = new TerrainCache(8);
        cache.put(tile(WORLD_A, 1, 2));
        assertNotNull(cache.get(new ChunkKey(WORLD_A, 1, 2)));
        assertNull(cache.get(new ChunkKey(WORLD_A, 9, 9)));
    }

    @Test
    @DisplayName("never grows past its bound")
    void respectsBound() {
        TerrainCache cache = new TerrainCache(16);
        for (int i = 0; i < 500; i++) {
            cache.put(tile(WORLD_A, i, 0));
        }
        assertEquals(16, cache.size());
        assertTrue(cache.evictions() >= 484L);
    }

    @Test
    @DisplayName("evicts least recently used, keeping recently touched tiles")
    void evictsLeastRecentlyUsed() {
        TerrainCache cache = new TerrainCache(3);
        cache.put(tile(WORLD_A, 0, 0));
        cache.put(tile(WORLD_A, 1, 0));
        cache.put(tile(WORLD_A, 2, 0));

        // Touch the oldest so it is no longer the LRU victim.
        assertNotNull(cache.get(new ChunkKey(WORLD_A, 0, 0)));
        cache.put(tile(WORLD_A, 3, 0));

        assertNotNull(cache.get(new ChunkKey(WORLD_A, 0, 0)), "recently used tile should survive");
        assertNull(cache.get(new ChunkKey(WORLD_A, 1, 0)), "untouched tile should be evicted");
    }

    @Test
    @DisplayName("world-keyed entries never collide across worlds")
    void worldsAreIsolated() {
        TerrainCache cache = new TerrainCache(8);
        cache.put(tile(WORLD_A, 5, 5));
        assertNull(cache.get(new ChunkKey(WORLD_B, 5, 5)),
                "same coordinates in another world must not alias");
    }

    @Test
    @DisplayName("invalidating a world clears only that world")
    void invalidateWorldIsScoped() {
        TerrainCache cache = new TerrainCache(32);
        cache.put(tile(WORLD_A, 0, 0));
        cache.put(tile(WORLD_A, 1, 0));
        cache.put(tile(WORLD_B, 0, 0));

        assertEquals(2, cache.invalidateWorld(WORLD_A));
        assertNull(cache.get(new ChunkKey(WORLD_A, 0, 0)));
        assertNotNull(cache.get(new ChunkKey(WORLD_B, 0, 0)));
    }

    @Test
    @DisplayName("invalidate removes a single chunk")
    void invalidateSingle() {
        TerrainCache cache = new TerrainCache(8);
        cache.put(tile(WORLD_A, 4, 4));
        cache.invalidate(new ChunkKey(WORLD_A, 4, 4));
        assertFalse(cache.contains(new ChunkKey(WORLD_A, 4, 4)));
    }

    @Test
    @DisplayName("hit rate reflects lookups")
    void tracksHitRate() {
        TerrainCache cache = new TerrainCache(8);
        cache.put(tile(WORLD_A, 0, 0));
        cache.get(new ChunkKey(WORLD_A, 0, 0));
        cache.get(new ChunkKey(WORLD_A, 0, 0));
        cache.get(new ChunkKey(WORLD_A, 7, 7));

        assertEquals(2L, cache.hits());
        assertEquals(1L, cache.misses());
        assertEquals(2.0D / 3.0D, cache.hitRate(), 0.0001D);
    }

    @Test
    @DisplayName("hit rate is zero before any lookup, not NaN")
    void hitRateStartsAtZero() {
        assertEquals(0.0D, new TerrainCache(4).hitRate(), 0.0D);
    }

    @Test
    @DisplayName("contains does not skew hit statistics")
    void containsDoesNotCountAsLookup() {
        TerrainCache cache = new TerrainCache(4);
        cache.put(tile(WORLD_A, 0, 0));
        cache.contains(new ChunkKey(WORLD_A, 0, 0));
        assertEquals(0L, cache.hits());
        assertEquals(0L, cache.misses());
    }

    @Test
    @DisplayName("rejects a non-positive bound")
    void rejectsInvalidBound() {
        assertThrows(IllegalArgumentException.class, () -> new TerrainCache(0));
        assertThrows(IllegalArgumentException.class, () -> new TerrainCache(-1));
    }

    @Test
    @DisplayName("concurrent access stays consistent and bounded")
    void survivesConcurrentAccess() throws InterruptedException {
        // The cache is read by render workers and written from the server thread, so it must not
        // corrupt or exceed its bound under contention.
        TerrainCache cache = new TerrainCache(64);
        int threads = 8;
        Thread[] workers = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            final int offset = t * 1000;
            workers[t] = new Thread(() -> {
                for (int i = 0; i < 2000; i++) {
                    cache.put(tile(WORLD_A, offset + i, 0));
                    cache.get(new ChunkKey(WORLD_A, offset + i, 0));
                }
            });
            workers[t].start();
        }
        for (Thread worker : workers) {
            worker.join();
        }
        assertTrue(cache.size() <= 64, "cache exceeded its bound: " + cache.size());
    }

    @Test
    @DisplayName("clear empties the cache")
    void clearEmpties() {
        TerrainCache cache = new TerrainCache(8);
        cache.put(tile(WORLD_A, 0, 0));
        cache.clear();
        assertEquals(0, cache.size());
        assertEquals(0L, cache.approximateBytes());
    }
}
