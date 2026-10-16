package onitama.client.ui;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/** Non-modal parchment details appear only while a public/own card is inspected. */
final class CardDetails extends MouseAdapter {
    private final JComponent owner;
    private final Timer delay;
    private Popup popup;

    private CardDetails(JComponent owner) {
        this.owner = owner;
        delay = new Timer(280, event -> show());
        delay.setRepeats(false);
        owner.addMouseListener(this);
        owner.addHierarchyListener(event -> { if (!owner.isShowing()) hide(); });
        ToolTipManager.sharedInstance().unregisterComponent(owner);
    }

    static CardDetails bind(JComponent owner) { return new CardDetails(owner); }

    @Override public void mouseEntered(MouseEvent event) { delay.restart(); }
    @Override public void mouseExited(MouseEvent event) { hide(); }
    @Override public void mousePressed(MouseEvent event) {
        if (SwingUtilities.isLeftMouseButton(event)) { delay.stop(); show(); }
    }

    private void show() {
        hide();
        if (!owner.isShowing() || owner.getToolTipText() == null || DeckDrawOverlay.isMasked(owner)) return;
        JLabel content = new JLabel(owner.getToolTipText());
        content.setFont(Theme.normal(12));
        content.setForeground(Theme.INK);
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.CREAM);
        panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.INK, 2),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        panel.add(content);
        Dimension size = panel.getPreferredSize();
        Point point = owner.getLocationOnScreen();
        Rectangle screen = owner.getGraphicsConfiguration().getBounds();
        int x = Math.max(screen.x + 8, Math.min(point.x, screen.x + screen.width - size.width - 8));
        int y = point.y - size.height - 10;
        if (y < screen.y + 8) y = point.y + owner.getHeight() + 8;
        popup = PopupFactory.getSharedInstance().getPopup(owner, panel, x, y);
        popup.show();
    }

    void hide() {
        delay.stop();
        if (popup != null) { popup.hide(); popup = null; }
    }
}
