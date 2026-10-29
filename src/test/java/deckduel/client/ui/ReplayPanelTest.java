package deckduel.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import deckduel.client.state.ClientModel;
import deckduel.client.state.ClientFixtures;
import deckduel.client.state.Screen;
import deckduel.client.state.ServerConnection;
import deckduel.net.*;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.junit.jupiter.api.Test;

/** The replay screen: its controls move playback only, never the network or a live match. */
class ReplayPanelTest {
    private record Ui(ClientModel model, ReplayPanel panel, List<Message> sent) { }

    private static Ui open(boolean complete) {
        List<Message> sent = new ArrayList<>();
        var model = new ClientModel(new ServerConnection() {
            public void send(Message message) { sent.add(message); }
            public boolean isConnected() { return true; }
            public void ensureConnected(deckduel.client.startup.ServerRoute route) { }
        });
        var panel = new ReplayPanel(model);
        panel.setSize(1280, 720);
        panel.doLayout();
        model.handleMessage(new LoginResponse(true, null, new UserProfile("alice", 0, 0)));
        model.requestHistory();
        model.requestReplay(5);
        model.handleMessage(ClientFixtures.replay(complete));
        sent.clear();
        return new Ui(model, panel, sent);
    }

    private static <T extends Component> T named(Container parent, Class<T> type, String name) {
        T found = search(parent, type, name);
        if (found == null) throw new AssertionError("no " + name);
        return found;
    }

    private static <T extends Component> T search(Container parent, Class<T> type, String name) {
        for (Component child : parent.getComponents()) {
            if (type.isInstance(child) && name.equals(child.getName())) return type.cast(child);
            if (child instanceof Container c) { T found = search(c, type, name); if (found != null) return found; }
        }
        return null;
    }

    private static Timer timer(ReplayPanel panel, String field) throws ReflectiveOperationException {
        var declared = ReplayPanel.class.getDeclaredField(field);
        declared.setAccessible(true);
        return (Timer) declared.get(panel);
    }

    @Test void controlsStepPlayAndChangeSpeedWithoutSendingAnything() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = open(true);
            try {
                assertEquals(Screen.REPLAY, ui.model.screen());
                var playback = ui.model.replay();
                JButton previous = named(ui.panel, JButton.class, "replayPrevious");
                JButton next = named(ui.panel, JButton.class, "replayNext");
                JButton play = named(ui.panel, JButton.class, "replayPlay");
                JButton speed = named(ui.panel, JButton.class, "replaySpeed");
                JLabel position = named(ui.panel, JLabel.class, "replayPosition");
                assertEquals("Replay · vs bob", named(ui.panel, JLabel.class, "replayTitle").getText());
                assertFalse(previous.isEnabled());
                assertTrue(position.getText().startsWith("Round 1 · Action 0 of"));
                assertEquals("Opening deal", named(ui.panel, JLabel.class, "replayAction").getText());
                next.doClick(); next.doClick();
                assertEquals(2, playback.index());
                assertTrue(position.getText().contains("Action 2 of"));
                previous.doClick();
                assertEquals(1, playback.index());
                play.doClick();
                assertTrue(playback.playing());
                assertTrue(play.getText().equalsIgnoreCase("Pause"));
                assertTrue(timer(ui.panel, "stepper").isRunning());
                speed.doClick();
                assertEquals(2.0, playback.speed());
                assertEquals(playback.stepMillis(), timer(ui.panel, "stepper").getDelay());
                assertTrue(speed.getText().equalsIgnoreCase("Speed 2×"));
                play.doClick();
                assertFalse(playback.playing());
                assertFalse(timer(ui.panel, "stepper").isRunning());
                // Keys work on the replay page only; just Back (Escape/Backspace) answers wherever focus is.
                assertBackOnly(ui.panel.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW));
                int before = playback.index();
                ui.panel.getActionMap().get("replay.next").actionPerformed(null);
                assertEquals(before + 1, playback.index());
                ui.panel.getActionMap().get("replay.play").actionPerformed(null);
                assertTrue(playback.playing());
                ui.panel.getActionMap().get("replay.play").actionPerformed(null);
                assertFalse(playback.playing());
                // Every frame paints, from the opening deal to the result.
                var image = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_ARGB);
                while (!playback.atEnd()) {
                    var g = image.createGraphics(); ui.panel.paint(g); g.dispose();
                    next.doClick();
                }
                assertFalse(next.isEnabled());
                assertNull(ui.model.match(), "no live match was created or touched");
                assertTrue(ui.sent.isEmpty(), "replay controls never send a request");

                named(ui.panel, JButton.class, "replayBack").doClick();
                assertEquals(Screen.HISTORY, ui.model.screen());
                assertNull(ui.model.replay());
                assertFalse(timer(ui.panel, "stepper").isRunning());
                assertTrue(ui.sent.isEmpty());
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        });
    }

    @Test void reducedMotionKeepsTheHighlightStillAndIncompleteRecordingsAreLabelled() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = open(false);
            try {
                assertTrue(named(ui.panel, JLabel.class, "replaySubtitle").getText().contains("Incomplete recording"));
                named(ui.panel, JButton.class, "replayNext").doClick();
                assertTrue(timer(ui.panel, "glow").isRunning(), "a fading glow marks the new card");
                ui.model.setReducedMotion(true);
                named(ui.panel, JButton.class, "replayNext").doClick();
                assertFalse(timer(ui.panel, "glow").isRunning(), "no animation with reduced motion");
                // A live match that starts (a hosted room was joined) replaces the replay and stops playback.
                ui.model.replay().play();
                var update = ClientFixtures.update("M9", deckduel.core.card.Seat.FIRST);
                ui.model.handleMessage(update);
                assertEquals(Screen.TABLE, ui.model.screen());
                assertNull(ui.model.replay());
                assertFalse(timer(ui.panel, "stepper").isRunning());
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        });
    }

    static void assertBackOnly(javax.swing.InputMap windowWide) {
        assertEquals(2, windowWide.size());
        for (String key : java.util.List.of("ESCAPE", "BACK_SPACE"))
            assertEquals("menu.back", windowWide.get(javax.swing.KeyStroke.getKeyStroke(key)));
    }
}
