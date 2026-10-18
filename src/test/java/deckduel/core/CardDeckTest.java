package deckduel.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * UT05 — Movement card catalog verification: 16 cards, 8 stamps each, card lookup by ID.
 */
class CardDeckTest {

    @Test
    void catalogHasSixteenCardsWithEightStampsEach() {
        List<Card> catalog = CardDeck.catalog();
        assertEquals(16, catalog.size());
        long blueStamps = catalog.stream().filter(card -> card.stamp() == PlayerColor.BLUE).count();
        long redStamps = catalog.stream().filter(card -> card.stamp() == PlayerColor.RED).count();
        assertEquals(8, blueStamps);
        assertEquals(8, redStamps);
    }

    @Test
    void cardByIdRejectsUnknownId() {
        assertThrows(IllegalArgumentException.class, () -> CardDeck.cardById("unicorn"));
    }
}
