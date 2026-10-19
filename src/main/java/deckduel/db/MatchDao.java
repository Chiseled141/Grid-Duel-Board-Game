package deckduel.db;

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
     * the given transaction.
     *
     * @return the generecorded match id
     * @throws SQLException to roll the whole transaction back
     */
    long recordMatch(Connection connection, deckduel.server.MatchResult result)
            throws SQLException;

    /**
     * Stores a card match's banked round scores (blue = first seat) inside the same
     * transaction as {@link #recordMatch}.
     */
    void recordRounds(Connection connection, long matchId, List<deckduel.core.card.RoundResult> rounds)
            throws SQLException;

    /** Banked round scores of one match, by round number (empty for legacy matches). */
    List<deckduel.core.card.RoundResult> roundsOf(long matchId) throws SQLException;

    /** Recent matches, newest first. */
    List<MatchRecord> recentMatches(int limit) throws SQLException;

    /** Recent matches in which the user played either seat, newest first. */
    List<MatchRecord> recentMatchesFor(String username, int limit) throws SQLException;

    /** One row of the matches table joined with the players' names. */
    record MatchRecord(long id, String blueUsername, String redUsername,
                       String winnerUsername, String endReason, int moveCount,
                       String playedAt,
                       String rulesVersion, Integer startedTurns, Integer completedTurns) {
    }
}
