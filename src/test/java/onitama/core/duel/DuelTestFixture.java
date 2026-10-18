package onitama.core.duel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import onitama.core.CardDeck;

/**
 * Test fixture providing baseline RulesConfig constructed from Appendix A and B
 * and docs/examples/game-balance.properties values.
 */
public final class DuelTestFixture {

    private DuelTestFixture() {}

    /**
     * Builds the default 24 skill definitions from Appendix B and the balance specification.
     */
    public static List<SkillDefinition> defaultSkillDefinitions() {
        List<SkillDefinition> skills = new ArrayList<>();

        skills.add(new SkillDefinition(
                "sidestep", "SIDESTEP", SkillCategory.MOVEMENT, 1, 1,
                Map.of(SkillParameter.DISTANCE, 1),
                "Move one friendly piece exactly {distance} square(s) horizontally or vertically to an empty square. Jump over intervening pieces.",
                "assets/skills/sidestep.png"));

        skills.add(new SkillDefinition(
                "dash", "DASH", SkillCategory.MOVEMENT, 2, 1,
                Map.of(SkillParameter.DISTANCE, 2),
                "Move one friendly piece exactly {distance} square(s) horizontally or vertically. Every traversed square, including the destination, must be empty.",
                "assets/skills/dash.png"));

        skills.add(new SkillDefinition(
                "blink", "BLINK", SkillCategory.MOVEMENT, 3, 1,
                Map.of(SkillParameter.RANGE, 3),
                "Teleport one friendly piece to an empty square within distance {range}. Ignore pieces between.",
                "assets/skills/blink.png"));

        skills.add(new SkillDefinition(
                "exchange", "EXCHANGE", SkillCategory.MOVEMENT, 2, 1,
                Map.of(SkillParameter.RANGE, 3),
                "Swap two friendly pieces within distance {range} of each other.",
                "assets/skills/exchange.png"));

        skills.add(new SkillDefinition(
                "advance", "ADVANCE", SkillCategory.MOVEMENT, 3, 1,
                Map.of(),
                "Move two different friendly pieces 1 square each toward the enemy starting edge. Both destinations must be empty.",
                "assets/skills/advance.png"));

        skills.add(new SkillDefinition(
                "recall", "RECALL", SkillCategory.MOVEMENT, 1, 1,
                Map.of(),
                "Teleport one friendly piece to any empty square on your starting row.",
                "assets/skills/recall.png"));

        skills.add(new SkillDefinition(
                "strike", "STRIKE", SkillCategory.ATTACK, 3, 1,
                Map.of(),
                "Choose a friendly piece. Eliminate 1 enemy horizontally or vertically adjacent to it. Your piece stays still.",
                "assets/skills/strike.png"));

        skills.add(new SkillDefinition(
                "pierce", "PIERCE", SkillCategory.ATTACK, 4, 1,
                Map.of(SkillParameter.DISTANCE, 2),
                "Choose a friendly piece. Eliminate an enemy exactly {distance} square(s) away horizontally or vertically. All intervening squares must be empty.",
                "assets/skills/pierce.png"));

        skills.add(new SkillDefinition(
                "surround", "SURROUND", SkillCategory.ATTACK, 2, 1,
                Map.of(SkillParameter.REQUIRED_ALLIES, 2),
                "Eliminate 1 enemy horizontally or vertically adjacent to at least {requiredAllies} friendly piece(s).",
                "assets/skills/surround.png"));

        skills.add(new SkillDefinition(
                "sweep", "SWEEP", SkillCategory.ATTACK, 5, 1,
                Map.of(SkillParameter.MAX_TARGETS, 2),
                "Choose a friendly piece. Eliminate up to {maxTargets} enemies horizontally or vertically adjacent to it.",
                "assets/skills/sweep.png"));

        skills.add(new SkillDefinition(
                "shove", "SHOVE", SkillCategory.CONTROL, 1, 1,
                Map.of(),
                "Choose a friendly piece. Push an adjacent enemy 1 square directly away. The destination must be empty and on the board.",
                "assets/skills/shove.png"));

        skills.add(new SkillDefinition(
                "pull", "PULL", SkillCategory.CONTROL, 1, 1,
                Map.of(),
                "Choose a friendly piece. Pull an enemy exactly 2 squares away horizontally or vertically into the empty square between.",
                "assets/skills/pull.png"));

        skills.add(new SkillDefinition(
                "vault", "VAULT", SkillCategory.MOVEMENT, 2, 1,
                Map.of(SkillParameter.DISTANCE, 2),
                "Move one friendly piece exactly {distance} square(s) horizontally or vertically to an empty square. You may jump over a piece.",
                "assets/skills/vault.png"));

        skills.add(new SkillDefinition(
                "flank", "FLANK", SkillCategory.ATTACK, 3, 1,
                Map.of(),
                "Move one friendly piece 1 square diagonally. The destination must be empty or contain an enemy. Eliminate that enemy if present.",
                "assets/skills/flank.png"));

        skills.add(new SkillDefinition(
                "transposition", "TRANSPOSITION", SkillCategory.CONTROL, 4, 1,
                Map.of(SkillParameter.RANGE, 2),
                "Swap one friendly piece with one enemy within distance {range}. Neither piece is eliminated.",
                "assets/skills/transposition.png"));

        skills.add(new SkillDefinition(
                "gust", "GUST", SkillCategory.CONTROL, 3, 1,
                Map.of(SkillParameter.MAX_TARGETS, 2),
                "Move up to {maxTargets} enemies 1 square each in the same horizontal or vertical direction. All selected destinations must be empty before the skill.",
                "assets/skills/gust.png"));

        skills.add(new SkillDefinition(
                "rally", "RALLY", SkillCategory.MOVEMENT, 3, 1,
                Map.of(SkillParameter.MAX_TARGETS, 2),
                "Choose a friendly anchor. Move up to {maxTargets} other friendly pieces 1 square each horizontally or vertically toward it. Each move must reduce distance and land empty.",
                "assets/skills/rally.png"));

        skills.add(new SkillDefinition(
                "scatter", "SCATTER", SkillCategory.MOVEMENT, 3, 1,
                Map.of(SkillParameter.MAX_TARGETS, 2),
                "Move up to {maxTargets} different friendly pieces 1 square diagonally each. All selected destinations must be empty before the skill.",
                "assets/skills/scatter.png"));

        skills.add(new SkillDefinition(
                "crossfire", "CROSSFIRE", SkillCategory.ATTACK, 4, 1,
                Map.of(SkillParameter.RANGE, 3),
                "Eliminate an enemy between two friendly pieces in one row or column. Each ally must be within distance {range} with no pieces between it and the target.",
                "assets/skills/crossfire.png"));

        skills.add(new SkillDefinition(
                "chain-strike", "CHAIN STRIKE", SkillCategory.ATTACK, 5, 1,
                Map.of(),
                "Choose a friendly piece. Eliminate an adjacent enemy, then optionally 1 enemy adjacent to the first target. Adjacency is horizontal or vertical.",
                "assets/skills/chain-strike.png"));

        skills.add(new SkillDefinition(
                "mana-surge", "MANA SURGE", SkillCategory.RESOURCE, 0, 1,
                Map.of(SkillParameter.MANA_GAIN, 2),
                "Gain {manaGain} mana, up to the mana cap. This uses your skill for the turn.",
                "assets/skills/mana-surge.png"));

        skills.add(new SkillDefinition(
                "refocus", "REFOCUS", SkillCategory.RESOURCE, 1, 1,
                Map.of(),
                "Permanently discard your other held skills, then draw that many skills.",
                "assets/skills/refocus.png"));

        skills.add(new SkillDefinition(
                "foresight", "FORESIGHT", SkillCategory.RESOURCE, 1, 1,
                Map.of(SkillParameter.LOOKAHEAD, 3),
                "Look at the top {lookahead} skill card(s), or all remaining cards if fewer. Return them to the top in any order before drawing your replacement.",
                "assets/skills/foresight.png"));

        skills.add(new SkillDefinition(
                "banish", "BANISH", SkillCategory.CONTROL, 4, 1,
                Map.of(),
                "Choose a friendly piece. Send an adjacent enemy to an empty square on that enemy's starting row. Adjacency is horizontal or vertical.",
                "assets/skills/banish.png"));

        var defaults = onitama.config.RulesConfigLoader.load(null);
        for (String id : List.of("steal", "spy", "draw-two", "sabotage", "barter", "siphon")) skills.add(defaults.skill(id));
        return List.copyOf(skills);
    }

    /**
     * Builds the full baseline RulesConfig using the 16 Appendix movement cards and 24 default skills.
     */
    public static RulesConfig defaultRulesConfig() {
        return new RulesConfig(
                "grid-duel-v4", 0,
                3,
                2,
                1,
                5,
                60,
                List.of(),
                defaultSkillDefinitions());
    }

    /**
     * Builds a RulesConfig with a custom turn limit.
     */
    public static RulesConfig rulesConfigWithTurnLimit(int turnLimit) {
        return new RulesConfig(
                "grid-duel-v4", 0,
                3,
                2,
                1,
                5,
                turnLimit,
                List.of(),
                defaultSkillDefinitions());
    }
    /** Builds a movement fixture while conserving all 16 cards across hands and deck. */
    static GameState withMovementCards(onitama.core.PlayerColor actor, String first, String second) {
        GameState state = onitama.core.duel.RulesEngine.newGame(defaultRulesConfig(), 42L);
        state.setTurn(actor);
        return state;
    }

}
