package dev.terralense.storage;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/** Versioned schema migrations. */
final class SchemaMigrations {
    /** Ordered migrations; index + 1 is the version they produce. */
    private static final List<List<String>> MIGRATIONS = List.of(
            // v1: initial schema.
            List.of(
                """
                CREATE TABLE IF NOT EXISTS waypoints (
                    id           INTEGER PRIMARY KEY AUTOINCREMENT,
                    owner        TEXT    NOT NULL,
                    name         TEXT    NOT NULL,
                    world_id     TEXT    NOT NULL,
                    x            INTEGER NOT NULL,
                    y            INTEGER NOT NULL,
                    z            INTEGER NOT NULL,
                    color        INTEGER NOT NULL,
                    icon         TEXT    NOT NULL,
                    visibility   TEXT    NOT NULL,
                    created_at   INTEGER NOT NULL
                )
                """,
                // Waypoint names are unique per owner so `/tl waypoint tp <name>` is unambiguous.
                """
                CREATE UNIQUE INDEX IF NOT EXISTS idx_waypoints_owner_name
                    ON waypoints (owner, name COLLATE NOCASE)
                """,
                """
                CREATE INDEX IF NOT EXISTS idx_waypoints_owner_world
                    ON waypoints (owner, world_id)
                """,
                """
                CREATE TABLE IF NOT EXISTS player_preferences (
                    player_id     TEXT    PRIMARY KEY,
                    enabled       INTEGER NOT NULL,
                    orientation   TEXT    NOT NULL,
                    zoom          INTEGER NOT NULL,
                    radar_enabled INTEGER NOT NULL,
                    updated_at    INTEGER NOT NULL
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS death_locations (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_id  TEXT    NOT NULL,
                    world_id   TEXT    NOT NULL,
                    x          INTEGER NOT NULL,
                    y          INTEGER NOT NULL,
                    z          INTEGER NOT NULL,
                    died_at    INTEGER NOT NULL
                )
                """,
                """
                CREATE INDEX IF NOT EXISTS idx_death_player_time
                    ON death_locations (player_id, died_at DESC)
                """
            ),
            // v2: per-player horizontal HUD nudge.
            List.of(
                """
                ALTER TABLE player_preferences
                    ADD COLUMN offset_nudge INTEGER NOT NULL DEFAULT 0
                """
            )
    );

    private SchemaMigrations() {
    }

    static int currentVersion() {
        return MIGRATIONS.size();
    }

    /**
     * Brings the database up to {@link #currentVersion()}.
     *
     * @return the version migrated to
     */
    static int apply(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS schema_version (
                        version    INTEGER NOT NULL,
                        applied_at INTEGER NOT NULL
                    )
                    """);
        }

        int existing = readVersion(connection);
        if (existing >= currentVersion()) {
            return existing;
        }

        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            for (int version = existing + 1; version <= MIGRATIONS.size(); version++) {
                for (String sql : MIGRATIONS.get(version - 1)) {
                    try (Statement statement = connection.createStatement()) {
                        statement.executeUpdate(sql);
                    }
                }
                recordVersion(connection, version);
            }
            connection.commit();
        } catch (SQLException ex) {
            // A partially applied migration would be worse than none.
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
        return currentVersion();
    }

    private static int readVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static void recordVersion(Connection connection, int version) throws SQLException {
        try (var ps = connection.prepareStatement(
                "INSERT INTO schema_version (version, applied_at) VALUES (?, ?)")) {
            ps.setInt(1, version);
            ps.setLong(2, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }
}
