package onitama.client.ui;

import onitama.core.Board;
import onitama.core.Card;
import onitama.core.PlayerColor;
import onitama.core.Square;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;

/**
 * One movement card rendered as a cream sticker: the card name in the
 * display font and a mini 5x5 pattern grid, drawn from the same {@link Card}
 * data the engine uses (single source of truth). The pattern is always drawn
 * from {@code viewerColor}'s perspective: the opponent's cards are
 * pre-rotated so the player can plan with what they will receive. A designed
 * card-face template from the AssetStore replaces the drawn face when
 * present. Clicking the panel fires the given callback (own cards only).
 */
public final class CardPanel extends JComponent {

    private static final int CELL = 15;
    private static final int PADDING = 10;
    private static final int NAME_AREA = 22;
    private static final int WIDTH = 2 * PADDING + Board.SIZE * CELL;
    private static final int HEIGHT = NAME_AREA + 2 * PADDING + Board.SIZE * CELL;

    private final Card card;
    private final PlayerColor viewerColor;
    private boolean selected;

    /**
     * Creates the card panel.
     *
     * @param card the card to render
     * @param viewerColor the side whose orientation the pattern is drawn in
     * @param onSelect fired when the panel is clicked (null for read-only cards)
     */
    public CardPanel(Card card, PlayerColor viewerColor, Runnable onSelect) {
        this.card = card;
        this.viewerColor = viewerColor;
        setPreferredSize(new Dimension(WIDTH + 4, HEIGHT + 4));
        setToolTipText(card.name());
        if (onSelect != null) {
            setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    onSelect.run();
                }
            });
        }
    }

    /** Marks this card as the currently selected one (gold frame + shadow). */
    public void setSelected(boolean selected) {
        this.selected = selected;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = UiKit.nice(graphics);
        g.setColor(Theme.BG);
        g.fillRect(0, 0, getWidth(), getHeight());

        int cardW = WIDTH;
        int cardH = HEIGHT;
        int offset = selected ? 0 : 3;
        int x0 = 2 + offset;
        int y0 = 2 + offset;

        Image face = AssetStore.optional("card-face-template.png");
        if (selected) {
            g.setColor(Theme.SHADOW);
            g.fillRoundRect(x0 + 4, y0 + 4, cardW, cardH, 12, 12);
        }
        if (face != null) {
            g.drawImage(face, x0, y0, cardW, cardH, null);
        } else {
            g.setColor(Theme.CREAM);
            g.fillRoundRect(x0, y0, cardW, cardH, 12, 12);
        }
        g.setColor(Theme.INK);
        g.setStroke(new BasicStroke(selected ? 3f : 2f));
        if (selected) {
            g.setColor(Theme.AMBER);
        }
        g.drawRoundRect(x0, y0, cardW - 1, cardH - 1, 12, 12);

        // Card name.
        g.setColor(Theme.INK);
        g.setFont(Theme.display(12f));
        var metrics = g.getFontMetrics();
        String name = card.name().toUpperCase();
        g.drawString(name, x0 + (cardW - metrics.stringWidth(name)) / 2,
                y0 + NAME_AREA - 8);

        // Mini pattern grid, forward = up (the engine stamps the marks).
        int gridTop = y0 + NAME_AREA + PADDING;
        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                int px = x0 + PADDING + col * CELL;
                int py = gridTop + row * CELL;
                g.setColor(Theme.BOARD_LIGHT);
                g.fillRect(px, py, CELL, CELL);
                g.setColor(new Color(35, 31, 32, 90));
                g.drawRect(px, py, CELL, CELL);
            }
        }
        drawMark(g, x0 + PADDING + 2 * CELL, gridTop + 2 * CELL, "M", Theme.INK);
        for (Square destination : card.destinationsFrom(new Square(2, 2), viewerColor)) {
            drawMark(g, x0 + PADDING + destination.x() * CELL,
                    gridTop + (Board.SIZE - 1 - destination.y()) * CELL,
                    "X", Theme.TEAL);
        }
        g.dispose();
    }

    private void drawMark(Graphics2D g, int px, int py, String mark, java.awt.Color color) {
        g.setFont(Theme.bold(11f));
        var metrics = g.getFontMetrics();
        g.drawString(mark, px + CELL / 2 - metrics.stringWidth(mark) / 2,
                py + CELL / 2 + metrics.getAscent() / 2 - 1);
    }
}
