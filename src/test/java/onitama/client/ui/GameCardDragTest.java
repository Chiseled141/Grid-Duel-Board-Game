package onitama.client.ui;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import onitama.client.state.*;
import onitama.config.RulesConfigLoader;
import onitama.core.duel.*;
import onitama.net.*;
import onitama.client.ClientSettings;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the actual match hand binding and movement submission, not a replacement callback. */
class GameCardDragTest {
    private static void layout(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents()) if (child instanceof Container c) layout(c);
    }
    private static CardPanel movement(Container parent) {
        for (Component child : parent.getComponents()) {
            if (child instanceof CardHandPanel hand)
                for (Component card : hand.getComponents()) if (card instanceof CardPanel c) return c;
            if (child instanceof Container c) { CardPanel found = movement(c); if (found != null) return found; }
        }
        return null;
    }
    private static void gesture(CardPanel card, int pull) {
        card.dispatchEvent(new MouseEvent(card, MouseEvent.MOUSE_PRESSED, 1, MouseEvent.BUTTON1_DOWN_MASK,
                50, 100, 1, false, MouseEvent.BUTTON1));
        card.dispatchEvent(new MouseEvent(card, MouseEvent.MOUSE_DRAGGED, 2, MouseEvent.BUTTON1_DOWN_MASK,
                50, 100 - pull, 0, false, MouseEvent.NOBUTTON));
        card.dispatchEvent(new MouseEvent(card, MouseEvent.MOUSE_RELEASED, 3, 0,
                50, 100 - pull, 1, false, MouseEvent.BUTTON1));
    }
    @ParameterizedTest @CsvSource({"false,60", "true,0", "true,60"})
    void actualHandSelectionThenLegalPieceAndDestinationBeforeSending(boolean click, int pull) throws Exception {
        List<Message> sent = new ArrayList<>();
        ClientModel[] model = new ClientModel[1]; GamePanel[] panel = new GamePanel[1];
        var state = RulesEngine.newGame(RulesConfigLoader.load(null), 42);
        var view = RulesEngine.viewFor(state, state.turn());
        SwingUtilities.invokeAndWait(() -> {
            model[0] = new ClientModel(new ServerConnection() {
                public void send(Message message) { sent.add(message); }
                public boolean isConnected() { return true; }
                public void ensureConnected(String host, int port) { }
            });
            var settings = new ClientSettings(); settings.setClickToSelectCards(click);
            panel[0] = new GamePanel(model[0], settings);
            model[0].handleMatchSync(new MatchSync("drag", "ROOM", view, false, 1));
            panel[0].setSize(1440, 900); layout(panel[0]);
        });
        // Drain the resize event before interacting, as a real visible window does.
        SwingUtilities.invokeAndWait(() -> {
            try {
                var card = movement(panel[0]); assertNotNull(card);
                if (!click) { gesture(card, 0); assertNull(model[0].selectedCardId()); assertTrue(sent.isEmpty()); }
                gesture(card, pull); assertEquals(card.card().id(), model[0].selectedCardId());
                assertTrue(CardPlayOverlay.isMasked(card)); assertTrue(sent.isEmpty());
                if (click) {
                    model[0].setReducedMotion(true);
                    CardPanel other = java.util.Arrays.stream(card.getParent().getComponents())
                            .filter(c -> c instanceof CardPanel && c != card).map(c -> (CardPanel) c)
                            .findFirst().orElseThrow();
                    gesture(other, 10); // Too far for a click, too short for an upward draw.
                    assertEquals(card.card().id(), model[0].selectedCardId());
                    assertTrue(CardPlayOverlay.isMasked(card), "canceled replacement restores raised selection immediately");
                    assertFalse(CardPlayOverlay.isMasked(other));
                }
                var move = Targeting.legalMoves(view).stream()
                        .filter(m -> m.movementCardId().equals(card.card().id())).findFirst().orElseThrow();
                var from = view.pieces().stream().filter(p -> p.piece().id() == move.pieceId()).findFirst().orElseThrow();
                model[0].boardClicked(from.square());
                assertTrue(CardPlayOverlay.isMasked(card)); assertTrue(sent.isEmpty());
                model[0].boardClicked(move.to());
                assertEquals(1, sent.stream().filter(m -> m instanceof ActionRequest).count());
                assertEquals(view, model[0].view(), "submission cannot consume cards before acceptance");
            } finally { panel[0].removeNotify(); }
        });
    }
}
