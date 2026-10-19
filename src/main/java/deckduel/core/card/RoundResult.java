package deckduel.core.card;

import java.io.Serial;
import java.io.Serializable;

/** The banked final scores of one finished round. */
public record RoundResult(int round, Seat starter, long firstScore, long secondScore) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /** The seat with the higher round score, or null for a tied round. */
    public Seat winner() {
        if (firstScore == secondScore) return null;
        return firstScore > secondScore ? Seat.FIRST : Seat.SECOND;
    }

    /** A seat's banked score for this round. */
    public long scoreOf(Seat seat) { return seat == Seat.FIRST ? firstScore : secondScore; }
}
