package deckduel.client.ui;

import deckduel.client.ClientSettings;
import deckduel.client.state.ClientFixtures;
import deckduel.client.state.ClientModel;
import deckduel.client.state.ReplayIndex;
import deckduel.client.state.ReplayPlayback;
import deckduel.client.state.Screen;
import deckduel.client.state.ServerConnection;
import deckduel.net.*;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real recipient-visible replay data through the Swing controls, with no network side effects. */
class ReplayReviewTest {
    private record Ui(ClientModel model, ReplayPanel panel, List<Message> sent) { }

    private Ui open(boolean complete) {
        var sent = new ArrayList<Message>();
        var model = new ClientModel(new ServerConnection() {
            public void send(Message message) { sent.add(message); }
            public boolean isConnected() { return true; }
            public void ensureConnected(deckduel.client.startup.ServerRoute route) { }
        });
        var panel = new ReplayPanel(model); panel.setSize(1280, 720);
        model.handleMessage(new LoginResponse(true, null, new UserProfile("alice", 0, 0)));
        model.setReducedMotion(true); model.requestHistory(); model.requestReplay(5);
        model.handleMessage(ClientFixtures.replay(complete));
        layout(panel); sent.clear();
        return new Ui(model, panel, sent);
    }

    private static <T extends Component> T named(Container parent, Class<T> type, String name) {
        for (var child : parent.getComponents()) {
            if (type.isInstance(child) && name.equals(child.getName())) return type.cast(child);
            if (child instanceof Container container) {
                T found = search(container, type, name);
                if (found != null) return found;
            }
        }
        throw new AssertionError("Missing component: " + name);
    }

    private static <T extends Component> T search(Container parent, Class<T> type, String name) {
        for (var child : parent.getComponents()) {
            if (type.isInstance(child) && name.equals(child.getName())) return type.cast(child);
            if (child instanceof Container container) {
                T found = search(container, type, name); if (found != null) return found;
            }
        }
        return null;
    }

    private static void layout(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents()) if (child instanceof Container container) layout(container);
    }

    private static Timer timer(ReplayPanel panel) {
        try {
            var field = ReplayPanel.class.getDeclaredField("stepper"); field.setAccessible(true);
            return (Timer) field.get(panel);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    @Test void scrubbingWhilePlayingPausesAndKeepsControlsAndPositionInSync() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = open(true);
            try {
                var playback = ui.model.replay();
                var slider = named(ui.panel, JSlider.class, "replaySeek");
                assertEquals(0, slider.getMinimum()); assertEquals(playback.length() - 1, slider.getMaximum());
                named(ui.panel, JButton.class, "replayPlay").doClick(0);
                assertTrue(timer(ui.panel).isRunning());
                slider.setValueIsAdjusting(true); slider.setValue(9); slider.setValue(10);
                assertEquals(10, playback.index()); assertFalse(playback.playing());
                assertFalse(timer(ui.panel).isRunning());
                slider.setValueIsAdjusting(false);
                assertEquals(10, playback.index());
                assertTrue(named(ui.panel, JLabel.class, "replayPosition").getText().contains("Action 10 of"));
                playback.next(); assertEquals(11, slider.getValue());
                assertTrue(ui.sent.isEmpty()); assertNull(ui.model.match());
            } finally { ui.panel.stop(); }
        });
    }

    @Test void boundaryButtonsAndRoundSelectorSeekWithoutRestartingPlayback() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = open(true);
            try {
                var playback = ui.model.replay();
                JButton first = named(ui.panel, JButton.class, "replayFirst");
                JButton last = named(ui.panel, JButton.class, "replayLast");
                assertFalse(first.isEnabled()); assertTrue(last.isEnabled());
                last.doClick(0); assertTrue(playback.atEnd()); assertFalse(last.isEnabled());
                assertFalse(named(ui.panel, JButton.class, "replayNextRound").isEnabled());
                first.doClick(0); assertEquals(0, playback.index());
                JComboBox<?> rounds = named(ui.panel, JComboBox.class, "replayRounds");
                rounds.setSelectedIndex(1); assertEquals(2, playback.frame().round());
                assertEquals(playback.navigation().rounds().get(1).index(), playback.index());
                playback.play(); named(ui.panel, JButton.class, "replayNextRound").doClick(0);
                assertEquals(3, playback.frame().round()); assertFalse(playback.playing());
                named(ui.panel, JButton.class, "replayPreviousRound").doClick(0);
                assertEquals(2, playback.frame().round());
                assertTrue(ui.sent.isEmpty());
            } finally { ui.panel.stop(); }
        });
    }

    @Test void newKeyboardBindingsStayScopedToReplayAndPauseOnSeek() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = open(true);
            try {
                var playback = ui.model.replay();
                ui.panel.getActionMap().get("replay.nextRound").actionPerformed(null);
                assertEquals(2, playback.frame().round());
                ui.panel.getActionMap().get("replay.last").actionPerformed(null); assertTrue(playback.atEnd());
                ui.panel.getActionMap().get("replay.first").actionPerformed(null); assertEquals(0, playback.index());
                ReplayPanelTest.assertBackOnly(ui.panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW));
                ui.model.closeReplay();
                int saved = playback.index();
                ui.panel.getActionMap().get("replay.last").actionPerformed(null);
                assertEquals(saved, playback.index()); assertFalse(timer(ui.panel).isRunning());
                assertTrue(ui.sent.isEmpty());
            } finally { ui.panel.stop(); }
        });
    }

    @Test void journalFiltersUseOriginalFrameIndicesAndFilteredOutPositionsDoNotSeek() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = open(true);
            try {
                var playback = ui.model.replay();
                named(ui.panel, JButton.class, "replayDetailsToggle").doClick(0);
                var list = named(ui.panel, JList.class, "replayJournal");
                JComboBox<?> filter = named(ui.panel, JComboBox.class, "replayJournalFilter");
                filter.setSelectedItem("Spells");
                assertTrue(list.getModel().getSize() > 0);
                var entry = (ReplayIndex.Entry) list.getModel().getElementAt(0);
                assertTrue(entry.kinds().contains(ReplayIndex.Kind.SPELL));
                list.setSelectedIndex(0);
                assertEquals(entry.index(), playback.index()); assertTrue(playback.index() > 0);
                playback.seek(0);
                assertEquals(-1, list.getSelectedIndex(), "opening is absent from a Spell-only journal");
                int saved = playback.index(); filter.setSelectedItem("Kings");
                assertEquals(saved, playback.index(), "changing a filter cannot move playback");
                filter.setSelectedItem("Recording gaps");
                assertEquals(0, list.getModel().getSize());
                assertTrue(named(ui.panel, JLabel.class, "replayJournalCount").getText().contains("No matching actions"));
                assertTrue(ui.sent.isEmpty());
            } finally { ui.panel.stop(); }
        });
    }

    @Test void scoreDrawerUpdatesOnSeekAndLabelsResetsAndBanking() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = open(true);
            try {
                var playback = ui.model.replay();
                JTextArea score = named(ui.panel, JTextArea.class, "replayScoreChanges");
                assertTrue(score.getText().contains("no previous recorded action"));
                playback.seekRound(2);
                assertTrue(score.getText().contains("Round 1 -> 2"));
                assertTrue(score.getText().contains("Banked total:"));
                assertTrue(score.getText().contains("Multiplier effect:"));
                assertTrue(score.getText().contains("Seven-nation and King points are added after"));
                assertEquals(0, score.getCaretPosition());
                assertFalse(score.isEditable()); assertTrue(ui.sent.isEmpty());
            } finally { ui.panel.stop(); }
        });
    }

    @Test void journalSearchCombinesWithMarkersAndDoesNotChangeThePlaybackPosition() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = open(true);
            try {
                var playback = ui.model.replay();
                JTextField search = named(ui.panel, JTextField.class, "replayJournalSearch");
                JCheckBox keyOnly = named(ui.panel, JCheckBox.class, "replayKeyMoments");
                JComboBox<?> filter = named(ui.panel, JComboBox.class, "replayJournalFilter");
                JList<?> journal = named(ui.panel, JList.class, "replayJournal");
                search.setText("BoB"); keyOnly.doClick(0); filter.setSelectedItem("Spells");
                assertEquals(0, playback.index()); assertTrue(journal.getModel().getSize() > 0);
                for (int i = 0; i < journal.getModel().getSize(); i++) {
                    var row = (ReplayIndex.Entry) journal.getModel().getElementAt(i);
                    assertTrue(row.text().toLowerCase(java.util.Locale.ROOT).contains("bob"));
                    assertTrue(row.kinds().contains(ReplayIndex.Kind.SPELL)); assertTrue(row.keyMoment());
                }
                journal.setSelectedIndex(journal.getModel().getSize() - 1);
                int position = playback.index();
                search.setText("<html>unknown</html>");
                assertEquals(0, journal.getModel().getSize()); assertEquals(position, playback.index());
                search.setText(""); assertTrue(journal.getModel().getSize() > 0);
                assertTrue(ui.sent.isEmpty());
            } finally { ui.panel.stop(); }
        });
    }

    @Test void reviewLayoutsAndJournalRenderAtSupportedSizes() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = open(false);
            try {
                ui.model.replay().seekRound(2);
                for (Dimension size : List.of(new Dimension(1280, 720), new Dimension(1960, 1080))) {
                    ui.panel.setSize(size); layout(ui.panel);
                    var timeline = named(ui.panel, ReplayTimeline.class, "replayTimeline");
                    var toggle = named(ui.panel, JButton.class, "replayDetailsToggle");
                    var drawer = named(ui.panel, ReplayDetailsPanel.class, "replayDetails");
                    assertTrue(drawer.getY() + drawer.getHeight() <= timeline.getY());
                    assertTrue(toggle.getY() >= timeline.getY() + timeline.getHeight());
                    toggle.doClick(0); layout(ui.panel);
                    assertTrue(drawer.isVisible());
                    for (int tab = 0; tab < 2; tab++) {
                        named(ui.panel, JTabbedPane.class, "replayDetailTabs").setSelectedIndex(tab);
                        layout(ui.panel); capture(ui.panel, "review-" + size.width + "-tab" + tab);
                    }
                    toggle.doClick(0); assertFalse(drawer.isVisible());
                    assertTrue(named(ui.panel, JLabel.class, "replaySubtitle").getText().contains("Incomplete"));
                }
            } finally { ui.panel.stop(); }
        });
    }

    private static void capture(JPanel panel, String name) {
        try {
            var image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics(); panel.paint(graphics); graphics.dispose();
            Path file = Path.of("target", "replay-qa", name + ".png");
            Files.createDirectories(file.getParent()); ImageIO.write(image, "png", file.toFile());
        } catch (Exception failure) { throw new AssertionError(failure); }
    }
}
