package dev.terralense.hud;

import dev.terralense.config.HudPosition;
import dev.terralense.resourcepack.PackLayout;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnhancedGridTest {
    @Test void thirtyTwoCellLayoutFitsFontTexture() {
        PackLayout layout = PackLayout.of(32, 128, 8, HudPosition.TOP_RIGHT);
        assertEquals(32, layout.gridSize());
        assertEquals(256, layout.texturePixelHeight());
        assertTrue(layout.fitsGlyphLimit());
    }
    @Test void glyphBlocksDoNotOverlapAtThirtyTwoRows() {
        for (int row = 0; row < 32; row++) {
            for (int shape = 0; shape < HudGlyphs.MARKER_SHAPES; shape++) {
                int marker = HudGlyphs.markerGlyph(shape, row);
                for (int step = 0; step < HudGlyphs.ARROW_STEPS; step++) {
                    assertNotEquals(marker, HudGlyphs.arrowGlyph(step, row));
                }
            }
        }
    }
    @Test void allAllocatedGlyphsAreUniqueAtMaximumGrid() {
        java.util.Set<Integer> glyphs = new java.util.HashSet<>();
        for (int row = 0; row < 32; row++) {
            assertTrue(glyphs.add((int) HudGlyphs.rowGlyph(row)));
            for (int shape = 0; shape < HudGlyphs.MARKER_SHAPES; shape++)
                assertTrue(glyphs.add((int) HudGlyphs.markerGlyph(shape, row)));
        }
        for (int step = 0; step < HudGlyphs.ARROW_STEPS; step++)
            assertTrue(glyphs.add((int) HudGlyphs.arrowGlyph(step, 16)));
        for (int step = 0; step < HudGlyphs.spaceGlyphCount(); step++)
            assertTrue(glyphs.add((int) HudGlyphs.spaceGlyph(step)));
        for (int step = 0; step < HudGlyphs.forwardSpaceGlyphCount(); step++)
            assertTrue(glyphs.add((int) HudGlyphs.forwardSpaceGlyph(step)));
        assertTrue(glyphs.add((int) HudGlyphs.KERN_GLYPH));
    }
    @Test void thirtyTwoCellLayoutsStayWithinTextureBounds() {
        for (HudPosition position : HudPosition.values()) {
            PackLayout layout = PackLayout.of(32, 128, 8, position);
            assertEquals(32, layout.textureRows());
            assertEquals(256, layout.texturePixelHeight());
            assertEquals(layout.cellScreenSize(), layout.renderedGlyphWidth());
            assertTrue(layout.fitsGlyphLimit());
        }
        assertThrows(IllegalArgumentException.class, () -> new PackLayout(33, 128, 8, 0));
        assertThrows(IllegalArgumentException.class, () -> new PackLayout(32, 128, -1, 0));
    }
}
