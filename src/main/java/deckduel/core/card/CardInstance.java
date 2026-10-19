package deckduel.core.card;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/** One physical copy of a card in a match; the id distinguishes duplicate copies. */
public record CardInstance(long id, CardDef def) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /** Requires a definition. */
    public CardInstance { Objects.requireNonNull(def, "def"); }
}
