package dev.terralense.api.marker;

import java.util.Collection;
import java.util.UUID;

/** Supplies markers for a viewing player. */
public interface MarkerProvider {
    /** Stable identifier, used in diagnostics and to unregister. */
    String id();

    /**
     * Markers to consider for the given viewer.
     *
     * @param viewer the player the minimap belongs to
     * @param worldId the world the viewer is currently in
     */
    Collection<Marker> markersFor(UUID viewer, UUID worldId);
}
