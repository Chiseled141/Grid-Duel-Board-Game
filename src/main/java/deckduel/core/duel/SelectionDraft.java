package deckduel.core.duel;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import deckduel.core.Square;

/**
 * Local UI/policy draft of targets selected so far for a skill.
 * May be incomplete. Order follows target conventions.
 */
public record SelectionDraft(
        List<Integer> pieceIds,
        List<Square> destinations,
        Direction direction) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public SelectionDraft {
        pieceIds = pieceIds == null ? List.of() : List.copyOf(pieceIds);
        destinations = destinations == null ? List.of() : List.copyOf(destinations);
    }

    public static SelectionDraft empty() {
        return new SelectionDraft(List.of(), List.of(), null);
    }
}
