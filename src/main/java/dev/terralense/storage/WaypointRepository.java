package dev.terralense.storage;

import dev.terralense.api.marker.MarkerVisibility;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Waypoint persistence. */
public final class WaypointRepository {
    private final Database database;

    public WaypointRepository(Database database) {
        this.database = database;
    }

    /**
     * Inserts a waypoint.
     *
     * @return the stored waypoint with its assigned id, or empty when the name is already taken by
     *     that owner
     */
    public Optional<Waypoint> insert(Waypoint waypoint) throws SQLException {
        return database.withConnection(connection -> {
            String sql = """
                    INSERT INTO waypoints
                        (owner, name, world_id, x, y, z, color, icon, visibility, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            try (PreparedStatement ps = connection.prepareStatement(sql,
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, waypoint.owner().toString());
                ps.setString(2, waypoint.name());
                ps.setString(3, waypoint.worldId().toString());
                ps.setInt(4, waypoint.x());
                ps.setInt(5, waypoint.y());
                ps.setInt(6, waypoint.z());
                ps.setInt(7, waypoint.color());
                ps.setString(8, waypoint.icon());
                ps.setString(9, waypoint.visibility().configName());
                ps.setLong(10, waypoint.createdAt());
                ps.executeUpdate();

                try (ResultSet keys = ps.getGeneratedKeys()) {
                    long id = keys.next() ? keys.getLong(1) : 0L;
                    return Optional.of(waypoint.withId(id));
                }
            } catch (SQLException ex) {
                // The unique index on (owner, name) is the authority on duplicates, so a constraint
                // violation is an expected outcome rather than an error worth propagating.
                if (isUniqueViolation(ex)) {
                    return Optional.empty();
                }
                throw ex;
            }
        });
    }

    /** Deletes a waypoint by owner and name, case-insensitively. */
    public boolean delete(UUID owner, String name) throws SQLException {
        return database.withConnection(connection -> {
            String sql = "DELETE FROM waypoints WHERE owner = ? AND name = ? COLLATE NOCASE";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, owner.toString());
                ps.setString(2, name);
                return ps.executeUpdate() > 0;
            }
        });
    }

    /** Finds one waypoint by owner and name. */
    public Optional<Waypoint> find(UUID owner, String name) throws SQLException {
        return database.withConnection(connection -> {
            String sql = """
                    SELECT id, owner, name, world_id, x, y, z, color, icon, visibility, created_at
                    FROM waypoints
                    WHERE owner = ? AND name = ? COLLATE NOCASE
                    """;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, owner.toString());
                ps.setString(2, name);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.<Waypoint>empty();
                }
            }
        });
    }

    /** All waypoints owned by a player, newest first. */
    public List<Waypoint> findByOwner(UUID owner) throws SQLException {
        return database.withConnection(connection -> {
            String sql = """
                    SELECT id, owner, name, world_id, x, y, z, color, icon, visibility, created_at
                    FROM waypoints
                    WHERE owner = ?
                    ORDER BY created_at DESC
                    """;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, owner.toString());
                return readAll(ps);
            }
        });
    }

    /** Waypoints a player should see in one world: their own plus public ones. */
    public List<Waypoint> findVisible(UUID viewer, UUID worldId) throws SQLException {
        return database.withConnection(connection -> {
            String sql = """
                    SELECT id, owner, name, world_id, x, y, z, color, icon, visibility, created_at
                    FROM waypoints
                    WHERE world_id = ? AND (owner = ? OR visibility = 'public')
                    """;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, worldId.toString());
                ps.setString(2, viewer.toString());
                return readAll(ps);
            }
        });
    }

    /** Number of waypoints an owner has, used to enforce a per-player cap. */
    public int countByOwner(UUID owner) throws SQLException {
        return database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) FROM waypoints WHERE owner = ?")) {
                ps.setString(1, owner.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        });
    }

    private static List<Waypoint> readAll(PreparedStatement ps) throws SQLException {
        List<Waypoint> waypoints = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                waypoints.add(map(rs));
            }
        }
        return waypoints;
    }

    private static Waypoint map(ResultSet rs) throws SQLException {
        return new Waypoint(
                rs.getLong("id"),
                UUID.fromString(rs.getString("owner")),
                rs.getString("name"),
                UUID.fromString(rs.getString("world_id")),
                rs.getInt("x"),
                rs.getInt("y"),
                rs.getInt("z"),
                rs.getInt("color"),
                rs.getString("icon"),
                MarkerVisibility.fromString(rs.getString("visibility"))
                        .orElse(MarkerVisibility.PRIVATE),
                rs.getLong("created_at"));
    }

    private static boolean isUniqueViolation(SQLException ex) {
        String message = ex.getMessage();
        return message != null && message.toUpperCase(java.util.Locale.ROOT).contains("UNIQUE");
    }
}
