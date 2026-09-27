package onitama.net;

import onitama.core.GameState;
import onitama.core.PlayerColor;

import java.io.Serial;

/**
 * S→C. Sent to both players when a match begins: which color you play, the
 * room code, the opponent's name and the full authoritative initial state
 * (board, both hands, transit card, first player).
 */
public record MatchStart(PlayerColor yourColor, String roomCode, String opponentUsername,
                         GameState initialState) implements Message {

    @Serial
    private static final long serialVersionUID = 1L;
}
