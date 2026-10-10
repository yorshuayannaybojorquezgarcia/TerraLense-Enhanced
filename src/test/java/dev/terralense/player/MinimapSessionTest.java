package dev.terralense.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.terralense.config.MapOrientation;
import dev.terralense.config.TerraLenseConfig;
import dev.terralense.player.MinimapSession.UpdateDecision;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Verifies the update-avoidance logic, which is what keeps the plugin cheap on a busy server. */
class MinimapSessionTest {
    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID WORLD = UUID.randomUUID();
    private static final UUID NETHER = UUID.randomUUID();

    private static MinimapSession session(MapOrientation orientation) {
        PlayerPreferences prefs = new PlayerPreferences(true, orientation, 2, true);
        // Smoothing 1.0 removes interpolation so decisions are deterministic.
        return new MinimapSession(PLAYER, prefs, 1.0D);
    }

    private static final TerraLenseConfig CONFIG = TerraLenseConfig.defaults();

    @Test
    @DisplayName("the first update samples terrain")
    void firstUpdateSamplesTerrain() {
        MinimapSession s = session(MapOrientation.NORTH_UP);
        assertEquals(UpdateDecision.RESAMPLE_TERRAIN, s.evaluate(WORLD, 0, 0, 0.0F, CONFIG));
        assertNotNull(s.viewport());
    }

    @Test
    @DisplayName("standing perfectly still does no work at all")
    void standingStillSkips() {
        MinimapSession s = session(MapOrientation.NORTH_UP);
        s.evaluate(WORLD, 100, 100, 45.0F, CONFIG);
        assertEquals(UpdateDecision.SKIP, s.evaluate(WORLD, 100, 100, 45.0F, CONFIG));
        assertEquals(UpdateDecision.SKIP, s.evaluate(WORLD, 100, 100, 45.0F, CONFIG));
    }

    @Test
    @DisplayName("walking within one cell does not re-sample terrain")
    void subCellMovementDoesNotResample() {
        // At zoom 2 a cell is 2 blocks wide, so a 1 block step stays inside it.
        MinimapSession s = session(MapOrientation.NORTH_UP);
        s.evaluate(WORLD, 100, 100, 0.0F, CONFIG);
        assertEquals(UpdateDecision.SKIP, s.evaluate(WORLD, 101, 100, 0.0F, CONFIG));
    }

    @Test
    @DisplayName("crossing a cell boundary re-samples terrain")
    void crossingCellResamples() {
        MinimapSession s = session(MapOrientation.NORTH_UP);
        s.evaluate(WORLD, 100, 100, 0.0F, CONFIG);
        assertEquals(UpdateDecision.RESAMPLE_TERRAIN, s.evaluate(WORLD, 140, 100, 0.0F, CONFIG));
    }

    @Test
    @DisplayName("turning in place only re-transforms, never re-samples")
    void rotationOnlyTransforms() {
        // The whole point of separating terrain from transform: spinning must not touch the world.
        MinimapSession s = session(MapOrientation.PLAYER_UP);
        s.evaluate(WORLD, 100, 100, 0.0F, CONFIG);
        assertEquals(UpdateDecision.TRANSFORM_ONLY, s.evaluate(WORLD, 100, 100, 90.0F, CONFIG));
        assertEquals(UpdateDecision.TRANSFORM_ONLY, s.evaluate(WORLD, 100, 100, 180.0F, CONFIG));
    }

    @Test
    @DisplayName("north-up mode also re-transforms on turn, to move the arrow")
    void northUpUpdatesArrowOnTurn() {
        MinimapSession s = session(MapOrientation.NORTH_UP);
        s.evaluate(WORLD, 100, 100, 0.0F, CONFIG);
        assertEquals(UpdateDecision.TRANSFORM_ONLY, s.evaluate(WORLD, 100, 100, 90.0F, CONFIG));
    }

    @Test
    @DisplayName("a terrain change forces a re-sample")
    void terrainDirtyForcesResample() {
        MinimapSession s = session(MapOrientation.NORTH_UP);
        s.evaluate(WORLD, 100, 100, 0.0F, CONFIG);
        s.markTerrainDirty();
        assertEquals(UpdateDecision.RESAMPLE_TERRAIN, s.evaluate(WORLD, 100, 100, 0.0F, CONFIG));
    }

    @Test
    @DisplayName("a config change rebuilds the HUD without re-sampling")
    void hudDirtyTransformsOnly() {
        MinimapSession s = session(MapOrientation.NORTH_UP);
        s.evaluate(WORLD, 100, 100, 0.0F, CONFIG);
        s.markHudDirty();
        assertEquals(UpdateDecision.TRANSFORM_ONLY, s.evaluate(WORLD, 100, 100, 0.0F, CONFIG));
    }

    @Test
    @DisplayName("changing zoom rebuilds the viewport and re-samples")
    void zoomChangeResamples() {
        MinimapSession s = session(MapOrientation.NORTH_UP);
        s.evaluate(WORLD, 100, 100, 0.0F, CONFIG);
        s.preferences().setZoom(8);
        assertEquals(UpdateDecision.RESAMPLE_TERRAIN, s.evaluate(WORLD, 100, 100, 0.0F, CONFIG));
        assertEquals(8, s.viewport().blocksPerCell());
    }

    @Test
    @DisplayName("a world change resets state so no terrain leaks between worlds")
    void worldChangeResets() {
        MinimapSession s = session(MapOrientation.NORTH_UP);
        s.evaluate(WORLD, 100, 100, 0.0F, CONFIG);
        assertEquals(UpdateDecision.RESAMPLE_TERRAIN, s.evaluate(NETHER, 100, 100, 0.0F, CONFIG));
        assertEquals(NETHER, s.worldId());
    }

    @Test
    @DisplayName("zoom is clamped to the configured range")
    void zoomIsClamped() {
        MinimapSession s = session(MapOrientation.NORTH_UP);
        s.preferences().setZoom(999);
        s.evaluate(WORLD, 0, 0, 0.0F, CONFIG);
        assertEquals(CONFIG.maxZoom(), s.viewport().blocksPerCell());
    }

    @Test
    @DisplayName("smoothing keeps transforming until the map settles")
    void smoothingKeepsUpdatingUntilSettled() {
        PlayerPreferences prefs = new PlayerPreferences(true, MapOrientation.PLAYER_UP, 2, false);
        MinimapSession s = new MinimapSession(PLAYER, prefs, 0.25D);
        s.evaluate(WORLD, 0, 0, 0.0F, CONFIG);
        s.evaluate(WORLD, 0, 0, 90.0F, CONFIG);

        // The player stopped turning, but the map is mid-interpolation and must keep updating until
        // it catches up.
        assertEquals(UpdateDecision.TRANSFORM_ONLY, s.evaluate(WORLD, 0, 0, 90.0F, CONFIG));
    }

    @Test
    @DisplayName("smoothed rotation eventually settles and then skips")
    void smoothingEventuallySettles() {
        PlayerPreferences prefs = new PlayerPreferences(true, MapOrientation.PLAYER_UP, 2, false);
        MinimapSession s = new MinimapSession(PLAYER, prefs, 0.5D);
        s.evaluate(WORLD, 0, 0, 0.0F, CONFIG);
        for (int i = 0; i < 60; i++) {
            s.evaluate(WORLD, 0, 0, 90.0F, CONFIG);
        }
        assertEquals(UpdateDecision.SKIP, s.evaluate(WORLD, 0, 0, 90.0F, CONFIG));
    }

    @Test
    @DisplayName("preferences track dirtiness for persistence")
    void preferencesTrackDirty() {
        PlayerPreferences prefs = new PlayerPreferences(true, MapOrientation.NORTH_UP, 2, true);
        assertEquals(false, prefs.isDirty());
        prefs.setZoom(4);
        assertEquals(true, prefs.isDirty());
        prefs.markClean();
        // Setting the same value again is not a change.
        prefs.setZoom(4);
        assertEquals(false, prefs.isDirty());
    }

    @Test
    @DisplayName("preferences are clamped to server limits after a reload")
    void preferencesClampToConfig() {
        PlayerPreferences prefs = new PlayerPreferences(true, MapOrientation.NORTH_UP, 99, true);
        prefs.clampTo(CONFIG);
        assertEquals(CONFIG.maxZoom(), prefs.zoom());
        assertNotEquals(99, prefs.zoom());
    }
}
