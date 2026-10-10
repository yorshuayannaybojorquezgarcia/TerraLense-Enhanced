package dev.terralense.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HudVisualStyleTest {
    @Test void parsesStyles() {
        assertEquals(HudVisualStyle.MODERN, HudVisualStyle.fromConfig(" Modern ").orElseThrow());
        assertTrue(HudVisualStyle.fromConfig("unknown").isEmpty());
    }
    @Test void preservesClassicAndRemovesMinimalBorder() {
        assertEquals(HudShape.SQUARE, HudVisualStyle.CLASSIC.effectiveShape(HudShape.SQUARE));
        assertEquals(0, HudVisualStyle.MINIMAL.effectiveBorder(0xFF123456));
        assertEquals(0xFF123456, HudVisualStyle.CLASSIC.effectiveBorder(0xFF123456));
    }
}
