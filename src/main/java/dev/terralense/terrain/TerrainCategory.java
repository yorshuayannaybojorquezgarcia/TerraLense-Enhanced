package dev.terralense.terrain;

import java.util.Locale;

/** Coarse terrain classification used by the renderer. */
public enum TerrainCategory {
    /** No terrain sampled (ungenerated/unloaded chunk, or masked out). */
    UNKNOWN,

    GRASS,
    FOLIAGE,
    LEAVES,
    DIRT,
    FARMLAND,
    PATH,
    SAND,
    GRAVEL,
    CLAY,
    STONE,
    DEEPSLATE,
    ORE,
    WATER,
    ICE,
    SNOW,
    LAVA,
    WOOD,
    NETHER,
    END,
    STRUCTURE,
    METAL;

    public String configName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Liquids are excluded from slope shading so water reads as a flat surface. */
    public boolean isLiquid() {
        return this == WATER || this == LAVA;
    }
}
