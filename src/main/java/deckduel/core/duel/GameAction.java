package deckduel.core.duel;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import deckduel.core.Square;

/**
 * Actions that can be applied to a Deck Duel match.
 *
 */
public sealed interface GameAction permits
        GameAction.ConfirmOpening,
        GameAction.MovePiece,
        GameAction.SkipMovement,
        GameAction.CastSkill,
        GameAction.ResolveForesight,
        GameAction.EndTurn,
        GameAction.Resign {

    /** Confirms the locally marked opening skills; empty means keep all. */
    record ConfirmOpening(java.util.List<String> replaceInstanceIds) implements GameAction, Serializable {
        @Serial private static final long serialVersionUID = 1L;
        public ConfirmOpening {
            replaceInstanceIds = java.util.List.copyOf(Objects.requireNonNull(replaceInstanceIds));
            if (replaceInstanceIds.size() > 6) throw new IllegalArgumentException("At most six replacements");
            if (replaceInstanceIds.stream().anyMatch(id -> id.isBlank() || id.length() > 64))
                throw new IllegalArgumentException("Invalid skill identity");
        }
    }

    record MovePiece(int pieceId, Square to) implements GameAction, Serializable {
        @Serial private static final long serialVersionUID = 1L;
        public MovePiece {
            Objects.requireNonNull(to, "to must not be null");
        }
    }

    record SkipMovement() implements GameAction, Serializable {
        @Serial private static final long serialVersionUID = 1L;
    }

    record CastSkill(String skillInstanceId, SkillTargets targets) implements GameAction, Serializable {
        @Serial private static final long serialVersionUID = 1L;
        public CastSkill {
            Objects.requireNonNull(skillInstanceId, "skillInstanceId must not be null");
            Objects.requireNonNull(targets, "targets must not be null");
        }
    }

    record ResolveForesight(long choiceId, java.util.List<String> orderedInstanceIds) implements GameAction, Serializable {
        @Serial private static final long serialVersionUID = 1L;
        public ResolveForesight {
            orderedInstanceIds = orderedInstanceIds == null ? java.util.List.of() : java.util.List.copyOf(orderedInstanceIds);
        }
    }

    record EndTurn() implements GameAction, Serializable {
        @Serial private static final long serialVersionUID = 1L;
    }

    record Resign() implements GameAction, Serializable {
        @Serial private static final long serialVersionUID = 1L;
    }
}
