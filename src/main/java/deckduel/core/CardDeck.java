package deckduel.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The deck of the 16 movement cards with the fixed offset data from the
 * project brief (Appendix A).
 */
public final class CardDeck {

    private static final List<Card> CATALOG = buildCatalog();
    private static final Map<String, Card> BY_ID = CATALOG.stream()
            .collect(Collectors.toUnmodifiableMap(Card::id, c -> c));

    private CardDeck() {
    }

    private static List<Card> buildCatalog() {
        List<Card> cards = new ArrayList<>();
        // Offsets in Blue's native orientation, exactly as in Appendix A of the brief.
        // Stamp colors are a fixed assignment (8 blue / 8 red); per-card stamps
        // are not publicly standardized.
        cards.add(new Card("tiger", "Tiger", PlayerColor.BLUE,
                List.of(new Offset(0, 3), new Offset(0, -1))));
        cards.add(new Card("dragon", "Dragon", PlayerColor.RED,
                List.of(new Offset(-2, 1), new Offset(2, 1),
                        new Offset(-1, -1), new Offset(1, -1))));
        cards.add(new Card("frog", "Frog", PlayerColor.RED,
                List.of(new Offset(-3, 0), new Offset(-1, 1), new Offset(1, -1))));
        cards.add(new Card("rabbit", "Rabbit", PlayerColor.BLUE,
                List.of(new Offset(3, 0), new Offset(1, 1), new Offset(-1, -1))));
        cards.add(new Card("crab", "Crab", PlayerColor.BLUE,
                List.of(new Offset(0, 1), new Offset(-2, 0), new Offset(2, 0))));
        cards.add(new Card("elephant", "Elephant", PlayerColor.RED,
                List.of(new Offset(-1, 0), new Offset(1, 0),
                        new Offset(-1, 1), new Offset(1, 1))));
        cards.add(new Card("goose", "Goose", PlayerColor.BLUE,
                List.of(new Offset(-1, 0), new Offset(1, 0),
                        new Offset(-1, 1), new Offset(1, -1))));
        cards.add(new Card("rooster", "Rooster", PlayerColor.RED,
                List.of(new Offset(-1, 0), new Offset(1, 0),
                        new Offset(-1, -1), new Offset(1, 1))));
        cards.add(new Card("monkey", "Monkey", PlayerColor.BLUE,
                List.of(new Offset(-1, 1), new Offset(1, 1),
                        new Offset(-1, -1), new Offset(1, -1))));
        cards.add(new Card("mantis", "Mantis", PlayerColor.RED,
                List.of(new Offset(0, -1), new Offset(-1, 1), new Offset(1, 1))));
        cards.add(new Card("horse", "Horse", PlayerColor.RED,
                List.of(new Offset(0, 1), new Offset(0, -1), new Offset(-1, 0))));
        cards.add(new Card("ox", "Ox", PlayerColor.BLUE,
                List.of(new Offset(0, 1), new Offset(0, -1), new Offset(1, 0))));
        cards.add(new Card("crane", "Crane", PlayerColor.RED,
                List.of(new Offset(0, 1), new Offset(-1, -1), new Offset(1, -1))));
        cards.add(new Card("boar", "Boar", PlayerColor.BLUE,
                List.of(new Offset(0, 1), new Offset(-1, 0), new Offset(1, 0))));
        cards.add(new Card("eel", "Eel", PlayerColor.BLUE,
                List.of(new Offset(-1, 1), new Offset(-1, -1), new Offset(1, 0))));
        cards.add(new Card("cobra", "Cobra", PlayerColor.RED,
                List.of(new Offset(1, 1), new Offset(1, -1), new Offset(-1, 0))));
        return Collections.unmodifiableList(cards);
    }

    /** Returns all 16 cards of the game, in catalog order. */
    public static List<Card> catalog() {
        return CATALOG;
    }

    /**
     * Returns the card with the given id.
     *
     * @throws IllegalArgumentException if no card has this id
     */
    public static Card cardById(String id) {
        Card card = BY_ID.get(id);
        if (card == null) {
            throw new IllegalArgumentException("unknown card: " + id);
        }
        return card;
    }
}
