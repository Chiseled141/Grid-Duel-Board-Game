package onitama.core.duel;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import onitama.core.Square;

/**
 * Actions that can be applied to a Grid Duel match.
 *
 */
public sealed interface GameAction permits
        GameAction.MovePiece,
        GameAction.SkipMovement,
        GameAction.CastSkill,
        GameAction.ResolveForesight,
        GameAction.EndTurn,
        GameAction.Resign {

    record MovePiece(String movementCardId, int pieceId, Square to) implements GameAction, Serializable {
        @Serial private static final long serialVersionUID = 1L;
        public MovePiece {
            Objects.requireNonNull(movementCardId, "movementCardId must not be null");
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
