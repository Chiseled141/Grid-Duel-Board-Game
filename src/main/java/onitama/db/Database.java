package onitama.db;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * The SQLite database: one shared JDBC connection guarded by the Database
 * object's own monitor. Every access funnels through {@link #query(SqlWork)}
 * or {@link #inTransaction(SqlWork)}, so statements never interleave and the
 * transaction methods can wrap several statements atomically. A connection
 * pool would buy nothing at this scale (a few dozen players); one serialized
 * connection is the simplest correct choice — documented in
 * docs/DESIGN_DECISIONS.md.
 */
public final class Database implements AutoCloseable {

    /** A unit of SQL work executed with a connection. */
    @FunctionalInterface
    public interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    static {
        // Explicit (instead of relying on the JDBC 4 service loader) so a
        // shaded jar fails loudly here rather than mysteriously later.
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("SQLite JDBC driver not on the classpath", e);
        }
    }

    private final Connection connection;

    /**
     * Opens (creating if needed) the database file and initializes the schema
     * idempotently.
     */
    public Database(Path file) throws SQLException {
        if (file.getParent() != null) {
            file.getParent().toFile().mkdirs();
        }
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + file);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                      id            INTEGER PRIMARY KEY AUTOINCREMENT,
                      username      TEXT UNIQUE NOT NULL,
                      password_hash TEXT NOT NULL,
                      elo           INTEGER NOT NULL DEFAULT 1000,
                      wins          INTEGER NOT NULL DEFAULT 0,
                      losses        INTEGER NOT NULL DEFAULT 0,
                      created_at    TEXT NOT NULL
                    )""");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS matches (
                      id             INTEGER PRIMARY KEY AUTOINCREMENT,
                      blue_user_id   INTEGER NOT NULL REFERENCES users(id),
                      red_user_id    INTEGER NOT NULL REFERENCES users(id),
                      winner_user_id INTEGER REFERENCES users(id),
                      end_reason     TEXT NOT NULL,
                      move_count     INTEGER NOT NULL,
                      elo_blue_after INTEGER NOT NULL,
                      elo_red_after  INTEGER NOT NULL,
                      replay_path    TEXT,
                      played_at      TEXT NOT NULL
                    )""");
        }
    }

    /** Runs a read or a single-statement write under the database lock. */
    public synchronized <T> T query(SqlWork<T> work) throws SQLException {
        return work.run(connection);
    }

    /**
     * Runs several statements atomically: commit on success, rollback and
     * rethrow on any failure.
     */
    public synchronized <T> T inTransaction(SqlWork<T> work) throws SQLException {
        connection.setAutoCommit(false);
        try {
            T result = work.run(connection);
            connection.commit();
            return result;
        } catch (Exception e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
