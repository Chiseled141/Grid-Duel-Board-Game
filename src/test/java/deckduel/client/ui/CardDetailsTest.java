package deckduel.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import deckduel.cards.CatalogLoader;
import deckduel.core.card.*;
import java.util.List;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;

class CardDetailsTest {
    private final Catalog catalog = CatalogLoader.loadBundled();

    private CardInstance card(long id, String key) { return new CardInstance(id, catalog.card(key)); }

    private static String text(CardDetails details, String id) { return ((Label) details.lookup("#" + id)).getText(); }

    @Test void buffsUseExactRulesTextAndShowTheirCurrentPublicContribution() throws Exception {
        FxTesting.run(() -> {
            var details = new CardDetails(() -> { });
            new Scene(details, 1280, 720);
            Row row = Row.EMPTY.place(0, card(1, "hu-battering-ram")).place(1, card(2, "hu-pikemen"));
            for (Object[] example : new Object[][]{{"embassy", 3}, {"armory", 1}, {"ruined-keep", 10}}) {
                var landmark = new LandmarkCard(card(10, (String) example[0]), false);
                var side = new SideView(row, List.of(landmark), Scoring.score(row, List.of(landmark)), 0, 0, 10, 36, 0, 0, true);
                details.show(landmark, side, true);
                assertEquals(landmark.def().text(), text(details, "cardDetailBuff"));
                assertEquals("Current row bonus: +" + example[1] + " points", text(details, "cardDetailContribution"));
                var disabled = new LandmarkCard(landmark.card(), true);
                var disabledSide = new SideView(row, List.of(disabled), Scoring.score(row, List.of(disabled)), 0, 0, 10, 36, 0, 0, true);
                details.show(disabled, disabledSide, true);
                assertEquals("Current row bonus: +0 points", text(details, "cardDetailContribution"));
                assertTrue(text(details, "cardDetailStatus").contains("SABOTAGED"));
            }
        });
    }

    @Test void fullWatchtowerNameAndInformationEffectRemainReadableWithoutInventingPoints() throws Exception {
        FxTesting.run(() -> {
            var details = new CardDetails(() -> { });
            new Scene(details, 1280, 720);
            for (String key : List.of("watchtower", "archive")) {
                var landmark = new LandmarkCard(card(10, key), false);
                var side = new SideView(Row.EMPTY, List.of(landmark), Scoring.score(Row.EMPTY, List.of(landmark)),
                        0, 0, 10, 36, 0, 0, true);
                details.show(landmark, side, false);
                assertEquals(landmark.def().name(), text(details, "cardDetailName"));
                assertEquals(landmark.def().text(), text(details, "cardDetailBuff"));
                assertFalse(text(details, "cardDetailContribution").contains("points"));
                assertFalse(details.getAccessibleText().contains("deckTop"), "opponent inspection contains no private deck state");
            }
        });
    }
}
