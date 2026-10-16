package onitama.core.duel;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import onitama.core.PlayerColor;
import onitama.core.Square;

/**
 * Public events emitted when an action is accepted on the authoritative engine.
 */
public sealed interface PublicEvent permits
        PublicEvent.PieceMoved,
        PublicEvent.PieceEliminated,
        PublicEvent.MovementPlayed,
        PublicEvent.SkillCommitted,
        PublicEvent.TurnStarted,
        PublicEvent.MatchEnded {

    record PieceMoved(int pieceId, Square from, Square to) implements PublicEvent, Serializable {
        @Serial private static final long serialVersionUID = 1L;
        public PieceMoved {
            Objects.requireNonNull(from, "from must not be null");
            Objects.requireNonNull(to, "to must not be null");
        }
    }

    record PieceEliminated(int pieceId, Square at) implements PublicEvent, Serializable {
        @Serial private static final long serialVersionUID = 1L;
        public PieceEliminated {
            Objects.requireNonNull(at, "at must not be null");
        }
    }

    record MovementPlayed(PlayerColor actor, String cardId) implements PublicEvent, Serializable {
        @Serial private static final long serialVersionUID = 1L;
        public MovementPlayed {
            Objects.requireNonNull(actor, "actor must not be null");
            Objects.requireNonNull(cardId, "cardId must not be null");
        }
    }

    record SkillCommitted(PlayerColor actor, CardInstance skill) implements PublicEvent, Serializable {
        @Serial private static final long serialVersionUID = 1L;
        public SkillCommitted {
            Objects.requireNonNull(actor, "actor must not be null");
            Objects.requireNonNull(skill, "skill must not be null");
        }
    }

    record TurnStarted(PlayerColor actor, int startedTurns) implements PublicEvent, Serializable {
        @Serial private static final long serialVersionUID = 1L;
        public TurnStarted {
            Objects.requireNonNull(actor, "actor must not be null");
        }
    }

    record MatchEnded(GameOutcome outcome) implements PublicEvent, Serializable {
        @Serial private static final long serialVersionUID = 1L;
        public MatchEnded {
            Objects.requireNonNull(outcome, "outcome must not be null");
        }
    }
}
