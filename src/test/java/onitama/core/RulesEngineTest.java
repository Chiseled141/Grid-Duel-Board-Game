package onitama.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UT02 — core legality rules: jumping over pieces is allowed, landing on a
 * friendly piece is rejected, captures remove the enemy piece, off-board
 * destinations are never offered, and rejected moves leave the state unchanged.
 */
class RulesEngineTest {

    @Test
    void jumpingOverPiecesIsAllowed() {
        Board board = new Board();
        board.set(new Square(2, 2), new Piece(PlayerColor.BLUE, false));
        // Pieces standing between origin and destination must not block Tiger's leap.
        board.set(new Square(2, 3), new Piece(PlayerColor.RED, false));
        board.set(new Square(2, 1), new Piece(PlayerColor.BLUE, false));

        List<Square> destinations = RulesEngine.legalDestinations(board, new Square(2, 2),
                CardDeck.cardById("tiger"), PlayerColor.BLUE);

        assertTrue(destinations.contains(new Square(2, 4)), "Tiger must jump over (2,3)");
        assertFalse(destinations.contains(new Square(2, 1)),
                "Tiger's backward square (2,1) is friendly-occupied and must be excluded");
    }

    @Test
    void landingOnFriendlyPieceIsRejected() {
        Board board = new Board();
        board.set(new Square(2, 2), new Piece(PlayerColor.BLUE, false));
        board.set(new Square(1, 2), new Piece(PlayerColor.BLUE, false));

        List<Square> destinations = RulesEngine.legalDestinations(board, new Square(2, 2),
                CardDeck.cardById("elephant"), PlayerColor.BLUE);

        assertFalse(destinations.contains(new Square(1, 2)), "friendly square must be excluded");
        assertTrue(destinations.contains(new Square(3, 2)), "empty square stays legal");
    }

    @Test
    void captureRemovesEnemyPiece() {
        GameState state = TestPositions.position(PlayerColor.BLUE, "boar", "tiger",
                "dragon", "frog", "horse");
        state.board().set(new Square(2, 3), new Piece(PlayerColor.BLUE, false));
        state.board().set(new Square(2, 4), new Piece(PlayerColor.RED, false));

        RulesEngine.apply(state, new Move(new Square(2, 3), new Square(2, 4), "boar"));

        assertEquals(PlayerColor.BLUE, state.board().pieceAt(new Square(2, 4)).color(),
                "capturing piece occupies the square");
        assertNull(state.board().pieceAt(new Square(2, 3)), "origin square is emptied");
    }

    @Test
    void offBoardDestinationsAreNeverOffered() {
        Board board = new Board();
        board.set(new Square(2, 0), new Piece(PlayerColor.BLUE, true));

        List<Square> destinations = RulesEngine.legalDestinations(board, new Square(2, 0),
                CardDeck.cardById("tiger"), PlayerColor.BLUE);

        // (0,-1) would leave the board: only the forward leap (0,+2) remains.
        assertEquals(List.of(new Square(2, 2)), destinations);
        assertFalse(Board.isInside(2, -1));
        assertFalse(Board.isInside(5, 2));
        assertTrue(Board.isInside(0, 4));
    }

    @Test
    void movingOpponentPieceIsRejected() {
        GameState state = TestPositions.position(PlayerColor.BLUE, "boar", "tiger",
                "dragon", "frog", "horse");
        state.board().set(new Square(2, 3), new Piece(PlayerColor.RED, false));

        assertThrows(IllegalMoveException.class, () ->
                RulesEngine.apply(state, new Move(new Square(2, 3), new Square(2, 4), "boar")));
    }

    @Test
    void cardNotInHandIsRejected() {
        GameState state = TestPositions.position(PlayerColor.BLUE, "boar", "tiger",
                "dragon", "frog", "horse");
        state.board().set(new Square(2, 2), new Piece(PlayerColor.BLUE, false));

        assertThrows(IllegalMoveException.class, () ->
                RulesEngine.apply(state, new Move(new Square(2, 2), new Square(2, 3), "dragon")));
    }

    @Test
    void patternViolatingDestinationIsRejected() {
        GameState state = TestPositions.position(PlayerColor.BLUE, "boar", "tiger",
                "dragon", "frog", "horse");
        state.board().set(new Square(2, 2), new Piece(PlayerColor.BLUE, false));

        // Boar has no diagonal; (3,3) is not part of its pattern.
        assertThrows(IllegalMoveException.class, () ->
                RulesEngine.apply(state, new Move(new Square(2, 2), new Square(3, 3), "boar")));
    }

    @Test
    void rejectedMoveLeavesStateUnchanged() {
        GameState state = TestPositions.position(PlayerColor.BLUE, "boar", "tiger",
                "dragon", "frog", "horse");
        state.board().set(new Square(2, 3), new Piece(PlayerColor.RED, false));

        assertThrows(IllegalMoveException.class, () ->
                RulesEngine.apply(state, new Move(new Square(2, 3), new Square(2, 4), "boar")));

        assertEquals(0, state.moveNumber());
        assertEquals(PlayerColor.BLUE, state.turn());
        assertEquals(GameState.Status.ONGOING, state.status());
        assertEquals(new Piece(PlayerColor.RED, false), state.board().pieceAt(new Square(2, 3)));
    }

    @Test
    void moveAfterGameOverIsRejected() {
        GameState state = TestPositions.position(PlayerColor.BLUE, "boar", "tiger",
                "dragon", "frog", "horse");
        state.board().set(new Square(2, 3), new Piece(PlayerColor.BLUE, false));
        state.board().set(new Square(2, 4), new Piece(PlayerColor.RED, true));
        RulesEngine.apply(state, new Move(new Square(2, 3), new Square(2, 4), "boar"));
        assertEquals(GameState.Status.FINISHED, state.status());

        assertThrows(IllegalMoveException.class, () ->
                RulesEngine.apply(state, new Move(new Square(2, 3), new Square(2, 4), "boar")));
    }
}
