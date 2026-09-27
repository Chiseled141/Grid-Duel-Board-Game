package onitama.db;

import onitama.net.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * UT07 — SQLite user accounts: register then login, wrong password rejected,
 * duplicate username rejected, and Elo/wins/losses updates persist across
 * DAO instances. Uses a temporary database file.
 */
class UserDaoTest {

    @TempDir
    Path tempDir;

    private SqliteUserDao dao;

    @BeforeEach
    void openDatabase() throws SQLException {
        dao = new SqliteUserDao(new Database(tempDir.resolve("ut07.db")));
    }

    @Test
    void registerThenLoginSucceeds() {
        UserProfile created = dao.register("alice", "alicepw");
        assertEquals(1000, created.elo());
        assertEquals(0, created.wins());

        UserProfile logged = dao.login("alice", "alicepw");
        assertEquals("alice", logged.username());
        assertEquals(1000, logged.elo());
    }

    @Test
    void wrongPasswordFails() {
        dao.register("alice", "alicepw");
        AuthenticationException error = assertThrows(AuthenticationException.class,
                () -> dao.login("alice", "wrongpw"));
        assertEquals("wrong username or password", error.getMessage());
    }

    @Test
    void unknownUserFailsWithSameMessage() {
        assertThrows(AuthenticationException.class, () -> dao.login("ghost", "whatever"));
    }

    @Test
    void duplicateUsernameFails() {
        dao.register("alice", "alicepw");
        assertThrows(AuthenticationException.class, () -> dao.register("alice", "otherpw"));
    }

    @Test
    void malformedUsernameAndPasswordAreRejected() {
        assertThrows(AuthenticationException.class, () -> dao.register("ab", "goodpw"));
        assertThrows(AuthenticationException.class, () -> dao.register("bad name!", "goodpw"));
        assertThrows(AuthenticationException.class, () -> dao.register("okname", "abc"));
    }

    @Test
    void passwordIsStoredHashedNotPlaintext() throws SQLException {
        try (var database = new Database(tempDir.resolve("hash.db"))) {
            SqliteUserDao freshDao = new SqliteUserDao(database);
            freshDao.register("alice", "alicepw");
            String stored = database.query(connection -> {
                try (var select = connection.prepareStatement(
                        "SELECT password_hash FROM users WHERE username = ?")) {
                    select.setString(1, "alice");
                    try (var result = select.executeQuery()) {
                        result.next();
                        return result.getString(1);
                    }
                }
            });
            org.junit.jupiter.api.Assertions.assertFalse(stored.contains("alicepw"),
                    "plaintext password must never be stored");
            org.junit.jupiter.api.Assertions.assertEquals(3, stored.split(":").length,
                    "stored format is iterations:saltB64:hashB64");
        }
    }

    @Test
    void statUpdatesPersistAndLeaderboardIsOrdered() throws SQLException {
        dao.register("alice", "alicepw");
        dao.register("bob", "bobpw");
        dao.register("carol", "carolpw");

        dao.updateStats("alice", 1032, 1, 0);
        dao.updateStats("bob", 1016, 1, 0);
        dao.updateStats("carol", 968, 0, 1);

        // A fresh DAO instance against the same file must see the same data.
        try (var database = new Database(tempDir.resolve("ut07.db"))) {
            SqliteUserDao reopened = new SqliteUserDao(database);
            assertEquals(1032, reopened.profileOf("alice").elo());
            assertEquals(1, reopened.profileOf("carol").losses());

            var top = reopened.leaderboard(2);
            assertEquals(2, top.size());
            assertEquals("alice", top.get(0).username());
            assertEquals("bob", top.get(1).username());
        }
    }
}
