package onitama.core.duel;

import java.util.List;
import onitama.core.Card;
import onitama.core.CardDeck;
import onitama.core.Offset;
import onitama.core.PlayerColor;
import onitama.core.Square;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementRulesTest {

    @Test
    @DisplayName("Blue moves with native offsets and Red rotates 180 negating both dx and dy")
    void blueAndRedOrientation() {
        for (PlayerColor actor : PlayerColor.values()) {
            GameState state = DuelTestFixture.withMovementCards(actor, "frog", "tiger");
            int id = actor == PlayerColor.BLUE ? 0 : 6;
            Square from = state.board().findPiece(id);
            state.board().move(from, new Square(3, 3));
            // Frog's (-1,+1) becomes (+1,-1) for Red: both axes must rotate.
            Square destination = actor == PlayerColor.BLUE ? new Square(2, 4) : new Square(4, 2);
            var move = new GameAction.MovePiece("frog", id, destination);
            assertTrue(Targeting.legalMoves(RulesEngine.viewFor(state, actor)).contains(move));
            var result = RulesEngine.apply(state, actor, move);
            assertTrue(result instanceof ActionResult.Accepted);
            assertEquals(destination, ((ActionResult.Accepted) result).nextState().board().findPiece(id));
            Square wrongDirection = actor == PlayerColor.BLUE ? new Square(2, 2) : new Square(4, 4);
            assertTrue(RulesEngine.apply(state, actor,
                    new GameAction.MovePiece("frog", id, wrongDirection)) instanceof ActionResult.Rejected);
        }
    }

    @Test
    @DisplayName("Destination at x=5,6 and y=5,6 is accepted (7x7 bounds check)")
    void destinationIn7x7CornersAccepted() {
        RulesConfig config = DuelTestFixture.defaultRulesConfig();
        GameState state = RulesEngine.newGame(config, 42L);

        // Place a Blue piece at (5, 4) and give Blue Tiger (0, 2) -> destination (5, 6)
        state.board().move(new Square(0, 0), new Square(5, 4));
        state.mutableMovementHand(PlayerColor.BLUE).clear();
        state.mutableMovementHand(PlayerColor.BLUE).add("tiger");
        state.mutableMovementHand(PlayerColor.BLUE).add("crab");
        state.setTurn(PlayerColor.BLUE);
        state.setPhase(TurnPhase.MOVEMENT);

        // Clear square (5, 6) so it's empty
        if (state.board().pieceAt(5, 6) != null) {
            state.board().move(new Square(5, 6), new Square(3, 3));
        }

        GameAction.MovePiece move = new GameAction.MovePiece("tiger", 0, new Square(5, 6));
        ActionResult result = RulesEngine.apply(state, PlayerColor.BLUE, move);

        assertTrue(result instanceof ActionResult.Accepted, "Move to (5,6) should be accepted in 7x7 grid");
        GameState next = ((ActionResult.Accepted) result).nextState();
        assertEquals(new Square(5, 6), next.board().findPiece(0));
    }

    @Test
    @DisplayName("Off-board moves reject safely with INVALID_TARGETS")
    void offBoardMovesReject() {
        RulesConfig config = DuelTestFixture.defaultRulesConfig();
        GameState state = RulesEngine.newGame(config, 42L);
        state.setTurn(PlayerColor.BLUE);
        state.setPhase(TurnPhase.MOVEMENT);
        state.mutableMovementHand(PlayerColor.BLUE).clear();
        state.mutableMovementHand(PlayerColor.BLUE).add("tiger");
        state.mutableMovementHand(PlayerColor.BLUE).add("crab");

        // Piece 0 is at (0, 0). Tiger (0, -1) would land at (0, -1) which is off-board
        GameAction.MovePiece offBoardMove = new GameAction.MovePiece("tiger", 0, new Square(0, -1));
        ActionResult result = RulesEngine.apply(state, PlayerColor.BLUE, offBoardMove);

        assertTrue(result instanceof ActionResult.Rejected);
        assertEquals(ActionError.INVALID_TARGETS, ((ActionResult.Rejected) result).error());

        // Coordinate x=7
        GameAction.MovePiece offBoardX7 = new GameAction.MovePiece("tiger", 0, new Square(7, 0));
        ActionResult resX7 = RulesEngine.apply(state, PlayerColor.BLUE, offBoardX7);
        assertTrue(resX7 instanceof ActionResult.Rejected);
        assertEquals(ActionError.INVALID_TARGETS, ((ActionResult.Rejected) resX7).error());
    }

    @Test
    @DisplayName("Jumping over friendly and enemy pieces is legal")
    void movementCardJumping() {
        RulesConfig config = DuelTestFixture.defaultRulesConfig();
        GameState state = RulesEngine.newGame(config, 42L);
        state.setTurn(PlayerColor.BLUE);
        state.setPhase(TurnPhase.MOVEMENT);
        state.mutableMovementHand(PlayerColor.BLUE).clear();
        state.mutableMovementHand(PlayerColor.BLUE).add("tiger");
        state.mutableMovementHand(PlayerColor.BLUE).add("crab");

        // Place a piece at (0, 1) directly in front of piece 0 at (0, 0)
        state.board().move(new Square(1, 0), new Square(0, 1));

        // Piece 0 at (0, 0) moves to (0, 2) using Tiger, jumping over (0, 1)
        GameAction.MovePiece jump = new GameAction.MovePiece("tiger", 0, new Square(0, 2));
        ActionResult result = RulesEngine.apply(state, PlayerColor.BLUE, jump);

        assertTrue(result instanceof ActionResult.Accepted, "Jumping over intervening piece is legal");
        GameState next = ((ActionResult.Accepted) result).nextState();
        assertEquals(new Square(0, 2), next.board().findPiece(0));
        assertNotNull(next.board().pieceAt(0, 1)); // Intervening piece remains intact
    }

    @Test
    @DisplayName("Landing on friendly piece rejects, landing on enemy captures")
    void friendlyAndEnemyLanding() {
        RulesConfig config = DuelTestFixture.defaultRulesConfig();
        GameState state = RulesEngine.newGame(config, 42L);
        state.setTurn(PlayerColor.BLUE);
        state.setPhase(TurnPhase.MOVEMENT);
        state.mutableMovementHand(PlayerColor.BLUE).clear();
        state.mutableMovementHand(PlayerColor.BLUE).add("crab");
        state.mutableMovementHand(PlayerColor.BLUE).add("tiger");

        // Piece 0 at (0, 0). Crab: (0, 1), (-2, 0), (2, 0).
        // (2, 0) contains friendly Blue piece id 2.
        GameAction.MovePiece friendlyLanding = new GameAction.MovePiece("crab", 0, new Square(2, 0));
        ActionResult rejectResult = RulesEngine.apply(state, PlayerColor.BLUE, friendlyLanding);
        assertTrue(rejectResult instanceof ActionResult.Rejected);
        assertEquals(ActionError.INVALID_TARGETS, ((ActionResult.Rejected) rejectResult).error());

        // Put enemy Red piece id 6 at (0, 1)
        state.board().move(new Square(0, 6), new Square(0, 1));

        // Crab (0, 1) from (0, 0) lands on Red piece -> capture
        GameAction.MovePiece captureMove = new GameAction.MovePiece("crab", 0, new Square(0, 1));
        ActionResult captureResult = RulesEngine.apply(state, PlayerColor.BLUE, captureMove);
        assertTrue(captureResult instanceof ActionResult.Accepted);
        GameState next = ((ActionResult.Accepted) captureResult).nextState();

        assertEquals(0, next.board().pieceAt(0, 1).id());
        assertNull(next.board().findPiece(6)); // Red piece 6 captured
        assertEquals(5, next.board().countPieces(PlayerColor.RED));

        // Verify PublicEvents contains PieceMoved and PieceEliminated
        List<PublicEvent> events = ((ActionResult.Accepted) captureResult).events();
        boolean hasMoved = events.stream().anyMatch(e -> e instanceof PublicEvent.PieceMoved pm && pm.pieceId() == 0);
        boolean hasElim = events.stream().anyMatch(e -> e instanceof PublicEvent.PieceEliminated pe && pe.pieceId() == 6);
        assertTrue(hasMoved);
        assertTrue(hasElim);
    }

    @Test
    @DisplayName("Validation rejects wrong actor, dead piece, enemy piece, unheld card, wrong phase")
    void movementValidationRejections() {
        RulesConfig config = DuelTestFixture.defaultRulesConfig();
        GameState state = RulesEngine.newGame(config, 42L);
        state.setTurn(PlayerColor.BLUE);
        state.setPhase(TurnPhase.MOVEMENT);

        String heldCard = state.blueMovementHand().get(0);

        // Wrong actor (Red acts on Blue turn)
        ActionResult r1 = RulesEngine.apply(state, PlayerColor.RED,
                new GameAction.MovePiece(heldCard, 6, new Square(0, 5)));
        assertTrue(r1 instanceof ActionResult.Rejected);
        assertEquals(ActionError.NOT_YOUR_TURN, ((ActionResult.Rejected) r1).error());

        // Enemy piece as source
        ActionResult r2 = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.MovePiece(heldCard, 6, new Square(0, 5)));
        assertTrue(r2 instanceof ActionResult.Rejected);
        assertEquals(ActionError.INVALID_TARGETS, ((ActionResult.Rejected) r2).error());

        // Dead / nonexistent piece id (e.g. 99)
        ActionResult r3 = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.MovePiece(heldCard, 99, new Square(0, 1)));
        assertTrue(r3 instanceof ActionResult.Rejected);
        assertEquals(ActionError.INVALID_TARGETS, ((ActionResult.Rejected) r3).error());

        // Card not held
        ActionResult r4 = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.MovePiece("unheld-card", 0, new Square(0, 1)));
        assertTrue(r4 instanceof ActionResult.Rejected);
        assertEquals(ActionError.CARD_NOT_HELD, ((ActionResult.Rejected) r4).error());

        // Wrong phase (SKILL phase)
        state.setPhase(TurnPhase.SKILL);
        ActionResult r5 = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.MovePiece(heldCard, 0, new Square(0, 1)));
        assertTrue(r5 instanceof ActionResult.Rejected);
        assertEquals(ActionError.WRONG_PHASE, ((ActionResult.Rejected) r5).error());
    }

    @Test
    @DisplayName("Targeting.legalMoves returns all valid moves and none illegal")
    void targetingLegalMovesEnumeration() {
        RulesConfig config = DuelTestFixture.defaultRulesConfig();
        GameState state = RulesEngine.newGame(config, 42L);
        PlayerColor actor = state.turn();

        PlayerView view = RulesEngine.viewFor(state, actor);
        List<GameAction.MovePiece> moves = Targeting.legalMoves(view);

        assertFalse(moves.isEmpty(), "Active player must have legal starting moves");

        // Every generated move must be accepted by RulesEngine
        for (GameAction.MovePiece move : moves) {
            ActionResult res = RulesEngine.apply(state, actor, move);
            assertTrue(res instanceof ActionResult.Accepted,
                    "Move generated by Targeting must be accepted: " + move);
        }

        // Out of turn returns empty
        PlayerView oppView = RulesEngine.viewFor(state, actor.opponent());
        assertTrue(Targeting.legalMoves(oppView).isEmpty());

        // Wrong phase returns empty
        state.setPhase(TurnPhase.SKILL);
        PlayerView skillView = RulesEngine.viewFor(state, actor);
        assertTrue(Targeting.legalMoves(skillView).isEmpty());
    }
}
