package onitama.client.ui;

import onitama.core.CardDeck;
import onitama.core.PlayerColor;
import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.atomic.*;
import javax.swing.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real mouse gestures and deterministic timers exercise the card-drawing state machine. */
class CardPlayOverlayTest {
    private record Ui(JPanel scene, CardPanel card, CardPlayOverlay overlay, AtomicLong clock,
                      AtomicBoolean allowed, AtomicInteger selections, PlayedCardArea area) { }
    private Ui ui() {
        var clock = new AtomicLong(); var allowed = new AtomicBoolean(true); var calls = new AtomicInteger();
        var card = new CardPanel(CardDeck.cardById("tiger"), PlayerColor.BLUE, false, null);
        var scene = new JPanel(null); scene.setOpaque(false);
        card.setBounds(150, 300, 180, 230); scene.add(card);
        var area = new PlayedCardArea("YOU PLAYED"); area.setBounds(10, 50, 120, 200); scene.add(area);
        var overlay = new CardPlayOverlay(() -> { }, clock::get);
        var wrapper = DeckDrawOverlay.wrap(scene, overlay); wrapper.setSize(700, 600); wrapper.doLayout();
        overlay.bind(card, "move:tiger", allowed::get, () -> { calls.incrementAndGet(); card.setSelected(true); });
        return new Ui(scene, card, overlay, clock, allowed, calls, area);
    }
    private void event(Ui ui, int type, int y, int button) {
        ui.card.dispatchEvent(new MouseEvent(ui.card, type, 1, button == MouseEvent.BUTTON1
                ? MouseEvent.BUTTON1_DOWN_MASK : 0, 50, y, 1, false, button));
    }
    private void drag(Ui ui, int amount) {
        event(ui, MouseEvent.MOUSE_PRESSED, 100, MouseEvent.BUTTON1);
        event(ui, MouseEvent.MOUSE_DRAGGED, 100 - amount, MouseEvent.NOBUTTON);
        event(ui, MouseEvent.MOUSE_RELEASED, 100 - amount, MouseEvent.BUTTON1);
    }
    @Test void clickAndInsufficientDragNeverSelectAndReturnUnmasksCard() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = ui();
            try {
                drag(ui, 0); assertEquals(0, ui.selections.get());
                assertEquals(CardPlayOverlay.State.RETURNING, ui.overlay.state());
                ui.clock.set(250_000_000); ui.overlay.tick();
                assertFalse(CardPlayOverlay.isMasked(ui.card));
                drag(ui, CardPlayOverlay.THRESHOLD - 1);
                assertEquals(0, ui.selections.get()); assertFalse(ui.card.isSelected());
                ui.clock.addAndGet(250_000_000); ui.overlay.tick();
                assertEquals(CardPlayOverlay.State.IDLE, ui.overlay.state());
            } finally { ui.overlay.cancel(); }
        });
    }
    @Test void upwardDragSelectsOnlyOnReleaseAndStaysRaisedThroughRefresh() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = ui();
            try {
                event(ui, MouseEvent.MOUSE_PRESSED, 100, MouseEvent.BUTTON1);
                event(ui, MouseEvent.MOUSE_DRAGGED, 45, MouseEvent.NOBUTTON);
                assertEquals(0, ui.selections.get());
                assertEquals(CardPlayOverlay.State.DRAGGING, ui.overlay.state());
                event(ui, MouseEvent.MOUSE_RELEASED, 45, MouseEvent.BUTTON1);
                assertEquals(1, ui.selections.get()); assertTrue(ui.card.isSelected());
                ui.overlay.syncSelection(List.of(ui.card), false);
                assertEquals(CardPlayOverlay.State.CARD_SELECTED, ui.overlay.state());
                assertTrue(CardPlayOverlay.isMasked(ui.card));
                ui.overlay.syncSelection(List.of(ui.card), true);
                assertEquals(CardPlayOverlay.State.CARD_SELECTED, ui.overlay.state());
            } finally { ui.overlay.cancel(); }
        });
    }
    @Test void cancelingTargetingReturnsRaisedCardWithoutAnotherAction() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = ui();
            try {
                drag(ui, 60); ui.card.setSelected(false); ui.overlay.syncSelection(List.of(ui.card), false);
                assertEquals(CardPlayOverlay.State.RETURNING, ui.overlay.state());
                ui.clock.set(250_000_000); ui.overlay.tick();
                assertEquals(1, ui.selections.get()); assertFalse(CardPlayOverlay.isMasked(ui.card));
            } finally { ui.overlay.cancel(); }
        });
    }
    @Test void wrongButtonWrongPhaseAndDownwardDragDoNotSelect() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = ui();
            try {
                event(ui, MouseEvent.MOUSE_PRESSED, 100, MouseEvent.BUTTON3);
                event(ui, MouseEvent.MOUSE_RELEASED, 0, MouseEvent.BUTTON3);
                ui.allowed.set(false); drag(ui, 80); assertEquals(0, ui.selections.get());
                ui.allowed.set(true); drag(ui, -80); assertEquals(0, ui.selections.get());
            } finally { ui.overlay.cancel(); }
        });
    }
    @Test void acceptedPlayBlocksConflictsAndFinishesAtTheSideArea() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = ui();
            try {
                drag(ui, 60);
                var face = new BufferedImage(180, 230, BufferedImage.TYPE_INT_ARGB);
                ui.overlay.play("move:tiger", face, null, new Rectangle(150, 300, 180, 230), ui.area);
                assertTrue(ui.overlay.busy()); assertTrue(ui.overlay.contains(400, 400));
                ui.card.setSelected(false); drag(ui, 60); assertEquals(1, ui.selections.get());
                ui.clock.set(600_000_000); ui.overlay.tick();
                assertFalse(ui.overlay.busy()); assertFalse(ui.overlay.contains(400, 400));
                assertFalse(CardPlayOverlay.isMasked(ui.card));
            } finally { ui.overlay.cancel(); }
        });
    }
    @Test void reducedMotionAndLifecycleClearMasksAndBlockers() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = ui();
            try {
                drag(ui, 60); ui.overlay.returnSelection(); ui.overlay.setReducedMotion(true);
                assertFalse(CardPlayOverlay.isMasked(ui.card));
                ui.overlay.play("move:tiger", new BufferedImage(20, 30, 2), null,
                        new Rectangle(10, 10, 20, 30), ui.area);
                assertFalse(ui.overlay.busy());
                ui.overlay.setReducedMotion(false); ui.card.setSelected(false); drag(ui, 60);
                ui.overlay.cancel(); assertFalse(CardPlayOverlay.isMasked(ui.card));
                assertEquals(CardPlayOverlay.State.IDLE, ui.overlay.state());
            } finally { ui.overlay.cancel(); }
        });
    }
}
