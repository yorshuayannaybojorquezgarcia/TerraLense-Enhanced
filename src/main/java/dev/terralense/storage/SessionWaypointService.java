package dev.terralense.storage;

import dev.terralense.marker.WaypointMarkerProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory {@link WaypointService} used when the database is unavailable. */
public final class SessionWaypointService implements WaypointService {
    private final Map<UUID, List<Waypoint>> byOwner = new ConcurrentHashMap<>();
    private final WaypointMarkerProvider markers;

    public SessionWaypointService(WaypointMarkerProvider markers) {
        this.markers = markers;
    }

    @Override
    public CompletableFuture<AddOutcome> add(Waypoint waypoint) {
        UUID owner = waypoint.owner();
        AddOutcome outcome;
        synchronized (this) {
            List<Waypoint> existing = new ArrayList<>(byOwner.getOrDefault(owner, List.of()));
            if (existing.size() >= MAX_PER_PLAYER) {
                outcome = AddOutcome.LIMIT_REACHED;
            } else if (containsName(existing, waypoint.name())) {
                outcome = AddOutcome.DUPLICATE;
            } else {
                existing.add(waypoint);
                byOwner.put(owner, List.copyOf(existing));
                markers.put(owner, existing);
                outcome = AddOutcome.CREATED;
            }
        }
        return CompletableFuture.completedFuture(outcome);
    }

    @Override
    public CompletableFuture<Boolean> remove(UUID owner, String name) {
        boolean removed;
        synchronized (this) {
            List<Waypoint> existing = new ArrayList<>(byOwner.getOrDefault(owner, List.of()));
            removed = existing.removeIf(waypoint -> waypoint.name().equalsIgnoreCase(name));
            if (removed) {
                byOwner.put(owner, List.copyOf(existing));
                markers.put(owner, existing);
            }
        }
        return CompletableFuture.completedFuture(removed);
    }

    @Override
    public CompletableFuture<Optional<Waypoint>> find(UUID owner, String name) {
        return CompletableFuture.completedFuture(
                byOwner.getOrDefault(owner, List.of()).stream()
                        .filter(waypoint -> waypoint.name().equalsIgnoreCase(name))
                        .findFirst());
    }

    @Override
    public CompletableFuture<List<Waypoint>> list(UUID owner) {
        return CompletableFuture.completedFuture(byOwner.getOrDefault(owner, List.of()));
    }

    @Override
    public boolean isPersistent() {
        return false;
    }

    private static boolean containsName(List<Waypoint> waypoints, String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (Waypoint waypoint : waypoints) {
            if (waypoint.name().toLowerCase(Locale.ROOT).equals(lower)) {
                return true;
            }
        }
        return false;
    }
}
