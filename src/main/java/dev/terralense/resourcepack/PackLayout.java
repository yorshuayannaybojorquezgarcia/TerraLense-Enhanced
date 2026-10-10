package dev.terralense.resourcepack;

import dev.terralense.config.HudPosition;

/**
 * Geometry of the generated HUD font.
 *
 * @param gridSize map cells along one edge
 * @param hudSizePixels requested on-screen size in GUI pixels
 * @param marginPixels gap from the screen edge
 * @param liftCells rows of transparent padding below the map
 */
public record PackLayout(int gridSize, int hudSizePixels, int marginPixels, int liftCells) {
    /** Hard client limit on a bitmap glyph texture, in pixels, per dimension. */
    public static final int MAX_GLYPH_TEXTURE_PIXELS = 256;

    /** Texture pixels per cell. */
    public static final int CELL_TEXTURE_PIXELS = 8;

    /** Pixels of automatic spacing the client inserts between characters. */
    public static final int GLYPH_SPACING_PIXELS = 1;

    public PackLayout {
        if (gridSize < 8 || gridSize > MAX_GLYPH_TEXTURE_PIXELS / CELL_TEXTURE_PIXELS) {
            throw new IllegalArgumentException("gridSize out of range: " + gridSize);
        }
        if (hudSizePixels < 16 || hudSizePixels > 512) {
            throw new IllegalArgumentException("hudSizePixels out of range: " + hudSizePixels);
        }
        if (marginPixels < 0) {
            throw new IllegalArgumentException("marginPixels must not be negative");
        }
        if (liftCells < 0) {
            throw new IllegalArgumentException("liftCells must not be negative");
        }
        int texturePixels = (gridSize + liftCells) * CELL_TEXTURE_PIXELS;
        if (texturePixels > MAX_GLYPH_TEXTURE_PIXELS) {
            throw new IllegalArgumentException("glyph texture would be " + texturePixels
                    + "px tall, above the client limit of " + MAX_GLYPH_TEXTURE_PIXELS
                    + "; reduce gridSize or liftCells");
        }
    }

    /** Builds a layout, clamping the lift so the texture stays within the client's size limit. */
    public static PackLayout of(int gridSize, int hudSize, int margin, HudPosition position) {
        int maxCells = MAX_GLYPH_TEXTURE_PIXELS / CELL_TEXTURE_PIXELS;
        int availableLift = Math.max(0, maxCells - gridSize);
        // A top-anchored HUD asks for as much lift as the limit allows; a bottom-anchored one only
        // needs to clear the hotbar.
        int desiredLift = position.isTop() ? availableLift : Math.min(2, availableLift);
        return new PackLayout(gridSize, hudSize, margin, desiredLift);
    }

    /** On-screen size of one map cell, in GUI pixels. */
    public int cellScreenSize() {
        return Math.max(2, Math.round(hudSizePixels / (float) gridSize));
    }

    /** Rendered map size after integer cell rounding. */
    public int effectiveHudSize() {
        return cellScreenSize() * gridSize;
    }

    /** Total rows in each glyph texture: the map plus its bottom padding. */
    public int textureRows() {
        return gridSize + liftCells;
    }

    /** Texture height in pixels. */
    public int texturePixelHeight() {
        return textureRows() * CELL_TEXTURE_PIXELS;
    }

    /** Texture width in pixels: one cell. */
    public int texturePixelWidth() {
        return CELL_TEXTURE_PIXELS;
    }

    /** Declared provider height in GUI pixels. */
    public int providerHeight() {
        return textureRows() * cellScreenSize();
    }

    /** Declared ascent. */
    public int providerAscent() {
        return providerHeight();
    }

    /** Rendered width of one glyph, computed the way the client computes it. */
    public int renderedGlyphWidth() {
        return Math.round(texturePixelWidth()
                * (providerHeight() / (float) texturePixelHeight()));
    }

    /** Effective horizontal pitch of one cell, in GUI pixels. */
    public int cellAdvance() {
        return renderedGlyphWidth();
    }

    /** Advance declared for the kern glyph. */
    public int kernAdvance() {
        return -GLYPH_SPACING_PIXELS;
    }

    /** True when the texture is within the client's glyph size limit. */
    public boolean fitsGlyphLimit() {
        return texturePixelHeight() <= MAX_GLYPH_TEXTURE_PIXELS
                && texturePixelWidth() <= MAX_GLYPH_TEXTURE_PIXELS;
    }
}
