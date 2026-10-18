package deckduel.core.duel;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import deckduel.core.Square;

/**
 * Result of querying Targeting.skillOptions for available selections or completion.
 */
public record SelectionOptions(
        List<Integer> selectablePieceIds,
        List<Square> selectableSquares,
        List<Direction> selectableDirections,
        SkillTargets completeTargets,
        boolean canSubmit,
        ActionError error) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public SelectionOptions {
        selectablePieceIds = selectablePieceIds == null ? List.of() : List.copyOf(selectablePieceIds);
        selectableSquares = selectableSquares == null ? List.of() : List.copyOf(selectableSquares);
        selectableDirections = selectableDirections == null ? List.of() : List.copyOf(selectableDirections);
    }

    public static SelectionOptions error(ActionError error) {
        return new SelectionOptions(List.of(), List.of(), List.of(), null, false, error);
    }
}
