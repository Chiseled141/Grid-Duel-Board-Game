package deckduel.client.ui;

import deckduel.cards.CatalogLoader;
import deckduel.core.card.*;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class KingVisualsTest {
    @ParameterizedTest @EnumSource(King.class)
    void eachKingHasMatchingHoverAuraPersistentAuraAndScoreWithoutSlotNames(King king) throws Exception {
        var deck = DeckRules.deck(CatalogLoader.loadBundled());
        MatchState opening = null;
        for (long seed = 0; opening == null; seed++) {
            var state = MatchState.start(deck, deck, seed);
            if (state.view(Seat.FIRST).kingOffer().contains(king)) opening = state;
        }
        PlayerView offered = opening.view(Seat.FIRST);
        var units = deck.stream().filter(card -> card.occupiesSpace()).limit(6).toList();
        Row row = Row.EMPTY;
        for (int i = 0; i < units.size(); i++) row = row.place(i, new CardInstance(i + 1, units.get(i)));
        var status = new KingStatus(List.of(king), 2, false);
        var side = new SideView(row, Scoring.score(row, status), 2, 0, 8, 35, 0, 0, status);
        PlayerView ready = new PlayerView(Seat.FIRST, 2, MatchPhase.IN_PROGRESS, 1, Seat.FIRST,
                side, side, List.of(), List.of(), List.of(), null, null, List.of(), List.of());
        FxTesting.run(() -> {
            List<ChooseKing> moves = new ArrayList<>();
            var controls = new KingTableControls(moves::add);
            var scene = new Scene(new Pane(controls)); scene.getStylesheets().add(FxKit.STYLESHEET);
            controls.position(920, 92, 310, 1280, 720, 90, 126);
            controls.update(offered, System.currentTimeMillis() + 30_000, false, false, true);
            controls.applyCss(); controls.layout();
            Button offer = (Button) controls.lookup("#offer-" + king.id());
            ImageView art = (ImageView) offer.getGraphic();
            offer.getOnMouseEntered().handle(null);
            assertSameHue(king, assertInstanceOf(DropShadow.class, art.getEffect()));
            offer.getOnMouseExited().handle(null); assertNull(art.getEffect());
            offer.fire();
            Button confirm = (Button) controls.lookup("#confirm-" + king.id());
            assertEquals("CONFIRM", confirm.getText()); assertFalse(confirm.isDisabled());
            assertTrue(moves.isEmpty());
            confirm.fire(); assertEquals(List.of(new ChooseKing(king)), moves);
            controls.update(ready, 0, false, false, true);
            controls.applyCss(); controls.layout();
            for (String prefix : List.of("your", "foe")) {
                Button slot = (Button) controls.lookup("#" + prefix + "King0");
                assertSameHue(king, assertInstanceOf(DropShadow.class, ((ImageView) slot.getGraphic()).getEffect()));
                Label points = (Label) controls.lookup("#" + prefix + "KingPoints0");
                assertEquals("+" + side.score().kings().get(0).points(), points.getText());
                assertEquals(FxKit.display(22), points.getFont());
                assertEquals(KingVisuals.color(king), points.getTextFill());
                assertTrue(slot.getParent().getParent().lookupAll(".label").stream()
                        .map(node -> ((Label) node).getText()).allMatch(text -> text.matches("\\+\\d+|")),
                        "occupied positions contain only points; empty positions contain no text");
            }
            controls.reset();
        });
    }

    private static void assertSameHue(King king, DropShadow aura) {
        assertEquals(KingVisuals.color(king).getHue(), aura.getColor().getHue(), .01);
        assertEquals(0, aura.getOffsetX()); assertEquals(0, aura.getOffsetY());
    }
}
