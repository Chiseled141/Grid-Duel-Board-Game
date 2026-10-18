package deckduel.core.duel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import deckduel.config.RulesConfigLoader;
import deckduel.core.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

/** Layout constraints, variation and rewards from both randomized scoring locations. */
class ObjectiveLayoutTest {
    @Test void generatedLayoutsAreMirroredDistinctCentralAndVaryAcrossMatches() {
        var layouts = new HashSet<ObjectiveLayout>();
        var rules = RulesConfigLoader.load(null);
        for (int seed = 0; seed < 256; seed++) {
            var state = RulesEngine.newGame(rules, seed);
            var layout = state.objectives(); layouts.add(layout);
            assertEquals(2, layout.scoring().size()); assertEquals(2, layout.mana().size());
            var all = new HashSet<>(layout.scoring()); all.addAll(layout.mana());
            assertEquals(4, all.size());
            for (var square : all) {
                assertTrue(Board.isInside(square));
                assertTrue(all.contains(new Square(6 - square.x(), 6 - square.y())));
            }
            assertTrue(layout.scoring().stream().allMatch(s -> s.y() == 3));
            assertEquals(List.of(2, 4), layout.mana().stream().map(Square::y).sorted().toList());
            assertEquals(layout, RulesEngine.newGame(rules, seed).objectives());
            assertEquals(layout, RulesEngine.viewFor(state, PlayerColor.BLUE).objectives());
            assertEquals(layout, RulesEngine.viewFor(state, PlayerColor.RED).objectives());
        }
        assertTrue(layouts.size() > 10, "seeds must produce varied objective layouts");
    }

    @Test void layoutCannotBeMutatedAndRejectsInvalidPlacements() {
        var scoring = new ArrayList<>(List.of(new Square(0, 3), new Square(6, 3)));
        var mana = List.of(new Square(2, 2), new Square(4, 4));
        var layout = new ObjectiveLayout(scoring, mana);
        scoring.clear(); assertEquals(2, layout.scoring().size());
        assertThrows(UnsupportedOperationException.class, () -> layout.mana().clear());
        assertThrows(IllegalArgumentException.class, () -> new ObjectiveLayout(List.of(new Square(3, 3), new Square(3, 3)), mana));
        assertThrows(IllegalArgumentException.class, () -> new ObjectiveLayout(List.of(new Square(0, 3), new Square(5, 3)), mana));
        assertThrows(IllegalArgumentException.class, () -> new ObjectiveLayout(List.of(new Square(0, 2), new Square(6, 4)), mana));
        assertThrows(IllegalArgumentException.class, () -> new ObjectiveLayout(layout.scoring(), List.of(new Square(0, 3), new Square(6, 3))));
        assertThrows(IllegalArgumentException.class, () -> new ObjectiveLayout(layout.scoring(), List.of(new Square(0, 2), new Square(6, 2))));
    }

    @ParameterizedTest @EnumSource(PlayerColor.class)
    void bothScoreSquaresPayAndBothManaSquaresUseTheirActualPositions(PlayerColor color) {
        var state = RulesEngine.newGame(RulesConfigLoader.load(null), 73);
        int first = color == PlayerColor.BLUE ? 0 : 6;
        for (int i = 0; i < 2; i++) {
            state.board().move(state.board().findPiece(first + i), state.objectives().scoring().get(i));
            state.board().move(state.board().findPiece(first + i + 2), state.objectives().mana().get(i));
        }
        state.setTurn(color.opponent()); state.setBlueMana(0); state.setRedMana(0);
        var next = ((ActionResult.Accepted) RulesEngine.apply(state, color.opponent(), new GameAction.EndTurn())).nextState();
        assertEquals(2, next.score(color));
        assertEquals(3, color == PlayerColor.BLUE ? next.blueMana() : next.redMana());
        assertEquals(0, next.score(color.opponent()));
        assertEquals(state.objectives(), next.objectives());
        assertEquals(0, state.score(color));
    }

    @Test void rejectionAndCopiesNeverRerollLayoutOrAdvanceTheOriginalRandomState() {
        var state = RulesEngine.newGame(RulesConfigLoader.load(null), 9);
        long randomBefore = state.randomState().state();
        var layout = state.objectives();
        assertInstanceOf(ActionResult.Rejected.class, RulesEngine.apply(state, state.turn().opponent(), new GameAction.EndTurn()));
        assertEquals(randomBefore, state.randomState().state());
        var next = ((ActionResult.Accepted) RulesEngine.apply(state, state.turn(), new GameAction.EndTurn())).nextState();
        assertEquals(layout, next.objectives()); assertEquals(layout, state.objectives());
        assertEquals(randomBefore, state.randomState().state());
    }

    @Test void botUsesTheRandomScoringLocationRatherThanTheOldCenter() {
        for (int seed = 0; seed < 30; seed++) {
            var state = RulesEngine.newGame(RulesConfigLoader.load(null), seed);
            state.setTurn(PlayerColor.BLUE);
            var target = state.objectives().scoring().get(0);
            var origin = new deckduel.core.Square(target.x(), 2);
            state.board().move(new deckduel.core.Square(0, 0), origin);
            var action = PracticeBot.chooseAction(RulesEngine.viewFor(state, PlayerColor.BLUE), Difficulty.SENIOR, new Random(1));
            var move = assertInstanceOf(GameAction.MovePiece.class, action);
            assertTrue(state.objectives().scoring().contains(move.to()));
            assertInstanceOf(ActionResult.Accepted.class, RulesEngine.apply(state, PlayerColor.BLUE, action));
        }
    }
}
