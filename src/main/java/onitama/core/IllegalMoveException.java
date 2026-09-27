package onitama.core;

/**
 * Thrown by {@link RulesEngine} when a move or pass violates the rules of
 * Onitama (not your turn, card not in hand, illegal destination, illegal
 * pass, or the game is already over).
 */
public class IllegalMoveException extends OnitamaException {

    private static final long serialVersionUID = 1L;

    /** Creates an exception describing why the move is illegal. */
    public IllegalMoveException(String message) {
        super(message);
    }
}
