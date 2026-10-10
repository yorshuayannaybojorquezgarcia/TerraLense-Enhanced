package dev.terralense.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MapViewportTest {
    @Test
    @DisplayName("span scales with zoom")
    void spanScalesWithZoom() {
        assertEquals(64, new MapViewport(0, 0, 64, 1).spanBlocks());
        assertEquals(128, new MapViewport(0, 0, 64, 2).spanBlocks());
        assertEquals(512, new MapViewport(0, 0, 64, 8).spanBlocks());
    }

    @Test
    @DisplayName("the viewport is centred on the player")
    void isCentredOnPlayer() {
        MapViewport viewport = new MapViewport(100, 200, 64, 2);
        assertEquals(100 - 64, viewport.minBlockX());
        assertEquals(200 - 64, viewport.minBlockZ());
        assertEquals(100 + 63, viewport.maxBlockX());
        assertEquals(200 + 63, viewport.maxBlockZ());
    }

    @Test
    @DisplayName("block and cell conversions round-trip")
    void conversionsRoundTrip() {
        MapViewport viewport = new MapViewport(0, 0, 64, 4);
        for (int cell = 0; cell < 64; cell++) {
            int blockX = viewport.blockXForCell(cell);
            assertEquals(cell, viewport.cellXForBlock(blockX));
            // Any block inside the cell maps back to the same cell.
            assertEquals(cell, viewport.cellXForBlock(blockX + 3));
        }
    }

    @Test
    @DisplayName("negative world coordinates map correctly")
    void handlesNegativeCoordinates() {
        // Integer division truncates toward zero, which would misplace cells west/north of the
        // origin; floorDiv is required.
        MapViewport viewport = new MapViewport(-1000, -1000, 64, 4);
        for (int cell = 0; cell < 64; cell++) {
            int blockX = viewport.blockXForCell(cell);
            assertEquals(cell, viewport.cellXForBlock(blockX));
            assertEquals(cell, viewport.cellXForBlock(blockX + 3));
        }
        int blockZ = viewport.blockZForCell(10);
        assertEquals(10, viewport.cellZForBlock(blockZ));
    }

    @Test
    @DisplayName("cells are contiguous with no gaps or overlaps")
    void cellsTileTheViewport() {
        MapViewport viewport = new MapViewport(37, -91, 32, 3);
        for (int blockX = viewport.minBlockX(); blockX <= viewport.maxBlockX(); blockX++) {
            int cell = viewport.cellXForBlock(blockX);
            assertTrue(cell >= 0 && cell < 32, "block " + blockX + " fell outside the grid");
        }
    }

    @Test
    @DisplayName("containsBlock matches the covered area")
    void containsBlockMatchesBounds() {
        MapViewport viewport = new MapViewport(0, 0, 16, 2);
        assertTrue(viewport.containsBlock(0, 0));
        assertTrue(viewport.containsBlock(viewport.minBlockX(), viewport.minBlockZ()));
        assertTrue(viewport.containsBlock(viewport.maxBlockX(), viewport.maxBlockZ()));
        assertFalse(viewport.containsBlock(viewport.minBlockX() - 1, 0));
        assertFalse(viewport.containsBlock(0, viewport.maxBlockZ() + 1));
    }

    @Test
    @DisplayName("chunk range covers the whole viewport")
    void chunkRangeCoversViewport() {
        MapViewport viewport = new MapViewport(0, 0, 64, 2);
        assertEquals(viewport.minBlockX() >> 4, viewport.minChunkX());
        assertEquals(viewport.maxBlockX() >> 4, viewport.maxChunkX());
        assertTrue(viewport.chunkCount() > 0);

        // Every covered block's chunk must be inside the advertised range.
        for (int blockX = viewport.minBlockX(); blockX <= viewport.maxBlockX(); blockX += 7) {
            int chunkX = blockX >> 4;
            assertTrue(chunkX >= viewport.minChunkX() && chunkX <= viewport.maxChunkX());
        }
    }

    @Test
    @DisplayName("sub-cell movement does not trigger a re-sample")
    void subCellMovementSkipsResample() {
        // This is what stops the plugin re-sampling terrain every tick while a player walks a
        // fraction of a cell.
        MapViewport viewport = new MapViewport(100, 100, 64, 4);
        assertFalse(viewport.requiresResample(101, 101));
        assertFalse(viewport.requiresResample(102, 100));
    }

    @Test
    @DisplayName("crossing a cell boundary triggers a re-sample")
    void crossingCellTriggersResample() {
        MapViewport viewport = new MapViewport(100, 100, 64, 4);
        assertTrue(viewport.requiresResample(120, 100));
        assertTrue(viewport.requiresResample(100, 140));
    }

    @Test
    @DisplayName("zoom 1 makes every block its own cell")
    void zoomOneIsPerBlock() {
        MapViewport viewport = new MapViewport(0, 0, 64, 1);
        assertTrue(viewport.requiresResample(1, 0), "at zoom 1 a single block is a new cell");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 8})
    @DisplayName("distance from centre is zero at the centre and grows outward")
    void distanceGrowsOutward(int zoom) {
        MapViewport viewport = new MapViewport(0, 0, 64, zoom);
        assertEquals(0.0D, viewport.cellDistanceFromCenter(0, 0), 0.0001D);
        double near = viewport.cellDistanceFromCenter(zoom * 2, 0);
        double far = viewport.cellDistanceFromCenter(zoom * 10, 0);
        assertTrue(far > near);
    }

    @Test
    @DisplayName("withZoom keeps the centre")
    void withZoomKeepsCentre() {
        MapViewport viewport = new MapViewport(50, -50, 64, 2).withZoom(8);
        assertEquals(50, viewport.centerBlockX());
        assertEquals(-50, viewport.centerBlockZ());
        assertEquals(8, viewport.blocksPerCell());
    }

    @Test
    @DisplayName("centeredOn moves without changing zoom or size")
    void centeredOnMovesOnly() {
        MapViewport moved = new MapViewport(0, 0, 32, 4).centeredOn(500, 600);
        assertEquals(500, moved.centerBlockX());
        assertEquals(32, moved.gridSize());
        assertEquals(4, moved.blocksPerCell());
    }

    @Test
    @DisplayName("invalid dimensions are rejected")
    void rejectsInvalidDimensions() {
        assertThrows(IllegalArgumentException.class, () -> new MapViewport(0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new MapViewport(0, 0, 513, 1));
        assertThrows(IllegalArgumentException.class, () -> new MapViewport(0, 0, 64, 0));
        assertThrows(IllegalArgumentException.class, () -> new MapViewport(0, 0, 64, 65));
    }
}
