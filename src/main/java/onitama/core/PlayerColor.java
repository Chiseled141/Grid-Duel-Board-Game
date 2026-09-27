package onitama.core;

/**
 * The two players of Onitama. Blue's home row is {@code y = 0} and Red's home
 * row is {@code y = 4}; "forward" for a player means toward the opponent's
 * home row.
 */
public enum PlayerColor {
    BLUE,
    RED;

    /** Returns the opponent of this color. */
    public PlayerColor opponent() {
        return this == BLUE ? RED : BLUE;
    }

    /**
     * Returns the square of this color's own Temple Arch: (2,0) for Blue and
     * (2,4) for Red. The Way of the Stream win condition is about reaching the
     * {@link #opponent() opponent's} arch.
     */
    public Square templeArch() {
        return this == BLUE ? new Square(2, 0) : new Square(2, 4);
    }
}
