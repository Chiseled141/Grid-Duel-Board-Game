package deckduel.net;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * One recorded match from the recipient's point of view. Legacy rows from the retired game
 * have no round scores; {@code rulesVersion} tells them apart.
 */
public record HistoryEntry(String playedAt, String opponent, Result result, String endReason,
                           String rulesVersion, List<Long> yourRounds, List<Long> opponentRounds) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /** The recipient's result. */
    public enum Result { WIN, LOSS, DRAW }

    /** Copies the round lists. */
    public HistoryEntry {
        yourRounds = List.copyOf(yourRounds);
        opponentRounds = List.copyOf(opponentRounds);
    }

    /** Sum of the recipient's banked round scores. */
    public long yourTotal() { return yourRounds.stream().mapToLong(Long::longValue).sum(); }

    /** Sum of the opponent's banked round scores. */
    public long opponentTotal() { return opponentRounds.stream().mapToLong(Long::longValue).sum(); }
}
