package dev.terralense.map.rotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RotationEngineTest {
    private static final float EPSILON = 0.001F;

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "90, 90",
            "359, 359",
            "360, 0",
            "361, 1",
            "720, 0",
            "-1, 359",
            "-90, 270",
            "-360, 0",
            "-361, 359",
            "1080, 0",
    })
    @DisplayName("normalize maps any yaw into [0, 360)")
    void normalizeWrapsIntoRange(float input, float expected) {
        assertEquals(expected, RotationEngine.normalize(input), EPSILON);
    }

    @Test
    @DisplayName("normalize never returns exactly 360")
    void normalizeNeverReturnsFullTurn() {
        // Minecraft yaw drifts arbitrarily far; a tiny negative must not round up to 360 and
        // reintroduce the discontinuity we are trying to remove.
        assertEquals(0.0F, RotationEngine.normalize(-0.0000001F), EPSILON);
        for (float yaw = -1000.0F; yaw <= 1000.0F; yaw += 7.3F) {
            float result = RotationEngine.normalize(yaw);
            assertTrue(result >= 0.0F && result < 360.0F, "out of range for " + yaw + ": " + result);
        }
    }

    @Test
    @DisplayName("non-finite yaw degrades to zero instead of poisoning the render")
    void normalizeHandlesNonFinite() {
        assertEquals(0.0F, RotationEngine.normalize(Float.NaN), EPSILON);
        assertEquals(0.0F, RotationEngine.normalize(Float.POSITIVE_INFINITY), EPSILON);
    }

    @ParameterizedTest
    @CsvSource({
            "359, 0, 1",
            "0, 359, -1",
            "350, 10, 20",
            "10, 350, -20",
            "0, 90, 90",
            "0, 180, 180",
            "0, 181, -179",
            "90, 270, 180",
            "270, 90, 180",
    })
    @DisplayName("shortestDelta always takes the short way round")
    void shortestDeltaTakesShortestPath(float from, float to, float expected) {
        assertEquals(expected, RotationEngine.shortestDelta(from, to), EPSILON);
    }

    @Test
    @DisplayName("359 to 0 is a one degree move, not 359")
    void wraparoundIsNotTheLongWay() {
        float delta = RotationEngine.shortestDelta(359.0F, 0.0F);
        assertEquals(1.0F, delta, EPSILON);
        assertTrue(Math.abs(delta) <= 180.0F);
    }

    @Test
    @DisplayName("shortestDelta stays within (-180, 180] for arbitrary input")
    void shortestDeltaBounded() {
        for (float from = 0.0F; from < 360.0F; from += 11.0F) {
            for (float to = 0.0F; to < 360.0F; to += 13.0F) {
                float delta = RotationEngine.shortestDelta(from, to);
                assertTrue(delta > -180.0F && delta <= 180.0F,
                        "delta " + delta + " for " + from + "->" + to);
            }
        }
    }

    @Test
    @DisplayName("first update snaps so a joining player sees the correct angle immediately")
    void firstApproachSnaps() {
        RotationEngine engine = new RotationEngine(0.25D);
        assertEquals(137.0F, engine.approach(137.0F), EPSILON);
    }

    @Test
    @DisplayName("smoothing moves a fraction of the delta each step")
    void approachInterpolates() {
        RotationEngine engine = new RotationEngine(0.5D);
        engine.snapTo(0.0F);
        assertEquals(45.0F, engine.approach(90.0F), EPSILON);
        assertEquals(67.5F, engine.approach(90.0F), EPSILON);
    }

    @Test
    @DisplayName("smoothing crosses the 0/360 seam without spinning backwards")
    void approachCrossesSeamSmoothly() {
        RotationEngine engine = new RotationEngine(0.5D);
        engine.snapTo(358.0F);
        float next = engine.approach(2.0F);
        // Half of the +4 degree delta from 358 lands on 0.
        assertEquals(0.0F, next, EPSILON);
    }

    @Test
    @DisplayName("smoothing converges and reports settled")
    void approachConverges() {
        RotationEngine engine = new RotationEngine(0.4D);
        engine.snapTo(0.0F);
        for (int i = 0; i < 100; i++) {
            engine.approach(123.0F);
        }
        assertEquals(123.0F, engine.current(), EPSILON);
        assertTrue(engine.isSettled(123.0F));
        assertFalse(engine.isSettled(10.0F));
    }

    @Test
    @DisplayName("smoothing of 1.0 snaps directly")
    void smoothingOneSnaps() {
        RotationEngine engine = new RotationEngine(1.0D);
        engine.snapTo(10.0F);
        assertEquals(200.0F, engine.approach(200.0F), EPSILON);
    }

    @Test
    @DisplayName("reset drops smoothing state so a teleport does not sweep the map")
    void resetClearsState() {
        RotationEngine engine = new RotationEngine(0.2D);
        engine.snapTo(90.0F);
        engine.reset();
        assertEquals(0.0F, engine.current(), EPSILON);
        // After a reset the next update snaps rather than interpolating.
        assertEquals(300.0F, engine.approach(300.0F), EPSILON);
    }

    @Test
    @DisplayName("invalid smoothing is rejected at construction")
    void rejectsInvalidSmoothing() {
        assertThrows(IllegalArgumentException.class, () -> new RotationEngine(0.0D));
        assertThrows(IllegalArgumentException.class, () -> new RotationEngine(-0.5D));
        assertThrows(IllegalArgumentException.class, () -> new RotationEngine(1.5D));
    }

    @Test
    @DisplayName("arrow bearing accounts for yaw 0 facing south")
    void arrowBearingMatchesMinecraftYaw() {
        // Yaw 0 (south) must draw the arrow pointing down the screen.
        assertEquals(180.0F, RotationEngine.arrowBearingForYaw(0.0F), EPSILON);
        // Yaw 180 (north) points up.
        assertEquals(0.0F, RotationEngine.arrowBearingForYaw(180.0F), EPSILON);
        assertEquals(270.0F, RotationEngine.arrowBearingForYaw(90.0F), EPSILON);
    }

    @Test
    @DisplayName("terrain rotation offsets yaw by 180 so screen-up is the facing direction")
    void terrainRotationOffsetsByHalfTurn() {
        // Minecraft yaw 0 faces south, so a naive identity mapping renders the map upside down and
        // reversed.
        assertEquals(180.0F, RotationEngine.terrainRotationForYaw(0.0F), EPSILON);
        assertEquals(0.0F, RotationEngine.terrainRotationForYaw(180.0F), EPSILON);
        assertEquals(225.0F, RotationEngine.terrainRotationForYaw(45.0F), EPSILON);
        assertEquals(170.0F, RotationEngine.terrainRotationForYaw(-10.0F), EPSILON);
    }

    @Test
    @DisplayName("rotation increases with yaw, so the map follows the player's turn")
    void rotationFollowsTurnDirection() {
        // A right turn increases yaw; the sampling angle must increase with it.
        float before = RotationEngine.terrainRotationForYaw(10.0F);
        float after = RotationEngine.terrainRotationForYaw(40.0F);
        assertEquals(30.0F, RotationEngine.shortestDelta(before, after), EPSILON);
    }
}
