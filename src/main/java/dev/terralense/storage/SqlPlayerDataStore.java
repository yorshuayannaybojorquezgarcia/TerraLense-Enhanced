package dev.terralense.storage;

import dev.terralense.core.Log;
import dev.terralense.player.PlayerPreferences;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** SQLite-backed {@link PlayerDataStore}. */
public final class SqlPlayerDataStore implements PlayerDataStore {
    private final Database database;
    private final StorageService storage;
    private final PreferencesWriter preferences;
    private final WaypointRepository waypoints;
    private final Log log;

    public SqlPlayerDataStore(Database database,
                              StorageService storage,
                              PreferencesWriter preferences,
                              WaypointRepository waypoints,
                              Log log) {
        this.database = database;
        this.storage = storage;
        this.preferences = preferences;
        this.waypoints = waypoints;
        this.log = log;
    }

    @Override
    public void savePreferences(UUID playerId, PlayerPreferences prefs) {
        preferences.save(playerId, prefs);
    }

    @Override
    public CompletableFuture<LoadedPlayerData> load(UUID playerId) {
        return storage.submit(() -> new LoadedPlayerData(
                        preferences.load(playerId).orElse(null),
                        waypoints.findByOwner(playerId)))
                .exceptionally(throwable -> {
                    log.debug(() -> "could not load stored data for " + playerId
                            + ": " + throwable.getMessage());
                    return LoadedPlayerData.empty();
                });
    }

    @Override
    public void recordDeath(UUID playerId, UUID worldId, int x, int y, int z, long timestamp) {
        storage.submitAndForget(() -> database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO death_locations (player_id, world_id, x, y, z, died_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """)) {
                ps.setString(1, playerId.toString());
                ps.setString(2, worldId.toString());
                ps.setInt(3, x);
                ps.setInt(4, y);
                ps.setInt(5, z);
                ps.setLong(6, timestamp);
                ps.executeUpdate();
            }
            return null;
        }), "record death for " + playerId);
    }

    @Override
    public CompletableFuture<List<Waypoint>> reloadWaypoints(UUID playerId) {
        return storage.submit(() -> waypoints.findByOwner(playerId))
                .exceptionally(throwable -> {
                    log.debug(() -> "could not reload waypoints for " + playerId);
                    return List.of();
                });
    }

    @Override
    public boolean isPersistent() {
        return true;
    }
}
