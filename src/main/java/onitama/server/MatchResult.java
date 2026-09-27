package onitama.server;

import onitama.core.PlayerColor;
import onitama.core.WinCondition;

import java.util.List;

/**
 * Everything known about a finished match: players, result, ratings and the
 * complete half-move history. Consumed by the persistence hook in milestone
 * M5 (match row + Elo updates already applied) and by the replay writer.
 */
public record MatchResult(String roomCode,
                          String blueUsername, String redUsername,
                          PlayerColor winnerColor, WinCondition way,
                          int blueEloAfter, int redEloAfter,
                          List<HalfMove> history) {

    /** Returns the winner's username, or null on a draw. */
    public String winnerUsername() {
        return winnerColor == null ? null
                : winnerColor == PlayerColor.BLUE ? blueUsername : redUsername;
    }

    /** Returns the number of half-moves played (moves and passes). */
    public int moveCount() {
        return history.size();
    }
}
