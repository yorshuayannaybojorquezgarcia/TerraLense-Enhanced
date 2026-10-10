package dev.terralense.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TerrainColorResolverTest {
    @Test
    @DisplayName("unknown terrain resolves to fully transparent")
    void unknownIsTransparent() {
        TerrainColorResolver resolver = TerrainColorResolver.create(true, 0.25D);
        assertEquals(0, resolver.resolve(TerrainCategory.UNKNOWN, 0));
        assertEquals(0, resolver.resolve(TerrainCategory.UNKNOWN, 5));
    }

    @Test
    @DisplayName("every drawable category has an opaque colour")
    void everyCategoryHasColour() {
        TerrainColorResolver resolver = TerrainColorResolver.create(false, 0.0D);
        for (TerrainCategory category : TerrainCategory.values()) {
            int argb = resolver.resolve(category, 0);
            if (category == TerrainCategory.UNKNOWN) {
                continue;
            }
            int alpha = (argb >>> 24) & 0xFF;
            assertEquals(0xFF, alpha, "category " + category + " must be opaque");
        }
    }

    @Test
    @DisplayName("categories are visually distinct so terrain stays readable")
    void coloursAreDistinct() {
        TerrainColorResolver resolver = TerrainColorResolver.create(false, 0.0D);
        Set<Integer> seen = new HashSet<>();
        for (TerrainCategory category : TerrainCategory.values()) {
            if (category == TerrainCategory.UNKNOWN) {
                continue;
            }
            int argb = resolver.resolve(category, 0);
            assertTrue(seen.add(argb),
                    "duplicate colour " + Integer.toHexString(argb) + " for " + category);
        }
    }

    @Test
    @DisplayName("height shading lightens upslope and darkens downslope")
    void shadingRespondsToSlope() {
        TerrainColorResolver resolver = TerrainColorResolver.create(true, 0.5D);
        int flat = resolver.resolve(TerrainCategory.STONE, 0);
        int up = resolver.resolve(TerrainCategory.STONE, 4);
        int down = resolver.resolve(TerrainCategory.STONE, -4);

        assertNotEquals(flat, up);
        assertNotEquals(flat, down);
        assertTrue(luminance(up) > luminance(flat), "rising terrain should be lighter");
        assertTrue(luminance(down) < luminance(flat), "falling terrain should be darker");
    }

    @Test
    @DisplayName("shading is skipped entirely when disabled")
    void shadingCanBeDisabled() {
        TerrainColorResolver resolver = TerrainColorResolver.create(false, 0.9D);
        assertEquals(resolver.resolve(TerrainCategory.STONE, 0),
                resolver.resolve(TerrainCategory.STONE, 8));
    }

    @Test
    @DisplayName("liquids never receive slope shading")
    void liquidsStayFlat() {
        TerrainColorResolver resolver = TerrainColorResolver.create(true, 1.0D);
        assertEquals(resolver.resolve(TerrainCategory.WATER, 0),
                resolver.resolve(TerrainCategory.WATER, 6));
        assertEquals(resolver.resolve(TerrainCategory.LAVA, 0),
                resolver.resolve(TerrainCategory.LAVA, -6));
    }

    @Test
    @DisplayName("extreme slope cannot push channels outside 0-255")
    void shadingClampsChannels() {
        TerrainColorResolver resolver = TerrainColorResolver.create(true, 1.0D);
        for (int delta = -512; delta <= 512; delta += 16) {
            int argb = resolver.resolve(TerrainCategory.SNOW, delta);
            assertChannelsInRange(argb);
            assertChannelsInRange(resolver.resolve(TerrainCategory.DEEPSLATE, delta));
        }
    }

    @Test
    @DisplayName("shading strength is clamped to a sane range on construction")
    void strengthIsClamped() {
        TerrainColorResolver over = TerrainColorResolver.create(true, 99.0D);
        assertChannelsInRange(over.resolve(TerrainCategory.GRASS, 4));

        TerrainColorResolver under = TerrainColorResolver.create(true, -5.0D);
        // Negative strength clamps to zero, which disables shading.
        assertEquals(under.resolve(TerrainCategory.GRASS, 0),
                under.resolve(TerrainCategory.GRASS, 4));
    }

    @Test
    @DisplayName("palette overrides replace only the named categories")
    void overridesLayerOnDefaults() {
        Map<TerrainCategory, TerrainStyle> overrides = new EnumMap<>(TerrainCategory.class);
        overrides.put(TerrainCategory.GRASS, TerrainStyle.of(0x010203));

        TerrainColorResolver custom = TerrainColorResolver.create(overrides, false, 0.0D);
        TerrainColorResolver stock = TerrainColorResolver.create(false, 0.0D);

        assertEquals(0xFF010203, custom.resolve(TerrainCategory.GRASS, 0));
        assertEquals(stock.resolve(TerrainCategory.STONE, 0), custom.resolve(TerrainCategory.STONE, 0));
    }

    @Test
    @DisplayName("style rejects an out-of-range shade factor")
    void styleValidatesShadeFactor() {
        assertThrows(IllegalArgumentException.class, () -> new TerrainStyle(0xFFFFFFFF, -0.1D));
        assertThrows(IllegalArgumentException.class, () -> new TerrainStyle(0xFFFFFFFF, 2.5D));
    }

    @Test
    @DisplayName("of() forces full opacity so terrain is never accidentally invisible")
    void ofForcesOpacity() {
        assertEquals(0xFF, TerrainStyle.of(0x000000).alpha());
        assertEquals(0, TerrainStyle.transparent().alpha());
    }

    private static void assertChannelsInRange(int argb) {
        for (int shift : new int[] {16, 8, 0}) {
            int channel = (argb >>> shift) & 0xFF;
            assertTrue(channel >= 0 && channel <= 255, "channel out of range: " + channel);
        }
    }

    private static int luminance(int argb) {
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        return r + g + b;
    }
}
