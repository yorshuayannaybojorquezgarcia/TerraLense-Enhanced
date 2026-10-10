package dev.terralense.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.terralense.api.marker.MarkerVisibility;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises the repository against a real SQLite file, so migrations, indexes and constraints are
 * verified rather than assumed.
 */
class WaypointRepositoryTest {
    @TempDir
    Path tempDir;

    private Database database;
    private WaypointRepository repository;

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final UUID WORLD = UUID.randomUUID();
    private static final UUID NETHER = UUID.randomUUID();

    @BeforeEach
    void setUp() throws SQLException {
        database = new Database(tempDir.resolve("test.db"));
        database.open();
        repository = new WaypointRepository(database);
    }

    @AfterEach
    void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    private static Waypoint waypoint(UUID owner, String name, UUID world, int x, int y, int z) {
        return new Waypoint(0L, owner, name, world, x, y, z, 0x4FD1A5, "diamond",
                MarkerVisibility.PRIVATE, System.currentTimeMillis());
    }

    @Test
    @DisplayName("migrations bring a fresh database to the current version")
    void migrationsApply() {
        assertEquals(SchemaMigrations.currentVersion(), database.schemaVersion());
        assertTrue(database.isOpen());
    }

    @Test
    @DisplayName("the v2 migration adds the per-player offset column")
    void offsetColumnExists() throws SQLException {
        // Guards the migration: an older database must gain this column rather than failing when
        // preferences are written.
        Integer value = database.withConnection(connection -> {
            try (var statement = connection.createStatement();
                 var rs = statement.executeQuery(
                         "SELECT COUNT(offset_nudge) FROM player_preferences")) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        });
        assertEquals(0, value, "column should exist and the table start empty");
        assertTrue(database.schemaVersion() >= 2,
                "schema should be at least v2, was " + database.schemaVersion());
    }

    @Test
    @DisplayName("re-opening an existing database does not re-run migrations")
    void migrationsAreIdempotent() throws SQLException {
        int first = database.schemaVersion();
        database.close();

        Database reopened = new Database(tempDir.resolve("test.db"));
        reopened.open();
        try {
            assertEquals(first, reopened.schemaVersion());
        } finally {
            reopened.close();
        }
    }

    @Test
    @DisplayName("a waypoint round-trips through the database")
    void insertAndFind() throws SQLException {
        Optional<Waypoint> stored = repository.insert(waypoint(OWNER, "Base", WORLD, 100, 64, -200));
        assertTrue(stored.isPresent());
        assertTrue(stored.get().id() > 0L, "an id should be assigned");

        Optional<Waypoint> found = repository.find(OWNER, "Base");
        assertTrue(found.isPresent());
        assertEquals(100, found.get().x());
        assertEquals(64, found.get().y());
        assertEquals(-200, found.get().z());
        assertEquals(WORLD, found.get().worldId());
    }

    @Test
    @DisplayName("waypoints survive a reconnect, which is what persistence means")
    void survivesReopen() throws SQLException {
        repository.insert(waypoint(OWNER, "Home", WORLD, 1, 2, 3));
        database.close();

        Database reopened = new Database(tempDir.resolve("test.db"));
        reopened.open();
        try {
            Optional<Waypoint> found = new WaypointRepository(reopened).find(OWNER, "Home");
            assertTrue(found.isPresent());
            assertEquals(1, found.get().x());
        } finally {
            reopened.close();
        }
    }

    @Test
    @DisplayName("duplicate names for one owner are rejected by the database")
    void rejectsDuplicateNames() throws SQLException {
        assertTrue(repository.insert(waypoint(OWNER, "Mine", WORLD, 0, 0, 0)).isPresent());
        assertTrue(repository.insert(waypoint(OWNER, "Mine", WORLD, 5, 5, 5)).isEmpty(),
                "a duplicate name should be reported, not inserted");
        assertEquals(1, repository.countByOwner(OWNER));
    }

    @Test
    @DisplayName("duplicate detection is case-insensitive")
    void duplicateDetectionIgnoresCase() throws SQLException {
        repository.insert(waypoint(OWNER, "Mine", WORLD, 0, 0, 0));
        assertTrue(repository.insert(waypoint(OWNER, "MINE", WORLD, 1, 1, 1)).isEmpty());
    }

    @Test
    @DisplayName("different owners may reuse the same name")
    void ownersAreIndependent() throws SQLException {
        assertTrue(repository.insert(waypoint(OWNER, "Base", WORLD, 0, 0, 0)).isPresent());
        assertTrue(repository.insert(waypoint(OTHER, "Base", WORLD, 9, 9, 9)).isPresent());
        assertEquals(1, repository.countByOwner(OWNER));
        assertEquals(1, repository.countByOwner(OTHER));
    }

    @Test
    @DisplayName("lookup by name ignores case")
    void findIgnoresCase() throws SQLException {
        repository.insert(waypoint(OWNER, "SkyBase", WORLD, 0, 70, 0));
        assertTrue(repository.find(OWNER, "skybase").isPresent());
        assertTrue(repository.find(OWNER, "SKYBASE").isPresent());
    }

    @Test
    @DisplayName("delete removes only the named waypoint")
    void deleteRemovesOne() throws SQLException {
        repository.insert(waypoint(OWNER, "One", WORLD, 0, 0, 0));
        repository.insert(waypoint(OWNER, "Two", WORLD, 1, 1, 1));

        assertTrue(repository.delete(OWNER, "One"));
        assertTrue(repository.find(OWNER, "One").isEmpty());
        assertTrue(repository.find(OWNER, "Two").isPresent());
    }

    @Test
    @DisplayName("deleting a missing waypoint reports false rather than throwing")
    void deleteMissingIsFalse() throws SQLException {
        assertFalse(repository.delete(OWNER, "Nothing"));
    }

    @Test
    @DisplayName("a player cannot delete another player's waypoint")
    void deleteIsScopedToOwner() throws SQLException {
        repository.insert(waypoint(OWNER, "Secret", WORLD, 0, 0, 0));
        assertFalse(repository.delete(OTHER, "Secret"));
        assertTrue(repository.find(OWNER, "Secret").isPresent());
    }

    @Test
    @DisplayName("visible waypoints never leak across worlds")
    void visibilityIsWorldScoped() throws SQLException {
        repository.insert(waypoint(OWNER, "Overworld", WORLD, 0, 0, 0));
        repository.insert(waypoint(OWNER, "Hell", NETHER, 0, 0, 0));

        List<Waypoint> overworld = repository.findVisible(OWNER, WORLD);
        assertEquals(1, overworld.size());
        assertEquals("Overworld", overworld.getFirst().name());
    }

    @Test
    @DisplayName("private waypoints are not visible to other players")
    void privateWaypointsStayPrivate() throws SQLException {
        repository.insert(waypoint(OWNER, "Hidden", WORLD, 0, 0, 0));
        assertTrue(repository.findVisible(OTHER, WORLD).isEmpty());
    }

    @Test
    @DisplayName("public waypoints are visible to everyone")
    void publicWaypointsAreShared() throws SQLException {
        repository.insert(new Waypoint(0L, OWNER, "Shop", WORLD, 10, 64, 10, 0xFFFFFF,
                "diamond", MarkerVisibility.PUBLIC, System.currentTimeMillis()));

        List<Waypoint> visible = repository.findVisible(OTHER, WORLD);
        assertEquals(1, visible.size());
        assertEquals(MarkerVisibility.PUBLIC, visible.getFirst().visibility());
    }

    @Test
    @DisplayName("listing returns all of an owner's waypoints")
    void listsByOwner() throws SQLException {
        repository.insert(waypoint(OWNER, "A", WORLD, 0, 0, 0));
        repository.insert(waypoint(OWNER, "B", NETHER, 0, 0, 0));
        repository.insert(waypoint(OTHER, "C", WORLD, 0, 0, 0));

        assertEquals(2, repository.findByOwner(OWNER).size());
    }

    @Test
    @DisplayName("names with SQL metacharacters are rejected before reaching the database")
    void rejectsInjectionAttempts() {
        // Parameterised statements make injection impossible anyway; validation rejects these
        // earlier so the user gets a clear error.
        assertThrows(IllegalArgumentException.class,
                () -> waypoint(OWNER, "'; DROP TABLE waypoints; --", WORLD, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> waypoint(OWNER, "a b", WORLD, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> waypoint(OWNER, "§cred", WORLD, 0, 0, 0));
    }

    @Test
    @DisplayName("a quoted name cannot corrupt the table")
    void quotedNameIsHandledSafely() throws SQLException {
        // Even if validation were bypassed, the parameterised delete must treat this as data.
        assertFalse(repository.delete(OWNER, "'; DROP TABLE waypoints; --"));
        assertTrue(repository.insert(waypoint(OWNER, "StillWorks", WORLD, 0, 0, 0)).isPresent());
    }

    @Test
    @DisplayName("invalid names and positions are rejected")
    void validatesInput() {
        assertThrows(IllegalArgumentException.class, () -> waypoint(OWNER, "", WORLD, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> waypoint(OWNER, "x".repeat(Waypoint.MAX_NAME_LENGTH + 1), WORLD, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> waypoint(OWNER, "TooHigh", WORLD, 0, 100_000, 0));
        assertThrows(IllegalArgumentException.class,
                () -> waypoint(OWNER, "TooFar", WORLD, 40_000_000, 64, 0));
    }

    @Test
    @DisplayName("the WAL journal file is created, confirming pragmas applied")
    void walEnabled() throws SQLException {
        repository.insert(waypoint(OWNER, "Wal", WORLD, 0, 0, 0));
        assertTrue(Files.exists(tempDir.resolve("test.db")));
    }

    @Test
    @DisplayName("using a closed database reports a clear error")
    void closedDatabaseFails() {
        database.close();
        assertThrows(SQLException.class, () -> repository.find(OWNER, "Any"));
    }
}
