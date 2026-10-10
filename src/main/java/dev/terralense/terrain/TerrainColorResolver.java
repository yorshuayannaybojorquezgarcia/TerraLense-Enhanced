package dev.terralense.terrain;

import java.util.EnumMap;
import java.util.Map;

/** Resolves a terrain sample to a final packed ARGB pixel. */
public final class TerrainColorResolver {
    private final Map<TerrainCategory, TerrainStyle> styles;
    private final boolean heightShading;
    private final double shadingStrength;

    private TerrainColorResolver(Map<TerrainCategory, TerrainStyle> styles,
                                 boolean heightShading,
                                 double shadingStrength) {
        this.styles = new EnumMap<>(styles);
        this.heightShading = heightShading;
        this.shadingStrength = shadingStrength;
    }

    /**
     * Creates a resolver using the built-in palette.
     *
     * @param heightShading whether slope shading is applied
     * @param shadingStrength 0..1 global shading intensity
     */
    public static TerrainColorResolver create(boolean heightShading, double shadingStrength) {
        return new TerrainColorResolver(defaultPalette(), heightShading,
                Math.clamp(shadingStrength, 0.0D, 1.0D));
    }

    /**
     * Creates a resolver with palette overrides layered onto the defaults, so an operator only
     * needs to specify the categories they want to change.
     */
    public static TerrainColorResolver create(Map<TerrainCategory, TerrainStyle> overrides,
                                              boolean heightShading,
                                              double shadingStrength) {
        Map<TerrainCategory, TerrainStyle> merged = defaultPalette();
        if (overrides != null) {
            merged.putAll(overrides);
        }
        return new TerrainColorResolver(merged, heightShading,
                Math.clamp(shadingStrength, 0.0D, 1.0D));
    }

    /** The default palette. */
    private static Map<TerrainCategory, TerrainStyle> defaultPalette() {
        Map<TerrainCategory, TerrainStyle> palette = new EnumMap<>(TerrainCategory.class);
        palette.put(TerrainCategory.UNKNOWN, TerrainStyle.transparent());
        palette.put(TerrainCategory.GRASS, TerrainStyle.of(0x63_A34E));
        palette.put(TerrainCategory.FOLIAGE, TerrainStyle.of(0x79_B65C));
        palette.put(TerrainCategory.LEAVES, TerrainStyle.of(0x42_803A));
        palette.put(TerrainCategory.DIRT, TerrainStyle.of(0x8B_6A47));
        palette.put(TerrainCategory.FARMLAND, TerrainStyle.of(0x9C_6B3F));
        palette.put(TerrainCategory.PATH, TerrainStyle.of(0xA3_8A63));
        palette.put(TerrainCategory.SAND, TerrainStyle.of(0xE0_D2A0));
        palette.put(TerrainCategory.GRAVEL, TerrainStyle.of(0x9A_9791));
        palette.put(TerrainCategory.CLAY, TerrainStyle.of(0xA6_8A75));
        palette.put(TerrainCategory.STONE, TerrainStyle.of(0x8C_8C8C));
        palette.put(TerrainCategory.DEEPSLATE, TerrainStyle.of(0x53_5459));
        palette.put(TerrainCategory.ORE, TerrainStyle.of(0xB8_A05A));
        palette.put(TerrainCategory.WATER, TerrainStyle.of(0x36_78B8, 0.0D));
        palette.put(TerrainCategory.ICE, TerrainStyle.of(0xB4_D8E8));
        palette.put(TerrainCategory.SNOW, TerrainStyle.of(0xF2_F5F7));
        palette.put(TerrainCategory.LAVA, TerrainStyle.of(0xED_651D, 0.0D));
        palette.put(TerrainCategory.WOOD, TerrainStyle.of(0x8A_6234));
        palette.put(TerrainCategory.NETHER, TerrainStyle.of(0x82_3834));
        palette.put(TerrainCategory.END, TerrainStyle.of(0xDE_DBB0));
        palette.put(TerrainCategory.STRUCTURE, TerrainStyle.of(0xB0_A9A0));
        palette.put(TerrainCategory.METAL, TerrainStyle.of(0xC9_C2B4));
        return palette;
    }

    public TerrainStyle styleOf(TerrainCategory category) {
        TerrainStyle style = styles.get(category);
        return style != null ? style : styles.get(TerrainCategory.UNKNOWN);
    }

    /**
     * Resolves a pixel for one terrain cell.
     *
     * @param category terrain classification of the cell
     * @param heightDelta signed height difference against the neighbouring cell used for slope
     *     shading (north/up neighbour)
     * @return packed ARGB colour
     */
    public int resolve(TerrainCategory category, int heightDelta) {
        TerrainStyle style = styleOf(category);
        if (style.alpha() == 0) {
            return 0;
        }
        if (!heightShading || shadingStrength <= 0.0D || style.shadeFactor() <= 0.0D
                || category.isLiquid() || heightDelta == 0) {
            return style.argb();
        }
        return applyShade(style, heightDelta);
    }

    private int applyShade(TerrainStyle style, int heightDelta) {
        // Compress the height delta so a cliff does not blow out to pure black or white.
        double normalised = Math.clamp(heightDelta / 3.0D, -1.0D, 1.0D);
        double factor = 1.0D + normalised * shadingStrength * style.shadeFactor();

        int r = scaleChannel(style.red(), factor);
        int g = scaleChannel(style.green(), factor);
        int b = scaleChannel(style.blue(), factor);
        return (style.alpha() << 24) | (r << 16) | (g << 8) | b;
    }

    private static int scaleChannel(int value, double factor) {
        return Math.clamp((int) Math.round(value * factor), 0, 255);
    }
}
