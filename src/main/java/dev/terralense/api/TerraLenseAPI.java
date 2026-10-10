package dev.terralense.api;

import dev.terralense.api.marker.MarkerRegistry;

/** Public entry point for other plugins. */
public final class TerraLenseAPI {
    private static volatile TerraLenseAPI instance;

    private final MarkerRegistry markers;

    private TerraLenseAPI(MarkerRegistry markers) {
        this.markers = markers;
    }

    /** Registry for contributing markers to the minimap. */
    public MarkerRegistry markers() {
        return markers;
    }

    /**
     * The active API instance.
     *
     * @throws IllegalStateException if TerraLense is not enabled
     */
    public static TerraLenseAPI get() {
        TerraLenseAPI current = instance;
        if (current == null) {
            throw new IllegalStateException(
                    "TerraLense is not enabled; check TerraLenseAPI.isAvailable() first");
        }
        return current;
    }

    /** Whether the API is ready to use. */
    public static boolean isAvailable() {
        return instance != null;
    }

    /** Called by TerraLense on enable. */
    public static void install(MarkerRegistry markers) {
        instance = new TerraLenseAPI(markers);
    }

    /** Called by TerraLense on disable. */
    public static void uninstall() {
        instance = null;
    }
}
