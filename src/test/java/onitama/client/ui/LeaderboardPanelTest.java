package onitama.client.ui;

import onitama.client.ClientSettings;
import onitama.client.state.*;
import onitama.net.*;
import java.awt.*;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Ranking data, safe rendering and menu scene lifecycle share the same live model. */
class LeaderboardPanelTest {
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
            if (child instanceof JButton button && "Back to menu".equals(button.getText())) return button;
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

    @Test void sharedMenuSceneShowsRankingsSafelyAndReturnsWithoutLosingData() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var sent = new ArrayList<Message>();
            var model = new ClientModel(new ServerConnection() {
                public void send(Message message) { sent.add(message); }
                public boolean isConnected() { return true; }
                public void ensureConnected(String host, int port) { }
            });
            var me = new UserProfile("<html>alice</html>", 1200, 4, 2);
            model.handleMessage(new LoginResponse(true, null, me, null)); sent.clear();
            var back = new AtomicInteger();
            var panel = new LeaderboardPanel(model, new ClientSettings(), back::incrementAndGet);
            assertTrue(panel instanceof BattlefieldMenuPanel);
            var frame = new JFrame("Leaderboard regression");
            try {
                frame.setContentPane(panel); frame.setSize(1280, 720); frame.setVisible(true); frame.validate();
                model.requestLeaderboard();
                assertEquals(Screen.LEADERBOARD, model.screen());
                assertEquals(List.of(new LeaderboardRequest()), sent);
                assertTrue(panel.animationEnabled());
                model.setReducedMotion(true); assertFalse(panel.animationEnabled());
                model.setReducedMotion(false);
                var table = find(panel, JTable.class);
                assertEquals(0, table.getRowCount()); assertFalse(table.isShowing());
                model.handleMessage(new LeaderboardResponse(List.of(new UserProfile("leader", 1400, 8, 0), me)));
                assertEquals(2, table.getRowCount()); assertTrue(table.isShowing());
                assertEquals(1, table.getValueAt(0, 0)); assertEquals(1400, table.getValueAt(0, 2));
                assertFalse(table.isCellEditable(0, 1));
                var renderer = table.getCellRenderer(1, 1);
                var own = (JLabel) renderer.getTableCellRendererComponent(table, me.username(), false, false, 1, 1);
                Color ownColor = own.getBackground();
                assertEquals(me.username(), own.getText());
                assertEquals(Boolean.TRUE, own.getClientProperty("html.disable"));
                assertNull(own.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey));
                var other = (JLabel) renderer.getTableCellRendererComponent(table, "leader", false, false, 0, 1);
                assertNotEquals(ownColor, other.getBackground());
                for (var size : List.of(new Dimension(1280, 660), new Dimension(1960, 1180))) {
                    panel.setSize(size); layout(panel);
                    var sheet = panel.getComponent(0);
                    assertTrue(sheet.getWidth() <= 760 && sheet.getHeight() <= 720);
                    assertTrue(sheet.getX() >= 0 && sheet.getY() >= 0);
                    assertTrue(sheet.getX() + sheet.getWidth() <= size.width);
                    assertTrue(sheet.getY() + sheet.getHeight() <= size.height);
                    assertTrue(table.getColumnModel().getColumn(1).getWidth() >= 180);
                }
                backButton(panel).doClick(); assertEquals(1, back.get());
                model.leaveToLobby(); assertFalse(panel.animationEnabled());
                model.handleMessage(new LeaderboardResponse(List.of()));
                assertEquals(0, table.getRowCount());
            } finally { frame.dispose(); }
        });
    }
}
