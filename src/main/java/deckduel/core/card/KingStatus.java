package deckduel.core.card;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/** Public ownership and round counters; unannounced selections never enter this record. */
public record KingStatus(List<King> kings, int spellsPlayed, boolean edictUsed) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    public static final KingStatus EMPTY = new KingStatus(List.of(), 0, false);
    public KingStatus {
        kings = List.copyOf(kings);
        if (kings.size() > 2 || kings.stream().distinct().count() != kings.size() || spellsPlayed < 0)
            throw new IllegalArgumentException("Invalid King status");
    }
}
