package dev.terralense.storage;

import dev.terralense.player.PlayerPreferences;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Persistence boundary for per-player data. */
public interface PlayerDataStore {
    /** Queues a preferences save. */
    void savePreferences(UUID playerId, PlayerPreferences preferences);

    /** Loads stored data for a joining player. */
    CompletableFuture<LoadedPlayerData> load(UUID playerId);

    /** Records a death location. */
    void recordDeath(UUID playerId, UUID worldId, int x, int y, int z, long timestamp);

    /** Reloads a player's waypoints after a change. */
    CompletableFuture<List<Waypoint>> reloadWaypoints(UUID playerId);

    /** Whether data is actually being persisted. */
    boolean isPersistent();

    /**
     * Stored data for one player.
     *
     * @param preferences stored preferences, or {@code null} for a new player
     * @param waypoints the player's waypoints, never {@code null}
     */
    record LoadedPlayerData(PlayerPreferences preferences, List<Waypoint> waypoints) {
        public LoadedPlayerData {
            waypoints = waypoints == null ? List.of() : List.copyOf(waypoints);
        }

        public static LoadedPlayerData empty() {
            return new LoadedPlayerData(null, List.of());
        }
    }
}
