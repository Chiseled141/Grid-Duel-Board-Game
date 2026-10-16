package onitama.core.duel;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import onitama.core.Card;
import onitama.core.CardDeck;

/**
 * Immutable per-match rules and balance configuration.
 */
public record RulesConfig(
        String rulesVersion,
        int movementHandSize,
        int skillHandSize,
        int initialMana,
        int manaPerTurn,
        int manaCap,
        int turnLimit,
        List<Card> movementCards,
        List<SkillDefinition> skills) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String EXPECTED_RULES_VERSION = "grid-duel-v1";

    public static final Set<String> EXPECTED_MOVEMENT_IDS = Set.of(
            "tiger", "dragon", "frog", "rabbit", "crab", "elephant", "goose", "rooster",
            "monkey", "mantis", "horse", "ox", "crane", "boar", "eel", "cobra");

    public static final List<String> CANONICAL_SKILL_IDS = List.of(
            "sidestep", "dash", "blink", "exchange", "advance", "recall",
            "strike", "pierce", "surround", "sweep", "shove", "pull",
            "vault", "flank", "transposition", "gust", "rally", "scatter",
            "crossfire", "chain-strike", "mana-surge", "refocus", "foresight", "banish");

    public RulesConfig {
        Objects.requireNonNull(rulesVersion, "rulesVersion must not be null");
        Objects.requireNonNull(movementCards, "movementCards must not be null");
        Objects.requireNonNull(skills, "skills must not be null");
        movementCards = List.copyOf(movementCards);
        skills = List.copyOf(skills);
        validate(rulesVersion, movementHandSize, skillHandSize, initialMana,
                manaPerTurn, manaCap, turnLimit, movementCards, skills);
    }

    private static void validate(
            String rulesVersion,
            int movementHandSize,
            int skillHandSize,
            int initialMana,
            int manaPerTurn,
            int manaCap,
            int turnLimit,
            List<Card> movementCards,
            List<SkillDefinition> skills) {

        if (!EXPECTED_RULES_VERSION.equals(rulesVersion)) {
            throw new IllegalArgumentException("unsupported rules version: " + rulesVersion);
        }
        if (movementHandSize != 2) {
            throw new IllegalArgumentException("movementHandSize must be exactly 2: " + movementHandSize);
        }
        if (manaCap < 1 || manaCap > 20) {
            throw new IllegalArgumentException("manaCap must be 1..20: " + manaCap);
        }
        if (initialMana < 0 || initialMana > manaCap) {
            throw new IllegalArgumentException("initialMana must be 0..manaCap (" + manaCap + "): " + initialMana);
        }
        if (manaPerTurn < 1 || manaPerTurn > manaCap) {
            throw new IllegalArgumentException("manaPerTurn must be 1..manaCap (" + manaCap + "): " + manaPerTurn);
        }
        if (turnLimit < 2 || turnLimit > 500) {
            throw new IllegalArgumentException("turnLimit must be 2..500: " + turnLimit);
        }
        if (skillHandSize < 1 || skillHandSize > 6) {
            throw new IllegalArgumentException("skillHandSize must be 1..6: " + skillHandSize);
        }

        if (movementCards.size() != 16) {
            throw new IllegalArgumentException("movementCards count must be 16, got " + movementCards.size());
        }
        Set<String> moveIds = new HashSet<>();
        for (Card card : movementCards) {
            if (!EXPECTED_MOVEMENT_IDS.contains(card.id())) {
                throw new IllegalArgumentException("unknown movement card id: " + card.id());
            }
            if (!moveIds.add(card.id())) {
                throw new IllegalArgumentException("duplicate movement card id: " + card.id());
            }
            // Card.equals compares IDs only; compare the structural pattern explicitly.
            var expected = CardDeck.cardById(card.id()).offsets();
            if (card.offsets().size() != expected.size()
                    || !new HashSet<>(card.offsets()).equals(new HashSet<>(expected))) {
                throw new IllegalArgumentException("invalid movement pattern: " + card.id());
            }
        }

        if (skills.size() != 24) {
            throw new IllegalArgumentException("skills count must be 24, got " + skills.size());
        }
        Set<String> skillIds = new HashSet<>();
        int totalCopies = 0;
        for (SkillDefinition skill : skills) {
            if (!CANONICAL_SKILL_IDS.contains(skill.id())) {
                throw new IllegalArgumentException("unknown skill id: " + skill.id());
            }
            if (!skillIds.add(skill.id())) {
                throw new IllegalArgumentException("duplicate skill id: " + skill.id());
            }
            if (skill.name().isBlank() || skill.name().length() > 64) {
                throw new IllegalArgumentException("invalid skill name length for " + skill.id());
            }
            if (skill.descriptionTemplate().isBlank() || skill.descriptionTemplate().length() > 1024) {
                throw new IllegalArgumentException("invalid description length for " + skill.id());
            }
            if (skill.artworkPath().isBlank() || skill.artworkPath().length() > 256) {
                throw new IllegalArgumentException("invalid artwork path length for " + skill.id());
            }
            if (skill.manaCost() < 0 || skill.manaCost() > manaCap) {
                throw new IllegalArgumentException("skill cost out of bounds for " + skill.id() + ": " + skill.manaCost());
            }
            if (skill.copies() < 0 || skill.copies() > 4) {
                throw new IllegalArgumentException("skill copies must be 0..4 for " + skill.id() + ": " + skill.copies());
            }
            totalCopies += skill.copies();
            validateSkillParameters(skill, manaCap);
        }

        if (2 * skillHandSize > totalCopies) {
            throw new IllegalArgumentException("not enough skill copies for starting deal: 2 * "
                    + skillHandSize + " > " + totalCopies);
        }
    }

    private static void validateSkillParameters(SkillDefinition skill, int manaCap) {
        Map<SkillParameter, Integer> params = skill.parameters();
        switch (skill.id()) {
            case "sidestep" -> checkParam(params, SkillParameter.DISTANCE, 1, 6, true);
            case "dash" -> checkParam(params, SkillParameter.DISTANCE, 1, 6, true);
            case "vault" -> checkParam(params, SkillParameter.DISTANCE, 1, 6, true);
            case "pierce" -> checkParam(params, SkillParameter.DISTANCE, 1, 6, true);
            case "blink" -> checkParam(params, SkillParameter.RANGE, 1, 12, true);
            case "exchange" -> checkParam(params, SkillParameter.RANGE, 1, 12, true);
            case "transposition" -> checkParam(params, SkillParameter.RANGE, 1, 12, true);
            case "crossfire" -> checkParam(params, SkillParameter.RANGE, 1, 6, true);
            case "sweep" -> checkParam(params, SkillParameter.MAX_TARGETS, 1, 2, true);
            case "gust" -> checkParam(params, SkillParameter.MAX_TARGETS, 1, 2, true);
            case "rally" -> checkParam(params, SkillParameter.MAX_TARGETS, 1, 2, true);
            case "scatter" -> checkParam(params, SkillParameter.MAX_TARGETS, 1, 2, true);
            case "surround" -> checkParam(params, SkillParameter.REQUIRED_ALLIES, 1, 4, true);
            case "mana-surge" -> checkParam(params, SkillParameter.MANA_GAIN, 1, manaCap, true);
            case "foresight" -> checkParam(params, SkillParameter.LOOKAHEAD, 1, 5, true);
            default -> {
                if (!params.isEmpty()) {
                    throw new IllegalArgumentException("skill " + skill.id() + " must have no parameters, but has: " + params.keySet());
                }
            }
        }
    }

    private static void checkParam(Map<SkillParameter, Integer> params, SkillParameter key, int min, int max, boolean exact) {
        if (!params.containsKey(key)) {
            throw new IllegalArgumentException("missing parameter: " + key);
        }
        int val = params.get(key);
        if (val < min || val > max) {
            throw new IllegalArgumentException("parameter " + key + " out of bounds [" + min + ".." + max + "]: " + val);
        }
        if (exact && params.size() != 1) {
            throw new IllegalArgumentException("unexpected additional parameters: " + params.keySet());
        }
    }

    public SkillDefinition skill(String definitionId) {
        if (definitionId == null) return null;
        for (SkillDefinition s : skills) {
            if (s.id().equals(definitionId)) {
                return s;
            }
        }
        return null;
    }
}
