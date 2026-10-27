package deckduel.net;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * One recorded match from the recipient's point of view. Legacy rows from the retired game
 * have no round scores; {@code rulesVersion} tells them apart. Practice games appear too, but
 * never change wins or losses. {@code replayId} is the recipient's own recording, or null when
 * {@code replay} is {@link Replay#UNAVAILABLE}.
 */
public record HistoryEntry(String playedAt, String opponent, Result result, String endReason,
                           String rulesVersion, List<Long> yourRounds, List<Long> opponentRounds,
                           boolean practice, Long replayId, Replay replay) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /** The recipient's result. */
    public enum Result { WIN, LOSS, DRAW }

    /** Whether the recipient can watch this match again. */
    public enum Replay {
        /** Every recorded action is stored. */
        AVAILABLE,
        /** A recording exists but some actions or the stored result are missing. */
        INCOMPLETE,
        /** No recording exists, as for matches played before replays were recorded. */
        UNAVAILABLE
    }

    /** Copies the round lists and keeps the replay id consistent with its status. */
    public HistoryEntry {
        yourRounds = List.copyOf(yourRounds);
        opponentRounds = List.copyOf(opponentRounds);
        if ((replay == Replay.UNAVAILABLE) != (replayId == null))
            throw new IllegalArgumentException("Only an unavailable replay has no id");
    }

    /** Sum of the recipient's banked round scores. */
    public long yourTotal() { return yourRounds.stream().mapToLong(Long::longValue).sum(); }

    /** Sum of the opponent's banked round scores. */
    public long opponentTotal() { return opponentRounds.stream().mapToLong(Long::longValue).sum(); }
}
