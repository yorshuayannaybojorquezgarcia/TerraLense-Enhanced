package dev.terralense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.terralense.config.HudShape;
import dev.terralense.config.MapOrientation;
import dev.terralense.config.TerraLenseConfig;
import dev.terralense.hud.GlyphHudRenderer;
import dev.terralense.hud.HudFrame;
import dev.terralense.hud.HudMarker;
import dev.terralense.map.MapImage;
import dev.terralense.map.MapViewport;
import dev.terralense.map.TerrainGrid;
import dev.terralense.map.render.DefaultTerrainRenderer;
import dev.terralense.map.render.RenderOptions;
import dev.terralense.map.rotation.RotationEngine;
import dev.terralense.terrain.ChunkKey;
import dev.terralense.terrain.TerrainCategory;
import dev.terralense.terrain.TerrainColorResolver;
import dev.terralense.terrain.TerrainTile;
import dev.terralense.terrain.cache.TerrainCache;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** End-to-end exercise of the render pipeline without a live server. */
class PipelineSmokeTest {
    private static final UUID WORLD = UUID.randomUUID();
    private static final int GRID = TerraLenseConfig.GRID_SIZE;

    /** Fills a cache with tiles covering a viewport, as the sampler would. */
    private static TerrainCache populatedCache(MapViewport viewport) {
        TerrainCache cache = new TerrainCache(4096);
        for (int chunkX = viewport.minChunkX(); chunkX <= viewport.maxChunkX(); chunkX++) {
            for (int chunkZ = viewport.minChunkZ(); chunkZ <= viewport.maxChunkZ(); chunkZ++) {
                byte[] categories = new byte[TerrainTile.SIZE * TerrainTile.SIZE];
                short[] heights = new short[TerrainTile.SIZE * TerrainTile.SIZE];
                for (int i = 0; i < categories.length; i++) {
                    // Vary terrain so the output is not trivially uniform.
                    int localX = i & 15;
                    TerrainCategory category = switch ((chunkX + chunkZ + localX) % 4) {
                        case 0 -> TerrainCategory.GRASS;
                        case 1 -> TerrainCategory.WATER;
                        case 2 -> TerrainCategory.STONE;
                        default -> TerrainCategory.SAND;
                    };
                    categories[i] = (byte) category.ordinal();
                    heights[i] = (short) (64 + (i % 8));
                }
                cache.put(TerrainTile.of(new ChunkKey(WORLD, chunkX, chunkZ), categories, heights));
            }
        }
        return cache;
    }

    /** Mirrors TerrainService.assemble using cached tiles. */
    private static TerrainGrid assemble(TerrainCache cache, MapViewport viewport) {
        TerrainGrid grid = new TerrainGrid(viewport.gridSize());
        for (int cellZ = 0; cellZ < viewport.gridSize(); cellZ++) {
            for (int cellX = 0; cellX < viewport.gridSize(); cellX++) {
                int sampleX = viewport.blockXForCell(cellX) + viewport.blocksPerCell() / 2;
                int sampleZ = viewport.blockZForCell(cellZ) + viewport.blocksPerCell() / 2;
                ChunkKey key = ChunkKey.ofBlock(WORLD, sampleX, sampleZ);
                TerrainTile tile = cache.get(key);
                if (tile == null) {
                    continue;
                }
                grid.set(cellX, cellZ,
                        tile.categoryAt(sampleX - key.originX(), sampleZ - key.originZ()),
                        tile.heightAt(sampleX - key.originX(), sampleZ - key.originZ()));
            }
        }
        return grid;
    }

    @Test
    @DisplayName("the full pipeline produces a non-empty HUD from cached terrain")
    void pipelineProducesHud() {
        MapViewport viewport = new MapViewport(100, -250, GRID, 2);
        TerrainGrid grid = assemble(populatedCache(viewport), viewport);

        MapImage image = new DefaultTerrainRenderer(TerrainColorResolver.create(true, 0.25D))
                .render(grid, new RenderOptions(GRID, MapOrientation.PLAYER_UP, 45.0F,
                        HudShape.CIRCLE));

        Component hud = new GlyphHudRenderer(Key.key("terralense", "hud"))
                .render(image, new HudFrame(0.0F, true, List.of(new HudMarker(20, 20, 0, 0xFF00FF, 5))));

        String plain = PlainTextComponentSerializer.plainText().serialize(hud);
        assertFalse(plain.isEmpty(), "the pipeline produced no output");
        assertEquals(Key.key("terralense", "hud"), hud.style().font());
    }

    @Test
    @DisplayName("every zoom level renders without error and fills the centre")
    void allZoomLevelsRender() {
        for (int zoom = TerraLenseConfig.MIN_ZOOM; zoom <= TerraLenseConfig.MAX_ZOOM; zoom++) {
            MapViewport viewport = new MapViewport(0, 0, GRID, zoom);
            TerrainGrid grid = assemble(populatedCache(viewport), viewport);
            MapImage image = new DefaultTerrainRenderer(TerrainColorResolver.create(true, 0.25D))
                    .render(grid, new RenderOptions(GRID, MapOrientation.PLAYER_UP, 0.0F,
                            HudShape.CIRCLE));
            assertFalse(image.isTransparent(GRID / 2, GRID / 2),
                    "centre was empty at zoom " + zoom);
        }
    }

    @Test
    @DisplayName("negative world coordinates render correctly")
    void negativeCoordinatesRender() {
        // Regression guard: truncating division would misplace cells west/north of the origin.
        MapViewport viewport = new MapViewport(-5000, -7000, GRID, 4);
        TerrainGrid grid = assemble(populatedCache(viewport), viewport);
        MapImage image = new DefaultTerrainRenderer(TerrainColorResolver.create(false, 0.0D))
                .render(grid, new RenderOptions(GRID, MapOrientation.NORTH_UP, 0.0F,
                        HudShape.SQUARE));

        int filled = 0;
        for (int y = 0; y < GRID; y++) {
            for (int x = 0; x < GRID; x++) {
                if (!image.isTransparent(x, y)) {
                    filled++;
                }
            }
        }
        assertEquals(GRID * GRID, filled, "all cells should be covered by cached terrain");
    }

    @Test
    @DisplayName("rotating through a full turn never throws or empties the map")
    void rotationIsStableAcrossAllAngles() {
        MapViewport viewport = new MapViewport(0, 0, GRID, 2);
        TerrainGrid grid = assemble(populatedCache(viewport), viewport);
        DefaultTerrainRenderer renderer =
                new DefaultTerrainRenderer(TerrainColorResolver.create(true, 0.25D));
        GlyphHudRenderer hud = new GlyphHudRenderer(Key.key("terralense", "hud"));

        for (float yaw = -720.0F; yaw <= 720.0F; yaw += 6.5F) {
            float rotation = RotationEngine.terrainRotationForYaw(yaw);
            MapImage image = renderer.render(grid,
                    new RenderOptions(GRID, MapOrientation.PLAYER_UP, rotation, HudShape.CIRCLE));
            Component component = hud.render(image, HudFrame.of(0.0F));
            assertFalse(PlainTextComponentSerializer.plainText().serialize(component).isEmpty(),
                    "empty HUD at yaw " + yaw);
        }
    }

    @Test
    @DisplayName("the HUD serialises to valid JSON for the wire")
    void hudSerialises() {
        MapViewport viewport = new MapViewport(0, 0, GRID, 2);
        TerrainGrid grid = assemble(populatedCache(viewport), viewport);
        MapImage image = new DefaultTerrainRenderer(TerrainColorResolver.create(true, 0.25D))
                .render(grid, new RenderOptions(GRID, MapOrientation.PLAYER_UP, 30.0F,
                        HudShape.CIRCLE));
        Component hud = new GlyphHudRenderer(Key.key("terralense", "hud"))
                .render(image, HudFrame.of(0.0F));

        String json = GsonComponentSerializer.gson().serialize(hud);
        assertTrue(json.startsWith("{"), "expected a JSON object");
        assertTrue(json.contains("terralense:hud"), "the font must survive serialisation");
    }

    @Test
    @DisplayName("missing terrain renders as empty space instead of failing")
    void missingTerrainIsEmpty() {
        // An empty cache stands in for unloaded chunks, which TerraLense never loads on demand.
        MapViewport viewport = new MapViewport(0, 0, GRID, 2);
        TerrainGrid grid = assemble(new TerrainCache(64), viewport);
        MapImage image = new DefaultTerrainRenderer(TerrainColorResolver.create(true, 0.25D))
                .render(grid, new RenderOptions(GRID, MapOrientation.PLAYER_UP, 0.0F,
                        HudShape.CIRCLE));

        for (int y = 0; y < GRID; y++) {
            for (int x = 0; x < GRID; x++) {
                assertTrue(image.isTransparent(x, y));
            }
        }
        // And the HUD degrades to nothing rather than throwing.
        Component hud = new GlyphHudRenderer(Key.key("terralense", "hud"))
                .render(image, new HudFrame(0.0F, false, List.of()));
        assertTrue(PlainTextComponentSerializer.plainText().serialize(hud).isEmpty());
    }
}
