package dev.terralense.terrain;

/**
 * Visual treatment for a terrain category: a base colour plus how strongly it reacts to height
 * shading.
 *
 * @param argb packed 0xAARRGGBB base colour
 * @param shadeFactor multiplier applied to the global shading strength; 0 keeps the surface flat
 *     (used for liquids)
 */
public record TerrainStyle(int argb, double shadeFactor) {
    public TerrainStyle {
        if (shadeFactor < 0.0D || shadeFactor > 2.0D) {
            throw new IllegalArgumentException("shadeFactor out of range: " + shadeFactor);
        }
    }

    public static TerrainStyle of(int rgb) {
        return new TerrainStyle(0xFF00_0000 | (rgb & 0x00FF_FFFF), 1.0D);
    }

    public static TerrainStyle of(int rgb, double shadeFactor) {
        return new TerrainStyle(0xFF00_0000 | (rgb & 0x00FF_FFFF), shadeFactor);
    }

    /** Fully transparent style used for "nothing here". */
    public static TerrainStyle transparent() {
        return new TerrainStyle(0x0000_0000, 0.0D);
    }

    public int alpha() {
        return (argb >>> 24) & 0xFF;
    }

    public int red() {
        return (argb >>> 16) & 0xFF;
    }

    public int green() {
        return (argb >>> 8) & 0xFF;
    }

    public int blue() {
        return argb & 0xFF;
    }
}
