package onitama.core;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A movement card. A card is an immutable strategy: it supplies a fixed set of
 * destination offsets, stored in Blue's native orientation ({@code +y} forward).
 * When Red uses the card, every offset is rotated 180 degrees, because both
 * players share the same physical cards seen from opposite sides of the board.
 */
public final class Card implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String name;
    private final PlayerColor stamp;
    private final List<Offset> offsets;

    /**
     * Creates a card. The offset list is defensively copied and made
     * unmodifiable, so the card can safely be shared between threads.
     */
    public Card(String id, String name, PlayerColor stamp, List<Offset> offsets) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.stamp = Objects.requireNonNull(stamp);
        this.offsets = List.copyOf(offsets);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    /** Returns the fixed stamp color of this card (RED or BLUE). */
    public PlayerColor stamp() {
        return stamp;
    }

    /** Returns the offsets in Blue's native orientation, unmodifiable. */
    public List<Offset> offsets() {
        return offsets;
    }

    /**
     * Returns the on-board destination squares reachable from {@code origin}
     * when this card is played by {@code mover}. Offsets are rotated 180
     * degrees for Red. Squares outside the board are dropped; occupancy is
     * checked later by the rules engine (a card never "blocks" on its own).
     */
    public List<Square> destinationsFrom(Square origin, PlayerColor mover) {
        List<Square> result = new ArrayList<>();
        for (Offset offset : offsets) {
            Offset effective = mover == PlayerColor.BLUE ? offset : offset.rotate180();
            Square destination = origin.plus(effective);
            if (Board.isInside(destination)) {
                result.add(destination);
            }
        }
        return result;
    }

    /** Cards are identified by their id, so deserialized copies compare equal. */
    @Override
    public boolean equals(Object obj) {
        return obj instanceof Card other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
