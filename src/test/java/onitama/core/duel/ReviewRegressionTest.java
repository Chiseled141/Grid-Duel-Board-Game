package onitama.core.duel;

import java.util.List;
import java.util.Map;
import onitama.core.PlayerColor;
import onitama.core.Square;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Regression coverage for balance overrides and complete targeting drafts. */
class ReviewRegressionTest {
    private RulesConfig override(String id, int cost, Map<SkillParameter, Integer> parameters) {
        RulesConfig r = DuelTestFixture.defaultRulesConfig();
        var skills = r.skills().stream().map(s -> !s.id().equals(id) ? s : new SkillDefinition(
                s.id(), s.name(), s.category(), cost, s.copies(), parameters,
                s.descriptionTemplate(), s.artworkPath())).toList();
        return new RulesConfig(r.rulesVersion(), r.movementHandSize(), r.skillHandSize(),
                r.initialMana(), r.manaPerTurn(), r.manaCap(), r.turnLimit(), r.movementCards(), skills);
    }

    private GameState state(RulesConfig rules, String skill, PlayerColor actor) {
        for (long seed = 0; seed < 1000; seed++) {
            GameState s = RulesEngine.newGame(rules, seed);
            if (s.mutableSkillHand(actor).stream().noneMatch(c -> c.definitionId().equals(skill))) continue;
            s.setTurn(actor);
            s.setPhase(TurnPhase.SKILL);
            s.setBlueMana(5);
            s.setRedMana(5);
            return s;
        }
        throw new AssertionError("No fixture deal found");
    }

    private String held(GameState s, String id) {
        return s.mutableSkillHand(s.turn()).stream().filter(c -> c.definitionId().equals(id))
                .findFirst().orElseThrow().instanceId();
    }

    private SelectionOptions options(GameState s, String skill, List<Integer> ids, List<Square> squares) {
        return Targeting.skillOptions(RulesEngine.viewFor(s, s.turn()), held(s, skill),
                new SelectionDraft(ids, squares, null));
    }

    private void assertLegal(GameState s, String skill, SelectionOptions options) {
        assertTrue(options.canSubmit());
        assertInstanceOf(ActionResult.Accepted.class, RulesEngine.apply(s, s.turn(),
                new GameAction.CastSkill(held(s, skill), options.completeTargets())));
    }

    @ParameterizedTest @EnumSource(PlayerColor.class)
    void manaSurgePaysBeforeApplyingCap(PlayerColor actor) {
        GameState s = state(override("mana-surge", 1, Map.of(SkillParameter.MANA_GAIN, 2)), "mana-surge", actor);
        var result = assertInstanceOf(ActionResult.Accepted.class, RulesEngine.apply(s, actor,
                new GameAction.CastSkill(held(s, "mana-surge"), new SkillTargets.None())));
        assertEquals(5, actor == PlayerColor.BLUE ? result.nextState().blueMana() : result.nextState().redMana());
        assertEquals(5, s.blueMana());
        assertEquals(5, s.redMana());
        assertEquals(s.spentSkills().size() + 1, result.nextState().spentSkills().size());
    }

    @ParameterizedTest @EnumSource(PlayerColor.class)
    void advanceValidatesFinalAndStaleDrafts(PlayerColor actor) {
        GameState s = state(DuelTestFixture.defaultRulesConfig(), "advance", actor);
        int first = actor == PlayerColor.BLUE ? 0 : 6;
        int y = actor == PlayerColor.BLUE ? 1 : 5;
        var ids = List.of(first, first + 1);
        var squares = List.of(new Square(0, y), new Square(1, y));
        assertLegal(s, "advance", options(s, "advance", ids, squares));
        assertFalse(options(s, "advance", ids, List.of(new Square(0, 3), new Square(1, 3))).canSubmit());
        assertFalse(options(s, "advance", List.of(99, first + 1), squares).canSubmit());
        // The draft was valid when selected, but its first landing is now occupied.
        s.board().move(s.board().findPiece(first + 2), squares.get(0));
        assertFalse(options(s, "advance", ids, squares).canSubmit());
        assertFalse(options(s, "advance", ids, squares.subList(0, 1)).canSubmit());
    }

    @ParameterizedTest @ValueSource(strings = {"sweep", "gust"})
    void configuredSingleTargetDisablesSecondSelection(String skill) {
        GameState s = state(override(skill, 3, Map.of(SkillParameter.MAX_TARGETS, 1)), skill, PlayerColor.BLUE);
        s.board().move(s.board().findPiece(6), new Square(0, 1));
        s.board().move(s.board().findPiece(1), new Square(3, 3));
        s.board().move(s.board().findPiece(7), new Square(1, 0));
        var first = skill.equals("sweep") ? List.of(0, 6) : List.of(6);
        var both = skill.equals("sweep") ? List.of(0, 6, 7) : List.of(6, 7);
        Direction direction = skill.equals("gust") ? Direction.SOUTH : null;
        var view = RulesEngine.viewFor(s, s.turn());
        var single = Targeting.skillOptions(view, held(s, skill), new SelectionDraft(first, List.of(), direction));
        assertLegal(s, skill, single);
        assertTrue(single.selectablePieceIds().isEmpty());
        var excessive = Targeting.skillOptions(view, held(s, skill), new SelectionDraft(both, List.of(), direction));
        assertFalse(excessive.canSubmit());
        assertEquals(ActionError.INVALID_TARGETS, excessive.error());
    }

    @ParameterizedTest @ValueSource(strings = {"rally", "scatter"})
    void groupedDraftsRejectWrongOwnershipAndInvalidDestinations(String skill) {
        GameState s = state(DuelTestFixture.defaultRulesConfig(), skill, PlayerColor.BLUE);
        s.board().move(s.board().findPiece(0), new Square(0, 2));
        s.board().move(s.board().findPiece(1), new Square(1, 3));
        s.board().move(s.board().findPiece(2), new Square(4, 2));
        var ids = skill.equals("rally") ? List.of(2, 0, 1) : List.of(0, 1);
        var valid = skill.equals("rally") ? List.of(new Square(1, 2), new Square(2, 3))
                : List.of(new Square(1, 1), new Square(2, 4));
        assertLegal(s, skill, options(s, skill, ids, valid));
        assertFalse(options(s, skill, ids, List.of(new Square(6, 5), new Square(6, 4))).canSubmit());
        var wrongOwner = skill.equals("rally") ? List.of(2, 0, 6) : List.of(0, 6);
        assertFalse(options(s, skill, wrongOwner, valid).canSubmit());
        assertFalse(options(s, skill, ids, List.of(valid.get(0), valid.get(0))).canSubmit());
    }

    @ParameterizedTest @ValueSource(strings = {"rally", "scatter"})
    void configuredSingleMoverRejectsForgedTwoMoveDraft(String skill) {
        GameState s = state(override(skill, 3, Map.of(SkillParameter.MAX_TARGETS, 1)), skill, PlayerColor.BLUE);
        var ids = skill.equals("rally") ? List.of(2, 0, 1) : List.of(0, 1);
        assertFalse(options(s, skill, ids, List.of(new Square(1, 1), new Square(2, 1))).canSubmit());
    }
}
