package onitama.core.duel;

import java.util.List;
import onitama.core.PlayerColor;
import onitama.core.Square;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardTest {

    @Test
    @DisplayName("7x7 starting setup has 12 pieces, correct IDs, colors, coordinates, and center gaps")
    void startingBoardLayout() {
        Board board = Board.newGame();

        assertEquals(7, Board.SIZE);
        assertEquals(6, board.countPieces(PlayerColor.BLUE));
        assertEquals(6, board.countPieces(PlayerColor.RED));

        // Center gaps on home rows must be empty
        assertNull(board.pieceAt(3, 0));
        assertNull(board.pieceAt(3, 6));

        // Blue starting row y=0
        int[] blueCols = {0, 1, 2, 4, 5, 6};
        for (int i = 0; i < blueCols.length; i++) {
            int col = blueCols[i];
            Piece p = board.pieceAt(col, 0);
            assertNotNull(p);
            assertEquals(i, p.id());
            assertEquals(PlayerColor.BLUE, p.color());
            assertEquals(new Square(col, 0), board.findPiece(i));
        }

        // Red starting row y=6
        int[] redCols = {0, 1, 2, 4, 5, 6};
        for (int i = 0; i < redCols.length; i++) {
            int col = redCols[i];
            int expectedId = 6 + i;
            Piece p = board.pieceAt(col, 6);
            assertNotNull(p);
            assertEquals(expectedId, p.id());
            assertEquals(PlayerColor.RED, p.color());
            assertEquals(new Square(col, 6), board.findPiece(expectedId));
        }

        List<PlacedPiece> placed = board.placedPieces();
        assertEquals(12, placed.size());
    }

    @Test
    @DisplayName("Board bounds checking for 7x7 grid")
    void boardBounds() {
        assertTrue(Board.isInside(0, 0));
        assertTrue(Board.isInside(6, 6));
        assertTrue(Board.isInside(5, 5));
        assertTrue(Board.isInside(new Square(6, 0)));
        assertTrue(Board.isInside(new Square(0, 6)));

        assertFalse(Board.isInside(-1, 0));
        assertFalse(Board.isInside(0, -1));
        assertFalse(Board.isInside(7, 0));
        assertFalse(Board.isInside(0, 7));
        assertFalse(Board.isInside(7, 7));
        assertFalse(Board.isInside((Square) null));
    }

    @Test
    @DisplayName("Piece relocation and capture updates squares cleanly")
    void moveAndCapture() {
        Board board = Board.newGame();
        Square from = new Square(0, 0);
        Square to = new Square(0, 2);

        // Move to empty square
        Piece captured = board.move(from, to);
        assertNull(captured);
        assertNull(board.pieceAt(from));
        assertEquals(0, board.pieceAt(to).id());
        assertEquals(to, board.findPiece(0));

        // Move Red piece to capture Blue piece at (0,2)
        Square redFrom = new Square(0, 6);
        Piece capturedBlue = board.move(redFrom, to);
        assertNotNull(capturedBlue);
        assertEquals(0, capturedBlue.id());
        assertEquals(PlayerColor.BLUE, capturedBlue.color());
        assertEquals(6, board.pieceAt(to).id());
        assertNull(board.findPiece(0)); // Blue piece removed
        assertEquals(to, board.findPiece(6));
    }

    @Test
    @DisplayName("Board prevents duplicate piece IDs and off-board placement")
    void rejectDuplicatesAndOffBoard() {
        Board board = Board.newGame();

        // Attempt placing existing piece ID on a new square
        assertThrows(IllegalArgumentException.class,
                () -> board.set(new Square(3, 3), new Piece(0, PlayerColor.BLUE)));

        // Attempt placing off-board
        assertThrows(IllegalArgumentException.class,
                () -> board.set(new Square(7, 0), new Piece(20, PlayerColor.BLUE)));

        // Attempt moving off-board or from empty square
        assertThrows(IllegalArgumentException.class,
                () -> board.move(new Square(3, 3), new Square(3, 4)));
        assertThrows(IllegalArgumentException.class,
                () -> board.move(new Square(0, 0), new Square(7, 0)));
    }

    @Test
    @DisplayName("Board copy is completely independent of original")
    void deepCopyIsolation() {
        Board original = Board.newGame();
        Board copy = new Board(original);

        copy.move(new Square(0, 0), new Square(0, 2));

        assertNull(copy.pieceAt(0, 0));
        assertNotNull(copy.pieceAt(0, 2));

        // Original is untouched
        assertNotNull(original.pieceAt(0, 0));
        assertNull(original.pieceAt(0, 2));
    }
}
