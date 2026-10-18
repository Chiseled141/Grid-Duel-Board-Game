package deckduel.core.duel;

import java.util.List;
import deckduel.core.PlayerColor;
import deckduel.core.Square;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Card-free movement is identical for both colors and shares the engine's targeting rules. */
class MovementRulesTest {
    private GameState state(PlayerColor actor) {
        var state = RulesEngine.newGame(DuelTestFixture.defaultRulesConfig(), 42);
        state.setTurn(actor);
        return state;
    }

    @Test void bothColorsCanMoveInAllFourOrthogonalDirections() {
        for (PlayerColor actor : PlayerColor.values()) {
            var state = state(actor);
            int id = actor == PlayerColor.BLUE ? 0 : 6;
            state.board().move(state.board().findPiece(id), new Square(3, 3));
            var moves = Targeting.legalMoves(RulesEngine.viewFor(state, actor));
            for (Square to : List.of(new Square(2, 3), new Square(4, 3), new Square(3, 2), new Square(3, 4))) {
                var move = new GameAction.MovePiece(id, to);
                assertTrue(moves.contains(move));
                assertInstanceOf(ActionResult.Accepted.class, RulesEngine.apply(state, actor, move));
            }
        }
    }

    @Test void boardEdgeAndLastRankAreUsable() {
        var state = state(PlayerColor.BLUE);
        state.board().move(new Square(0, 0), new Square(5, 5));
        var accepted = assertInstanceOf(ActionResult.Accepted.class,
                RulesEngine.apply(state, PlayerColor.BLUE, new GameAction.MovePiece(0, new Square(5, 6))));
        assertEquals(new Square(5, 6), accepted.nextState().board().findPiece(0));
        assertNull(accepted.nextState().board().findPiece(10));
    }

    @Test void offBoardMovesReject() {
        var state = state(PlayerColor.BLUE);
        for (Square to : List.of(new Square(-1, 0), new Square(0, -1), new Square(7, 0))) {
            var rejected = assertInstanceOf(ActionResult.Rejected.class,
                    RulesEngine.apply(state, PlayerColor.BLUE, new GameAction.MovePiece(0, to)));
            assertEquals(ActionError.INVALID_TARGETS, rejected.error());
        }
    }

    @Test void jumpsDiagonalsAndStandingStillReject() {
        var state = state(PlayerColor.BLUE);
        for (Square to : List.of(new Square(0, 3), new Square(1, 1), new Square(0, 0)))
            assertInstanceOf(ActionResult.Rejected.class,
                    RulesEngine.apply(state, PlayerColor.BLUE, new GameAction.MovePiece(0, to)));
    }

    @Test void friendlyLandingRejectsAndEnemyLandingCapturesAndScores() {
        var state = state(PlayerColor.BLUE);
        assertInstanceOf(ActionResult.Rejected.class,
                RulesEngine.apply(state, PlayerColor.BLUE, new GameAction.MovePiece(0, new Square(1, 0))));
        state.board().move(new Square(0, 6), new Square(0, 1));
        var accepted = assertInstanceOf(ActionResult.Accepted.class,
                RulesEngine.apply(state, PlayerColor.BLUE, new GameAction.MovePiece(0, new Square(0, 1))));
        assertNull(accepted.nextState().board().findPiece(6));
        assertEquals(1, accepted.nextState().score(PlayerColor.BLUE));
        assertEquals(TurnPhase.SKILL, accepted.nextState().phase());
        assertTrue(accepted.events().stream().anyMatch(e -> e instanceof PublicEvent.PieceEliminated));
    }

    @Test void rejectsWrongActorEnemyMissingPieceAndRepeatedMovement() {
        var state = state(PlayerColor.BLUE);
        assertEquals(ActionError.NOT_YOUR_TURN, assertInstanceOf(ActionResult.Rejected.class,
                RulesEngine.apply(state, PlayerColor.RED, new GameAction.MovePiece(6, new Square(0, 5)))).error());
        for (int id : List.of(6, 99)) assertInstanceOf(ActionResult.Rejected.class,
                RulesEngine.apply(state, PlayerColor.BLUE, new GameAction.MovePiece(id, new Square(0, 1))));
        var accepted = (ActionResult.Accepted) RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.MovePiece(0, new Square(0, 1)));
        assertEquals(ActionError.WRONG_PHASE, assertInstanceOf(ActionResult.Rejected.class,
                RulesEngine.apply(accepted.nextState(), PlayerColor.BLUE,
                        new GameAction.MovePiece(0, new Square(0, 2)))).error());
    }

    @Test void generatedMovesAreAcceptedAndUnavailableOutOfTurnOrPhase() {
        var state = state(PlayerColor.BLUE);
        var moves = Targeting.legalMoves(RulesEngine.viewFor(state, PlayerColor.BLUE));
        assertEquals(7, moves.size());
        for (var move : moves) assertInstanceOf(ActionResult.Accepted.class, RulesEngine.apply(state, PlayerColor.BLUE, move));
        assertTrue(Targeting.legalMoves(RulesEngine.viewFor(state, PlayerColor.RED)).isEmpty());
        state.setPhase(TurnPhase.SKILL);
        assertTrue(Targeting.legalMoves(RulesEngine.viewFor(state, PlayerColor.BLUE)).isEmpty());
    }
}
