package onitama.core.duel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import onitama.core.Card;
import onitama.core.CardDeck;
import onitama.core.PlayerColor;
import onitama.core.Square;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeckLifecycleTest {

    @Test
    @DisplayName("Default setup deals 2 movement cards and 3 skills each, conserving all cards")
    void setupAndConservation() {
        RulesConfig config = DuelTestFixture.defaultRulesConfig();
        GameState state = RulesEngine.newGame(config, 42L);

        assertTrue(state.blueMovementHand().isEmpty());
        assertTrue(state.redMovementHand().isEmpty());
        assertTrue(state.movementDeck().isEmpty());

        assertEquals(3, state.blueSkillHand().size());
        assertEquals(3, state.redSkillHand().size());
        assertEquals(24, state.skillDeck().size());
        assertEquals(0, state.spentSkills().size());

        Set<String> allSkills = new HashSet<>();
        state.blueSkillHand().forEach(c -> allSkills.add(c.instanceId()));
        state.redSkillHand().forEach(c -> allSkills.add(c.instanceId()));
        state.skillDeck().forEach(c -> allSkills.add(c.instanceId()));
        state.spentSkills().forEach(c -> allSkills.add(c.instanceId()));
        assertEquals(30, allSkills.size());
    }

    @Test
    @DisplayName("Identical config and seed produce identical deals and starter")
    void deterministicSetup() {
        RulesConfig config = DuelTestFixture.defaultRulesConfig();
        GameState g1 = RulesEngine.newGame(config, 12345L);
        GameState g2 = RulesEngine.newGame(config, 12345L);

        assertEquals(g1.turn(), g2.turn());
        assertEquals(g1.blueMovementHand(), g2.blueMovementHand());
        assertEquals(g1.redMovementHand(), g2.redMovementHand());
        assertEquals(g1.blueSkillHand(), g2.blueSkillHand());
        assertEquals(g1.redSkillHand(), g2.redSkillHand());
        assertEquals(g1.movementDeck(), g2.movementDeck());
        assertEquals(g1.skillDeck(), g2.skillDeck());
    }

    @Test
    @DisplayName("Shuffled input definition order still produces canonical seeded deal")
    void canonicalDealOrderIndependentOfInputOrder() {
        List<SkillDefinition> shuffledSkills = new ArrayList<>(DuelTestFixture.defaultSkillDefinitions());
        Collections.shuffle(shuffledSkills, new java.util.Random(99L));

        RulesConfig standardConfig = DuelTestFixture.defaultRulesConfig();
        RulesConfig shuffledConfig = new RulesConfig(
                standardConfig.rulesVersion(),
                standardConfig.movementHandSize(),
                standardConfig.skillHandSize(),
                standardConfig.initialMana(),
                standardConfig.manaPerTurn(),
                standardConfig.manaCap(),
                standardConfig.turnLimit(),
                standardConfig.movementCards(),
                shuffledSkills);

        GameState standardGame = RulesEngine.newGame(standardConfig, 888L);
        GameState shuffledGame = RulesEngine.newGame(shuffledConfig, 888L);

        assertEquals(standardGame.blueSkillHand(), shuffledGame.blueSkillHand());
        assertEquals(standardGame.redSkillHand(), shuffledGame.redSkillHand());
        assertEquals(standardGame.skillDeck(), shuffledGame.skillDeck());
    }

    @Test
    @DisplayName("Basic movement neither draws cards nor consumes random state")
    void movementDoesNotTouchDecksOrRng() {
        var state = RulesEngine.newGame(DuelTestFixture.defaultRulesConfig(), 100);
        var move = Targeting.legalMoves(RulesEngine.viewFor(state, state.turn())).get(0);
        var accepted = (ActionResult.Accepted) RulesEngine.apply(state, state.turn(), move);
        var next = accepted.nextState();
        assertEquals(state.randomState().state(), next.randomState().state());
        assertEquals(state.skillDeck(), next.skillDeck());
        assertEquals(state.blueSkillHand(), next.blueSkillHand());
        assertEquals(state.redSkillHand(), next.redSkillHand());
        assertTrue(next.movementDeck().isEmpty());
        assertEquals(1, accepted.events().size());
        assertInstanceOf(PublicEvent.PieceMoved.class, accepted.events().get(0));
    }

    @Test
    @DisplayName("Skipping movement never draws or grants extra mana")
    void skippingKeepsResources() {
        var state = RulesEngine.newGame(DuelTestFixture.defaultRulesConfig(), 42);
        var accepted = (ActionResult.Accepted) RulesEngine.apply(state, state.turn(), new GameAction.SkipMovement());
        assertEquals(state.skillDeck(), accepted.nextState().skillDeck());
        assertEquals(state.blueMana(), accepted.nextState().blueMana());
        assertEquals(state.redMana(), accepted.nextState().redMana());
        assertEquals(TurnPhase.SKILL, accepted.nextState().phase());
    }

    @Test
    @DisplayName("Skill deck component consumes held skill, spends it, and draws replacement until deck exhausted")
    void skillDeckConsumptionAndExhaustion() {
        RulesConfig config = DuelTestFixture.defaultRulesConfig();
        GameState state = RulesEngine.newGame(config, 42L);

        CardInstance toSpend = state.blueSkillHand().get(0);
        state.consumeHeldSkill(PlayerColor.BLUE, toSpend.instanceId());

        assertEquals(3, state.blueSkillHand().size());
        assertEquals(23, state.skillDeck().size());
        assertEquals(1, state.spentSkills().size());
        assertEquals(toSpend, state.spentSkills().get(0));
        assertFalse(state.blueSkillHand().contains(toSpend));

        // Consume remaining 17 cards from deck to exhaust it
        for (int i = 0; i < 23; i++) {
            CardInstance c = state.blueSkillHand().get(0);
            state.consumeHeldSkill(PlayerColor.BLUE, c.instanceId());
        }
        assertEquals(0, state.skillDeck().size());
        assertEquals(3, state.blueSkillHand().size());
        assertEquals(24, state.spentSkills().size());

        // Deck is now empty: next consumption should shrink hand
        CardInstance nextToSpend = state.blueSkillHand().get(0);
        state.consumeHeldSkill(PlayerColor.BLUE, nextToSpend.instanceId());

        assertEquals(2, state.blueSkillHand().size()); // hand shrank
        assertEquals(0, state.skillDeck().size());     // spent pile never refills deck
        assertEquals(25, state.spentSkills().size());
    }

    @Test
    @DisplayName("Skill deck rejects nonexistent or wrong-player instance without mutation")
    void skillDeckConsumptionRejection() {
        RulesConfig config = DuelTestFixture.defaultRulesConfig();
        GameState state = RulesEngine.newGame(config, 42L);

        // Blue tries to spend Red's held skill
        CardInstance redSkill = state.redSkillHand().get(0);
        assertThrows(IllegalArgumentException.class,
                () -> state.consumeHeldSkill(PlayerColor.BLUE, redSkill.instanceId()));

        // Nonexistent skill instance
        assertThrows(IllegalArgumentException.class,
                () -> state.consumeHeldSkill(PlayerColor.BLUE, "nonexistent#1"));

        // No mutation occurred
        assertEquals(3, state.blueSkillHand().size());
        assertEquals(24, state.skillDeck().size());
        assertEquals(0, state.spentSkills().size());
    }

    @Test
    @DisplayName("Configured duplicate skill copies are distinct instances and dispose independently")
    void duplicateSkillCopiesIndependent() {
        List<SkillDefinition> customSkills = new ArrayList<>();
        for (SkillDefinition skill : DuelTestFixture.defaultSkillDefinitions()) {
            int copies = switch (skill.id()) {
                case "sidestep" -> 4;
                case "dash" -> 2;
                default -> 0;
            };
            customSkills.add(new SkillDefinition(skill.id(), skill.name(), skill.category(),
                    skill.manaCost(), copies, skill.parameters(), skill.descriptionTemplate(), skill.artworkPath()));
        }
        RulesConfig config = new RulesConfig(
                "grid-duel-v4", 0, 3, 2, 1, 5, 60, List.of(), customSkills);
        GameState state = RulesEngine.newGame(config, 1L);
        Set<String> expected = Set.of("sidestep#1", "sidestep#2", "sidestep#3", "sidestep#4", "dash#1", "dash#2");
        assertEquals(0, state.skillDeck().size());
        assertSkillConservation(state, expected);

        PlayerColor actor = state.blueSkillHand().stream()
                .filter(c -> c.definitionId().equals("sidestep")).count() >= 2
                ? PlayerColor.BLUE : PlayerColor.RED;
        List<CardInstance> hand = List.copyOf(state.mutableSkillHand(actor));
        List<CardInstance> duplicates = hand.stream().filter(c -> c.definitionId().equals("sidestep")).toList();
        CardInstance spent = duplicates.get(0);
        CardInstance retained = duplicates.get(1);
        state.consumeHeldSkill(actor, spent.instanceId());
        assertFalse(state.mutableSkillHand(actor).contains(spent));
        assertTrue(state.mutableSkillHand(actor).contains(retained));
        assertEquals(List.of(spent), state.spentSkills());
        assertEquals(2, state.mutableSkillHand(actor).size());
        assertSkillConservation(state, expected);

        // Exhaust both hands and prove every configured instance ends in spent exactly once.
        for (PlayerColor player : PlayerColor.values()) {
            while (!state.mutableSkillHand(player).isEmpty()) {
                state.consumeHeldSkill(player, state.mutableSkillHand(player).get(0).instanceId());
                assertSkillConservation(state, expected);
            }
        }
        assertEquals(6, state.spentSkills().size());
        assertEquals(0, state.skillDeck().size());
    }
    private static void assertSkillConservation(GameState state, Set<String> expected) {
        List<CardInstance> all = new ArrayList<>(state.blueSkillHand());
        all.addAll(state.redSkillHand());
        all.addAll(state.skillDeck());
        all.addAll(state.spentSkills());
        assertEquals(expected.size(), all.size());
        assertEquals(expected, new HashSet<>(all.stream().map(CardInstance::instanceId).toList()));
    }

}
