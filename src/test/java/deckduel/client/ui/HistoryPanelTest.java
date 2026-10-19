package deckduel.client.ui;

import deckduel.client.ClientSettings;
import deckduel.client.state.*;
import deckduel.net.*;
import java.awt.*;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** History data, safe rendering and menu scene lifecycle share the same live model. */
class HistoryPanelTest {
    private <T> T find(Container parent, Class<T> type) {
        for (var child : parent.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container container) {
                var found = find(container, type); if (found != null) return found;
            }
        }
        return null;
    }
    private JButton backButton(Container parent) {
        for (var child : parent.getComponents()) {
            if (child instanceof JButton button && "historyBack".equals(button.getName())) return button;
            if (child instanceof Container container) {
                var found = backButton(container); if (found != null) return found;
            }
        }
        return null;
    }
    private void layout(Container parent) {
        parent.doLayout();
        for (var child : parent.getComponents()) if (child instanceof Container container) layout(container);
    }

    @Test void sharedMenuSceneShowsHistorySafelyAndReturnsWithoutLosingData() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var sent = new ArrayList<Message>();
            var model = new ClientModel(new ServerConnection() {
                public void send(Message message) { sent.add(message); }
                public boolean isConnected() { return true; }
                public void ensureConnected(String host, int port) { }
            });
            model.handleMessage(new LoginResponse(true, null, new UserProfile("alice", 4, 2))); sent.clear();
            var back = new AtomicInteger();
            var panel = new HistoryPanel(model, new ClientSettings(), back::incrementAndGet);
            assertTrue(panel instanceof BattlefieldMenuPanel);
            var frame = new JFrame("History regression");
            try {
                frame.setContentPane(panel); frame.setSize(1280, 720); frame.setVisible(true); frame.validate();
                model.requestHistory();
                assertEquals(Screen.HISTORY, model.screen());
                assertEquals(List.of(new HistoryRequest()), sent);
                assertTrue(panel.animationEnabled());
                model.setReducedMotion(true); assertFalse(panel.animationEnabled());
                model.setReducedMotion(false);
                var table = find(panel, JTable.class);
                assertEquals(0, table.getRowCount()); assertFalse(table.isShowing());
                assertEquals(5, table.getColumnCount(), "history has no rating column");
                var win = new HistoryEntry("2026-10-19T10:31:14.5", "<html>bob</html>", HistoryEntry.Result.WIN, "SCORE",
                        "cards-proposal-1", List.of(36L, 40L, 30L), List.of(18L, 32L, 48L));
                var legacy = new HistoryEntry("2026-09-01T12:00:00", "carol", HistoryEntry.Result.LOSS, "FORFEIT",
                        "legacy-deckduel-v1", List.of(), List.of());
                model.handleMessage(new HistoryResponse(List.of(win, legacy)));
                assertEquals(2, table.getRowCount()); assertTrue(table.isShowing());
                assertEquals("2026-10-19 10:31", table.getValueAt(0, 0));
                assertEquals("106–98", table.getValueAt(0, 3));
                assertEquals("36–18 · 40–32 · 30–48", table.getValueAt(0, 4));
                assertEquals("Loss (forfeit)", table.getValueAt(1, 2));
                assertEquals("—", table.getValueAt(1, 3), "legacy rows have no card scores");
                assertFalse(table.isCellEditable(0, 1));
                var name = (JLabel) table.getCellRenderer(0, 1).getTableCellRendererComponent(table, win.opponent(), false, false, 0, 1);
                assertEquals(Boolean.TRUE, name.getClientProperty("html.disable"));
                assertNull(name.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey));
                for (var size : List.of(new Dimension(1280, 660), new Dimension(1960, 1180))) {
                    panel.setSize(size); layout(panel);
                    var sheet = panel.getComponent(0);
                    assertTrue(sheet.getX() >= 0 && sheet.getY() >= 0);
                    assertTrue(sheet.getX() + sheet.getWidth() <= size.width);
                    assertTrue(sheet.getY() + sheet.getHeight() <= size.height);
                }
                backButton(panel).doClick(); assertEquals(1, back.get());
                model.leaveToLobby(); assertFalse(panel.animationEnabled());
                model.handleMessage(new HistoryResponse(List.of()));
                assertEquals(0, table.getRowCount());
            } finally { frame.dispose(); }
        });
    }
}
