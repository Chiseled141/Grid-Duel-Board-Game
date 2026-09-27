package onitama.core;

/**
 * How a game of Onitama ended. STONE and STREAM are the two official ways to
 * win; DRAW is the 300-half-move house rule; FORFEIT is awarded by the server
 * when a player leaves a live game (resign, disconnect beyond the grace
 * period, or illegal protocol behavior).
 */
public enum WinCondition {
    STONE,
    STREAM,
    DRAW,
    FORFEIT;

    /**
     * Evaluates whether the just-applied move ended the game.
     *
     * @param boardAfter the board after the move was applied
     * @param move       the move that was applied
     * @param captured   the enemy piece captured by the move, or {@code null}
     * @return the satisfied win condition, or {@code null} if the game continues
     */
    public static WinCondition evaluate(Board boardAfter, Move move, Piece captured) {
        // Way of the Stone: capturing the enemy Master wins immediately. This is
        // checked first, so a Master that captures the enemy Master on the enemy
        // Temple Arch is credited with a Stone win.
        if (captured != null && captured.master()) {
            return STONE;
        }
        // Way of the Stream: your own Master lands on the opponent's Temple Arch.
        Piece arrived = boardAfter.pieceAt(move.to());
        if (arrived != null && arrived.master()
                && move.to().equals(arrived.color().opponent().templeArch())) {
            return STREAM;
        }
        return null;
    }
}
