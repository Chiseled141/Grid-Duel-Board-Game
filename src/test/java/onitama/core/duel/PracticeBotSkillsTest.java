package onitama.core.duel;

import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import onitama.config.RulesConfigLoader;
import onitama.core.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real engine acceptance for all 24 skills, both player orientations and bot levels. */
class PracticeBotSkillsTest {
    static Stream<Arguments> skills() {
        return RulesConfigLoader.load(null).skills().stream().flatMap(skill ->
                Stream.of(PlayerColor.values()).flatMap(actor -> Stream.of(Difficulty.values())
                        .map(level -> Arguments.of(skill.id(), actor, level))));
    }

    private GameState state(String skill, PlayerColor actor) {
        var game = RulesEngine.newGame(RulesConfigLoader.load(null), 42);
        for (var placed : List.copyOf(game.board().placedPieces())) game.board().remove(placed.square());
        int[][] allies = {{2,2},{2,3},{3,2},{1,2},{0,0},{5,0}};
        int[][] enemies = {{3,3},{4,2},{2,4},{3,4},{5,5},{6,6}};
        // Put an enemy between two allies for Crossfire and Surround fixtures.
        if (skill.equals("crossfire") || skill.equals("surround")) allies[3] = new int[]{4,3};
        if (skill.equals("pierce") || skill.equals("pull")) allies[2] = new int[]{3,1};
        if (skill.equals("sweep")) {
            allies[2] = new int[]{3,1};
            enemies[0] = new int[]{2,1}; enemies[1] = new int[]{3,2};
        }
        for (int i = 0; i < 6; i++) {
            int base = actor == PlayerColor.BLUE ? 0 : 6;
            game.board().set(new Square(allies[i][0], allies[i][1]), new Piece(base + i, actor));
            game.board().set(new Square(enemies[i][0], enemies[i][1]), new Piece(6 - base + i, actor.opponent()));
        }
        game.mutableSkillHand(actor).clear();
        game.mutableSkillHand(actor).add(new CardInstance(skill + "#1", skill));
        int mana = skill.equals("mana-surge") ? 3 : 5;
        game.setBlueMana(mana); game.setRedMana(mana);
        game.setTurn(actor); game.setPhase(TurnPhase.SKILL);
        return game;
    }

    @ParameterizedTest @MethodSource("skills")
    void castsEverySkillWithTargetsTheEngineAccepts(String skill, PlayerColor actor, Difficulty level) {
        var game = state(skill, actor);
        var view = RulesEngine.viewFor(game, actor);
        var action = PracticeBot.chooseAction(view, level, new Random(73));
        assertInstanceOf(GameAction.CastSkill.class, action, skill + " must be usable by " + level + " " + actor);
        if (level != Difficulty.ROOKIE && (skill.equals("sweep") || skill.equals("chain-strike"))) {
            var targets = assertInstanceOf(SkillTargets.Attack.class, ((GameAction.CastSkill) action).targets());
            assertEquals(2, targets.enemyIds().size(), "stronger bots complete optional extra eliminations");
        }
        assertEquals(action, PracticeBot.chooseAction(view, level, new Random(73)), "seeded policy remains reproducible");
        var accepted = assertInstanceOf(ActionResult.Accepted.class, RulesEngine.apply(game, actor, action));
        assertTrue(accepted.events().stream().anyMatch(e -> e instanceof PublicEvent.SkillCommitted));
        assertEquals(view, RulesEngine.viewFor(game, actor), "policy and immutable transition must not alter the input");
        if (skill.equals("foresight")) {
            var next = accepted.nextState();
            var choice = PracticeBot.chooseAction(RulesEngine.viewFor(next, actor), level, new Random(3));
            assertInstanceOf(GameAction.ResolveForesight.class, choice);
            assertInstanceOf(ActionResult.Accepted.class, RulesEngine.apply(next, actor, choice));
        }
    }

    @Test void strongerBotPrioritizesAnEliminationOverAResourceCard() {
        var game = state("strike", PlayerColor.BLUE);
        game.mutableSkillHand(PlayerColor.BLUE).add(0, new CardInstance("refocus#1", "refocus"));
        for (var level : List.of(Difficulty.SENIOR, Difficulty.LEGEND)) {
            var action = assertInstanceOf(GameAction.CastSkill.class,
                    PracticeBot.chooseAction(RulesEngine.viewFor(game, PlayerColor.BLUE), level, new Random(8)));
            assertEquals("strike#1", action.skillInstanceId());
            assertInstanceOf(ActionResult.Accepted.class, RulesEngine.apply(game, PlayerColor.BLUE, action));
        }
    }

    @Test void savesUnaffordableSkillsAndAvoidsWastingManaSurgeAtCap() {
        var game = state("blink", PlayerColor.BLUE); game.setBlueMana(0);
        assertInstanceOf(GameAction.EndTurn.class, PracticeBot.chooseAction(RulesEngine.viewFor(game, PlayerColor.BLUE), new Random(1)));
        game = state("mana-surge", PlayerColor.RED); game.setRedMana(game.rules().manaCap());
        assertInstanceOf(GameAction.EndTurn.class, PracticeBot.chooseAction(RulesEngine.viewFor(game, PlayerColor.RED), new Random(1)));
    }
}
