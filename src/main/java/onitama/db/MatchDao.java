package onitama.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Persistence for finished matches (interface per the brief's design map:
 * DAOs are interfaces, SQLite is one swappable implementation). Insertions
 * happen inside a caller-provided transaction so the match row and both
 * users' stat updates commit or roll back together.
 */
public interface MatchDao {

    /**
     * Inserts one finished match and updates both players' stats, all inside
     * the given transaction; {@code replayPath} may be null.
     *
     * @return the generated match id
     * @throws SQLException to roll the whole transaction back
     */
    long recordMatch(Connection connection, onitama.server.MatchResult result,
                     String replayPath) throws SQLException;

    /** Recent matches, newest first (for the report's read path and future UI). */
    List<MatchRecord> recentMatches(int limit) throws SQLException;

    /** One row of the matches table joined with the players' names. */
    record MatchRecord(long id, String blueUsername, String redUsername,
                       String winnerUsername, String endReason, int moveCount,
                       int eloBlueAfter, int eloRedAfter, String replayPath, String playedAt) {
    }
}
