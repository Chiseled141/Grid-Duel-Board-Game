package onitama.core.duel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import onitama.core.PlayerColor;
import onitama.core.Square;

/**
 * The 7x7 board for Grid Duel. Squares are indexed [y][x] with bounds 0..6.
 * Package-private mutation is confined to engine simulation and internal fixtures.
 */
public final class Board {

    public static final int SIZE = 7;

    private final Piece[][] squares = new Piece[SIZE][SIZE];

    Board() {}

    Board(Board other) {
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                squares[y][x] = other.squares[y][x];
            }
        }
    }

    public static Board newGame() {
        Board board = new Board();
        // Blue pieces 0..5 on y=0: x = 0, 1, 2, 4, 5, 6 (x=3 empty)
        board.set(new Square(0, 0), new Piece(0, PlayerColor.BLUE));
        board.set(new Square(1, 0), new Piece(1, PlayerColor.BLUE));
        board.set(new Square(2, 0), new Piece(2, PlayerColor.BLUE));
        board.set(new Square(4, 0), new Piece(3, PlayerColor.BLUE));
        board.set(new Square(5, 0), new Piece(4, PlayerColor.BLUE));
        board.set(new Square(6, 0), new Piece(5, PlayerColor.BLUE));

        // Red pieces 6..11 on y=6: x = 0, 1, 2, 4, 5, 6 (x=3 empty)
        board.set(new Square(0, 6), new Piece(6, PlayerColor.RED));
        board.set(new Square(1, 6), new Piece(7, PlayerColor.RED));
        board.set(new Square(2, 6), new Piece(8, PlayerColor.RED));
        board.set(new Square(4, 6), new Piece(9, PlayerColor.RED));
        board.set(new Square(5, 6), new Piece(10, PlayerColor.RED));
        board.set(new Square(6, 6), new Piece(11, PlayerColor.RED));

        // Keep existing stable IDs; the seventh piece fills each former center gap.
        board.set(new Square(3, 0), new Piece(12, PlayerColor.BLUE));
        board.set(new Square(3, 6), new Piece(13, PlayerColor.RED));
        return board;
    }

    public static boolean isInside(int x, int y) {
        return x >= 0 && x < SIZE && y >= 0 && y < SIZE;
    }

    public static boolean isInside(Square square) {
        return square != null && isInside(square.x(), square.y());
    }

    public Piece pieceAt(int x, int y) {
        if (!isInside(x, y)) return null;
        return squares[y][x];
    }

    public Piece pieceAt(Square square) {
        if (square == null || !isInside(square)) return null;
        return squares[square.y()][square.x()];
    }

    public Square findPiece(int pieceId) {
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                Piece p = squares[y][x];
                if (p != null && p.id() == pieceId) {
                    return new Square(x, y);
                }
            }
        }
        return null;
    }

    public Piece findPieceById(int pieceId) {
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                Piece p = squares[y][x];
                if (p != null && p.id() == pieceId) {
                    return p;
                }
            }
        }
        return null;
    }

    public List<PlacedPiece> placedPieces() {
        List<PlacedPiece> result = new ArrayList<>();
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                Piece p = squares[y][x];
                if (p != null) {
                    result.add(new PlacedPiece(p, new Square(x, y)));
                }
            }
        }
        return List.copyOf(result);
    }

    public int countPieces(PlayerColor color) {
        int count = 0;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                Piece p = squares[y][x];
                if (p != null && p.color() == color) {
                    count++;
                }
            }
        }
        return count;
    }

    void set(Square square, Piece piece) {
        Objects.requireNonNull(square, "square must not be null");
        if (!isInside(square)) {
            throw new IllegalArgumentException("square outside board: " + square);
        }
        if (piece != null) {
            Square existing = findPiece(piece.id());
            if (existing != null && !existing.equals(square)) {
                throw new IllegalArgumentException("piece id already present at " + existing + ": " + piece.id());
            }
        }
        squares[square.y()][square.x()] = piece;
    }

    Piece move(Square from, Square to) {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        if (!isInside(from) || !isInside(to)) {
            throw new IllegalArgumentException("square outside board: from=" + from + ", to=" + to);
        }
        Piece moving = pieceAt(from);
        if (moving == null) {
            throw new IllegalArgumentException("no piece at " + from);
        }
        Piece captured = pieceAt(to);
        squares[from.y()][from.x()] = null;
        squares[to.y()][to.x()] = moving;
        return captured;
    }

    Piece remove(Square square) {
        Objects.requireNonNull(square, "square must not be null");
        if (!isInside(square)) {
            throw new IllegalArgumentException("square outside board: " + square);
        }
        Piece removed = squares[square.y()][square.x()];
        squares[square.y()][square.x()] = null;
        return removed;
    }

    void swap(Square s1, Square s2) {
        Objects.requireNonNull(s1, "s1 must not be null");
        Objects.requireNonNull(s2, "s2 must not be null");
        if (!isInside(s1) || !isInside(s2)) {
            throw new IllegalArgumentException("square outside board: s1=" + s1 + ", s2=" + s2);
        }
        Piece p1 = squares[s1.y()][s1.x()];
        Piece p2 = squares[s2.y()][s2.x()];
        squares[s1.y()][s1.x()] = p2;
        squares[s2.y()][s2.x()] = p1;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int y = SIZE - 1; y >= 0; y--) {
            sb.append(y).append(" ");
            for (int x = 0; x < SIZE; x++) {
                Piece piece = squares[y][x];
                if (piece == null) {
                    sb.append("  . ");
                } else {
                    sb.append(piece.color() == PlayerColor.BLUE ? " B" : " R");
                    sb.append(piece.id() < 10 ? piece.id() + " " : piece.id());
                }
            }
            int end = sb.length();
            while (end > 0 && sb.charAt(end - 1) == ' ') {
                end--;
            }
            sb.setLength(end);
            sb.append(System.lineSeparator());
        }
        sb.append("     0   1   2   3   4   5   6").append(System.lineSeparator());
        return sb.toString();
    }
}
