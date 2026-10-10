package dev.terralense.hud;

/** Character allocation for the generated HUD font. */
public final class HudGlyphs {
    /** Start of the Private Use Area block used for row glyphs. */
    public static final char ROW_GLYPH_BASE = '\uE000';

    /** Start of the block used for negative-space (rewind) glyphs. */
    public static final char SPACE_GLYPH_BASE = '\uE200';

    /** Start of the block used for positive-space (skip) glyphs. */
    public static final char FORWARD_SPACE_GLYPH_BASE = '\uE280';

    /** Glyph that moves the cursor right by {@code 2^index} cell advances. */
    public static char forwardSpaceGlyph(int index) {
        requireRange(index, 0, 15, "index");
        return (char) (FORWARD_SPACE_GLYPH_BASE + index);
    }

    /** Number of power-of-two forward-skip glyphs defined in the pack. */
    public static int forwardSpaceGlyphCount() {
        return 16;
    }

    /** First character of the forward-skip glyph block. */
    public static char forwardSpaceGlyphBase() {
        return FORWARD_SPACE_GLYPH_BASE;
    }

    /** A single -1 pixel glyph, emitted after every cell. */
    public static final char KERN_GLYPH = '\uE2F0';

    /** Start of the block used for rotated player-arrow glyphs. */
    public static final char ARROW_GLYPH_BASE = '\uE300';

    /** Start of the block used for marker glyphs. */
    public static final char MARKER_GLYPH_BASE = '\uEB00';

    /** Number of discrete arrow rotations baked into the pack. */
    public static final int ARROW_STEPS = 16;

    private HudGlyphs() {
    }

    /** Glyph used to draw a cell on the given map row. */
    public static char rowGlyph(int row) {
        requireRange(row, 0, 0x7F, "row");
        return (char) (ROW_GLYPH_BASE + row);
    }

    /** Glyph that moves the cursor left by {@code 2^index} cell widths. */
    public static char spaceGlyph(int index) {
        requireRange(index, 0, 15, "index");
        return (char) (SPACE_GLYPH_BASE + index);
    }

    /** Number of power-of-two rewind glyphs defined in the pack. */
    public static int spaceGlyphCount() {
        return 16;
    }

    /** First character of the rewind glyph block. */
    public static char spaceGlyphBase() {
        return SPACE_GLYPH_BASE;
    }

    /** Glyph for the player arrow at the given rotation step. */
    public static char arrowGlyph(int step) {
        requireRange(step, 0, ARROW_STEPS - 1, "step");
        return (char) (ARROW_GLYPH_BASE + step);
    }

    /** Maps an angle in degrees to the nearest baked arrow step. */
    public static int arrowStepForAngle(float degrees) {
        float normalised = degrees % 360.0F;
        if (normalised < 0.0F) {
            normalised += 360.0F;
        }
        int step = Math.round(normalised / (360.0F / ARROW_STEPS));
        return step % ARROW_STEPS;
    }

    /** Number of distinct marker icon shapes baked into the pack. */
    public static final int MARKER_SHAPES = 4;

    /** Glyph for a marker icon on a specific map row. */
    public static char markerGlyph(int shapeIndex, int row) {
        requireRange(shapeIndex, 0, MARKER_SHAPES - 1, "shapeIndex");
        requireRange(row, 0, 0x7F, "row");
        return (char) (MARKER_GLYPH_BASE + shapeIndex * 0x80 + row);
    }

    /** Glyph for the player arrow at a rotation step, on a specific row. */
    public static char arrowGlyph(int step, int row) {
        requireRange(step, 0, ARROW_STEPS - 1, "step");
        requireRange(row, 0, 0x7F, "row");
        return (char) (ARROW_GLYPH_BASE + step * 0x80 + row);
    }

    private static void requireRange(int value, int min, int max, String name) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " out of range: " + value);
        }
    }
}
