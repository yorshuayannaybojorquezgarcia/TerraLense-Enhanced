package dev.terralense.storage;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** SQLite connection holder. */
public final class Database implements AutoCloseable {
    private final Path file;
    private final Object lock = new Object();
    private Connection connection;
    private int schemaVersion;

    public Database(Path file) {
        this.file = file;
    }

    /** Opens the database and applies pending migrations. */
    public void open() throws SQLException {
        synchronized (lock) {
            if (connection != null && !connection.isClosed()) {
                return;
            }
            // Loading the driver explicitly because it arrives through Paper's runtime library
            // loader, where automatic discovery is not assured.
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException ex) {
                throw new SQLException("SQLite driver unavailable", ex);
            }

            connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
            applyPragmas(connection);
            schemaVersion = SchemaMigrations.apply(connection);
        }
    }

    private static void applyPragmas(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            // WAL lets reads proceed during a write, which keeps a slow write from stalling other
            // queued storage work.
            statement.executeUpdate("PRAGMA journal_mode=WAL");
            // NORMAL trades a fsync per commit for durability only in the event of an OS crash;
            // acceptable for minimap data and much faster.
            statement.executeUpdate("PRAGMA synchronous=NORMAL");
            statement.executeUpdate("PRAGMA foreign_keys=ON");
            statement.executeUpdate("PRAGMA busy_timeout=5000");
        }
    }

    /** Runs an operation against the connection while holding the lock. */
    public <T> T withConnection(SqlFunction<T> work) throws SQLException {
        synchronized (lock) {
            if (connection == null || connection.isClosed()) {
                throw new SQLException("database is not open");
            }
            return work.apply(connection);
        }
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public Path file() {
        return file;
    }

    public boolean isOpen() {
        synchronized (lock) {
            try {
                return connection != null && !connection.isClosed();
            } catch (SQLException ex) {
                return false;
            }
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (connection == null) {
                return;
            }
            try {
                connection.close();
            } catch (SQLException ignored) {
                // Closing during shutdown; nothing useful left to do.
            } finally {
                connection = null;
            }
        }
    }

    /** Connection-scoped work that may throw {@link SQLException}. */
    @FunctionalInterface
    public interface SqlFunction<T> {
        T apply(Connection connection) throws SQLException;
    }
}
