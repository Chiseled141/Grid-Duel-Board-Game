package onitama.db;

import onitama.core.PlayerColor;
import onitama.core.WinCondition;
import onitama.net.UserProfile;
import onitama.server.MatchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * UT08 — match persistence: a finished match is recorded in one transaction
 * together with both players' stat updates; a failing transaction rolls back
 * completely (no partial rows, no stat changes); the recent-matches query is
 * ordered newest first. Uses a temporary database file.
 */
class MatchDaoTest {

    @TempDir
    Path tempDir;

    private Database database;
    private SqliteUserDao userDao;
    private SqliteMatchDao matchDao;

    @BeforeEach
    void openDatabase() throws SQLException {
        database = new Database(tempDir.resolve("ut08.db"));
        userDao = new SqliteUserDao(database);
        matchDao = new SqliteMatchDao(database, userDao);
        userDao.register("alice", "alicepw");
        userDao.register("bob", "bobpw");
    }

    @Test
    void recordedMatchPersistsWithStatsInOneTransaction() throws SQLException {
        MatchResult result = sampleResult("ROOM1", PlayerColor.BLUE, WinCondition.STONE);
        database.inTransaction(connection -> matchDao.recordMatch(connection, result));

        List<MatchDao.MatchRecord> records = matchDao.recentMatches(10);
        assertEquals(1, records.size());
        MatchDao.MatchRecord record = records.get(0);
        assertEquals("alice", record.blueUsername());
        assertEquals("bob", record.redUsername());
        assertEquals("alice", record.winnerUsername());
        assertEquals("STONE", record.endReason());
        assertEquals(2, record.moveCount());
        assertEquals(1016, record.eloBlueAfter());
        assertEquals(984, record.eloRedAfter());

        // Both users' rows were updated inside the same transaction.
        UserProfile blue = userDao.profileOf("alice");
        assertEquals(1016, blue.elo());
        assertEquals(1, blue.wins());
        assertEquals(0, blue.losses());
        UserProfile red = userDao.profileOf("bob");
        assertEquals(984, red.elo());
        assertEquals(0, red.wins());
        assertEquals(1, red.losses());
    }

    @Test
    void drawRecordsNoWinner() throws SQLException {
        // A real result first, so the draw below proves it leaves an existing
        // W/L record untouched rather than merely an empty one.
        database.inTransaction(connection -> matchDao.recordMatch(
                connection, sampleResult("ROOM1", PlayerColor.BLUE, WinCondition.STONE)));

        MatchResult result = sampleResult("ROOM2", null, WinCondition.DRAW);
        database.inTransaction(connection -> matchDao.recordMatch(connection, result));
        assertNull(matchDao.recentMatches(1).get(0).winnerUsername());

        // A draw moves Elo only: neither player's wins nor losses may change.
        UserProfile blue = userDao.profileOf("alice");
        assertEquals(1, blue.wins(), "a draw must not change wins");
        assertEquals(0, blue.losses(), "a draw must not be counted as a loss");
        UserProfile red = userDao.profileOf("bob");
        assertEquals(0, red.wins(), "a draw must not change wins");
        assertEquals(1, red.losses(), "a draw must not be counted as a loss");
    }

    @Test
    void failingTransactionRollsBackEverything() throws SQLException {
        MatchResult good = sampleResult("ROOM1", PlayerColor.BLUE, WinCondition.STONE);
        database.inTransaction(connection -> matchDao.recordMatch(connection, good));
        UserProfile blueBefore = userDao.profileOf("alice");

        // A match referencing an unknown user fails the transaction mid-way:
        // the match insert alone would succeed, so this proves the rollback.
        MatchResult bad = new MatchResult("ROOM9", "ghost", "bob",
                PlayerColor.RED, WinCondition.FORFEIT, 1000, 1000, 0);
        assertThrows(SQLException.class, () ->
                database.inTransaction(connection -> matchDao.recordMatch(connection, bad)));

        assertEquals(1, matchDao.recentMatches(10).size(), "no partial match row survived");
        UserProfile blueAfter = userDao.profileOf("alice");
        assertEquals(blueBefore.elo(), blueAfter.elo(), "stats untouched by the rolled-back transaction");
        assertEquals(blueBefore.wins(), blueAfter.wins());
    }

    @Test
    void recentMatchesAreOrderedNewestFirst() throws SQLException {
        // Each sample match has a distinct end reason, which doubles as its marker.
        database.inTransaction(connection -> matchDao.recordMatch(
                connection, sampleResult("ROOM1", PlayerColor.BLUE, WinCondition.STONE)));
        database.inTransaction(connection -> matchDao.recordMatch(
                connection, sampleResult("ROOM2", PlayerColor.RED, WinCondition.FORFEIT)));
        database.inTransaction(connection -> matchDao.recordMatch(
                connection, sampleResult("ROOM3", null, WinCondition.DRAW)));

        List<MatchDao.MatchRecord> records = matchDao.recentMatches(2);
        assertEquals(2, records.size(), "limit is applied");
        assertEquals("DRAW", records.get(0).endReason(), "newest first");
        assertEquals("FORFEIT", records.get(1).endReason());
    }

    private MatchResult sampleResult(String roomCode, PlayerColor winner, WinCondition way) {
        return new MatchResult(roomCode, "alice", "bob", winner, way,
                winner == PlayerColor.BLUE ? 1016 : winner == PlayerColor.RED ? 984 : 1000,
                winner == PlayerColor.BLUE ? 984 : winner == PlayerColor.RED ? 1016 : 1000,
                2);
    }
}
