package deckduel.client.ui;

import deckduel.client.ClientSettings;
import deckduel.client.state.ClientModel;
import deckduel.client.state.HistoryFilter;
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
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Filtering must preserve the identity of the row used to request a personal replay. */
class HistoryFilteringTest {
    private record Ui(ClientModel model, HistoryPanel panel, List<Message> sent) { }
    private static HistoryEntry entry(String opponent, HistoryEntry.Result result, boolean practice, Long replayId) {
        return new HistoryEntry("2026-10-28T19:00:00", opponent, result, "SCORE", "cards-neutral-5",
                List.of(30L, 50L, 60L), List.of(40L, 45L, 35L), practice, replayId,
                replayId == null ? HistoryEntry.Replay.UNAVAILABLE : HistoryEntry.Replay.AVAILABLE);
    }
    private static final List<HistoryEntry> ENTRIES = List.of(
            entry("Alice", HistoryEntry.Result.WIN, false, 30L),
            entry("Bob", HistoryEntry.Result.LOSS, false, 20L),
            entry("Bot · Hard", HistoryEntry.Result.WIN, true, 10L),
            entry("Carol", HistoryEntry.Result.DRAW, false, null));

    private Ui open() {
        var sent = new ArrayList<Message>();
        var model = new ClientModel(new ServerConnection() {
            public void send(Message message) { sent.add(message); }
            public boolean isConnected() { return true; }
            public void ensureConnected(deckduel.client.startup.ServerRoute route) { }
        });
        model.handleMessage(new LoginResponse(true, null, new UserProfile("you", 5, 3)));
        model.setReducedMotion(true);
        var panel = new HistoryPanel(model, new ClientSettings(), model::leaveToLobby);
        model.requestHistory(); model.handleMessage(new HistoryResponse(ENTRIES)); sent.clear();
        return new Ui(model, panel, sent);
    }

    private static <T extends Component> T find(Container root, Class<T> type, String name) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child) && name.equals(child.getName())) return type.cast(child);
            if (child instanceof Container container) {
                T result = find(container, type, name); if (result != null) return result;
            }
        }
        return null;
    }

    @Test void filteredRowRequestsItsOwnReplayAndFilteringItOutClearsSelection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = open();
            JTable table = find(ui.panel, JTable.class, "historyTable");
            JTextField search = find(ui.panel, JTextField.class, "historySearch");
            JButton watch = find(ui.panel, JButton.class, "watchReplay");
            table.setRowSelectionInterval(1, 1);
            search.setText("BOB");
            assertEquals(1, table.getRowCount()); assertEquals("Bob", table.getValueAt(0, 1));
            assertEquals(0, table.getSelectedRow(), "same replay stays selected after filtering");
            assertTrue(watch.isEnabled()); assertTrue(ui.sent.isEmpty());
            watch.doClick(0); assertEquals(List.of(new ReplayRequest(20)), ui.sent);
            ui.sent.clear(); search.setText("Alice");
            assertEquals(-1, table.getSelectedRow()); assertFalse(watch.isEnabled());
            assertTrue(ui.sent.isEmpty());
        });
    }

    @Test void resultModeAndSearchCombineAndClearRestoresLoadedEntriesWithoutARequest() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = open();
            JTable table = find(ui.panel, JTable.class, "historyTable");
            JComboBox<?> result = find(ui.panel, JComboBox.class, "historyResultFilter");
            JComboBox<?> mode = find(ui.panel, JComboBox.class, "historyModeFilter");
            JTextField search = find(ui.panel, JTextField.class, "historySearch");
            result.setSelectedItem(HistoryFilter.Result.WIN); mode.setSelectedItem(HistoryFilter.Mode.PRACTICE);
            search.setText("hard");
            assertEquals(1, table.getRowCount()); assertEquals("Bot · Hard", table.getValueAt(0, 1));
            result.setSelectedItem(HistoryFilter.Result.LOSS);
            assertEquals(0, table.getRowCount());
            assertEquals("No matches found", find(ui.panel, JLabel.class, "historyEmptyTitle").getText());
            assertFalse(find(ui.panel, JButton.class, "watchReplay").isEnabled());
            JButton clear = find(ui.panel, JButton.class, "historyClearFilters");
            assertTrue(clear.isEnabled()); clear.doClick(0);
            assertEquals(4, table.getRowCount()); assertFalse(clear.isEnabled());
            assertEquals("", search.getText());
            assertEquals(HistoryFilter.Result.ALL, result.getSelectedItem());
            assertEquals(HistoryFilter.Mode.ALL, mode.getSelectedItem());
            assertTrue(ui.sent.isEmpty());
        });
    }

    @Test void refreshedHistoryKeepsTheFilterAndReplayIdentityWhenRowsMove() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = open();
            JTextField search = find(ui.panel, JTextField.class, "historySearch"); search.setText("bo");
            JTable table = find(ui.panel, JTable.class, "historyTable"); table.setRowSelectionInterval(0, 0);
            assertEquals("Bob", table.getValueAt(0, 1));
            var newest = entry("Boris", HistoryEntry.Result.WIN, false, 40L);
            ui.model.handleMessage(new HistoryResponse(List.of(newest, ENTRIES.get(2), ENTRIES.get(1))));
            assertEquals("bo", search.getText()); assertEquals(3, table.getRowCount());
            assertEquals(2, table.getSelectedRow());
            find(ui.panel, JButton.class, "watchReplay").doClick(0);
            assertEquals(List.of(new ReplayRequest(20)), ui.sent);
            ui.sent.clear(); ui.model.handleMessage(new HistoryResponse(List.of(newest)));
            assertEquals(-1, table.getSelectedRow());
            assertFalse(find(ui.panel, JButton.class, "watchReplay").isEnabled());
        });
    }

    @Test void noRecordingAndLegacyRowsKeepTheirExistingAvailabilityAfterFiltering() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = open();
            find(ui.panel, JTextField.class, "historySearch").setText("carol");
            JTable table = find(ui.panel, JTable.class, "historyTable");
            assertEquals(1, table.getRowCount()); table.setRowSelectionInterval(0, 0);
            assertEquals("Unavailable", table.getValueAt(0, 5));
            assertFalse(find(ui.panel, JButton.class, "watchReplay").isEnabled());
            ui.model.handleMessage(new HistoryResponse(List.of()));
            find(ui.panel, JButton.class, "historyClearFilters").doClick(0);
            assertEquals("No recorded matches yet", find(ui.panel, JLabel.class, "historyEmptyTitle").getText());
            assertTrue(ui.sent.isEmpty());
        });
    }

    @Test void searchTreatsOpponentTextLiterallyAndControlsFitSupportedSizes() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = open();
            var literal = entry("<html>Bob</html>", HistoryEntry.Result.WIN, false, 50L);
            ui.model.handleMessage(new HistoryResponse(List.of(literal)));
            find(ui.panel, JTextField.class, "historySearch").setText("<html>");
            assertEquals(1, find(ui.panel, JTable.class, "historyTable").getRowCount());
            for (Dimension size : List.of(new Dimension(1280, 660), new Dimension(1960, 1080))) {
                ui.panel.setSize(size); layout(ui.panel);
                var filter = find(ui.panel, HistoryFiltersPanel.class, "historyFilters");
                for (Component child : filter.getComponents()) {
                    assertTrue(child.getX() >= 0 && child.getY() >= 0);
                    assertTrue(child.getX() + child.getWidth() <= filter.getWidth());
                    assertTrue(child.getY() + child.getHeight() <= filter.getHeight());
                }
                capture(ui.panel, "history-filter-" + size.width);
            }
        });
    }

    private static void layout(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents()) if (child instanceof Container container) layout(container);
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
