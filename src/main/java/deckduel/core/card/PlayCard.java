package deckduel.core.card;

import java.io.Serial;

/**
 * Plays one hand card. A Unit names a row space (an empty one, or the Unit to replace on a full
 * row); a Spell uses {@link #NO_SPACE} and names its target when it takes one: a Unit or another
 * card in the caster's hand.
 */
public record PlayCard(long cardId, int slot, Long targetId) implements Move {
    @Serial private static final long serialVersionUID = 1L;
    /** The slot value for a Spell. */
    public static final int NO_SPACE = -1;

    /** Plays a Unit into a space. */
    public static PlayCard place(long cardId, int slot) { return new PlayCard(cardId, slot, null); }

    /** Casts a Spell at a target, or with none when it takes no target or none is legal. */
    public static PlayCard cast(long cardId, Long targetId) { return new PlayCard(cardId, NO_SPACE, targetId); }
}
