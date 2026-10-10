package dev.terralense.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.terralense.map.MapImage;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GlyphHudRendererTest {
    private static final Key FONT = Key.key("terralense", "hud");

    private final GlyphHudRenderer renderer = new GlyphHudRenderer(FONT);

    private static MapImage filled(int size, int argb) {
        MapImage image = new MapImage(size);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                image.set(x, y, argb);
            }
        }
        return image;
    }

    private static HudFrame noOverlay() {
        return new HudFrame(0.0F, false, List.of());
    }

    @Test
    @DisplayName("the font is applied once on the root so children inherit it")
    void fontAppliedAtRoot() {
        Component component = renderer.render(filled(8, 0xFF112233), noOverlay());
        assertEquals(FONT, component.style().font());
    }

    @Test
    @DisplayName("italics are pinned off so glyph alignment cannot be skewed")
    void italicsDisabled() {
        Component component = renderer.render(filled(8, 0xFF112233), noOverlay());
        assertEquals(TextDecoration.State.FALSE, component.style().decoration(TextDecoration.ITALIC));
    }

    @Test
    @DisplayName("a uniform row collapses into a single run instead of one glyph each")
    void uniformRowIsRunLengthMerged() {
        // Component count drives packet size, so flat terrain must not produce one component per
        // cell.
        MapImage image = filled(32, 0xFF445566);
        Component component = renderer.render(image, noOverlay());

        long glyphRuns = countTextRuns(component, HudGlyphs.rowGlyph(0));
        assertEquals(1, glyphRuns, "a uniform row should emit exactly one run");
    }

    @Test
    @DisplayName("a fully transparent image produces no glyphs at all")
    void transparentImageEmitsNothing() {
        Component component = renderer.render(new MapImage(16), noOverlay());
        String plain = PlainTextComponentSerializer.plainText().serialize(component);
        assertTrue(plain.isEmpty(), "expected no output, got " + plain.length() + " chars");
    }

    @Test
    @DisplayName("each row uses its own glyph so rows stack vertically")
    void rowsUseDistinctGlyphs() {
        int size = 12;
        Component component = renderer.render(filled(size, 0xFFFFFFFF), noOverlay());
        String plain = PlainTextComponentSerializer.plainText().serialize(component);

        for (int row = 0; row < size; row++) {
            assertTrue(plain.indexOf(HudGlyphs.rowGlyph(row)) >= 0,
                    "row glyph " + row + " missing from output");
        }
    }

    @Test
    @DisplayName("rewind glyphs are emitted so rows start from the left edge")
    void emitsRewindGlyphs() {
        Component component = renderer.render(filled(16, 0xFF010101), noOverlay());
        String plain = PlainTextComponentSerializer.plainText().serialize(component);

        boolean hasRewind = false;
        for (int i = 0; i < HudGlyphs.spaceGlyphCount(); i++) {
            if (plain.indexOf(HudGlyphs.spaceGlyph(i)) >= 0) {
                hasRewind = true;
                break;
            }
        }
        assertTrue(hasRewind, "no negative-space glyph emitted");
    }

    @Test
    @DisplayName("rewind decomposes into powers of two, not one glyph per cell")
    void rewindUsesPowersOfTwo() {
        // A 64 wide row must rewind with a single 64-wide glyph, not 64 glyphs.
        Component component = renderer.render(filled(64, 0xFF020202), noOverlay());
        String plain = PlainTextComponentSerializer.plainText().serialize(component);

        int rewindCount = 0;
        for (char c : plain.toCharArray()) {
            if (c >= HudGlyphs.spaceGlyphBase() && c < HudGlyphs.spaceGlyphBase() + 16) {
                rewindCount++;
            }
        }
        // 64 rows, each needing a single power-of-two rewind of width 64.
        assertEquals(64, rewindCount, "expected one rewind glyph per row");
    }

    @Test
    @DisplayName("different colours are not merged together")
    void differentColoursAreSeparateRuns() {
        MapImage image = new MapImage(4);
        for (int x = 0; x < 4; x++) {
            image.set(x, 0, x < 2 ? 0xFFFF0000 : 0xFF00FF00);
        }
        Component component = renderer.render(image, noOverlay());
        assertEquals(2, countTextRuns(component, HudGlyphs.rowGlyph(0)));
    }

    @Test
    @DisplayName("colours reach the output as component colours")
    void coloursArePreserved() {
        MapImage image = new MapImage(2);
        image.set(0, 0, 0xFF123456);
        image.set(1, 0, 0xFF123456);

        List<Component> runs = collectRuns(renderer.render(image, noOverlay()),
                HudGlyphs.rowGlyph(0));
        assertEquals(1, runs.size());
        assertEquals(0x123456, runs.getFirst().color().value());
    }

    @Test
    @DisplayName("the player arrow occupies the centre cell")
    void arrowDrawnAtCentre() {
        int size = 16;
        Component component = renderer.render(filled(size, 0xFF333333), HudFrame.of(0.0F));
        String plain = PlainTextComponentSerializer.plainText().serialize(component);

        int step = HudGlyphs.arrowStepForAngle(0.0F);
        assertTrue(plain.indexOf(HudGlyphs.arrowGlyph(step, size / 2)) >= 0, "arrow glyph missing");
    }

    @Test
    @DisplayName("the arrow glyph changes as the player turns")
    void arrowFollowsAngle() {
        MapImage image = filled(16, 0xFF333333);
        String north = PlainTextComponentSerializer.plainText()
                .serialize(renderer.render(image, HudFrame.of(0.0F)));
        String east = PlainTextComponentSerializer.plainText()
                .serialize(renderer.render(image, HudFrame.of(90.0F)));
        assertFalse(north.equals(east), "arrow should differ between headings");
    }

    @Test
    @DisplayName("markers are drawn at their cell")
    void markersAreDrawn() {
        MapImage image = filled(16, 0xFF333333);
        HudFrame frame = new HudFrame(0.0F, false,
                List.of(new HudMarker(3, 4, 0, 0xFF00FF, 10)));
        String plain = PlainTextComponentSerializer.plainText()
                .serialize(renderer.render(image, frame));
        assertTrue(plain.indexOf(HudGlyphs.markerGlyph(0, 4)) >= 0);
    }

    @Test
    @DisplayName("higher priority markers win a contested cell")
    void higherPriorityMarkerWins() {
        MapImage image = filled(16, 0xFF333333);
        HudFrame frame = new HudFrame(0.0F, false, List.of(
                new HudMarker(5, 5, 0, 0xFF0000, 1),
                new HudMarker(5, 5, 2, 0x00FF00, 99)));
        String plain = PlainTextComponentSerializer.plainText()
                .serialize(renderer.render(image, frame));

        assertTrue(plain.indexOf(HudGlyphs.markerGlyph(2, 5)) >= 0, "winning marker missing");
        assertFalse(plain.indexOf(HudGlyphs.markerGlyph(0, 5)) >= 0, "losing marker should be dropped");
    }

    @Test
    @DisplayName("out-of-bounds markers are ignored rather than throwing")
    void outOfBoundsMarkersIgnored() {
        MapImage image = filled(8, 0xFF333333);
        HudFrame frame = new HudFrame(0.0F, false, List.of(
                new HudMarker(-1, 0, 0, 0xFFFFFF, 1),
                new HudMarker(0, 99, 0, 0xFFFFFF, 1),
                new HudMarker(99, 99, 0, 0xFFFFFF, 1)));
        Component component = renderer.render(image, frame);
        assertFalse(PlainTextComponentSerializer.plainText().serialize(component).isEmpty());
    }

    @Test
    @DisplayName("markers survive a transparent background")
    void markersDrawOverTransparentCells() {
        // Shape-masked corners are transparent; a marker there must still draw.
        HudFrame frame = new HudFrame(0.0F, false, List.of(new HudMarker(0, 0, 1, 0xFFFFFF, 5)));
        Component component = renderer.render(new MapImage(8), frame);
        String plain = PlainTextComponentSerializer.plainText().serialize(component);
        assertTrue(plain.indexOf(HudGlyphs.markerGlyph(1, 0)) >= 0);
    }

    private static long countTextRuns(Component root, char glyph) {
        return collectRuns(root, glyph).size();
    }

    private static List<Component> collectRuns(Component root, char glyph) {
        List<Component> matches = new ArrayList<>();
        collect(root, glyph, matches);
        return matches;
    }

    private static void collect(Component component, char glyph, List<Component> out) {
        if (component instanceof TextComponent text) {
            String content = text.content();
            if (!content.isEmpty() && content.charAt(0) == glyph) {
                out.add(component);
            }
        }
        for (Component child : component.children()) {
            collect(child, glyph, out);
        }
    }
}
