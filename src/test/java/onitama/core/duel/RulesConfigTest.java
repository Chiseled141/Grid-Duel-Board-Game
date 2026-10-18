package onitama.core.duel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import onitama.core.Card;
import onitama.core.CardDeck;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RulesConfigTest {

    @Test
    @DisplayName("Default configuration constructs successfully and validates bounds")
    void defaultConfigurationValid() {
        RulesConfig config = DuelTestFixture.defaultRulesConfig();
        assertEquals("grid-duel-v4", config.rulesVersion());
        assertEquals(0, config.movementHandSize());
        assertEquals(3, config.skillHandSize());
        assertEquals(2, config.initialMana());
        assertEquals(1, config.manaPerTurn());
        assertEquals(5, config.manaCap());
        assertEquals(60, config.turnLimit());
        assertEquals(0, config.movementCards().size());
        assertEquals(30, config.skills().size());
    }

    @Test
    @DisplayName("Mutating input collections does not affect RulesConfig")
    void defensiveCopyCollections() {
        List<Card> cards = new ArrayList<>();
        List<SkillDefinition> skills = new ArrayList<>(DuelTestFixture.defaultSkillDefinitions());

        RulesConfig config = new RulesConfig("grid-duel-v4", 0, 3, 2, 1, 5, 60, cards, skills);

        cards.add(CardDeck.cardById("tiger"));
        skills.remove(0);

        assertEquals(0, config.movementCards().size());
        assertEquals(30, config.skills().size());
        assertThrows(UnsupportedOperationException.class, () -> config.movementCards().remove(0));
        assertThrows(UnsupportedOperationException.class, () -> config.skills().remove(0));
    }

    @Test
    @DisplayName("Reject invalid rules version, hand sizes, and mana parameters")
    void rejectInvalidBasicBounds() {
        List<Card> cards = List.of();
        List<SkillDefinition> skills = DuelTestFixture.defaultSkillDefinitions();

        // Unsupported version
        assertThrows(IllegalArgumentException.class,
                () -> new RulesConfig("legacy-v1", 0, 3, 2, 1, 5, 60, cards, skills));

        // Movement hand size must be exactly 0
        assertThrows(IllegalArgumentException.class,
                () -> new RulesConfig("grid-duel-v4", 1, 3, 2, 1, 5, 60, cards, skills));
        assertThrows(IllegalArgumentException.class,
                () -> new RulesConfig("grid-duel-v4", 3, 3, 2, 1, 5, 60, cards, skills));

        // Skill hand size 1..6
        assertThrows(IllegalArgumentException.class,
                () -> new RulesConfig("grid-duel-v4", 0, 0, 2, 1, 5, 60, cards, skills));
        assertThrows(IllegalArgumentException.class,
                () -> new RulesConfig("grid-duel-v4", 0, 7, 2, 1, 5, 60, cards, skills));

        // Mana cap 1..20
        assertThrows(IllegalArgumentException.class,
                () -> new RulesConfig("grid-duel-v4", 0, 3, 2, 1, 0, 60, cards, skills));
        assertThrows(IllegalArgumentException.class,
                () -> new RulesConfig("grid-duel-v4", 0, 3, 2, 1, 21, 60, cards, skills));

        // Initial mana 0..manaCap
        assertThrows(IllegalArgumentException.class,
                () -> new RulesConfig("grid-duel-v4", 0, 3, -1, 1, 5, 60, cards, skills));
        assertThrows(IllegalArgumentException.class,
                () -> new RulesConfig("grid-duel-v4", 0, 3, 6, 1, 5, 60, cards, skills));

        // Income 1..manaCap
        assertThrows(IllegalArgumentException.class,
                () -> new RulesConfig("grid-duel-v4", 0, 3, 2, 0, 5, 60, cards, skills));
        assertThrows(IllegalArgumentException.class,
                () -> new RulesConfig("grid-duel-v4", 0, 3, 2, 6, 5, 60, cards, skills));

        // Turn limit 2..500
        assertThrows(IllegalArgumentException.class,
                () -> new RulesConfig("grid-duel-v4", 0, 3, 2, 1, 5, 1, cards, skills));
        assertThrows(IllegalArgumentException.class,
                () -> new RulesConfig("grid-duel-v4", 0, 3, 2, 1, 5, 501, cards, skills));
    }

    @Test
    @DisplayName("Reject incomplete or duplicate movement card decks")
    void rejectInvalidMovementCards() {
        List<SkillDefinition> skills = DuelTestFixture.defaultSkillDefinitions();

        // 15 cards
        List<Card> incomplete = CardDeck.catalog().subList(0, 15);
        assertThrows(IllegalArgumentException.class,
                () -> new RulesConfig("grid-duel-v4", 0, 3, 2, 1, 5, 60, incomplete, skills));

        // Duplicate card
        List<Card> duplicate = new ArrayList<>(CardDeck.catalog());
        duplicate.set(1, duplicate.get(0));
        assertThrows(IllegalArgumentException.class,
                () -> new RulesConfig("grid-duel-v4", 0, 3, 2, 1, 5, 60, duplicate, skills));
    }

    @Test
    @DisplayName("Movement IDs cannot hide changed, missing, or duplicate offsets")
    void rejectChangedMovementPatterns() {
        Card tiger = CardDeck.cardById("tiger");
        for (var offsets : List.of(
                List.of(new onitama.core.Offset(0, 6)),
                List.of(new onitama.core.Offset(0, 2)),
                List.of(new onitama.core.Offset(0, 2), new onitama.core.Offset(0, 2)))) {
            List<Card> cards = new ArrayList<>(CardDeck.catalog());
            cards.set(0, new Card(tiger.id(), tiger.name(), tiger.stamp(), offsets));
            assertThrows(IllegalArgumentException.class, () -> new RulesConfig(
                    "grid-duel-v4", 0, 3, 2, 1, 5, 60, cards, DuelTestFixture.defaultSkillDefinitions()));
        }
        // Offset ordering is presentation only; the same pattern remains valid.
        List<Card> reordered = new ArrayList<>(CardDeck.catalog());
        reordered.set(0, new Card(tiger.id(), tiger.name(), tiger.stamp(),
                List.of(tiger.offsets().get(1), tiger.offsets().get(0))));
        assertThrows(IllegalArgumentException.class, () -> new RulesConfig("grid-duel-v4", 0, 3, 2, 1, 5, 60,
                reordered, DuelTestFixture.defaultSkillDefinitions()));
    }

    @Test
    @DisplayName("Reject insufficient skill copies to satisfy starting deal")
    void rejectInsufficientSkillCopies() {
        List<SkillDefinition> skills = new ArrayList<>();
        for (SkillDefinition skill : DuelTestFixture.defaultSkillDefinitions()) {
            // Set copies to 0 for almost everything
            skills.add(new SkillDefinition(
                    skill.id(), skill.name(), skill.category(), skill.manaCost(),
                    skill.id().equals("sidestep") ? 1 : 0,
                    skill.parameters(), skill.descriptionTemplate(), skill.artworkPath()));
        }
        // Total enabled copies = 1, but 2 * skillHandSize(3) = 6 -> must fail
        assertThrows(IllegalArgumentException.class,
                () -> new RulesConfig("grid-duel-v4", 0, 3, 2, 1, 5, 60, List.of(), skills));
    }

    @Test
    @DisplayName("Reject invalid skill parameters, costs, and copy bounds")
    void rejectInvalidSkillParameters() {
        // Cost exceeding manaCap (5)
        List<SkillDefinition> invalidCost = new ArrayList<>(DuelTestFixture.defaultSkillDefinitions());
        invalidCost.set(0, new SkillDefinition(
                "sidestep", "SIDESTEP", SkillCategory.MOVEMENT, 6, 1,
                Map.of(SkillParameter.DISTANCE, 1), "desc", "art"));
        assertThrows(IllegalArgumentException.class,
                () -> new RulesConfig("grid-duel-v4", 0, 3, 2, 1, 5, 60, List.of(), invalidCost));

        // Copies out of range 0..4
        List<SkillDefinition> invalidCopies = new ArrayList<>(DuelTestFixture.defaultSkillDefinitions());
        invalidCopies.set(0, new SkillDefinition(
                "sidestep", "SIDESTEP", SkillCategory.MOVEMENT, 1, 5,
                Map.of(SkillParameter.DISTANCE, 1), "desc", "art"));
        assertThrows(IllegalArgumentException.class,
                () -> new RulesConfig("grid-duel-v4", 0, 3, 2, 1, 5, 60, List.of(), invalidCopies));

        // Distance parameter out of range (1..6)
        List<SkillDefinition> invalidDistance = new ArrayList<>(DuelTestFixture.defaultSkillDefinitions());
        invalidDistance.set(0, new SkillDefinition(
                "sidestep", "SIDESTEP", SkillCategory.MOVEMENT, 1, 1,
                Map.of(SkillParameter.DISTANCE, 7), "desc", "art"));
        assertThrows(IllegalArgumentException.class,
                () -> new RulesConfig("grid-duel-v4", 0, 3, 2, 1, 5, 60, List.of(), invalidDistance));

        // Unexpected parameter on skill that has none
        List<SkillDefinition> extraParam = new ArrayList<>(DuelTestFixture.defaultSkillDefinitions());
        extraParam.set(4, new SkillDefinition(
                "advance", "ADVANCE", SkillCategory.MOVEMENT, 3, 1,
                Map.of(SkillParameter.DISTANCE, 1), "desc", "art"));
        assertThrows(IllegalArgumentException.class,
                () -> new RulesConfig("grid-duel-v4", 0, 3, 2, 1, 5, 60, List.of(), extraParam));
    }
}
