package dev.terralense.terrain;

import dev.terralense.compatibility.PlatformScheduler;
import dev.terralense.core.Log;
import dev.terralense.map.MapViewport;
import dev.terralense.map.TerrainGrid;
import dev.terralense.terrain.cache.TerrainCache;
import java.util.UUID;
import org.bukkit.Chunk;
import org.bukkit.World;

/** Supplies terrain grids, bridging live world state and the async render pipeline. */
public final class TerrainService {
    private final TerrainCache cache;
    private final TerrainSampler sampler;
    private final PlatformScheduler scheduler;
    private final Log log;

    public TerrainService(TerrainCache cache,
                          TerrainSampler sampler,
                          PlatformScheduler scheduler,
                          Log log) {
        this.cache = cache;
        this.sampler = sampler;
        this.scheduler = scheduler;
        this.log = log;
    }

    /** Captures snapshots for every chunk the viewport needs that is not already cached. */
    public PendingSnapshots captureSnapshots(World world, MapViewport viewport) {
        if (!scheduler.isServerThread()) {
            throw new IllegalStateException("chunk snapshots must be taken on the server thread");
        }
        UUID worldId = world.getUID();
        PendingSnapshots pending = new PendingSnapshots(worldId, world.getMinHeight(),
                world.getMaxHeight() - 1);

        for (int chunkX = viewport.minChunkX(); chunkX <= viewport.maxChunkX(); chunkX++) {
            for (int chunkZ = viewport.minChunkZ(); chunkZ <= viewport.maxChunkZ(); chunkZ++) {
                ChunkKey key = new ChunkKey(worldId, chunkX, chunkZ);
                if (cache.contains(key)) {
                    continue;
                }
                // isChunkLoaded avoids triggering a load; an unloaded chunk is left absent and
                // renders as empty space.
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    continue;
                }
                try {
                    Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                    // No biome or block-light data: the surface map needs neither, and skipping
                    // them makes the snapshot cheaper.
                    pending.add(key, chunk.getChunkSnapshot(true, false, false));
                } catch (RuntimeException ex) {
                    final int failedX = chunkX;
                    final int failedZ = chunkZ;
                    log.debug(() -> "could not snapshot chunk " + failedX + "," + failedZ
                            + ": " + ex.getMessage());
                }
            }
        }
        return pending;
    }

    /** Samples pending snapshots into the cache and builds a grid. */
    public TerrainGrid buildGrid(PendingSnapshots pending, MapViewport viewport) {
        for (PendingSnapshots.Entry entry : pending.entries()) {
            TerrainTile tile = sampler.sample(entry.key(), entry.snapshot(),
                    pending.minHeight(), pending.maxHeight());
            cache.put(tile);
        }
        return assemble(pending.worldId(), viewport);
    }

    /** Assembles a grid from cached tiles. */
    private TerrainGrid assemble(UUID worldId, MapViewport viewport) {
        TerrainGrid grid = new TerrainGrid(viewport.gridSize());
        int blocksPerCell = viewport.blocksPerCell();

        ChunkKey lastKey = null;
        TerrainTile lastTile = null;

        for (int cellZ = 0; cellZ < viewport.gridSize(); cellZ++) {
            int blockZ = viewport.blockZForCell(cellZ);
            for (int cellX = 0; cellX < viewport.gridSize(); cellX++) {
                int blockX = viewport.blockXForCell(cellX);

                // Sample the middle of the cell so a zoomed-out cell is representative rather than
                // biased to its corner.
                int sampleX = blockX + blocksPerCell / 2;
                int sampleZ = blockZ + blocksPerCell / 2;

                ChunkKey key = ChunkKey.ofBlock(worldId, sampleX, sampleZ);
                // Adjacent cells usually share a chunk, so caching the last lookup removes most
                // cache hits from the inner loop.
                if (!key.equals(lastKey)) {
                    lastKey = key;
                    lastTile = cache.get(key);
                }
                if (lastTile == null) {
                    continue;
                }
                int localX = sampleX - key.originX();
                int localZ = sampleZ - key.originZ();
                grid.set(cellX, cellZ, lastTile.categoryAt(localX, localZ),
                        lastTile.heightAt(localX, localZ));
            }
        }
        return grid;
    }

    /** Invalidates the tile covering a block, e.g. after a block change. */
    public void invalidateBlock(UUID worldId, int blockX, int blockZ) {
        cache.invalidate(ChunkKey.ofBlock(worldId, blockX, blockZ));
    }

    public void invalidateChunk(UUID worldId, int chunkX, int chunkZ) {
        cache.invalidate(new ChunkKey(worldId, chunkX, chunkZ));
    }

    public void invalidateWorld(UUID worldId) {
        cache.invalidateWorld(worldId);
    }

    public TerrainCache cache() {
        return cache;
    }
}
