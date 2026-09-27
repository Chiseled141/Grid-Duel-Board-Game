package onitama.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UT05 — dealing: five distinct cards split 2/2/1, all taken from the catalog,
 * and the stamp color of the transit card decides who moves first.
 */
class CardDeckTest {

    @Test
    void dealReturnsFiveDistinctCatalogCards() {
        CardDeck.Deal deal = CardDeck.deal(new java.util.Random(7));

        List<Card> dealt = new ArrayList<>(deal.blueHand());
        dealt.addAll(deal.redHand());
        dealt.add(deal.transit());

        assertEquals(5, dealt.size());
        assertEquals(5, new HashSet<>(dealt).size(), "all five dealt cards are distinct");
        assertEquals(2, deal.blueHand().size());
        assertEquals(2, deal.redHand().size());
        assertTrue(CardDeck.catalog().containsAll(dealt), "dealt cards come from the catalog");
    }

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
    void transitStampPicksFirstPlayer(int seed) {
        CardDeck.Deal deal = CardDeck.deal(new java.util.Random(seed));
        assertEquals(deal.transit().stamp(), deal.firstPlayer(),
                "first player must match the transit card's stamp color");
    }

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
