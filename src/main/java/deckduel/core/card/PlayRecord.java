package deckduel.core.card;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * The public facts of the latest accepted card play, so clients can show it happening: the card
 * (now revealed), where it went or what it targeted, the Unit it replaced or destroyed, a card it
 * took from a hand ({@code handCard}: the Exchange or random discard, or the stolen card), how many
 * cards the actor drew from it, and both rows straight after it, which still exist when the play
 * ended the round and the rows were cleared.
 */
public record PlayRecord(Seat actor, CardInstance card, int slot, Long targetId, CardInstance replaced,
                         CardInstance removed, CardInstance handCard, int drawn, Row actorRow, Row opponentRow)
        implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /** Requires the actor, card and rows. */
    public PlayRecord {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(card, "card");
        Objects.requireNonNull(actorRow, "actorRow");
        Objects.requireNonNull(opponentRow, "opponentRow");
    }
}
