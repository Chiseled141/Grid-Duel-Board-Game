package onitama.core;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * The 5x5 board. Squares are indexed {@code [y][x]}: y = 0 is Blue's home row,
 * y = 4 is Red's home row. The board is mutable, but all mutating methods are
 * package-private so that only the rules engine (and core-internal tests) can
 * change a position; every other package can only read it.
 */
public final class Board implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Board size: Onitama is played on a 5x5 grid. */
    public static final int SIZE = 5;

    private final Piece[][] squares = new Piece[SIZE][SIZE];

    /** Creates an empty board. */
    Board() {
    }

    /** Creates an independent copy of another board (deep: pieces are shared
     *  only because {@link Piece} is immutable). Package-private: the copy is
     *  used by {@link GameState#GameState(GameState)} and core-internal code. */
    Board(Board other) {
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                squares[y][x] = other.squares[y][x];
            }
        }
    }

    /**
     * Creates a board in the standard starting position: each player has a
     * Master on their Temple Arch and four Students on the rest of the home row.
     */
    public static Board newGame() {
        Board board = new Board();
        board.set(new Square(2, 0), new Piece(PlayerColor.BLUE, true));
        board.set(new Square(0, 0), new Piece(PlayerColor.BLUE, false));
        board.set(new Square(1, 0), new Piece(PlayerColor.BLUE, false));
        board.set(new Square(3, 0), new Piece(PlayerColor.BLUE, false));
        board.set(new Square(4, 0), new Piece(PlayerColor.BLUE, false));
        board.set(new Square(2, 4), new Piece(PlayerColor.RED, true));
        board.set(new Square(0, 4), new Piece(PlayerColor.RED, false));
        board.set(new Square(1, 4), new Piece(PlayerColor.RED, false));
        board.set(new Square(3, 4), new Piece(PlayerColor.RED, false));
        board.set(new Square(4, 4), new Piece(PlayerColor.RED, false));
        return board;
    }

    /** Returns true if the given coordinates are inside the 5x5 board. */
    public static boolean isInside(int x, int y) {
        return x >= 0 && x < SIZE && y >= 0 && y < SIZE;
    }

    /** Returns true if the given square is inside the 5x5 board. */
    public static boolean isInside(Square square) {
        return isInside(square.x(), square.y());
    }

    /** Returns the piece on the square, or {@code null} if it is empty. */
    public Piece pieceAt(int x, int y) {
        return squares[y][x];
    }

    /** Returns the piece on the square, or {@code null} if it is empty. */
    public Piece pieceAt(Square square) {
        return pieceAt(square.x(), square.y());
    }

    /** Returns all squares currently occupied by a piece of the given color. */
    public List<Square> occupiedBy(PlayerColor color) {
        List<Square> result = new ArrayList<>();
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                Piece piece = squares[y][x];
                if (piece != null && piece.color() == color) {
                    result.add(new Square(x, y));
                }
            }
        }
        return result;
    }

    /** Places a piece on a square (package-private: engine/test setup only). */
    void set(Square square, Piece piece) {
        squares[square.y()][square.x()] = piece;
    }

    /**
     * Relocates the piece from one square to another, capturing and returning
     * the enemy piece that may have been on the destination (or {@code null}).
     * Package-private: only the rules engine moves pieces.
     */
    Piece move(Square from, Square to) {
        Piece captured = pieceAt(to);
        set(to, pieceAt(from));
        set(from, null);
        return captured;
    }

    /** Returns a compact two-line-per-row ASCII rendering, used by the demo CLI. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                Piece piece = squares[y][x];
                if (piece == null) {
                    sb.append(" . ");
                } else {
                    sb.append(piece.color() == PlayerColor.BLUE ? "B" : "R");
                    sb.append(piece.master() ? "M " : "S ");
                }
            }
            // Cells are space-padded for alignment; the row itself ends
            // cleanly with no trailing whitespace.
            int end = sb.length();
            while (end > 0 && sb.charAt(end - 1) == ' ') {
                end--;
            }
            sb.setLength(end);
            sb.append(System.lineSeparator());
        }
        return sb.toString();
    }
}
