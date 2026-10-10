package dev.terralense.storage;

import dev.terralense.config.MapOrientation;
import dev.terralense.player.PlayerPreferences;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;
import java.util.UUID;

/** Reads and writes {@link PlayerPreferences}. */
public final class PreferencesWriter {
    private final Database database;
    private final StorageService storage;

    public PreferencesWriter(Database database, StorageService storage) {
        this.database = database;
        this.storage = storage;
    }

    /** Queues a save. */
    public void save(UUID playerId, PlayerPreferences preferences) {
        if (!preferences.isDirty()) {
            return;
        }
        // Snapshot the values now: the preferences object keeps mutating on the server thread while
        // the write runs on the storage thread.
        boolean enabled = preferences.minimapEnabled();
        String orientation = preferences.orientation().configName();
        int zoom = preferences.zoom();
        boolean radar = preferences.radarEnabled();
        int nudge = preferences.offsetNudge();
        preferences.markClean();

        storage.submitAndForget(() -> database.withConnection(connection -> {
            String sql = """
                    INSERT INTO player_preferences
                        (player_id, enabled, orientation, zoom, radar_enabled, offset_nudge, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(player_id) DO UPDATE SET
                        enabled = excluded.enabled,
                        orientation = excluded.orientation,
                        zoom = excluded.zoom,
                        radar_enabled = excluded.radar_enabled,
                        offset_nudge = excluded.offset_nudge,
                        updated_at = excluded.updated_at
                    """;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, playerId.toString());
                ps.setInt(2, enabled ? 1 : 0);
                ps.setString(3, orientation);
                ps.setInt(4, zoom);
                ps.setInt(5, radar ? 1 : 0);
                ps.setInt(6, nudge);
                ps.setLong(7, System.currentTimeMillis());
                ps.executeUpdate();
            }
            return null;
        }), "save preferences for " + playerId);
    }

    /** Loads stored preferences. */
    public Optional<PlayerPreferences> load(UUID playerId) throws java.sql.SQLException {
        return database.withConnection(connection -> {
            String sql = """
                    SELECT enabled, orientation, zoom, radar_enabled, offset_nudge
                    FROM player_preferences WHERE player_id = ?
                    """;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, playerId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    MapOrientation orientation = MapOrientation
                            .fromConfig(rs.getString("orientation"))
                            .orElse(MapOrientation.PLAYER_UP);
                    PlayerPreferences loaded = new PlayerPreferences(
                            rs.getInt("enabled") != 0,
                            orientation,
                            rs.getInt("zoom"),
                            rs.getInt("radar_enabled") != 0);
                    loaded.setOffsetNudge(rs.getInt("offset_nudge"));
                    return Optional.of(loaded);
                }
            }
        });
    }
}
