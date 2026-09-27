package onitama.core;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Immutable snapshot of a game's opening deal: both hands and the transit
 * card. Cards are immutable, so holding references is safe even though the
 * players' hands in {@link GameState} keep changing — this snapshot preserves
 * the opening position for replay files and match records.
 */
public record DealSnapshot(List<Card> blueHand, List<Card> redHand, Card transit)
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Builds the snapshot from defensive copies of the given hands. */
    public DealSnapshot {
        blueHand = List.copyOf(blueHand);
        redHand = List.copyOf(redHand);
    }

    /** The first player, decided by the transit card's stamp color. */
    public PlayerColor firstPlayer() {
        return transit.stamp();
    }
}
