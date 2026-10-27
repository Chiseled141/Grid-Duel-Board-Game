package deckduel.core.card;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** One player's seven-space Unit row; only occupied spaces are stored, ordered by space. */
public record Row(List<RowCard> cards) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    /** Spaces per row. */
    public static final int SIZE = 7;
    /** An empty row, as at the start of every round. */
    public static final Row EMPTY = new Row(List.of());

    /** Rejects duplicate spaces and keeps the cards sorted by space. */
    public Row {
        List<RowCard> sorted = new ArrayList<>(cards);
        sorted.sort(Comparator.comparingInt(RowCard::slot));
        for (int i = 1; i < sorted.size(); i++)
            if (sorted.get(i).slot() == sorted.get(i - 1).slot()) throw new IllegalArgumentException("Space used twice");
        cards = List.copyOf(sorted);
    }

    /** The card in a space, or null when the space is empty. */
    public RowCard at(int slot) {
        return cards.stream().filter(card -> card.slot() == slot).findFirst().orElse(null);
    }

    /** The row card with the given instance id, or null. */
    public RowCard find(long cardId) {
        return cards.stream().filter(card -> card.card().id() == cardId).findFirst().orElse(null);
    }

    /** Whether every space is occupied. */
    public boolean isFull() { return cards.size() == SIZE; }

    /** Places a card, replacing any card already in that space. */
    public Row place(int slot, CardInstance card) {
        List<RowCard> next = new ArrayList<>(cards);
        next.removeIf(existing -> existing.slot() == slot);
        next.add(RowCard.placed(slot, card));
        return new Row(next);
    }

    /** Removes the card with the given instance id. */
    public Row remove(long cardId) {
        List<RowCard> next = new ArrayList<>(cards);
        next.removeIf(existing -> existing.card().id() == cardId);
        return new Row(next);
    }

    /** Replaces one card with an updated copy of itself. */
    public Row update(RowCard updated) {
        List<RowCard> next = new ArrayList<>(cards);
        next.replaceAll(existing -> existing.slot() == updated.slot() ? updated : existing);
        return new Row(next);
    }
}
