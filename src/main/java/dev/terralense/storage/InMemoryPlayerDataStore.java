package dev.terralense.storage;

import dev.terralense.player.PlayerPreferences;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Fallback {@link PlayerDataStore} used when the database is unavailable. */
public final class InMemoryPlayerDataStore implements PlayerDataStore {
    private final Map<UUID, List<Waypoint>> waypoints = new ConcurrentHashMap<>();

    @Override
    public void savePreferences(UUID playerId, PlayerPreferences preferences) {
        // Nothing to do: preferences live in the session for this run only.
        preferences.markClean();
    }

    @Override
    public CompletableFuture<LoadedPlayerData> load(UUID playerId) {
        return CompletableFuture.completedFuture(
                new LoadedPlayerData(null, waypoints.getOrDefault(playerId, List.of())));
    }

    @Override
    public void recordDeath(UUID playerId, UUID worldId, int x, int y, int z, long timestamp) {
        // The in-memory death marker provider already holds this for the session.
    }

    @Override
    public CompletableFuture<List<Waypoint>> reloadWaypoints(UUID playerId) {
        return CompletableFuture.completedFuture(waypoints.getOrDefault(playerId, List.of()));
    }

    @Override
    public boolean isPersistent() {
        return false;
    }

    /** Drops a player's session data on quit. */
    public void forget(UUID playerId) {
        waypoints.remove(playerId);
    }
}
