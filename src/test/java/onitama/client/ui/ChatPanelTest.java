package onitama.client.ui;

import onitama.client.state.ClientModel;
import onitama.client.state.ServerConnection;
import onitama.config.RulesConfigLoader;
import onitama.core.PlayerColor;
import onitama.core.duel.RulesEngine;
import onitama.net.*;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatPanelTest {
    private <T extends Component> T find(Container parent, Class<T> type) {
        for (Component child : parent.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container nested) {
                T found = find(nested, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test
    void enterSendsPlainTextAndNewMatchClearsComposerAndTranscript() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<Message> sent = new ArrayList<>();
            var model = new ClientModel(new ServerConnection() {
                public void send(Message message) { sent.add(message); }
                public boolean isConnected() { return true; }
                public void ensureConnected(String host, int port) { }
            });
            var panel = new ChatPanel(model);
            var input = find(panel, JTextField.class);
            var history = find(panel, JList.class);
            assertFalse(input.isEnabled());
            var view = RulesEngine.project(RulesEngine.newGame(RulesConfigLoader.load(null), 42), PlayerColor.BLUE);
            model.handleMatchSync(new MatchSync("one", "ROOM", view, false, 1));
            assertTrue(input.isEnabled());
            input.setText("<html>Hello 👋</html>");
            input.postActionEvent();
            assertEquals(List.of(new ChatRequest("one", "<html>Hello 👋</html>")), sent);
            assertEquals("", input.getText());
            assertEquals(0, history.getModel().getSize());
            model.handleMessage(new ChatMessage("one", "alice", "<html>Hello 👋</html>"));
            assertEquals(1, history.getModel().getSize());
            ChatMessage accepted = (ChatMessage) history.getModel().getElementAt(0);
            assertEquals("<html>Hello 👋</html>", accepted.text());
            @SuppressWarnings("unchecked")
            var renderer = ((JList<ChatMessage>) history).getCellRenderer();
            var bubble = (Container) renderer.getListCellRendererComponent(history, accepted, 0, false, false);
            assertEquals("alice: <html>Hello 👋</html>", bubble.getAccessibleContext().getAccessibleName());
            assertNull(((JComponent) bubble).getToolTipText(), "untrusted chat must not enter Swing HTML tooltips");
            // TextLayout paints literal characters; no HTML parser is involved.
            bubble.setSize(220, 80);
            var image = new java.awt.image.BufferedImage(220, 80, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var graphics = image.createGraphics();
            bubble.paint(graphics);
            graphics.dispose();
            var longMessage = new ChatMessage("one", "alice", "A long message for wrapping. ".repeat(9));
            history.setSize(140, 200);
            var narrow = renderer.getListCellRendererComponent(history, longMessage, 0, false, false).getPreferredSize();
            history.setSize(250, 200);
            var wide = renderer.getListCellRendererComponent(history, longMessage, 0, false, false).getPreferredSize();
            assertTrue(narrow.height > wide.height, "messages reflow rather than clip at narrow widths");
            input.setText("x".repeat(281));
            assertEquals("", input.getText());
            input.setText("draft");
            model.handleMatchSync(new MatchSync("two", "ROOM", view, false, 1));
            assertEquals("", input.getText());
            assertEquals(0, history.getModel().getSize());
            model.handleMatchSync(new MatchSync("two", "ROOM", view, true, 1));
            assertFalse(input.isEnabled());
        });
    }
}
