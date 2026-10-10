package dev.terralense.marker;

import dev.terralense.api.marker.Marker;
import dev.terralense.api.marker.MarkerProvider;
import dev.terralense.api.marker.MarkerType;
import dev.terralense.storage.Waypoint;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Waypoint markers, served from an in-memory cache. */
public final class WaypointMarkerProvider implements MarkerProvider {
    private final Map<UUID, List<Waypoint>> byOwner = new ConcurrentHashMap<>();

    @Override
    public String id() {
        return "terralense:waypoints";
    }

    /** Replaces the cached waypoints for one player. */
    public void put(UUID owner, List<Waypoint> waypoints) {
        byOwner.put(owner, List.copyOf(waypoints));
    }

    /** Drops a player's cache, on quit. */
    public void remove(UUID owner) {
        byOwner.remove(owner);
    }

    public List<Waypoint> cached(UUID owner) {
        return byOwner.getOrDefault(owner, List.of());
    }

    public void clear() {
        byOwner.clear();
    }

    @Override
    public Collection<Marker> markersFor(UUID viewerId, UUID worldId) {
        List<Waypoint> waypoints = byOwner.get(viewerId);
        if (waypoints == null || waypoints.isEmpty()) {
            return List.of();
        }
        List<Marker> markers = new ArrayList<>(waypoints.size());
        for (Waypoint waypoint : waypoints) {
            if (!waypoint.worldId().equals(worldId)) {
                continue;
            }
            markers.add(Marker.builder(waypoint.name(), MarkerType.WAYPOINT, worldId)
                    .position(waypoint.x(), waypoint.y(), waypoint.z())
                    .color(waypoint.color())
                    .visibility(waypoint.visibility())
                    .owner(waypoint.owner())
                    .label(waypoint.name())
                    .build());
        }
        return markers;
    }
}
