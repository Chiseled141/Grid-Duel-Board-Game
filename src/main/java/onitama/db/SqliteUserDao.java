package onitama.db;

import onitama.net.UserProfile;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * SQLite implementation of {@link UserDao}. Every statement is a
 * {@link java.sql.PreparedStatement} with bound parameters — SQL injection is
 * structurally impossible. Passwords are stored only as PBKDF2 hashes
 * ({@code iterations:saltB64:hashB64}).
 */
public final class SqliteUserDao implements UserDao {

    private static final Pattern VALID_USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final int MIN_PASSWORD_LENGTH = 4;

    private final Database database;

    /** Creates the DAO on top of an open database. */
    public SqliteUserDao(Database database) {
        this.database = database;
    }

    @Override
    public UserProfile register(String username, String password) {
        validate(username, password);
        String hash = PasswordHasher.hash(password);
        String createdAt = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        try {
            database.query(connection -> {
                try (var insert = connection.prepareStatement(
                        "INSERT INTO users (username, password_hash, elo, wins, losses, created_at) "
                                + "VALUES (?, ?, 1000, 0, 0, ?)")) {
                    insert.setString(1, username);
                    insert.setString(2, hash);
                    insert.setString(3, createdAt);
                    insert.executeUpdate();
                }
                return null;
            });
        } catch (SQLException e) {
            // SQLITE_CONSTRAINT (error code 19) is raised on a UNIQUE violation.
            // Using the error code is locale-independent and more robust than
            // inspecting the English error message.
            if (e.getErrorCode() == 19) {
                throw new AuthenticationException("username is already taken");
            }
            throw new PersistenceException("could not register user", e);
        }
        return new UserProfile(username, 1000, 0, 0);
    }

    @Override
    public UserProfile login(String username, String password) {
        Row row;
        try {
            row = database.query(connection -> {
                try (var select = connection.prepareStatement(
                        "SELECT password_hash, elo, wins, losses FROM users WHERE username = ?")) {
                    select.setString(1, username);
                    try (ResultSet result = select.executeQuery()) {
                        if (result.next()) {
                            return new Row(result.getString("password_hash"), result.getInt("elo"),
                                    result.getInt("wins"), result.getInt("losses"));
                        }
                        return null;
                    }
                }
            });
        } catch (SQLException e) {
            throw new PersistenceException("could not look up user", e);
        }
        // Same message for unknown user and wrong password: no account probing.
        if (row == null || !PasswordHasher.verify(password, row.passwordHash())) {
            throw new AuthenticationException("wrong username or password");
        }
        return new UserProfile(username, row.elo(), row.wins(), row.losses());
    }

    @Override
    public UserProfile profileOf(String username) {
        try {
            return database.query(connection -> {
                try (var select = connection.prepareStatement(
                        "SELECT elo, wins, losses FROM users WHERE username = ?")) {
                    select.setString(1, username);
                    try (ResultSet result = select.executeQuery()) {
                        if (!result.next()) {
                            throw new AuthenticationException("unknown user: " + username);
                        }
                        return new UserProfile(username, result.getInt("elo"),
                                result.getInt("wins"), result.getInt("losses"));
                    }
                }
            });
        } catch (SQLException e) {
            throw new PersistenceException("could not load profile", e);
        }
    }

    @Override
    public void updateStats(String username, int elo, int wins, int losses) throws PersistenceException {
        try {
            database.query(connection -> {
                try (var update = connection.prepareStatement(
                        "UPDATE users SET elo = ?, wins = ?, losses = ? WHERE username = ?")) {
                    update.setInt(1, elo);
                    update.setInt(2, wins);
                    update.setInt(3, losses);
                    update.setString(4, username);
                    update.executeUpdate();
                }
                return null;
            });
        } catch (SQLException e) {
            throw new PersistenceException("could not update stats for " + username, e);
        }
    }

    @Override
    public List<UserProfile> leaderboard(int limit) {
        try {
            return database.query(connection -> {
                List<UserProfile> top = new ArrayList<>();
                try (var select = connection.prepareStatement(
                        "SELECT username, elo, wins, losses FROM users ORDER BY elo DESC LIMIT ?")) {
                    select.setInt(1, limit);
                    try (ResultSet result = select.executeQuery()) {
                        while (result.next()) {
                            top.add(new UserProfile(result.getString("username"),
                                    result.getInt("elo"), result.getInt("wins"),
                                    result.getInt("losses")));
                        }
                    }
                }
                return top;
            });
        } catch (SQLException e) {
            throw new PersistenceException("could not load leaderboard", e);
        }
    }

    /** The userId of a username, or null if the user does not exist. */
    Long userIdOf(String username, Connection connection) throws SQLException {
        try (var select = connection.prepareStatement(
                "SELECT id FROM users WHERE username = ?")) {
            select.setString(1, username);
            try (ResultSet result = select.executeQuery()) {
                return result.next() ? result.getLong("id") : null;
            }
        }
    }

    /** Updates one user's Elo and counters; used inside the match transaction. */
    void updateStatsInside(Connection connection, String username, int elo, int wins, int losses)
            throws SQLException {
        try (var update = connection.prepareStatement(
                "UPDATE users SET elo = ?, wins = ?, losses = ? WHERE username = ?")) {
            update.setInt(1, elo);
            update.setInt(2, wins);
            update.setInt(3, losses);
            update.setString(4, username);
            update.executeUpdate();
        }
    }

    private static void validate(String username, String password) {
        if (!VALID_USERNAME.matcher(username).matches()) {
            throw new AuthenticationException(
                    "username must be 3-16 letters, digits or underscores");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new AuthenticationException("password must be at least 4 characters");
        }
    }

    private record Row(String passwordHash, int elo, int wins, int losses) {
    }
}
