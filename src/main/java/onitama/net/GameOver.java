package onitama.net;

import onitama.core.PlayerColor;
import onitama.core.WinCondition;

import java.io.Serial;

/**
 * S→C. A match has ended: the winner (null on a draw), how it ended, and both
 * players' Elo ratings after the update. The server persists the match
 * before sending this.
 */
public record GameOver(PlayerColor winnerColor, WinCondition way,
                       int blueEloAfter, int redEloAfter) implements Message {

    @Serial
    private static final long serialVersionUID = 1L;
}
