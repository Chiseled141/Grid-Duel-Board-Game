package deckduel.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import deckduel.cards.CatalogLoader;
import deckduel.core.card.*;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;

class CardDetailsTest {
    private final Catalog catalog = CatalogLoader.loadBundled();

    private static String text(CardDetails details, String id) { return ((Label) details.lookup("#" + id)).getText(); }

    @Test void aSpyRevealShowsTheCardsExactTextAndClosesWithoutAMove() throws Exception {
        FxTesting.run(() -> {
            boolean[] closed = {false};
            var details = new CardDetails(() -> closed[0] = true);
            new Scene(details, 1280, 720);
            for (String key : new String[]{"dravorn-guardman", "reconnaissance"}) {
                var card = new CardInstance(10, catalog.card(key));
                details.showRevealed(card);
                assertTrue(details.isVisible());
                assertEquals(card.def().name(), text(details, "cardDetailName"));
                assertEquals(card.def().text(), text(details, "cardDetailBuff"));
                assertTrue(text(details, "cardDetailContribution").contains("remains in the opponent's hand"));
            }
            details.close();
            assertFalse(details.isVisible());
            assertTrue(closed[0], "closing hands focus back to the table");
        });
    }
}
