package dev.terralense.storage;

import dev.terralense.marker.WaypointMarkerProvider;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Waypoint operations used by commands. */
public interface WaypointService {
    /** Per-player waypoint cap. */
    int MAX_PER_PLAYER = 64;

    /** Attempts to create a waypoint. */
    CompletableFuture<AddOutcome> add(Waypoint waypoint);

    /** Removes a waypoint by name. */
    CompletableFuture<Boolean> remove(UUID owner, String name);

    /** Finds one waypoint by name. */
    CompletableFuture<Optional<Waypoint>> find(UUID owner, String name);

    /** Lists a player's waypoints. */
    CompletableFuture<List<Waypoint>> list(UUID owner);

    /** Whether waypoints survive a restart. */
    boolean isPersistent();

    /** Result of an add attempt. */
    enum AddOutcome {
        CREATED,
        DUPLICATE,
        LIMIT_REACHED,
        FAILED
    }

    /** Session-only implementation used when storage is unavailable. */
    static WaypointService inMemory(WaypointMarkerProvider markers) {
        return new SessionWaypointService(markers);
    }

    /** Database-backed implementation. */
    static WaypointService persistent(WaypointRepository repository,
                                      StorageService storage,
                                      WaypointMarkerProvider markers) {
        return new SqlWaypointService(repository, storage, markers);
    }
}
