package dev.terralense.player;

import dev.terralense.config.MapOrientation;
import dev.terralense.config.TerraLenseConfig;
import dev.terralense.map.MapViewport;
import dev.terralense.map.rotation.RotationEngine;
import java.util.UUID;
import net.kyori.adventure.text.Component;

/** Per-player minimap state. */
public final class MinimapSession {
    private final UUID playerId;
    private final PlayerPreferences preferences;
    private final RotationEngine rotation;

    /** How long the HUD may go without being resent, in milliseconds. */
    private static final long KEEPALIVE_MILLIS = 1_500L;

    private UUID worldId;
    private MapViewport viewport;
    private float lastYaw;
    private boolean terrainDirty = true;
    private boolean hudDirty = true;
    private boolean renderInFlight;
    private long lastRenderNanos;
    private long lastSentMillis;
    private Component lastFrame;

    public MinimapSession(UUID playerId, PlayerPreferences preferences, double rotationSmoothing) {
        this.playerId = playerId;
        this.preferences = preferences;
        this.rotation = new RotationEngine(rotationSmoothing);
    }

    public UUID playerId() {
        return playerId;
    }

    public PlayerPreferences preferences() {
        return preferences;
    }

    public RotationEngine rotationEngine() {
        return rotation;
    }

    public MapViewport viewport() {
        return viewport;
    }

    public UUID worldId() {
        return worldId;
    }

    public boolean isRenderInFlight() {
        return renderInFlight;
    }

    public void setRenderInFlight(boolean inFlight) {
        this.renderInFlight = inFlight;
    }

    public long lastRenderNanos() {
        return lastRenderNanos;
    }

    public void recordRender(long durationNanos) {
        this.lastRenderNanos = durationNanos;
    }

    /** Forces a terrain re-sample on the next update. */
    public void markTerrainDirty() {
        this.terrainDirty = true;
    }

    /** Forces the HUD to be rebuilt, e.g. after a config change. */
    public void markHudDirty() {
        this.hudDirty = true;
    }

    /**
     * Resets positional state after a world change or teleport, so the map does not sweep across
     * the world or briefly show the previous dimension.
     */
    public void resetFor(UUID newWorldId) {
        this.worldId = newWorldId;
        this.viewport = null;
        this.terrainDirty = true;
        this.hudDirty = true;
        this.rotation.reset();
    }

    /**
     * Decides what work this update needs.
     *
     * @param blockX current player block X
     * @param blockZ current player block Z
     * @param yaw current player yaw
     */
    public UpdateDecision evaluate(UUID currentWorldId,
                                   int blockX,
                                   int blockZ,
                                   float yaw,
                                   TerraLenseConfig config) {
        if (!currentWorldId.equals(worldId)) {
            resetFor(currentWorldId);
        }

        int zoom = config.clampZoom(preferences.zoom());
        boolean viewportChanged = false;

        if (viewport == null || viewport.blocksPerCell() != zoom
                || viewport.gridSize() != config.hudGridSize()) {
            viewport = new MapViewport(blockX, blockZ, config.hudGridSize(), zoom);
            viewportChanged = true;
        } else if (viewport.requiresResample(blockX, blockZ)) {
            viewport = viewport.centeredOn(blockX, blockZ);
            viewportChanged = true;
        }

        float targetRotation = preferences.orientation() == MapOrientation.PLAYER_UP
                ? RotationEngine.terrainRotationForYaw(yaw)
                : 0.0F;
        boolean rotationSettled = rotation.isSettled(targetRotation);
        rotation.approach(targetRotation);

        boolean yawChanged = Math.abs(RotationEngine.shortestDelta(lastYaw, yaw))
                >= RotationEngine.SETTLED_EPSILON;
        lastYaw = yaw;

        boolean needsTerrain = terrainDirty || viewportChanged;
        boolean needsTransform = !rotationSettled || yawChanged || hudDirty;

        if (needsTerrain) {
            terrainDirty = false;
            hudDirty = false;
            return UpdateDecision.RESAMPLE_TERRAIN;
        }
        if (needsTransform) {
            hudDirty = false;
            return UpdateDecision.TRANSFORM_ONLY;
        }
        // Nothing changed, but the action bar fades on its own, so the last frame has to be resent
        // periodically to keep the map visible.
        if (needsKeepalive()) {
            return UpdateDecision.RESEND;
        }
        return UpdateDecision.SKIP;
    }

    /** The cheapest action that will produce a correct frame. */
    public enum UpdateDecision {
        /** Nothing changed and the HUD is still on screen; do nothing. */
        SKIP,

        /**
         * Nothing changed, but the action bar is about to fade; resend the last rendered frame
         * without re-rendering anything.
         */
        RESEND,

        /** Reuse cached terrain and re-apply rotation/markers. */
        TRANSFORM_ONLY,

        /** Terrain must be re-sampled from the world. */
        RESAMPLE_TERRAIN
    }

    /** The most recently rendered frame, or {@code null} before the first render. */
    public Component lastFrame() {
        return lastFrame;
    }

    /** Records a delivered frame so it can be resent as a keepalive. */
    public void recordSent(Component frame) {
        this.lastFrame = frame;
        this.lastSentMillis = System.currentTimeMillis();
    }

    /** True when the action bar is close enough to fading that it needs a resend. */
    private boolean needsKeepalive() {
        return lastFrame != null
                && System.currentTimeMillis() - lastSentMillis >= KEEPALIVE_MILLIS;
    }
}
