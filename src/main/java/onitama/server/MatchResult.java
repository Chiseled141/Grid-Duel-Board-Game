package onitama.server;

import onitama.core.PlayerColor;
import onitama.core.duel.EndReason;

/**
 * Everything known about a finished match: players, result, ratings, rules version,
 * and turn counters. Consumed by the persistence hook (match row + both
 * players' Elo/wins/losses, one transaction).
 */
public record MatchResult(String roomCode,
                          String blueUsername, String redUsername,
                          PlayerColor winnerColor, EndReason way,
                          int blueEloAfter, int redEloAfter,
                          String rulesVersion, int startedTurns, int completedTurns) {

    /** Returns the winner's username, or null on a draw. */
    public String winnerUsername() {
        return winnerColor == null ? null
                : winnerColor == PlayerColor.BLUE ? blueUsername : redUsername;
    }
}
