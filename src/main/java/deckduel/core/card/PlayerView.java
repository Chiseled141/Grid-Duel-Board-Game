package deckduel.core.card;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Everything one player may know about a match: both public sides plus the recipient's private
 * facts: their own hand, the cards their Reconnaissance is showing ({@code looking}) and the
 * opponent's hand cards their Spy revealed ({@code revealed}). It never contains the opponent's
 * other hand cards, deck order, seeds or RNG state. {@code lastPlay}
 * describes the card play that produced this revision, or is null.
 */
public record PlayerView(Seat seat, int revision, MatchPhase phase, int round, Seat turn,
                         SideView you, SideView opponent, List<CardInstance> hand,
                         List<RoundResult> rounds, List<MatchEvent> events, MatchOutcome outcome,
                         PlayRecord lastPlay, List<CardInstance> looking, List<CardInstance> revealed, List<King> kingOffer, King selectedKing, ChoiceKind choiceKind)
        implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /** Copies lists and checks that finished views carry an outcome. */
    public PlayerView {
        Objects.requireNonNull(seat, "seat");
        Objects.requireNonNull(phase, "phase");
        kingOffer = List.copyOf(kingOffer);
        Objects.requireNonNull(choiceKind, "choiceKind");
        hand = List.copyOf(hand);
        rounds = List.copyOf(rounds);
        events = List.copyOf(events);
        looking = List.copyOf(looking);
        revealed = List.copyOf(revealed);
        if ((phase == MatchPhase.FINISHED) != (outcome != null))
            throw new IllegalArgumentException("Only finished matches have an outcome");
    }

    /** View fixture with no pending King choice. Old serialized frames lack required King fields and are refused. */
    public PlayerView(Seat seat, int revision, MatchPhase phase, int round, Seat turn, SideView you, SideView opponent,
                      List<CardInstance> hand, List<RoundResult> rounds, List<MatchEvent> events, MatchOutcome outcome,
                      PlayRecord lastPlay, List<CardInstance> looking, List<CardInstance> revealed) {
        this(seat, revision, phase, round, turn, you, opponent, hand, rounds, events, outcome, lastPlay, looking,
                revealed, List.of(), null, looking.isEmpty() ? ChoiceKind.NONE : ChoiceKind.RECONNAISSANCE);
    }

    /** Whether this seat still needs to confirm its King. */
    public boolean choosingKing() { return phase == MatchPhase.KING_SELECTION && selectedKing == null; }

    /** Whether this recipient can send a choice or a normal move. */
    public boolean needsAction() { return choosingKing() || yourTurn(); }

    /** Whether it is the recipient's turn. */
    public boolean yourTurn() { return phase == MatchPhase.IN_PROGRESS && turn == seat; }

    /** Whether the recipient must keep one of the cards their Reconnaissance revealed. */
    public boolean keeping() { return yourTurn() && !looking.isEmpty(); }

    /** Both public tables from the recipient's side. */
    public Rules.Board board() { return new Rules.Board(you.row(), opponent.row()); }

    /** Every card play the recipient could make now; empty when it is not their turn to play. */
    public List<PlayCard> legalPlays() {
        return yourTurn() && looking.isEmpty() && you.playsRemaining() > 0 ? Rules.legalPlays(hand, board()) : List.of();
    }

    /** Whether the recipient may swap at least one hand card now. */
    public boolean canSwap() { return swapLimit() > 0; }

    /** How many hand cards the recipient may swap at once now: limited by swaps left, deck and hand. */
    public int swapLimit() {
        if (!yourTurn() || !looking.isEmpty()) return 0;
        return Math.max(0, Math.min(you.drawsRemaining(), Math.min(you.deckCount(), hand.size())));
    }

    /** Whether the recipient can give up one play allowance to skip this turn. */
    public boolean canSkip() {
        return yourTurn() && looking.isEmpty() && you.playsRemaining() > 0;
    }
}
