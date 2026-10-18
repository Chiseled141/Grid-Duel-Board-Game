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
    private static void gesture(JComponent card, int pull) {
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
                assertNull(movement(panel[0]), "movement cards are removed from the hand");
                assertNull(model[0].selectedCardId());
                var move = Targeting.legalMoves(view).get(0);
                var from = view.pieces().stream().filter(p -> p.piece().id() == move.pieceId()).findFirst().orElseThrow();
                model[0].boardClicked(from.square());
                assertTrue(sent.isEmpty());
                model[0].boardClicked(move.to());
                assertEquals(1, sent.stream().filter(m -> m instanceof ActionRequest).count());
                assertEquals(view, model[0].view(), "submission cannot consume cards before acceptance");
            } finally { panel[0].removeNotify(); }
        });
    }
    @ParameterizedTest @CsvSource({"true,false", "false,false", "true,true", "false,true"})
    void switchAndDeselectAtTheVisibleRaisedCard(boolean click, boolean skill) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var sent = new ArrayList<Message>();
            var model = new ClientModel(new ServerConnection() {
                public void send(Message message) { sent.add(message); }
                public boolean isConnected() { return true; }
                public void ensureConnected(String host, int port) { }
            });
            var settings = new ClientSettings(); settings.setClickToSelectCards(click);
            var panel = new GamePanel(model, settings);
            try {
                var game = RulesEngine.newGame(RulesConfigLoader.load(null), 42);
                game = ((ActionResult.Accepted) RulesEngine.apply(game, game.turn(),
                        new GameAction.SkipMovement())).nextState();
                var view = RulesEngine.viewFor(game, game.turn());
                model.handleMatchSync(new MatchSync("switch", "ROOM", view, false, 1));
                model.setReducedMotion(true);
                panel.setSize(1440, 900); layout(panel);
                var handField = GamePanel.class.getDeclaredField("hand"); handField.setAccessible(true);
                var actualHand = (CardHandPanel) handField.get(panel);
                var cards = java.util.Arrays.stream(actualHand.getComponents())
                        .filter(c -> c instanceof SkillCardPanel)
                        .map(c -> (JComponent) c).toList();
                JComponent first = cards.get(0), second = cards.get(1);
                gesture(first, click ? 0 : 60);
                assertTrue(CardPlayOverlay.isMasked(first));
                gesture(second, click ? 0 : 60);
                assertFalse(CardPlayOverlay.isMasked(first), "switching restores the prior card");
                assertTrue(CardPlayOverlay.isMasked(second));
                assertEquals(((SkillCardPanel) second).instance().instanceId(), model.selectedSkillInstanceId());
                var field = GamePanel.class.getDeclaredField("playOverlay"); field.setAccessible(true);
                var overlay = (CardPlayOverlay) field.get(panel);
                var slot = SwingUtilities.convertRectangle(second.getParent(), second.getBounds(), overlay);
                int x = slot.x + slot.width / 2, y = slot.y - 42;
                var target = SwingUtilities.getDeepestComponentAt(overlay.getParent(),
                        x + overlay.getX(), y + overlay.getY());
                assertSame(overlay, target, "the painted raised card must receive input");
                overlay.dispatchEvent(new MouseEvent(overlay, MouseEvent.MOUSE_PRESSED, 10,
                        MouseEvent.BUTTON1_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON1));
                overlay.dispatchEvent(new MouseEvent(overlay, MouseEvent.MOUSE_RELEASED, 11,
                        0, x, y, 1, false, MouseEvent.BUTTON1));
                assertNull(model.selectedCardId()); assertNull(model.selectedSkillInstanceId());
                assertFalse(CardPlayOverlay.isMasked(second));
                assertEquals(CardPlayOverlay.State.IDLE, overlay.state());
                assertTrue(sent.isEmpty(), "local selection must never submit a gameplay action");
                assertEquals(view, model.view());
            } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
            finally { panel.removeNotify(); }
        });
    }

}
