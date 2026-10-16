package onitama.core;

/**
 * The two players of Grid Duel. Blue's home row is {@code y = 0} and Red's home
 * row is {@code y = 6}; "forward" for a player means toward the opponent's
 * home row.
 */
public enum PlayerColor {
    BLUE,
    RED;

    /** Returns the opponent of this color. */
    public PlayerColor opponent() {
        return this == BLUE ? RED : BLUE;
    }
}
