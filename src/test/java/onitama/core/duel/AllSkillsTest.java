package onitama.core.duel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import onitama.core.PlayerColor;
import onitama.core.Square;
import org.junit.jupiter.api.Test;

class AllSkillsTest {

    private GameState createTestState(String skillDefId, int mana, PlayerColor actor) {
        GameState state = RulesEngine.newGame(DuelTestFixture.defaultRulesConfig(), 999L);
        state.mutableSkillHand(actor).clear();
        state.mutableSkillHand(actor).add(new CardInstance(skillDefId + "#1", skillDefId));
        if (actor == PlayerColor.BLUE) {
            state.setBlueMana(mana);
        } else {
            state.setRedMana(mana);
        }
        state.setTurn(actor);
        state.setPhase(TurnPhase.SKILL);
        return state;
    }

    // --- Movement Skills ---

    @Test
    void testDashClearPathAndBlocked() {
        GameState state = createTestState("dash", 2, PlayerColor.BLUE);
        // Piece 0 at (0, 0). Move to (0, 2). (0, 1) is empty.
        ActionResult res = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("dash#1", new SkillTargets.Relocate(0, new Square(0, 2))));
        assertTrue(res instanceof ActionResult.Accepted);
        GameState next = ((ActionResult.Accepted) res).nextState();
        assertEquals(new Square(0, 2), next.board().findPiece(0));

        // Blocked path: put enemy at (0, 1)
        state.board().move(new Square(0, 6), new Square(0, 1));
        ActionResult blockedRes = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("dash#1", new SkillTargets.Relocate(0, new Square(0, 2))));
        assertTrue(blockedRes instanceof ActionResult.Rejected r && r.error() == ActionError.INVALID_TARGETS);
    }

    @Test
    void testBlink() {
        GameState state = createTestState("blink", 3, PlayerColor.BLUE);
        // Piece 0 at (0, 0). Blink to (2, 1) (Manhattan 3).
        ActionResult res = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("blink#1", new SkillTargets.Relocate(0, new Square(2, 1))));
        assertTrue(res instanceof ActionResult.Accepted);
        GameState next = ((ActionResult.Accepted) res).nextState();
        assertEquals(new Square(2, 1), next.board().findPiece(0));

        // Distance 4 rejected (default range is 3)
        ActionResult resFar = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("blink#1", new SkillTargets.Relocate(0, new Square(2, 2))));
        assertTrue(resFar instanceof ActionResult.Rejected r && r.error() == ActionError.INVALID_TARGETS);
    }

    @Test
    void testAdvanceBlueAndRed() {
        // Blue moves forward (dy = +1)
        GameState blueState = createTestState("advance", 3, PlayerColor.BLUE);
        // Piece 0 at (0, 0) -> (0, 1), Piece 1 at (1, 0) -> (1, 1)
        ActionResult blueRes = RulesEngine.apply(blueState, PlayerColor.BLUE,
                new GameAction.CastSkill("advance#1", new SkillTargets.MoveGroup(List.of(
                        new Relocation(0, new Square(0, 1)),
                        new Relocation(1, new Square(1, 1))))));
        assertTrue(blueRes instanceof ActionResult.Accepted);
        GameState blueNext = ((ActionResult.Accepted) blueRes).nextState();
        assertEquals(new Square(0, 1), blueNext.board().findPiece(0));
        assertEquals(new Square(1, 1), blueNext.board().findPiece(1));

        // Red moves forward (dy = -1)
        GameState redState = createTestState("advance", 3, PlayerColor.RED);
        // Red pieces 6 at (0, 6) -> (0, 5), 7 at (1, 6) -> (1, 5)
        ActionResult redRes = RulesEngine.apply(redState, PlayerColor.RED,
                new GameAction.CastSkill("advance#1", new SkillTargets.MoveGroup(List.of(
                        new Relocation(6, new Square(0, 5)),
                        new Relocation(7, new Square(1, 5))))));
        assertTrue(redRes instanceof ActionResult.Accepted);
        GameState redNext = ((ActionResult.Accepted) redRes).nextState();
        assertEquals(new Square(0, 5), redNext.board().findPiece(6));
        assertEquals(new Square(1, 5), redNext.board().findPiece(7));

        // Reject duplicate pieces or backward direction
        ActionResult badRes = RulesEngine.apply(blueState, PlayerColor.BLUE,
                new GameAction.CastSkill("advance#1", new SkillTargets.MoveGroup(List.of(
                        new Relocation(0, new Square(0, 1)),
                        new Relocation(0, new Square(0, 1))))));
        assertTrue(badRes instanceof ActionResult.Rejected r && r.error() == ActionError.INVALID_TARGETS);
    }

    @Test
    void testRecallBlueAndRed() {
        GameState blueState = createTestState("recall", 1, PlayerColor.BLUE);
        // Move piece 0 to (3, 3), then recall to empty (3, 0)
        blueState.board().move(new Square(0, 0), new Square(3, 3));
        ActionResult res = RulesEngine.apply(blueState, PlayerColor.BLUE,
                new GameAction.CastSkill("recall#1", new SkillTargets.Relocate(0, new Square(3, 0))));
        assertTrue(res instanceof ActionResult.Accepted);
        GameState next = ((ActionResult.Accepted) res).nextState();
        assertEquals(new Square(3, 0), next.board().findPiece(0));

        // Red recalls to y = 6
        GameState redState = createTestState("recall", 1, PlayerColor.RED);
        redState.board().move(new Square(0, 6), new Square(3, 3));
        ActionResult redRes = RulesEngine.apply(redState, PlayerColor.RED,
                new GameAction.CastSkill("recall#1", new SkillTargets.Relocate(6, new Square(3, 6))));
        assertTrue(redRes instanceof ActionResult.Accepted);
        assertEquals(new Square(3, 6), ((ActionResult.Accepted) redRes).nextState().board().findPiece(6));

        // Reject wrong row
        ActionResult badRes = RulesEngine.apply(blueState, PlayerColor.BLUE,
                new GameAction.CastSkill("recall#1", new SkillTargets.Relocate(0, new Square(3, 1))));
        assertTrue(badRes instanceof ActionResult.Rejected r && r.error() == ActionError.INVALID_TARGETS);
    }

    @Test
    void testVaultJumpsOverPieces() {
        GameState state = createTestState("vault", 2, PlayerColor.BLUE);
        // Put piece 0 at (0, 0), place piece 1 at (0, 1), vault over piece 1 to (0, 2)
        state.board().move(new Square(1, 0), new Square(0, 1));
        ActionResult res = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("vault#1", new SkillTargets.Relocate(0, new Square(0, 2))));
        assertTrue(res instanceof ActionResult.Accepted);
        assertEquals(new Square(0, 2), ((ActionResult.Accepted) res).nextState().board().findPiece(0));
    }

    @Test
    void testRally() {
        GameState state = createTestState("rally", 3, PlayerColor.BLUE);
        // Anchor 0 at (3, 3), piece 1 at (3, 5), piece 2 at (1, 3)
        state.board().move(new Square(0, 0), new Square(3, 3));
        state.board().move(new Square(1, 0), new Square(3, 5));
        state.board().move(new Square(2, 0), new Square(1, 3));

        // Piece 1 moves (3, 5) -> (3, 4) (closer to (3, 3)), Piece 2 moves (1, 3) -> (2, 3) (closer)
        ActionResult res = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("rally#1", new SkillTargets.Rally(0, List.of(
                        new Relocation(1, new Square(3, 4)),
                        new Relocation(2, new Square(2, 3))))));
        assertTrue(res instanceof ActionResult.Accepted);
        GameState next = ((ActionResult.Accepted) res).nextState();
        assertEquals(new Square(3, 4), next.board().findPiece(1));
        assertEquals(new Square(2, 3), next.board().findPiece(2));

        // Reject moving away from anchor
        ActionResult badRes = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("rally#1", new SkillTargets.Rally(0, List.of(
                        new Relocation(1, new Square(3, 6))))));
        assertTrue(badRes instanceof ActionResult.Rejected r && r.error() == ActionError.INVALID_TARGETS);
    }

    @Test
    void testScatter() {
        GameState state = createTestState("scatter", 3, PlayerColor.BLUE);
        // Piece 0 at (0, 0) moves diagonally to (1, 1), Piece 5 at (6, 0) moves diagonally to (5, 1)
        ActionResult res = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("scatter#1", new SkillTargets.MoveGroup(List.of(
                        new Relocation(0, new Square(1, 1)),
                        new Relocation(5, new Square(5, 1))))));
        assertTrue(res instanceof ActionResult.Accepted);
        GameState next = ((ActionResult.Accepted) res).nextState();
        assertEquals(new Square(1, 1), next.board().findPiece(0));
        assertEquals(new Square(5, 1), next.board().findPiece(5));
    }

    // --- Attack Skills ---

    @Test
    void testPierceClearPathAndBlocked() {
        GameState state = createTestState("pierce", 4, PlayerColor.BLUE);
        // Friendly 0 at (3, 3), enemy 6 at (3, 5) (distance 2 orthogonal). Middle (3, 4) is empty.
        state.board().move(new Square(0, 0), new Square(3, 3));
        state.board().move(new Square(0, 6), new Square(3, 5));

        ActionResult res = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("pierce#1", new SkillTargets.Attack(0, List.of(6))));
        assertTrue(res instanceof ActionResult.Accepted);
        assertNull(((ActionResult.Accepted) res).nextState().board().findPiece(6));

        // Blocked path
        state.board().move(new Square(1, 0), new Square(3, 4));
        ActionResult blocked = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("pierce#1", new SkillTargets.Attack(0, List.of(6))));
        assertTrue(blocked instanceof ActionResult.Rejected r && r.error() == ActionError.INVALID_TARGETS);
    }

    @Test
    void testSurround() {
        GameState state = createTestState("surround", 2, PlayerColor.BLUE);
        // Enemy 6 at (3, 3). Allies 0 at (2, 3) and 1 at (4, 3) (2 allies adjacent).
        state.board().move(new Square(0, 6), new Square(3, 3));
        state.board().move(new Square(0, 0), new Square(2, 3));
        state.board().move(new Square(1, 0), new Square(4, 3));

        ActionResult res = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("surround#1", new SkillTargets.Enemy(6)));
        assertTrue(res instanceof ActionResult.Accepted);
        assertNull(((ActionResult.Accepted) res).nextState().board().findPiece(6));

        // Only 1 ally: rejected
        state.board().move(new Square(4, 3), new Square(4, 4));
        ActionResult res1 = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("surround#1", new SkillTargets.Enemy(6)));
        assertTrue(res1 instanceof ActionResult.Rejected r && r.error() == ActionError.INVALID_TARGETS);
    }

    @Test
    void testSweep() {
        GameState state = createTestState("sweep", 5, PlayerColor.BLUE);
        // Friendly 0 at (3, 3). Enemies 6 at (3, 4) and 7 at (4, 3).
        state.board().move(new Square(0, 0), new Square(3, 3));
        state.board().move(new Square(0, 6), new Square(3, 4));
        state.board().move(new Square(1, 6), new Square(4, 3));

        ActionResult res = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("sweep#1", new SkillTargets.Attack(0, List.of(6, 7))));
        assertTrue(res instanceof ActionResult.Accepted);
        GameState next = ((ActionResult.Accepted) res).nextState();
        assertNull(next.board().findPiece(6));
        assertNull(next.board().findPiece(7));
    }

    @Test
    void testFlankMoveAndCapture() {
        GameState state = createTestState("flank", 3, PlayerColor.BLUE);
        // Friendly 0 at (0, 0) moves diagonally to (1, 1) (empty)
        ActionResult resEmpty = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("flank#1", new SkillTargets.Relocate(0, new Square(1, 1))));
        assertTrue(resEmpty instanceof ActionResult.Accepted);
        assertEquals(new Square(1, 1), ((ActionResult.Accepted) resEmpty).nextState().board().findPiece(0));

        // Place enemy 6 at (1, 1) -> capture
        state.board().move(new Square(0, 6), new Square(1, 1));
        ActionResult resCap = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("flank#1", new SkillTargets.Relocate(0, new Square(1, 1))));
        assertTrue(resCap instanceof ActionResult.Accepted);
        GameState capNext = ((ActionResult.Accepted) resCap).nextState();
        assertEquals(new Square(1, 1), capNext.board().findPiece(0));
        assertNull(capNext.board().findPiece(6));
    }

    @Test
    void testCrossfire() {
        GameState state = createTestState("crossfire", 4, PlayerColor.BLUE);
        // Enemy 6 at (3, 3). Ally 0 at (1, 3), Ally 1 at (5, 3) (horizontal crossfire, clear intervening).
        state.board().move(new Square(0, 6), new Square(3, 3));
        state.board().move(new Square(0, 0), new Square(1, 3));
        state.board().move(new Square(1, 0), new Square(5, 3));

        ActionResult res = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("crossfire#1", new SkillTargets.Enemy(6)));
        assertTrue(res instanceof ActionResult.Accepted);
        assertNull(((ActionResult.Accepted) res).nextState().board().findPiece(6));

        // Blocked horizontal path: enemy 7 at (2, 3) blocks the line between ally 0 and enemy 6
        GameState blockedState = createTestState("crossfire", 4, PlayerColor.BLUE);
        blockedState.board().move(new Square(0, 6), new Square(3, 3));
        blockedState.board().move(new Square(0, 0), new Square(1, 3));
        blockedState.board().move(new Square(1, 0), new Square(5, 3));
        blockedState.board().move(new Square(1, 6), new Square(2, 3)); // enemy piece blocks
        ActionResult blocked = RulesEngine.apply(blockedState, PlayerColor.BLUE,
                new GameAction.CastSkill("crossfire#1", new SkillTargets.Enemy(6)));
        assertTrue(blocked instanceof ActionResult.Rejected r && r.error() == ActionError.INVALID_TARGETS);
    }

    @Test
    void testChainStrike() {
        GameState state = createTestState("chain-strike", 5, PlayerColor.BLUE);
        // Friendly 0 at (3, 3). Enemy 6 at (3, 4). Enemy 7 at (4, 4) (adjacent to 6's square).
        state.board().move(new Square(0, 0), new Square(3, 3));
        state.board().move(new Square(0, 6), new Square(3, 4));
        state.board().move(new Square(1, 6), new Square(4, 4));

        ActionResult res = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("chain-strike#1", new SkillTargets.Attack(0, List.of(6, 7))));
        assertTrue(res instanceof ActionResult.Accepted);
        GameState next = ((ActionResult.Accepted) res).nextState();
        assertNull(next.board().findPiece(6));
        assertNull(next.board().findPiece(7));
    }

    // --- Control Skills ---

    @Test
    void testShove() {
        GameState state = createTestState("shove", 1, PlayerColor.BLUE);
        // Friendly 0 at (3, 3), Enemy 6 at (3, 4). Shove pushes 6 to (3, 5).
        state.board().move(new Square(0, 0), new Square(3, 3));
        state.board().move(new Square(0, 6), new Square(3, 4));

        ActionResult res = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("shove#1", new SkillTargets.Displace(0, 6)));
        assertTrue(res instanceof ActionResult.Accepted);
        assertEquals(new Square(3, 5), ((ActionResult.Accepted) res).nextState().board().findPiece(6));

        // Shove off board rejected
        GameState offBoardState = createTestState("shove", 1, PlayerColor.BLUE);
        offBoardState.board().move(new Square(0, 0), new Square(0, 5));
        offBoardState.board().move(new Square(0, 6), new Square(0, 6)); // pushed to (0, 7) off-board
        ActionResult offBoard = RulesEngine.apply(offBoardState, PlayerColor.BLUE,
                new GameAction.CastSkill("shove#1", new SkillTargets.Displace(0, 6)));
        assertTrue(offBoard instanceof ActionResult.Rejected r && r.error() == ActionError.INVALID_TARGETS);
    }

    @Test
    void testPull() {
        GameState state = createTestState("pull", 1, PlayerColor.BLUE);
        // Friendly 0 at (3, 3), Enemy 6 at (3, 5). Middle (3, 4) empty. Pull 6 to (3, 4).
        state.board().move(new Square(0, 0), new Square(3, 3));
        state.board().move(new Square(0, 6), new Square(3, 5));

        ActionResult res = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("pull#1", new SkillTargets.Displace(0, 6)));
        assertTrue(res instanceof ActionResult.Accepted);
        assertEquals(new Square(3, 4), ((ActionResult.Accepted) res).nextState().board().findPiece(6));
    }

    @Test
    void testTransposition() {
        GameState state = createTestState("transposition", 4, PlayerColor.BLUE);
        // Friendly 0 at (3, 3), Enemy 6 at (3, 5) (distance 2 <= range 2). Swap without capture.
        state.board().move(new Square(0, 0), new Square(3, 3));
        state.board().move(new Square(0, 6), new Square(3, 5));

        ActionResult res = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("transposition#1", new SkillTargets.Swap(0, 6)));
        assertTrue(res instanceof ActionResult.Accepted);
        GameState next = ((ActionResult.Accepted) res).nextState();
        assertEquals(new Square(3, 5), next.board().findPiece(0));
        assertEquals(new Square(3, 3), next.board().findPiece(6));
    }

    @Test
    void testGust() {
        GameState state = createTestState("gust", 3, PlayerColor.BLUE);
        // Enemies 6 at (3, 3) and 7 at (4, 3) shifted SOUTH (dy = +1) to (3, 4) and (4, 4)
        state.board().move(new Square(0, 6), new Square(3, 3));
        state.board().move(new Square(1, 6), new Square(4, 3));

        ActionResult res = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("gust#1", new SkillTargets.ShiftEnemies(List.of(6, 7), Direction.SOUTH)));
        assertTrue(res instanceof ActionResult.Accepted);
        GameState next = ((ActionResult.Accepted) res).nextState();
        assertEquals(new Square(3, 4), next.board().findPiece(6));
        assertEquals(new Square(4, 4), next.board().findPiece(7));
    }

    @Test
    void testBanish() {
        GameState state = createTestState("banish", 4, PlayerColor.BLUE);
        // Friendly 0 at (3, 3), adjacent enemy 6 at (3, 4).
        // Enemy 6 is RED, starting row is y = 6. Destination (3, 6) is empty.
        state.board().move(new Square(0, 0), new Square(3, 3));
        state.board().move(new Square(0, 6), new Square(3, 4));

        ActionResult res = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("banish#1", new SkillTargets.RelocateEnemy(0, 6, new Square(3, 6))));
        assertTrue(res instanceof ActionResult.Accepted);
        assertEquals(new Square(3, 6), ((ActionResult.Accepted) res).nextState().board().findPiece(6));

        // Wrong row rejected
        ActionResult badRow = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("banish#1", new SkillTargets.RelocateEnemy(0, 6, new Square(3, 5))));
        assertTrue(badRow instanceof ActionResult.Rejected r && r.error() == ActionError.INVALID_TARGETS);
    }

    // --- Resource Skills ---

    @Test
    void testManaSurge() {
        GameState state = createTestState("mana-surge", 2, PlayerColor.BLUE);
        ActionResult res = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("mana-surge#1", new SkillTargets.None()));
        assertTrue(res instanceof ActionResult.Accepted);
        GameState next = ((ActionResult.Accepted) res).nextState();
        assertEquals(4, next.blueMana()); // 2 + 2 = 4 (cost 0)
        assertEquals(TurnPhase.END, next.phase());

        // Cap enforcement (manaCap 5)
        GameState capState = createTestState("mana-surge", 4, PlayerColor.BLUE);
        ActionResult capRes = RulesEngine.apply(capState, PlayerColor.BLUE,
                new GameAction.CastSkill("mana-surge#1", new SkillTargets.None()));
        assertTrue(capRes instanceof ActionResult.Accepted);
        assertEquals(5, ((ActionResult.Accepted) capRes).nextState().blueMana());
    }

    @Test
    void testRefocus() {
        GameState state = createTestState("refocus", 2, PlayerColor.BLUE);
        List<String> originalHand = List.copyOf(state.blueMovementHand());

        ActionResult res = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("refocus#1", new SkillTargets.None()));
        assertTrue(res instanceof ActionResult.Accepted);
        GameState next = ((ActionResult.Accepted) res).nextState();

        assertEquals(1, next.blueMana()); // 2 - 1
        assertEquals(2, next.blueMovementHand().size());
        assertEquals(TurnPhase.END, next.phase());
        assertEquals(12, next.movementDeck().size()); // 16 total - 2 Red - 2 Blue = 12 deck
    }

    @Test
    void testSkillEliminationPrecedenceOverDrawOn60thTurn() {
        // Strike elimination on turn 60: startedTurns=60, completedTurns=59
        GameState state = createTestState("strike", 3, PlayerColor.BLUE);
        state.setStartedTurns(60);
        state.setCompletedTurns(59);

        // Leave only 1 enemy piece on board
        for (PlacedPiece pp : state.board().placedPieces()) {
            if (pp.piece().color() == PlayerColor.RED && pp.piece().id() != 6) {
                state.board().remove(pp.square());
            }
        }
        state.board().move(new Square(0, 0), new Square(3, 3));
        state.board().move(new Square(0, 6), new Square(3, 4));

        ActionResult res = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("strike#1", new SkillTargets.Attack(0, List.of(6))));
        assertTrue(res instanceof ActionResult.Accepted);
        GameState winState = ((ActionResult.Accepted) res).nextState();

        assertEquals(TurnPhase.FINISHED, winState.phase());
        assertNotNull(winState.outcome());
        assertEquals(EndReason.ELIMINATION, winState.outcome().reason());
        assertEquals(PlayerColor.BLUE, winState.outcome().winner());
        assertEquals(60, winState.startedTurns());
        assertEquals(59, winState.completedTurns()); // Before EndTurn draw check!
    }
}
