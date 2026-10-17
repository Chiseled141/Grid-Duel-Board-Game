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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeckLifecycleTest {

    @Test
    @DisplayName("Default setup deals 2 movement cards and 3 skills each, conserving all cards")
    void setupAndConservation() {
        RulesConfig config = DuelTestFixture.defaultRulesConfig();
        GameState state = RulesEngine.newGame(config, 42L);

        assertEquals(2, state.blueMovementHand().size());
        assertEquals(2, state.redMovementHand().size());
        assertEquals(12, state.movementDeck().size());

        Set<String> allMovement = new HashSet<>();
        allMovement.addAll(state.blueMovementHand());
        allMovement.addAll(state.redMovementHand());
        allMovement.addAll(state.movementDeck());
        assertEquals(16, allMovement.size());
        assertEquals(RulesConfig.EXPECTED_MOVEMENT_IDS, allMovement);

        assertEquals(3, state.blueSkillHand().size());
        assertEquals(3, state.redSkillHand().size());
        assertEquals(18, state.skillDeck().size());
        assertEquals(0, state.spentSkills().size());

        Set<String> allSkills = new HashSet<>();
        state.blueSkillHand().forEach(c -> allSkills.add(c.instanceId()));
        state.redSkillHand().forEach(c -> allSkills.add(c.instanceId()));
        state.skillDeck().forEach(c -> allSkills.add(c.instanceId()));
        state.spentSkills().forEach(c -> allSkills.add(c.instanceId()));
        assertEquals(24, allSkills.size());
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
    @DisplayName("Movement card recycling preserves two-card hand size and all 16 cards")
    void movementCardRecycling() {
        RulesConfig config = DuelTestFixture.defaultRulesConfig();
        GameState state = RulesEngine.newGame(config, 100L);
        PlayerColor actor = state.turn();

        PlayerView view = RulesEngine.viewFor(state, actor);
        List<GameAction.MovePiece> legalMoves = Targeting.legalMoves(view);
        assertFalse(legalMoves.isEmpty());

        GameAction.MovePiece move = legalMoves.get(0);
        ActionResult result = RulesEngine.apply(state, actor, move);
        assertTrue(result instanceof ActionResult.Accepted);
        GameState next = ((ActionResult.Accepted) result).nextState();

        List<String> nextHand = actor == PlayerColor.BLUE ? next.blueMovementHand() : next.redMovementHand();
        assertEquals(2, nextHand.size());
        assertEquals(12, next.movementDeck().size());

        // Conservation check
        Set<String> allCards = new HashSet<>();
        allCards.addAll(next.blueMovementHand());
        allCards.addAll(next.redMovementHand());
        allCards.addAll(next.movementDeck());
        assertEquals(16, allCards.size());
    }

    @Test
    @DisplayName("A played movement card can be redrawn immediately when shuffled to index 0")
    void immediateRedrawPossible() {
        RulesConfig config = DuelTestFixture.defaultRulesConfig();

        // Search for a seed where the played card is redrawn immediately
        boolean redrawn = false;
        for (long seed = 0; seed < 200; seed++) {
            GameState state = RulesEngine.newGame(config, seed);
            PlayerColor actor = state.turn();
            PlayerView view = RulesEngine.viewFor(state, actor);
            List<GameAction.MovePiece> legalMoves = Targeting.legalMoves(view);
            if (legalMoves.isEmpty()) continue;

            GameAction.MovePiece move = legalMoves.get(0);
            ActionResult res = RulesEngine.apply(state, actor, move);
            if (res instanceof ActionResult.Accepted accepted) {
                GameState next = accepted.nextState();
                List<String> nextHand = actor == PlayerColor.BLUE ? next.blueMovementHand() : next.redMovementHand();
                if (nextHand.contains(move.movementCardId())) {
                    redrawn = true;
                    // Proves return -> shuffle -> draw immediately returned the same card
                    assertTrue(nextHand.contains(move.movementCardId()));
                    break;
                }
            }
        }
        assertTrue(redrawn, "Expected at least one seed among 200 to redraw the played card immediately");
    }

    @Test
    @DisplayName("Skill deck component consumes held skill, spends it, and draws replacement until deck exhausted")
    void skillDeckConsumptionAndExhaustion() {
        RulesConfig config = DuelTestFixture.defaultRulesConfig();
        GameState state = RulesEngine.newGame(config, 42L);

        CardInstance toSpend = state.blueSkillHand().get(0);
        state.consumeHeldSkill(PlayerColor.BLUE, toSpend.instanceId());

        assertEquals(3, state.blueSkillHand().size());
        assertEquals(17, state.skillDeck().size());
        assertEquals(1, state.spentSkills().size());
        assertEquals(toSpend, state.spentSkills().get(0));
        assertFalse(state.blueSkillHand().contains(toSpend));

        // Consume remaining 17 cards from deck to exhaust it
        for (int i = 0; i < 17; i++) {
            CardInstance c = state.blueSkillHand().get(0);
            state.consumeHeldSkill(PlayerColor.BLUE, c.instanceId());
        }
        assertEquals(0, state.skillDeck().size());
        assertEquals(3, state.blueSkillHand().size());
        assertEquals(18, state.spentSkills().size());

        // Deck is now empty: next consumption should shrink hand
        CardInstance nextToSpend = state.blueSkillHand().get(0);
        state.consumeHeldSkill(PlayerColor.BLUE, nextToSpend.instanceId());

        assertEquals(2, state.blueSkillHand().size()); // hand shrank
        assertEquals(0, state.skillDeck().size());     // spent pile never refills deck
        assertEquals(19, state.spentSkills().size());
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
        assertEquals(18, state.skillDeck().size());
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
                "grid-duel-v3", 2, 3, 2, 1, 5, 60, CardDeck.catalog(), customSkills);
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
