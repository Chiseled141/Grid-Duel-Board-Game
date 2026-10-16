package onitama.core.duel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import onitama.core.PlayerColor;
import onitama.core.Square;
import org.junit.jupiter.api.Test;

class SkillLifecycleTest {

    private GameState createTestStateWithSkill(String skillDefId, int mana) {
        GameState state = RulesEngine.newGame(DuelTestFixture.defaultRulesConfig(), 12345L);
        String instId = skillDefId + "#1";
        CardInstance card = new CardInstance(instId, skillDefId);
        state.mutableSkillHand(PlayerColor.BLUE).clear();
        state.mutableSkillHand(PlayerColor.BLUE).add(card);
        state.setBlueMana(mana);
        state.setTurn(PlayerColor.BLUE);
        state.setPhase(TurnPhase.SKILL);
        return state;
    }

    @Test
    void testSidestepSuccess() {
        GameState state = createTestStateWithSkill("sidestep", 3);
        int initialSkillDeckSize = state.skillDeck().size();
        int initialSpentSize = state.spentSkills().size();
        long initialRevision = state.revision();

        // Blue piece 0 is at (0, 0). (0, 1) is empty.
        GameAction.CastSkill action = new GameAction.CastSkill(
                "sidestep#1",
                new SkillTargets.Relocate(0, new Square(0, 1)));

        ActionResult res = RulesEngine.apply(state, PlayerColor.BLUE, action);
        assertTrue(res instanceof ActionResult.Accepted);
        ActionResult.Accepted accepted = (ActionResult.Accepted) res;
        GameState next = accepted.nextState();

        assertEquals(2, next.blueMana()); // 3 - 1
        assertEquals(TurnPhase.END, next.phase());
        assertEquals(initialRevision + 1, next.revision());
        assertEquals(new Square(0, 1), next.board().findPiece(0));
        assertNull(next.board().pieceAt(new Square(0, 0)));

        // Card lifecycle
        assertFalse(next.blueSkillHand().stream().anyMatch(c -> c.instanceId().equals("sidestep#1")));
        assertEquals(initialSpentSize + 1, next.spentSkills().size());
        assertEquals("sidestep#1", next.spentSkills().get(next.spentSkills().size() - 1).instanceId());
        assertEquals(initialSkillDeckSize - 1, next.skillDeck().size());
        assertEquals(1, next.blueSkillHand().size()); // replacement was drawn

        // Events
        List<PublicEvent> events = accepted.events();
        assertTrue(events.get(0) instanceof PublicEvent.SkillCommitted sc && sc.actor() == PlayerColor.BLUE);
        assertTrue(events.get(1) instanceof PublicEvent.PieceMoved pm && pm.pieceId() == 0 && pm.to().equals(new Square(0, 1)));

        // Explicit EndTurn advances to Red
        ActionResult endRes = RulesEngine.apply(next, PlayerColor.BLUE, new GameAction.EndTurn());
        assertTrue(endRes instanceof ActionResult.Accepted);
        GameState afterEnd = ((ActionResult.Accepted) endRes).nextState();
        assertEquals(PlayerColor.RED, afterEnd.turn());
        assertEquals(TurnPhase.MOVEMENT, afterEnd.phase());
    }

    @Test
    void testSidestepRejectionsAndAtomicity() {
        GameState state = createTestStateWithSkill("sidestep", 3);

        // 1. Destination occupied by friendly piece (1, 0)
        ActionResult res1 = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("sidestep#1", new SkillTargets.Relocate(0, new Square(1, 0))));
        assertTrue(res1 instanceof ActionResult.Rejected r && r.error() == ActionError.INVALID_TARGETS);

        // 2. Off board
        ActionResult res2 = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("sidestep#1", new SkillTargets.Relocate(0, new Square(-1, 0))));
        assertTrue(res2 instanceof ActionResult.Rejected r && r.error() == ActionError.INVALID_TARGETS);

        // 3. Distance 2 (Sidestep default is distance 1)
        ActionResult res3 = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("sidestep#1", new SkillTargets.Relocate(0, new Square(0, 2))));
        assertTrue(res3 instanceof ActionResult.Rejected r && r.error() == ActionError.INVALID_TARGETS);

        // 4. Enemy piece as target piece
        ActionResult res4 = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("sidestep#1", new SkillTargets.Relocate(6, new Square(0, 5))));
        assertTrue(res4 instanceof ActionResult.Rejected r && r.error() == ActionError.INVALID_TARGETS);

        // Original state untouched
        assertEquals(3, state.blueMana());
        assertEquals(1, state.blueSkillHand().size());
        assertEquals(TurnPhase.SKILL, state.phase());
    }

    @Test
    void testStrikeSuccessAndEliminationWin() {
        GameState state = createTestStateWithSkill("strike", 3);
        // Put piece 0 at (3, 3) and enemy piece 6 at (3, 4)
        state.board().move(new Square(0, 0), new Square(3, 3));
        state.board().move(new Square(0, 6), new Square(3, 4));

        GameAction.CastSkill action = new GameAction.CastSkill(
                "strike#1",
                new SkillTargets.Attack(0, List.of(6)));

        ActionResult res = RulesEngine.apply(state, PlayerColor.BLUE, action);
        assertTrue(res instanceof ActionResult.Accepted);
        GameState next = ((ActionResult.Accepted) res).nextState();

        assertEquals(0, next.blueMana()); // 3 - 3
        assertNull(next.board().pieceAt(new Square(3, 4)));
        assertNull(next.board().findPiece(6));
        assertEquals(new Square(3, 3), next.board().findPiece(0)); // source stayed still

        // Card bookkeeping finished
        assertEquals("strike#1", next.spentSkills().get(next.spentSkills().size() - 1).instanceId());
        assertEquals(TurnPhase.END, next.phase());

        // Now test elimination win when last enemy is eliminated
        // Clear all red pieces except piece 7 at (1, 6)
        GameState oneEnemyState = createTestStateWithSkill("strike", 3);
        for (PlacedPiece pp : oneEnemyState.board().placedPieces()) {
            if (pp.piece().color() == PlayerColor.RED && pp.piece().id() != 6) {
                oneEnemyState.board().remove(pp.square());
            }
        }
        assertEquals(1, oneEnemyState.board().countPieces(PlayerColor.RED));
        oneEnemyState.board().move(new Square(0, 0), new Square(3, 3));
        oneEnemyState.board().move(new Square(0, 6), new Square(3, 4));

        ActionResult winRes = RulesEngine.apply(oneEnemyState, PlayerColor.BLUE,
                new GameAction.CastSkill("strike#1", new SkillTargets.Attack(0, List.of(6))));
        assertTrue(winRes instanceof ActionResult.Accepted);
        GameState winState = ((ActionResult.Accepted) winRes).nextState();

        assertEquals(TurnPhase.FINISHED, winState.phase());
        assertNotNull(winState.outcome());
        assertEquals(PlayerColor.BLUE, winState.outcome().winner());
        assertEquals(EndReason.ELIMINATION, winState.outcome().reason());
        // Card bookkeeping still executed
        assertEquals("strike#1", winState.spentSkills().get(winState.spentSkills().size() - 1).instanceId());
    }

    @Test
    void testStrikeRejections() {
        GameState state = createTestStateWithSkill("strike", 3);
        state.board().move(new Square(0, 0), new Square(3, 3));
        state.board().move(new Square(0, 6), new Square(3, 5)); // Manhattan distance 2

        // Non-adjacent enemy
        ActionResult res1 = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("strike#1", new SkillTargets.Attack(0, List.of(6))));
        assertTrue(res1 instanceof ActionResult.Rejected r && r.error() == ActionError.INVALID_TARGETS);

        // Attacking friendly piece
        ActionResult res2 = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("strike#1", new SkillTargets.Attack(0, List.of(1))));
        assertTrue(res2 instanceof ActionResult.Rejected r && r.error() == ActionError.INVALID_TARGETS);

        // Enemy piece acting as source
        ActionResult res3 = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("strike#1", new SkillTargets.Attack(6, List.of(1))));
        assertTrue(res3 instanceof ActionResult.Rejected r && r.error() == ActionError.INVALID_TARGETS);
    }

    @Test
    void testExchangeSuccessAndRejections() {
        GameState state = createTestStateWithSkill("exchange", 2);
        // Piece 0 is at (0, 0), Piece 1 is at (1, 0) - distance 1 <= 3
        ActionResult res = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("exchange#1", new SkillTargets.Swap(0, 1)));
        assertTrue(res instanceof ActionResult.Accepted);
        GameState next = ((ActionResult.Accepted) res).nextState();

        assertEquals(0, next.blueMana()); // 2 - 2
        assertEquals(new Square(1, 0), next.board().findPiece(0));
        assertEquals(new Square(0, 0), next.board().findPiece(1));

        // Reject self swap
        ActionResult resSelf = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("exchange#1", new SkillTargets.Swap(0, 0)));
        assertTrue(resSelf instanceof ActionResult.Rejected r && r.error() == ActionError.INVALID_TARGETS);

        // Reject out of range (Piece 0 at (0, 0) and Piece 5 at (6, 0), distance 6 > 3)
        ActionResult resRange = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("exchange#1", new SkillTargets.Swap(0, 5)));
        assertTrue(resRange instanceof ActionResult.Rejected r && r.error() == ActionError.INVALID_TARGETS);

        // Reject swap with enemy piece
        ActionResult resEnemy = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("exchange#1", new SkillTargets.Swap(0, 6)));
        assertTrue(resEnemy instanceof ActionResult.Rejected r && r.error() == ActionError.INVALID_TARGETS);
    }

    @Test
    void testCommonLifecycleConstraints() {
        GameState state = createTestStateWithSkill("sidestep", 3);

        // 1. Wrong phase (MOVEMENT)
        state.setPhase(TurnPhase.MOVEMENT);
        ActionResult resPhase = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("sidestep#1", new SkillTargets.Relocate(0, new Square(0, 1))));
        assertTrue(resPhase instanceof ActionResult.Rejected r && r.error() == ActionError.WRONG_PHASE);

        // 2. Wrong actor (Red's turn)
        state.setPhase(TurnPhase.SKILL);
        ActionResult resActor = RulesEngine.apply(state, PlayerColor.RED,
                new GameAction.CastSkill("sidestep#1", new SkillTargets.Relocate(0, new Square(0, 1))));
        assertTrue(resActor instanceof ActionResult.Rejected r && r.error() == ActionError.NOT_YOUR_TURN);

        // 3. Insufficient mana (cost 1, mana 0)
        state.setBlueMana(0);
        ActionResult resMana = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("sidestep#1", new SkillTargets.Relocate(0, new Square(0, 1))));
        assertTrue(resMana instanceof ActionResult.Rejected r && r.error() == ActionError.INSUFFICIENT_MANA);

        // 4. Card not held
        state.setBlueMana(3);
        ActionResult resCard = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("sidestep#2", new SkillTargets.Relocate(0, new Square(0, 1))));
        assertTrue(resCard instanceof ActionResult.Rejected r && r.error() == ActionError.CARD_NOT_HELD);

        // 5. Empty skill deck handles draw gracefully
        state.mutableSkillDeck().clear();
        ActionResult resEmptyDeck = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("sidestep#1", new SkillTargets.Relocate(0, new Square(0, 1))));
        assertTrue(resEmptyDeck instanceof ActionResult.Accepted);
        GameState next = ((ActionResult.Accepted) resEmptyDeck).nextState();
        assertEquals(0, next.blueSkillHand().size()); // spent, no replacement drawn
        assertEquals(0, next.skillDeck().size());
    }

    @Test
    void testTargetingSkillOptions() {
        GameState state = createTestStateWithSkill("sidestep", 3);
        PlayerView view = RulesEngine.viewFor(state, PlayerColor.BLUE);

        // Empty draft: returns piece IDs that have legal moves
        SelectionOptions opt1 = Targeting.skillOptions(view, "sidestep#1", SelectionDraft.empty());
        assertFalse(opt1.selectablePieceIds().isEmpty());
        assertTrue(opt1.selectablePieceIds().contains(0));
        assertFalse(opt1.canSubmit());

        // Draft with piece 0: returns empty orthogonal squares
        SelectionDraft draft1 = new SelectionDraft(List.of(0), List.of(), null);
        SelectionOptions opt2 = Targeting.skillOptions(view, "sidestep#1", draft1);
        assertTrue(opt2.selectableSquares().contains(new Square(0, 1)));
        assertFalse(opt2.canSubmit());

        // Draft with piece 0 and square (0, 1): complete!
        SelectionDraft draft2 = new SelectionDraft(List.of(0), List.of(new Square(0, 1)), null);
        SelectionOptions opt3 = Targeting.skillOptions(view, "sidestep#1", draft2);
        assertTrue(opt3.canSubmit());
        assertNotNull(opt3.completeTargets());
        assertTrue(opt3.completeTargets() instanceof SkillTargets.Relocate rel
                && rel.pieceId() == 0 && rel.to().equals(new Square(0, 1)));

        // Error handling on invalid/partial drafts
        SelectionDraft invalidDraft = new SelectionDraft(List.of(999), List.of(), null);
        SelectionOptions errOpt = Targeting.skillOptions(view, "sidestep#1", invalidDraft);
        assertNotNull(errOpt.error());
    }
}
