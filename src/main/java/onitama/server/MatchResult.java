package onitama.server;

import onitama.core.PlayerColor;
import onitama.core.WinCondition;

/**
 * Everything known about a finished match: players, result, ratings and the
 * half-move count. Consumed by the persistence hook (match row + both
 * players' Elo/wins/losses, one transaction).
 */
public record MatchResult(String roomCode,
                          String blueUsername, String redUsername,
                          PlayerColor winnerColor, WinCondition way,
                          int blueEloAfter, int redEloAfter,
                          int moveCount) {

    /** Returns the winner's username, or null on a draw. */
    public String winnerUsername() {
        return winnerColor == null ? null
                : winnerColor == PlayerColor.BLUE ? blueUsername : redUsername;
    }
}
