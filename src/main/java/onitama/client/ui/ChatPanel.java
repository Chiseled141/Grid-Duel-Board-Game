package onitama.client.ui;

import onitama.client.state.ClientModel;
import onitama.client.state.ClientModelListener;
import onitama.client.state.Screen;
import onitama.net.ChatMessage;
import onitama.net.ChatRequest;
import onitama.net.GameOver;

import java.awt.*;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;

/** Styled match conversation with plain-text bubbles and a bounded composer. */
public final class ChatPanel extends JPanel {
    private final ClientModel model;
    private final DefaultListModel<ChatMessage> messages = new DefaultListModel<>();
    private final JList<ChatMessage> history;
    private final JTextField input = new JTextField() {
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (getText().isEmpty() && !isFocusOwner()) {
                Graphics2D g = UiKit.nice(graphics);
                g.setColor(Theme.MUTED);
                g.setFont(getFont());
                g.drawString(isEnabled() ? "Say something…" : "Chat unavailable", 10,
                        (getHeight() - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent());
                g.dispose();
            }
        }
    };
    private final JButton send = UiKit.miniPill("Send", UiKit.Pill.GOLD);
    private final JPanel composer = new JPanel(new BorderLayout(5, 0));
    private final JLabel practiceNote = new JLabel("<html>Chat is available in<br>multiplayer matches.</html>");
    private JScrollPane scroll;
    private final JLabel status = UiKit.label("", 9f);
    private String matchId;
    private final javax.swing.Timer cooldown = new javax.swing.Timer(550, e -> refreshAvailability());

    /** Builds the conversation surface; all sends go through the EDT-owned model. */
    public ChatPanel(ClientModel model) {
        super(new BorderLayout(0, 6));
        this.model = model;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(13, 13, 14, 14));
        setMinimumSize(new Dimension(100, 100));
        JPanel header = new JPanel(new BorderLayout(4, 0));
        header.setOpaque(false);
        JLabel title = UiKit.label("Match chat", 13f);
        title.setFont(Theme.cardTitle(14f));
        title.setForeground(Theme.INK);
        header.add(title, BorderLayout.WEST);
        status.setFont(Theme.bold(9f));
        status.setHorizontalAlignment(SwingConstants.RIGHT);
        header.add(status, BorderLayout.EAST);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(176, 150, 104)),
                BorderFactory.createEmptyBorder(0, 0, 9, 0)));
        add(header, BorderLayout.NORTH);
        practiceNote.setFont(Theme.normal(11));
        practiceNote.setForeground(new Color(110, 88, 59));
        practiceNote.setVerticalAlignment(SwingConstants.TOP);
        history = new JList<>(messages) {
            @Override public void doLayout() {
                // Recompute wrapped row heights immediately, including offscreen rendering.
                if (getWidth() > 0 && getFixedCellWidth() != getWidth()) setFixedCellWidth(getWidth());
                super.doLayout();
            }

            @Override protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                if (!messages.isEmpty()) return;
                Graphics2D g = UiKit.nice(graphics);
                g.setFont(Theme.normal(11f));
                g.setColor(new Color(119, 97, 65));
                g.drawString("Your correspondence begins here.", 1, Math.min(22, Math.max(12, getHeight() / 2)));
                g.dispose();
            }
        };
        history.setName("matchChatHistory");
        history.setOpaque(false);
        history.setFocusable(false);
        history.setCellRenderer((list, message, index, selected, focus) -> new Bubble(message));
        scroll = new JScrollPane(history, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setName("chatScroll");
        scroll.setBorder(null);
        scroll.setMinimumSize(new Dimension(0, 0));
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        add(scroll, BorderLayout.CENTER);
        composer.setOpaque(false);
        input.setName("matchChatInput");
        input.setBackground(new Color(247, 238, 215));
        input.setForeground(Theme.INK);
        input.setCaretColor(Theme.INK);
        input.setDisabledTextColor(new Color(119, 97, 65));
        input.setFont(Theme.normal(12f));
        input.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.GRID_LINE),
                BorderFactory.createEmptyBorder(4, 9, 4, 9)));
        input.setToolTipText("Message your opponent · 280 characters · Enter to send");
        ((AbstractDocument) input.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override public void insertString(FilterBypass bypass, int offset, String text, AttributeSet attrs)
                    throws BadLocationException {
                replace(bypass, offset, 0, text, attrs);
            }
            @Override public void replace(FilterBypass bypass, int offset, int length, String text, AttributeSet attrs)
                    throws BadLocationException {
                String addition = text == null ? "" : text;
                if (bypass.getDocument().getLength() - length + addition.length() <= ChatRequest.MAX_LENGTH)
                    bypass.replace(offset, length, addition, attrs);
            }
        });
        input.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { refreshSend(); }
            @Override public void removeUpdate(DocumentEvent event) { refreshSend(); }
            @Override public void changedUpdate(DocumentEvent event) { refreshSend(); }
        });
        input.addActionListener(e -> sendMessage());
        send.addActionListener(e -> sendMessage());
        send.setFont(Theme.bold(11));
        send.setBorder(BorderFactory.createEmptyBorder(4, 8, 7, 8));
        send.setPreferredSize(new Dimension(59, 32));
        composer.add(input, BorderLayout.CENTER);
        composer.add(send, BorderLayout.EAST);
        add(composer, BorderLayout.SOUTH);
        cooldown.setRepeats(false);
        model.addListener(new ClientModelListener() {
            @Override public void onChatChanged() { refreshHistory(); }
            @Override public void onMatchChanged() { refreshAvailability(); }
            @Override public void onScreenChanged(Screen screen) { refreshAvailability(); }
            @Override public void onGameOver(GameOver over) { refreshAvailability(); }
            @Override public void onConnectionLost() { refreshAvailability(); }
        });
        refreshHistory();
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = UiKit.nice(graphics);
        MatchStyle.parchment(g, getWidth(), getHeight());
        g.dispose();
    }

    private void sendMessage() {
        if (cooldown.isRunning() || !model.sendChat(input.getText())) return;
        input.setText("");
        cooldown.restart();
        refreshAvailability();
        input.requestFocusInWindow();
    }

    private void refreshSend() {
        send.setEnabled(model.canChat() && !cooldown.isRunning() && ChatRequest.validText(input.getText()));
    }

    private void refreshHistory() {
        messages.clear();
        for (var message : model.chatMessages()) messages.addElement(message);
        refreshAvailability();
        if (!messages.isEmpty()) history.ensureIndexIsVisible(messages.size() - 1);
    }

    private void refreshAvailability() {
        String current = model.currentSync() == null ? null : model.currentSync().matchId();
        if (!java.util.Objects.equals(matchId, current)) {
            matchId = current;
            input.setText("");
            cooldown.stop();
        }
        boolean available = model.canChat();
        input.setEnabled(available);
        refreshSend();
        boolean practice = model.isPracticeMode();
        composer.setVisible(!practice);
        scroll.setVisible(!practice);
        if (practice && practiceNote.getParent() == null) {
            remove(scroll); add(practiceNote, BorderLayout.CENTER);
        } else if (!practice && scroll.getParent() == null) {
            remove(practiceNote); add(scroll, BorderLayout.CENTER);
        }
        status.setForeground(new Color(119, 88, 41));
        status.setText(model.isPracticeMode() ? "PRACTICE" : model.isMatchOver() ? "ENDED"
                : available ? "LIVE" : "OFFLINE");
        history.repaint();
    }

    private final class Bubble extends JPanel {
        private final ChatMessage message;
        private final boolean own;

        Bubble(ChatMessage message) {
            this.message = message;
            own = model.me() != null && message.sender().equals(model.me().username());
            setOpaque(false);
            getAccessibleContext().setAccessibleName(message.sender() + ": " + message.text());
        }

        private java.util.List<java.awt.font.TextLayout> lines(int width) {
            var text = new java.text.AttributedString(message.text());
            text.addAttribute(java.awt.font.TextAttribute.FONT, Theme.normal(12f));
            var measure = new java.awt.font.LineBreakMeasurer(text.getIterator(),
                    new java.awt.font.FontRenderContext(null, true, true));
            var result = new java.util.ArrayList<java.awt.font.TextLayout>();
            while (measure.getPosition() < message.text().length())
                result.add(measure.nextLayout(Math.max(20, width - 39)));
            return result;
        }

        @Override public Dimension getPreferredSize() {
            int width = Math.max(100, history.getWidth());
            float height = 34;
            for (var line : lines(width)) height += line.getAscent() + line.getDescent() + line.getLeading();
            return new Dimension(width, (int) Math.ceil(height));
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = UiKit.nice(graphics);
            try {
                g.setColor(own ? new Color(216, 200, 161, 170) : new Color(248, 238, 212, 160));
                g.fillRect(0, 2, getWidth(), getHeight() - 7);
                g.setColor(new Color(176, 150, 104));
                g.drawLine(0, getHeight() - 5, getWidth(), getHeight() - 5);
                int x = 9;
                g.setFont(Theme.bold(9f));
                g.setColor(new Color(124, 86, 35));
                g.drawString(own ? "YOU" : message.sender(), x, 17);
                g.setColor(Theme.INK);
                float y = 24;
                for (var line : lines(getWidth())) {
                    y += line.getAscent();
                    line.draw(g, x, y);
                    y += line.getDescent() + line.getLeading();
                }
            } finally { g.dispose(); }
        }
    }
}
