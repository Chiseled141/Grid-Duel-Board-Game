package onitama.core.duel;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import onitama.core.PlayerColor;
import onitama.core.Square;
import org.junit.jupiter.api.Test;

class TargetingAllSkillsTest {

    private GameState createTestState(String skillDefId, int mana) {
        GameState state = RulesEngine.newGame(DuelTestFixture.defaultRulesConfig(), 777L);
        state.mutableSkillHand(PlayerColor.BLUE).clear();
        state.mutableSkillHand(PlayerColor.BLUE).add(new CardInstance(skillDefId + "#1", skillDefId));
        state.setBlueMana(mana);
        state.setTurn(PlayerColor.BLUE);
        state.setPhase(TurnPhase.SKILL);
        return state;
    }

    @Test
    void testTargetingDash() {
        GameState state = createTestState("dash", 2);
        PlayerView view = RulesEngine.viewFor(state, PlayerColor.BLUE);

        // Piece 0 at (0, 0) can dash to (0, 2)
        SelectionOptions opt1 = Targeting.skillOptions(view, "dash#1", SelectionDraft.empty());
        assertTrue(opt1.selectablePieceIds().contains(0));

        SelectionOptions opt2 = Targeting.skillOptions(view, "dash#1", new SelectionDraft(List.of(0), List.of(), null));
        assertTrue(opt2.selectableSquares().contains(new Square(0, 2)));

        SelectionOptions opt3 = Targeting.skillOptions(view, "dash#1", new SelectionDraft(List.of(0), List.of(new Square(0, 2)), null));
        assertTrue(opt3.canSubmit());
        assertNotNull(opt3.completeTargets());

        // Verify that RulesEngine accepts the complete target
        ActionResult res = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("dash#1", opt3.completeTargets()));
        assertTrue(res instanceof ActionResult.Accepted);
    }

    @Test
    void testTargetingAdvance() {
        GameState state = createTestState("advance", 3);
        PlayerView view = RulesEngine.viewFor(state, PlayerColor.BLUE);

        SelectionOptions opt1 = Targeting.skillOptions(view, "advance#1", SelectionDraft.empty());
        assertTrue(opt1.selectablePieceIds().contains(0));

        // Select piece 0 -> offers forward square (0, 1)
        SelectionOptions opt2 = Targeting.skillOptions(view, "advance#1", new SelectionDraft(List.of(0), List.of(), null));
        assertTrue(opt2.selectableSquares().contains(new Square(0, 1)));

        // Select square (0, 1) -> offers remaining piece 1
        SelectionOptions opt3 = Targeting.skillOptions(view, "advance#1", new SelectionDraft(List.of(0), List.of(new Square(0, 1)), null));
        assertTrue(opt3.selectablePieceIds().contains(1));

        // Select piece 1 -> offers forward square (1, 1)
        SelectionOptions opt4 = Targeting.skillOptions(view, "advance#1", new SelectionDraft(List.of(0, 1), List.of(new Square(0, 1)), null));
        assertTrue(opt4.selectableSquares().contains(new Square(1, 1)));

        // Select square (1, 1) -> complete!
        SelectionOptions opt5 = Targeting.skillOptions(view, "advance#1", new SelectionDraft(List.of(0, 1), List.of(new Square(0, 1), new Square(1, 1)), null));
        assertTrue(opt5.canSubmit());

        ActionResult res = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("advance#1", opt5.completeTargets()));
        assertTrue(res instanceof ActionResult.Accepted);
    }

    @Test
    void testTargetingGust() {
        GameState state = createTestState("gust", 3);
        // Put enemy 6 at (3, 3)
        state.board().move(new Square(0, 6), new Square(3, 3));
        PlayerView view = RulesEngine.viewFor(state, PlayerColor.BLUE);

        // Select enemy 6 first -> offers directions
        SelectionOptions opt1 = Targeting.skillOptions(view, "gust#1", new SelectionDraft(List.of(6), List.of(), null));
        assertTrue(opt1.selectableDirections().contains(Direction.SOUTH));

        // Select SOUTH -> complete!
        SelectionOptions opt2 = Targeting.skillOptions(view, "gust#1", new SelectionDraft(List.of(6), List.of(), Direction.SOUTH));
        assertTrue(opt2.canSubmit());

        ActionResult res = RulesEngine.apply(state, PlayerColor.BLUE,
                new GameAction.CastSkill("gust#1", opt2.completeTargets()));
        assertTrue(res instanceof ActionResult.Accepted);
    }

    @Test
    void testTargetingNoneSkills() {
        List<String> noneSkills = List.of("mana-surge", "refocus", "foresight");
        for (String skillId : noneSkills) {
            GameState state = createTestState(skillId, 3);
            PlayerView view = RulesEngine.viewFor(state, PlayerColor.BLUE);

            SelectionOptions opt = Targeting.skillOptions(view, skillId + "#1", SelectionDraft.empty());
            assertTrue(opt.canSubmit());
            assertNotNull(opt.completeTargets());
            assertTrue(opt.completeTargets() instanceof SkillTargets.None);

            ActionResult res = RulesEngine.apply(state, PlayerColor.BLUE,
                    new GameAction.CastSkill(skillId + "#1", opt.completeTargets()));
            assertTrue(res instanceof ActionResult.Accepted);
        }
    }
}
