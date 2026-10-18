package deckduel.core.duel;

import java.util.List;
import java.util.Objects;

/**
 * Result of applying an action to an authoritative GameState.
 */
public sealed interface ActionResult permits ActionResult.Accepted, ActionResult.Rejected {

    record Accepted(GameState nextState, List<PublicEvent> events) implements ActionResult {
        public Accepted {
            Objects.requireNonNull(nextState, "nextState must not be null");
            events = List.copyOf(events);
        }
    }

    record Rejected(ActionError error) implements ActionResult {
        public Rejected {
            Objects.requireNonNull(error, "error must not be null");
        }
    }
}
