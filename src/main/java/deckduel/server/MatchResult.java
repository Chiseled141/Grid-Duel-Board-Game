package deckduel.server;

import deckduel.core.PlayerColor;
import deckduel.core.EndReason;

/**
 * Everything known about a finished match: players, result, rules version,
 * and turn counters. Consumed by the persistence hook (match row + both
 * players' wins/losses, one transaction). Card matches store the first seat as
 * blue and count rounds started/completed in the turn columns; their rules version
 * distinguishes them from legacy rows.
 */
public record MatchResult(String roomCode,
                          String blueUsername, String redUsername,
                          PlayerColor winnerColor, EndReason way,
                          String rulesVersion, int startedTurns, int completedTurns) {

    /** Returns the winner's username, or null on a draw. */
    public String winnerUsername() {
        return winnerColor == null ? null
                : winnerColor == PlayerColor.BLUE ? blueUsername : redUsername;
    }
}
