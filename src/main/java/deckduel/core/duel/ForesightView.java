package deckduel.core.duel;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Private view of a pending Foresight choice.
 */
public record ForesightView(
        long choiceId,
        String skillInstanceId,
        int reservedMana,
        List<CardInstance> preview) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public ForesightView {
        Objects.requireNonNull(skillInstanceId, "skillInstanceId must not be null");
        preview = preview == null ? List.of() : List.copyOf(preview);
    }
}
