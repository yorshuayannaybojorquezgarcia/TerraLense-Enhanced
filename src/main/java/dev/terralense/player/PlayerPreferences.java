package dev.terralense.player;

import dev.terralense.config.MapOrientation;
import dev.terralense.config.TerraLenseConfig;

/** Per-player settings, mutable at runtime and persisted asynchronously. */
public final class PlayerPreferences {
    private boolean minimapEnabled;
    private MapOrientation orientation;
    private int zoom;
    private boolean radarEnabled;

    /** Per-player horizontal nudge, in cells, added to the configured offset. */
    private int offsetNudge;

    private boolean dirty;

    public PlayerPreferences(boolean minimapEnabled,
                             MapOrientation orientation,
                             int zoom,
                             boolean radarEnabled) {
        this.minimapEnabled = minimapEnabled;
        this.orientation = orientation;
        this.zoom = zoom;
        this.radarEnabled = radarEnabled;
    }

    public int offsetNudge() {
        return offsetNudge;
    }

    public void setOffsetNudge(int offsetNudge) {
        int clamped = Math.clamp(offsetNudge, -160, 160);
        if (this.offsetNudge != clamped) {
            this.offsetNudge = clamped;
            this.dirty = true;
        }
    }

    /** Defaults taken from server configuration, for a first-time player. */
    public static PlayerPreferences defaultsFrom(TerraLenseConfig config) {
        return new PlayerPreferences(config.hudEnabled(), config.orientation(),
                config.defaultZoom(), config.markerPlayers());
    }

    public boolean minimapEnabled() {
        return minimapEnabled;
    }

    public void setMinimapEnabled(boolean enabled) {
        if (this.minimapEnabled != enabled) {
            this.minimapEnabled = enabled;
            this.dirty = true;
        }
    }

    public MapOrientation orientation() {
        return orientation;
    }

    public void setOrientation(MapOrientation orientation) {
        if (this.orientation != orientation) {
            this.orientation = orientation;
            this.dirty = true;
        }
    }

    public int zoom() {
        return zoom;
    }

    public void setZoom(int zoom) {
        if (this.zoom != zoom) {
            this.zoom = zoom;
            this.dirty = true;
        }
    }

    public boolean radarEnabled() {
        return radarEnabled;
    }

    public void setRadarEnabled(boolean radarEnabled) {
        if (this.radarEnabled != radarEnabled) {
            this.radarEnabled = radarEnabled;
            this.dirty = true;
        }
    }

    /** True when there are unsaved changes worth writing. */
    public boolean isDirty() {
        return dirty;
    }

    public void markClean() {
        this.dirty = false;
    }

    /** Brings stored preferences back within the bounds the server currently allows. */
    public void clampTo(TerraLenseConfig config) {
        int clamped = config.clampZoom(zoom);
        if (clamped != zoom) {
            zoom = clamped;
            dirty = true;
        }
        if (!config.markerPlayers() && radarEnabled) {
            // The operator disabled the radar; drop the preference so it does not silently re-
            // enable if the setting comes back.
            radarEnabled = false;
        }
    }
}
