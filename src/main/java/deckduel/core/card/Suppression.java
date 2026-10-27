package deckduel.core.card;

/** What a disable suppresses on a Unit until the round ends or a clearing effect removes it. */
public enum Suppression {
    /** A Unit's printed points. */
    POINTS,
    /** A Unit's nation and class tags, and therefore its combos and tag-based effects. */
    TAGS
}
