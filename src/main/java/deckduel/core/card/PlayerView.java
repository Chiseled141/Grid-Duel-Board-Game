package deckduel.core.card;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Everything one player may know about a match: both public sides plus the recipient's private
 * facts: their own hand, their Landmark offers while choosing, the cards their Reconnaissance is
 * showing ({@code looking}), the opponent's hand cards their Spy revealed ({@code revealed}) and,
 * with an active Watchtower, the top card of their own deck ({@code deckTop}, else null). It never
 * contains the opponent's other hand cards, deck order, seeds or RNG state. {@code lastPlay}
 * describes the card play that produced this revision, or is null.
 */
public record PlayerView(Seat seat, int revision, MatchPhase phase, int round, Seat turn,
                         SideView you, SideView opponent, List<CardInstance> hand,
                         List<RoundResult> rounds, List<MatchEvent> events, MatchOutcome outcome,
                         PlayRecord lastPlay, List<CardInstance> offers, List<CardInstance> looking,
                         List<CardInstance> revealed, CardInstance deckTop)
        implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /** Copies lists and checks that finished views carry an outcome. */
    public PlayerView {
        Objects.requireNonNull(seat, "seat");
        Objects.requireNonNull(phase, "phase");
        hand = List.copyOf(hand);
        rounds = List.copyOf(rounds);
        events = List.copyOf(events);
        offers = List.copyOf(offers);
        looking = List.copyOf(looking);
        revealed = List.copyOf(revealed);
        if ((phase == MatchPhase.FINISHED) != (outcome != null))
            throw new IllegalArgumentException("Only finished matches have an outcome");
    }

    /** Whether the recipient must still choose their Landmarks. */
    public boolean choosingLandmarks() { return phase == MatchPhase.CHOOSING_LANDMARKS && !you.ready(); }

    /** Whether it is the recipient's turn. */
    public boolean yourTurn() { return phase == MatchPhase.IN_PROGRESS && turn == seat; }

    /** Whether the recipient must keep one of the cards their Reconnaissance revealed. */
    public boolean keeping() { return yourTurn() && !looking.isEmpty(); }

    /** Both public tables from the recipient's side. */
    public Rules.Board board() { return new Rules.Board(you.row(), you.landmarks(), opponent.row(), opponent.landmarks()); }

    /** Every card play the recipient could make now; empty when it is not their turn to play. */
    public List<PlayCard> legalPlays() {
        return yourTurn() && looking.isEmpty() && you.playsRemaining() > 0 ? Rules.legalPlays(hand, board()) : List.of();
    }

    /** Whether the recipient may make a manual draw now. */
    public boolean canDraw() {
        return yourTurn() && looking.isEmpty() && you.drawsRemaining() > 0 && you.deckCount() > 0;
    }

    /** Whether the recipient can give up one play allowance to skip this turn. */
    public boolean canSkip() {
        return yourTurn() && looking.isEmpty() && you.playsRemaining() > 0;
    }
}
