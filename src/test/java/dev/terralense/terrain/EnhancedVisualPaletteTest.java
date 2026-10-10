package dev.terralense.terrain;

import dev.terralense.config.HudVisualStyle;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnhancedVisualPaletteTest {
    @Test
    void presetsHaveDifferentGrassAndWaterColors() {
        var modern = TerrainColorResolver.create(HudVisualStyle.MODERN, false, 0);
        var classic = TerrainColorResolver.create(HudVisualStyle.CLASSIC, false, 0);
        var minimal = TerrainColorResolver.create(HudVisualStyle.MINIMAL, false, 0);
        assertNotEquals(classic.resolve(TerrainCategory.GRASS, 0), modern.resolve(TerrainCategory.GRASS, 0));
        assertNotEquals(modern.resolve(TerrainCategory.WATER, 0), minimal.resolve(TerrainCategory.WATER, 0));
        assertEquals(0, modern.resolve(TerrainCategory.UNKNOWN, 0));
    }
}
