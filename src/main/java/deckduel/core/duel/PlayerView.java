package deckduel.core.duel;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import deckduel.core.PlayerColor;

/**
 * Recipient-specific immutable projection of match state.
 */
public record PlayerView(
        PlayerColor viewer,
        RulesConfig rules,
        long revision,
        List<PlacedPiece> pieces,
        PlayerColor turn,
        TurnPhase phase,
        int startedTurns,
        int completedTurns,
        List<String> blueMovementHand,
        List<String> redMovementHand,
        List<CardInstance> ownSkillHand,
        int opponentSkillCount,
        int blueMana,
        int redMana,
        int reservedMana,
        int movementDeckCount,
        int skillDeckCount,
        List<CardInstance> spentSkills,
        ForesightView ownChoice,
        GameOutcome outcome,
        int blueScore,
        int redScore,
        ObjectiveLayout objectives,
        boolean openingConfirmed,
        boolean opponentOpeningConfirmed,
        List<CardInstance> revealedEnemySkills,
        List<CardInstance> ownBurnedDraws) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Builds an ordinary view without private hand intel or overflow draws. */
    public PlayerView(PlayerColor viewer, RulesConfig rules, long revision, List<PlacedPiece> pieces,
                      PlayerColor turn, TurnPhase phase, int startedTurns, int completedTurns,
                      List<String> blueMovementHand, List<String> redMovementHand,
                      List<CardInstance> ownSkillHand, int opponentSkillCount, int blueMana, int redMana,
                      int reservedMana, int movementDeckCount, int skillDeckCount,
                      List<CardInstance> spentSkills, ForesightView ownChoice, GameOutcome outcome,
                      int blueScore, int redScore, ObjectiveLayout objectives,
                      boolean openingConfirmed, boolean opponentOpeningConfirmed) {
        this(viewer, rules, revision, pieces, turn, phase, startedTurns, completedTurns,
                blueMovementHand, redMovementHand, ownSkillHand, opponentSkillCount, blueMana, redMana,
                reservedMana, movementDeckCount, skillDeckCount, spentSkills, ownChoice, outcome,
                blueScore, redScore, objectives, openingConfirmed, opponentOpeningConfirmed, List.of(), List.of());
    }

    /** Compatibility constructor for ordinary in-turn projections. */
    public PlayerView(PlayerColor viewer, RulesConfig rules, long revision, List<PlacedPiece> pieces,
                      PlayerColor turn, TurnPhase phase, int startedTurns, int completedTurns,
                      List<String> blueMovementHand, List<String> redMovementHand,
                      List<CardInstance> ownSkillHand, int opponentSkillCount, int blueMana, int redMana,
                      int reservedMana, int movementDeckCount, int skillDeckCount,
                      List<CardInstance> spentSkills, ForesightView ownChoice, GameOutcome outcome,
                      int blueScore, int redScore, ObjectiveLayout objectives) {
        this(viewer, rules, revision, pieces, turn, phase, startedTurns, completedTurns,
                blueMovementHand, redMovementHand, ownSkillHand, opponentSkillCount, blueMana, redMana,
                reservedMana, movementDeckCount, skillDeckCount, spentSkills, ownChoice, outcome,
                blueScore, redScore, objectives, false, false);
    }

    /** Creates a view with no points yet, for an initial match projection. */
    public PlayerView(PlayerColor viewer, RulesConfig rules, long revision, List<PlacedPiece> pieces,
                      PlayerColor turn, TurnPhase phase, int startedTurns, int completedTurns,
                      List<String> blueMovementHand, List<String> redMovementHand,
                      List<CardInstance> ownSkillHand, int opponentSkillCount, int blueMana, int redMana,
                      int reservedMana, int movementDeckCount, int skillDeckCount,
                      List<CardInstance> spentSkills, ForesightView ownChoice, GameOutcome outcome) {
        this(viewer, rules, revision, pieces, turn, phase, startedTurns, completedTurns,
                blueMovementHand, redMovementHand, ownSkillHand, opponentSkillCount, blueMana, redMana,
                reservedMana, movementDeckCount, skillDeckCount, spentSkills, ownChoice, outcome, 0, 0, ObjectiveLayout.standard());
    }

    /** Returns the public score for either side. */
    public int score(PlayerColor color) { return color == PlayerColor.BLUE ? blueScore : redScore; }

    public PlayerView {
        Objects.requireNonNull(objectives, "objectives must not be null");
        if (blueScore < 0 || redScore < 0) throw new IllegalArgumentException("scores must be nonnegative");
        Objects.requireNonNull(viewer, "viewer must not be null");
        Objects.requireNonNull(rules, "rules must not be null");
        pieces = List.copyOf(pieces);
        blueMovementHand = List.copyOf(blueMovementHand);
        redMovementHand = List.copyOf(redMovementHand);
        ownSkillHand = List.copyOf(ownSkillHand);
        spentSkills = List.copyOf(spentSkills);
        revealedEnemySkills = List.copyOf(revealedEnemySkills);
        ownBurnedDraws = List.copyOf(ownBurnedDraws);
    }
}
