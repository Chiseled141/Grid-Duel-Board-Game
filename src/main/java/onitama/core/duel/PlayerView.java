package onitama.core.duel;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import onitama.core.PlayerColor;

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
        GameOutcome outcome) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public PlayerView {
        Objects.requireNonNull(viewer, "viewer must not be null");
        Objects.requireNonNull(rules, "rules must not be null");
        pieces = List.copyOf(pieces);
        blueMovementHand = List.copyOf(blueMovementHand);
        redMovementHand = List.copyOf(redMovementHand);
        ownSkillHand = List.copyOf(ownSkillHand);
        spentSkills = List.copyOf(spentSkills);
    }
}
