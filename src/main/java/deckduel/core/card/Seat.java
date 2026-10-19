package deckduel.core.card;

/** One of the two players in a match; FIRST is the host or first-queued player. */
public enum Seat {
    FIRST, SECOND;

    /** The opposing seat. */
    public Seat other() { return this == FIRST ? SECOND : FIRST; }
}
