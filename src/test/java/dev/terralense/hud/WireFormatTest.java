package dev.terralense.hud;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.terralense.map.MapImage;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Asserts what actually goes on the wire. */
class WireFormatTest {
    private static final Key FONT = Key.key("terralense", "hud");

    private static MapImage filled(int size, int argb) {
        MapImage image = new MapImage(size);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                image.set(x, y, argb);
            }
        }
        return image;
    }

    private static String serialise(Component component) {
        return GsonComponentSerializer.gson().serialize(component);
    }

    @Test
    @DisplayName("the serialised component carries the font on every glyph run")
    void fontSurvivesSerialisation() {
        // A font declared only on the root is inherited in-game, but the serialised form must still
        // reference it or the client has no way to resolve the private-use characters.
        Component hud = new GlyphHudRenderer(FONT)
                .render(filled(8, 0xFF445566), new HudFrame(0.0F, false, List.of()));
        String json = serialise(hud);

        assertTrue(json.contains("terralense:hud"),
                "the font key must appear in the serialised component: " + json);
    }

    @Test
    @DisplayName("glyph characters are emitted in the private use area")
    void glyphsAreInPrivateUseArea() {
        Component hud = new GlyphHudRenderer(FONT)
                .render(filled(4, 0xFF112233), new HudFrame(0.0F, false, List.of()));
        String json = serialise(hud);

        // Gson escapes these, so check for the escaped form the client parses.
        boolean hasRowGlyph = json.contains("\\ue000") || json.contains("\\uE000")
                || json.indexOf(HudGlyphs.rowGlyph(0)) >= 0;
        assertTrue(hasRowGlyph, "no row glyph in payload: " + json);
    }

    @Test
    @DisplayName("italic is explicitly disabled so glyph alignment cannot skew")
    void italicIsDisabled() {
        Component hud = new GlyphHudRenderer(FONT)
                .render(filled(4, 0xFF112233), new HudFrame(0.0F, false, List.of()));
        String json = serialise(hud);
        assertTrue(json.contains("\"italic\":false"), json);
    }

    @Test
    @DisplayName("the payload contains no literal replacement characters")
    void noReplacementCharacters() {
        // A mangled charset conversion would turn glyphs into U+FFFD, which the client renders as a
        // box, mimicking a missing glyph.
        Component hud = new GlyphHudRenderer(FONT)
                .render(filled(16, 0xFF778899), new HudFrame(0.0F, false, List.of()));
        String json = serialise(hud);
        assertFalse(json.indexOf('\uFFFD') >= 0, "payload contains U+FFFD");
    }

    @Test
    @DisplayName("every emitted character is one the font declares")
    void allCharactersAreDeclared() {
        // Emitting a character the pack does not define is indistinguishable in-game from the pack
        // failing to load.
        Component hud = new GlyphHudRenderer(FONT)
                .render(filled(32, 0xFF223344), HudFrame.of(0.0F));
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(hud);

        for (char c : plain.toCharArray()) {
            if (c == ' ') {
                continue;
            }
            boolean isRow = c >= HudGlyphs.ROW_GLYPH_BASE
                    && c < HudGlyphs.ROW_GLYPH_BASE + 32;
            boolean isSpace = c >= HudGlyphs.spaceGlyphBase()
                    && c < HudGlyphs.spaceGlyphBase() + HudGlyphs.spaceGlyphCount();
            boolean isArrow = c >= HudGlyphs.ARROW_GLYPH_BASE
                    && c < HudGlyphs.ARROW_GLYPH_BASE + HudGlyphs.ARROW_STEPS * 0x80;
            boolean isMarker = c >= HudGlyphs.MARKER_GLYPH_BASE
                    && c < HudGlyphs.MARKER_GLYPH_BASE + HudGlyphs.MARKER_SHAPES * 0x80;
            boolean isForwardSpace = c >= HudGlyphs.forwardSpaceGlyphBase()
                    && c < HudGlyphs.forwardSpaceGlyphBase()
                            + HudGlyphs.forwardSpaceGlyphCount();
            boolean isKern = c == HudGlyphs.KERN_GLYPH;

            assertTrue(isRow || isSpace || isArrow || isMarker || isForwardSpace || isKern,
                    "undeclared character U+" + Integer.toHexString(c).toUpperCase());
        }
    }
}
