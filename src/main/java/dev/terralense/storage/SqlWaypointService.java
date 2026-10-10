package dev.terralense.storage;

import dev.terralense.marker.WaypointMarkerProvider;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Database-backed {@link WaypointService}. */
public final class SqlWaypointService implements WaypointService {
    private final WaypointRepository repository;
    private final StorageService storage;
    private final WaypointMarkerProvider markers;

    public SqlWaypointService(WaypointRepository repository,
                              StorageService storage,
                              WaypointMarkerProvider markers) {
        this.repository = repository;
        this.storage = storage;
        this.markers = markers;
    }

    @Override
    public CompletableFuture<AddOutcome> add(Waypoint waypoint) {
        UUID owner = waypoint.owner();
        return storage.submit(() -> {
            // Cap checked on the storage thread so the count and insert cannot race against another
            // command from the same player.
            if (repository.countByOwner(owner) >= MAX_PER_PLAYER) {
                return AddOutcome.LIMIT_REACHED;
            }
            if (repository.insert(waypoint).isEmpty()) {
                return AddOutcome.DUPLICATE;
            }
            markers.put(owner, repository.findByOwner(owner));
            return AddOutcome.CREATED;
        }).exceptionally(throwable -> AddOutcome.FAILED);
    }

    @Override
    public CompletableFuture<Boolean> remove(UUID owner, String name) {
        return storage.submit(() -> {
            boolean removed = repository.delete(owner, name);
            if (removed) {
                markers.put(owner, repository.findByOwner(owner));
            }
            return removed;
        });
    }

    @Override
    public CompletableFuture<Optional<Waypoint>> find(UUID owner, String name) {
        return storage.submit(() -> repository.find(owner, name));
    }

    @Override
    public CompletableFuture<List<Waypoint>> list(UUID owner) {
        return storage.submit(() -> repository.findByOwner(owner));
    }

    @Override
    public boolean isPersistent() {
        return true;
    }
}
