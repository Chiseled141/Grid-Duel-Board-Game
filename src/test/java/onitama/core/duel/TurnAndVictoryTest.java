package onitama.core.duel;

import java.util.List;
import onitama.core.PlayerColor;
import onitama.core.Square;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurnAndVictoryTest {

    @Test
    @DisplayName("Turn-start mana grant happens once, obeys mana cap, and captures grant no mana")
    void manaAccountingAndCap() {
        RulesConfig config = DuelTestFixture.defaultRulesConfig();
        GameState state = RulesEngine.newGame(config, 42L);

        PlayerColor starter = state.turn();
        assertEquals(1, state.startedTurns());
        assertEquals(0, state.completedTurns());

        // Starter begins with 3 mana (initial 2 + income 1), opponent begins with 2 mana
        int starterMana = starter == PlayerColor.BLUE ? state.blueMana() : state.redMana();
        int opponentMana = starter == PlayerColor.BLUE ? state.redMana() : state.blueMana();
        assertEquals(3, starterMana);
        assertEquals(2, opponentMana);

        // Active player ends turn without moving -> gives turn to opponent
        ActionResult r1 = RulesEngine.apply(state, starter, new GameAction.EndTurn());
        assertTrue(r1 instanceof ActionResult.Accepted);
        GameState s1 = ((ActionResult.Accepted) r1).nextState();

        assertEquals(starter.opponent(), s1.turn());
        assertEquals(2, s1.startedTurns());
        assertEquals(1, s1.completedTurns());

        // Opponent now gained 1 mana (from 2 to 3)
        int newActiveMana = s1.turn() == PlayerColor.BLUE ? s1.blueMana() : s1.redMana();
        assertEquals(3, newActiveMana);

        // Cycle turns repeatedly to reach mana cap (5)
        GameState current = s1;
        for (int i = 0; i < 10; i++) {
            ActionResult res = RulesEngine.apply(current, current.turn(), new GameAction.EndTurn());
            assertTrue(res instanceof ActionResult.Accepted);
            current = ((ActionResult.Accepted) res).nextState();
        }
        assertTrue(current.blueMana() <= 5, "Blue mana must not exceed mana cap 5: " + current.blueMana());
        assertTrue(current.redMana() <= 5, "Red mana must not exceed mana cap 5: " + current.redMana());
        assertEquals(5, current.blueMana());
        assertEquals(5, current.redMana());
    }

    @Test
    @DisplayName("SkipMovement transitions to SKILL phase without drawing cards or changing counters")
    void skipMovementTransition() {
        RulesConfig config = DuelTestFixture.defaultRulesConfig();
        GameState state = RulesEngine.newGame(config, 42L);
        PlayerColor actor = state.turn();

        assertEquals(TurnPhase.MOVEMENT, state.phase());
        long rev = state.revision();

        ActionResult result = RulesEngine.apply(state, actor, new GameAction.SkipMovement());
        assertTrue(result instanceof ActionResult.Accepted);
        GameState next = ((ActionResult.Accepted) result).nextState();

        assertEquals(TurnPhase.SKILL, next.phase());
        assertEquals(rev + 1, next.revision());
        assertEquals(actor, next.turn());
        assertEquals(state.startedTurns(), next.startedTurns());
        assertEquals(state.completedTurns(), next.completedTurns());
        assertEquals(state.blueMovementHand(), next.blueMovementHand());
        assertEquals(state.redMovementHand(), next.redMovementHand());

        // Moving after SkipMovement is rejected with WRONG_PHASE

        ActionResult moveResult = RulesEngine.apply(next, actor,
                new GameAction.MovePiece(actor == PlayerColor.BLUE ? 0 : 6, new Square(0, 2)));
        assertTrue(moveResult instanceof ActionResult.Rejected);
        assertEquals(ActionError.WRONG_PHASE, ((ActionResult.Rejected) moveResult).error());
    }

    @Test
    @DisplayName("Move then second move in same turn rejects with WRONG_PHASE")
    void secondMoveInTurnRejects() {
        RulesConfig config = DuelTestFixture.defaultRulesConfig();
        GameState state = RulesEngine.newGame(config, 42L);
        PlayerColor actor = state.turn();

        PlayerView view = RulesEngine.viewFor(state, actor);
        List<GameAction.MovePiece> moves = Targeting.legalMoves(view);
        GameAction.MovePiece move = moves.get(0);

        ActionResult r1 = RulesEngine.apply(state, actor, move);
        assertTrue(r1 instanceof ActionResult.Accepted);
        GameState s1 = ((ActionResult.Accepted) r1).nextState();
        assertEquals(TurnPhase.SKILL, s1.phase());

        // Attempt second move
        ActionResult r2 = RulesEngine.apply(s1, actor, move);
        assertTrue(r2 instanceof ActionResult.Rejected);
        assertEquals(ActionError.WRONG_PHASE, ((ActionResult.Rejected) r2).error());
    }

    @Test
    @DisplayName("Configured turn limit results in DRAW with completedTurns matching limit")
    void turnLimitDraw() {
        RulesConfig config = DuelTestFixture.rulesConfigWithTurnLimit(4);
        GameState state = RulesEngine.newGame(config, 42L);

        GameState current = state;
        for (int i = 0; i < 4; i++) {
            assertEquals(TurnPhase.MOVEMENT, current.phase());
            ActionResult res = RulesEngine.apply(current, current.turn(), new GameAction.EndTurn());
            assertTrue(res instanceof ActionResult.Accepted);
            current = ((ActionResult.Accepted) res).nextState();
        }

        assertEquals(TurnPhase.FINISHED, current.phase());
        assertEquals(4, current.completedTurns());
        assertNotNull(current.outcome());
        assertEquals(EndReason.DRAW, current.outcome().reason());
        assertNull(current.outcome().winner());
    }

    @Test
    @DisplayName("Elimination win on 60th turn takes precedence over turn limit draw")
    void eliminationPrecedenceOverDraw() {
        RulesConfig config = DuelTestFixture.defaultRulesConfig(); // turnLimit = 60
        GameState state = RulesEngine.newGame(config, 42L);

        // Advance turns until startedTurns=60, completedTurns=59
        GameState current = state;
        while (current.startedTurns() < 60) {
            ActionResult res = RulesEngine.apply(current, current.turn(), new GameAction.EndTurn());
            assertTrue(res instanceof ActionResult.Accepted);
            current = ((ActionResult.Accepted) res).nextState();
        }

        assertEquals(60, current.startedTurns());
        assertEquals(59, current.completedTurns());
        PlayerColor active = current.turn();

        // If active ends turn, it becomes DRAW at completedTurns=60
        ActionResult drawRes = RulesEngine.apply(current, active, new GameAction.EndTurn());
        assertTrue(drawRes instanceof ActionResult.Accepted);
        GameState drawState = ((ActionResult.Accepted) drawRes).nextState();
        assertEquals(EndReason.DRAW, drawState.outcome().reason());

        // Keep the starting IDs/colors and deck conservation valid in this sparse fixture.
        GameState elimMatch = DuelTestFixture.withMovementCards(active, "ox", "crab");
        int attackerId = active == PlayerColor.BLUE ? 0 : 6;
        int enemyId = active == PlayerColor.BLUE ? 6 : 0;
        for (PlacedPiece placed : elimMatch.board().placedPieces()) {
            elimMatch.board().set(placed.square(), null);
        }
        Square dest = active == PlayerColor.BLUE ? new Square(3, 4) : new Square(3, 2);
        elimMatch.board().set(new Square(3, 3), new Piece(attackerId, active));
        elimMatch.board().set(dest, new Piece(enemyId, active.opponent()));
        elimMatch.setStartedTurns(60);
        elimMatch.setCompletedTurns(59);
        elimMatch.setRevision(current.revision());

        GameAction.MovePiece winMove = new GameAction.MovePiece(attackerId, dest);
        ActionResult winResult = RulesEngine.apply(elimMatch, active, winMove);
        assertTrue(winResult instanceof ActionResult.Accepted);
        GameState winState = ((ActionResult.Accepted) winResult).nextState();

        assertEquals(TurnPhase.FINISHED, winState.phase());
        assertNotNull(winState.outcome());
        assertEquals(EndReason.ELIMINATION, winState.outcome().reason());
        assertEquals(active, winState.outcome().winner());
        assertEquals(60, winState.startedTurns());
        assertEquals(59, winState.completedTurns()); // Not 60!
        assertTrue(winState.blueMovementHand().isEmpty());
        assertTrue(winState.redMovementHand().isEmpty());
        assertTrue(winState.movementDeck().isEmpty());
        assertEquals(elimMatch.blueMana(), winState.blueMana());
        assertEquals(elimMatch.redMana(), winState.redMana());
        assertEquals(elimMatch.revision() + 1, winState.revision());
        assertEquals(List.of(new PublicEvent.PieceMoved(attackerId, new Square(3, 3), dest),
                new PublicEvent.PieceEliminated(enemyId, dest),
                new PublicEvent.MatchEnded(winState.outcome())),
                ((ActionResult.Accepted) winResult).events());
    }

    @Test
    @DisplayName("Resign and forfeit work out of turn and terminate match with FORFEIT")
    void resignAndForfeit() {
        RulesConfig config = DuelTestFixture.defaultRulesConfig();
        GameState state = RulesEngine.newGame(config, 42L);

        // Non-active player resigns
        PlayerColor inactive = state.turn().opponent();
        ActionResult res = RulesEngine.apply(state, inactive, new GameAction.Resign());
        assertTrue(res instanceof ActionResult.Accepted);
        GameState next = ((ActionResult.Accepted) res).nextState();

        assertEquals(TurnPhase.FINISHED, next.phase());
        assertEquals(EndReason.FORFEIT, next.outcome().reason());
        assertEquals(inactive.opponent(), next.outcome().winner());

        // Repeated action on finished match rejects with GAME_FINISHED
        ActionResult repeat = RulesEngine.apply(next, state.turn(), new GameAction.EndTurn());
        assertTrue(repeat instanceof ActionResult.Rejected);
        assertEquals(ActionError.GAME_FINISHED, ((ActionResult.Rejected) repeat).error());

        // Host forfeit
        GameState fresh = RulesEngine.newGame(config, 42L);
        ActionResult forfeitRes = RulesEngine.forfeit(fresh, PlayerColor.BLUE);
        assertTrue(forfeitRes instanceof ActionResult.Accepted);
        GameState forfeitState = ((ActionResult.Accepted) forfeitRes).nextState();
        assertEquals(TurnPhase.FINISHED, forfeitState.phase());
        assertEquals(EndReason.FORFEIT, forfeitState.outcome().reason());
        assertEquals(PlayerColor.RED, forfeitState.outcome().winner());
    }
}
