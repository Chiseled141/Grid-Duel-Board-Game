package onitama.core.duel;

import java.util.List;
import java.util.Objects;
import onitama.core.PlayerColor;

/**
 * Host-only record representing a pending Foresight selection during TurnPhase.CHOICE.
 */
record PendingForesight(
        PlayerColor actor,
        CardInstance skillInstance,
        int reservedMana,
        long choiceId,
        List<CardInstance> preview) {

    PendingForesight {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(skillInstance, "skillInstance must not be null");
        preview = preview == null ? List.of() : List.copyOf(preview);
    }
}
