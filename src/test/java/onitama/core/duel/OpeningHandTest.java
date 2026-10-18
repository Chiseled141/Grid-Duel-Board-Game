package onitama.core.duel;

import onitama.core.PlayerColor;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OpeningHandTest {
    private GameState start() { return RulesEngine.newOpeningGame(onitama.config.RulesConfigLoader.load(null), 42); }
    private GameState confirm(GameState state, PlayerColor color, List<String> ids) {
        return assertInstanceOf(ActionResult.Accepted.class,
                RulesEngine.apply(state, color, new GameAction.ConfirmOpening(ids))).nextState();
    }
    @Test void bothPlayersConfirmOnceBeforeFirstTurnAndMana() {
        GameState state = start();
        assertEquals(TurnPhase.OPENING, state.phase()); assertEquals(0, state.startedTurns());
        assertEquals(2, state.blueMana()); assertEquals(2, state.redMana());
        assertInstanceOf(ActionResult.Rejected.class, RulesEngine.apply(state, state.turn(), new GameAction.EndTurn()));
        PlayerColor first = state.turn().opponent();
        GameState once = confirm(state, first, List.of());
        assertEquals(TurnPhase.OPENING, once.phase()); assertEquals(1, once.revision());
        assertTrue(RulesEngine.viewFor(once, first).openingConfirmed());
        assertTrue(RulesEngine.viewFor(once, first.opponent()).opponentOpeningConfirmed());
        assertInstanceOf(ActionResult.Rejected.class, RulesEngine.apply(once, first, new GameAction.ConfirmOpening(List.of())));
        GameState ready = confirm(once, first.opponent(), List.of());
        assertEquals(TurnPhase.MOVEMENT, ready.phase()); assertEquals(1, ready.startedTurns());
        assertEquals(3, ready.turn() == PlayerColor.BLUE ? ready.blueMana() : ready.redMana());
        assertEquals(2, ready.turn() == PlayerColor.BLUE ? ready.redMana() : ready.blueMana());
        assertEquals(state.blueSkillHand(), ready.blueSkillHand());
        assertEquals(state.redSkillHand(), ready.redSkillHand());
    }
    @Test void replacedSlotsAreDifferentAndAllCardsAreConservedWithoutSpending() {
        GameState state = start();
        var original = state.blueSkillHand();
        List<String> replace = List.of(original.get(0).instanceId(), original.get(2).instanceId());
        GameState next = confirm(state, PlayerColor.BLUE, replace);
        assertEquals(original.get(1), next.blueSkillHand().get(1));
        assertFalse(next.blueSkillHand().stream().anyMatch(c -> replace.contains(c.instanceId())));
        assertEquals(state.redSkillHand(), next.redSkillHand());
        assertEquals(state.skillDeck().size(), next.skillDeck().size());
        assertTrue(next.spentSkills().isEmpty());
        Set<CardInstance> all = new HashSet<>(next.skillDeck());
        all.addAll(next.blueSkillHand()); all.addAll(next.redSkillHand()); assertEquals(30, all.size());
        assertEquals(original, state.blueSkillHand(), "input must remain unchanged");
        assertEquals(next.skillDeck(), confirm(start(), PlayerColor.BLUE, replace).skillDeck());
    }
    @Test void invalidChoicesPreserveHandRevisionAndRandomState() {
        GameState state = start(); long random = state.randomState().copy().nextLong();
        String id = state.blueSkillHand().get(0).instanceId();
        for (List<String> ids : List.of(List.of(id, id), List.of(state.redSkillHand().get(0).instanceId()), List.of("bad"))) {
            assertInstanceOf(ActionResult.Rejected.class, RulesEngine.apply(state, PlayerColor.BLUE, new GameAction.ConfirmOpening(ids)));
            assertEquals(0, state.revision()); assertEquals(random, state.randomState().copy().nextLong());
        }
        assertThrows(IllegalArgumentException.class, () -> new GameAction.ConfirmOpening(List.of(id,id,id,id,id,id,id)));
    }
    @Test void allThreeCanBeReplacedAndChoiceSurvivesProjection() {
        GameState state = start();
        var ids = state.redSkillHand().stream().map(CardInstance::instanceId).toList();
        var next = confirm(state, PlayerColor.RED, ids);
        var own = RulesEngine.viewFor(next, PlayerColor.RED);
        var other = RulesEngine.viewFor(next, PlayerColor.BLUE);
        assertTrue(own.openingConfirmed()); assertFalse(own.opponentOpeningConfirmed());
        assertFalse(other.openingConfirmed()); assertTrue(other.opponentOpeningConfirmed());
        assertEquals(next.blueSkillHand(), other.ownSkillHand());
        assertTrue(next.redSkillHand().stream().noneMatch(c -> ids.contains(c.instanceId())));
    }
}
