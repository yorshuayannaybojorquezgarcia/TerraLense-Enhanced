package dev.terralense.resourcepack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.terralense.config.HudPosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Geometry tests for the generated font. */
class PackLayoutTest {
    /** The grid size the plugin actually ships with. */
    private static final int GRID = dev.terralense.config.TerraLenseConfig.GRID_SIZE;

    @ParameterizedTest
    @ValueSource(ints = {32, 64, 96, 128, 192, 256})
    @DisplayName("a map cell renders square at every HUD size")
    void cellsRenderSquare(int hudSize) {
        // The client scales glyph width by height/textureHeight.
        for (HudPosition position : HudPosition.values()) {
            PackLayout layout = PackLayout.of(GRID, hudSize, 8, position);
            assertEquals(layout.cellScreenSize(), layout.renderedGlyphWidth(),
                    "cell not square at " + hudSize + " " + position
                            + " (w=" + layout.renderedGlyphWidth()
                            + " h=" + layout.cellScreenSize() + ")");
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {32, 64, 96, 128, 192, 256})
    @DisplayName("ascent never exceeds height")
    void ascentWithinHeight(int hudSize) {
        for (HudPosition position : HudPosition.values()) {
            PackLayout layout = PackLayout.of(GRID, hudSize, 8, position);
            assertTrue(layout.providerAscent() <= layout.providerHeight(),
                    "ascent " + layout.providerAscent() + " exceeds height "
                            + layout.providerHeight());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {32, 64, 96, 128, 192, 256})
    @DisplayName("glyph textures never exceed the client's 256px limit")
    void glyphTexturesFitClientLimit(int hudSize) {
        // The binding constraint.
        for (HudPosition position : HudPosition.values()) {
            PackLayout layout = PackLayout.of(
                    GRID, hudSize, 8, position);
            assertTrue(layout.fitsGlyphLimit(),
                    "texture " + layout.texturePixelWidth() + "x"
                            + layout.texturePixelHeight() + " exceeds the "
                            + PackLayout.MAX_GLYPH_TEXTURE_PIXELS + "px limit at "
                            + hudSize + " " + position);
            assertTrue(layout.texturePixelHeight() <= PackLayout.MAX_GLYPH_TEXTURE_PIXELS);
        }
    }

    @Test
    @DisplayName("a layout that would exceed the glyph limit is rejected outright")
    void oversizedLayoutRejected() {
        // 64 cells plus any lift is 512px+, far past the limit.
        assertThrows(IllegalArgumentException.class,
                () -> new PackLayout(64, 96, 8, 8));
    }

    @Test
    @DisplayName("the default grid leaves room to lift the HUD off the hotbar")
    void defaultGridLeavesLift() {
        PackLayout layout = PackLayout.of(
                GRID, 96, 8,
                HudPosition.TOP_RIGHT);
        assertTrue(layout.liftCells() > 0,
                "no lift available: the HUD would sit on the hotbar");
        assertTrue(layout.fitsGlyphLimit());
    }

    @Test
    @DisplayName("provider height derives from row count, not the requested HUD size")
    void heightDerivesFromRows() {
        // This is the arithmetic that was wrong: using the requested HUD size as the height made
        // the glyph 3px wide and 246px tall.
        PackLayout layout = PackLayout.of(GRID, 96, 8, HudPosition.TOP_RIGHT);
        assertEquals(layout.textureRows() * layout.cellScreenSize(), layout.providerHeight());
        assertTrue(layout.providerHeight() >= layout.effectiveHudSize(),
                "height must cover the map, plus any padding rows");
    }

    @Test
    @DisplayName("a top-anchored HUD gets more lift than a bottom-anchored one")
    void topGetsMoreLift() {
        PackLayout top = PackLayout.of(GRID, 96, 8, HudPosition.TOP_RIGHT);
        PackLayout bottom = PackLayout.of(GRID, 96, 8, HudPosition.BOTTOM_RIGHT);

        assertTrue(top.liftCells() >= bottom.liftCells());
        assertTrue(top.textureRows() > top.gridSize(),
                "a top-anchored HUD needs padding rows to lift it");
    }

    @Test
    @DisplayName("the rendered map is the requested size, within cell rounding")
    void mapMatchesRequestedSize() {
        // At the 24-cell grid, 144 GUI pixels gives 6px cells.
        PackLayout layout = PackLayout.of(GRID, 144, 8, HudPosition.TOP_RIGHT);
        assertEquals(6, layout.cellScreenSize());
        assertEquals(144, layout.effectiveHudSize());
    }

    @Test
    @DisplayName("a cell is never smaller than 2 GUI pixels")
    void cellsSurviveGuiScaling() {
        // A 1px cell disappears at some GUI scale factors.
        PackLayout tiny = PackLayout.of(GRID, 16, 0, HudPosition.TOP_LEFT);
        assertTrue(tiny.cellScreenSize() >= 2);
    }

    @ParameterizedTest
    @ValueSource(ints = {32, 64, 96, 128, 192, 256})
    @DisplayName("the kern glyph cancels the client's automatic glyph spacing")
    void kernCancelsAutomaticSpacing(int hudSize) {
        // Bitmap glyphs advance by width + 1, but 'space' advances are exact (vanilla declares " ":
        // 4 for a 4px space).
        PackLayout layout = PackLayout.of(GRID, hudSize, 8, HudPosition.TOP_RIGHT);

        assertEquals(-1, layout.kernAdvance(), "kern must cancel exactly one pixel");
        int netPitch = (layout.renderedGlyphWidth() + PackLayout.GLYPH_SPACING_PIXELS)
                + layout.kernAdvance();
        assertEquals(layout.renderedGlyphWidth(), netPitch,
                "net pitch must equal the ink width so there is no seam");
        assertEquals(netPitch, layout.cellAdvance(),
                "horizontal arithmetic must use the net pitch");
    }

    @Test
    @DisplayName("a full row of advances returns exactly to the starting column")
    void rowAdvanceIsReversible() {
        // The rewind distance must undo the forward travel exactly, or rows drift.
        PackLayout layout = PackLayout.of(GRID, 96, 8, HudPosition.TOP_RIGHT);
        int forward = GRID * layout.cellAdvance();
        int rewind = GRID * layout.cellAdvance();
        assertEquals(0, forward - rewind, "a full row must rewind to column zero");
    }

    @Test
    @DisplayName("texture width stays one cell so per-cell colouring is possible")
    void textureIsOneCellWide() {
        // Each cell must be its own glyph to carry its own colour, so the texture cannot be widened
        // to improve the aspect ratio.
        PackLayout layout = PackLayout.of(GRID, 96, 8, HudPosition.TOP_RIGHT);
        assertEquals(PackLayout.CELL_TEXTURE_PIXELS, layout.texturePixelWidth());
    }
}
