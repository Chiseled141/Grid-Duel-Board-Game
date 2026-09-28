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
 * One movement card rendered as a cream sticker in the retro-pop style: a
 * gold ribbon with the card name, the 5x5 pattern grid, and a small stamp
 * seal in the player color that decided the first move. The pattern is drawn
 * from the same {@link Card} data the engine uses (single source of truth),
 * always from {@code viewerColor}'s perspective: the opponent's cards are
 * pre-rotated so the player can plan with what they will receive. Opponent
 * cards are drawn dimmed. A designed card-face template from the AssetStore
 * replaces the drawn face when present. Clicking the panel fires the given
 * callback (own cards only).
 */
public final class CardPanel extends JComponent {

    private static final int CELL = 15;
    private static final int PADDING = 10;
    private static final int RIBBON = 18;
    private static final int WIDTH = 2 * PADDING + Board.SIZE * CELL;
    private static final int HEIGHT = RIBBON + 2 * PADDING + Board.SIZE * CELL;

    private final Card card;
    private final PlayerColor viewerColor;
    private final boolean dimmed;
    private boolean selected;

    /**
     * Creates the card panel.
     *
     * @param card the card to render
     * @param viewerColor the side whose orientation the pattern is drawn in
     * @param dimmed true for the opponent's cards (drawn muted, no shadow)
     * @param onSelect fired when the panel is clicked (null for read-only cards)
     */
    public CardPanel(Card card, PlayerColor viewerColor, boolean dimmed, Runnable onSelect) {
        this.card = card;
        this.viewerColor = viewerColor;
        this.dimmed = dimmed;
        setPreferredSize(new Dimension(WIDTH + 6, HEIGHT + 6));
        setToolTipText(card.name() + (dimmed ? " (opponent's card)" : ""));
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
        int x0 = 2;
        int y0 = 2;

        // Selected own cards lift with a hard shadow; opponents sit flat.
        if (selected) {
            g.setColor(Theme.SHADOW);
            g.fillRoundRect(x0 + 4, y0 + 4, cardW, cardH, 12, 12);
        }

        Image face = AssetStore.optional("card-face-template.png");
        g.setColor(dimmed ? new Color(0xE3DCC4) : Theme.CREAM);
        g.fillRoundRect(x0, y0, cardW, cardH, 12, 12);
        if (face != null) {
            g.drawImage(face, x0, y0, cardW, cardH, null);
        }
        g.setColor(Theme.INK);
        g.setStroke(new BasicStroke(selected ? 3f : 1.8f));
        if (selected) {
            g.setColor(Theme.AMBER);
        }
        g.drawRoundRect(x0, y0, cardW - 1, cardH - 1, 12, 12);

        // Gold name ribbon across the top.
        g.setColor(dimmed ? new Color(0xD9B95F) : Theme.AMBER);
        g.fillRoundRect(x0 + 4, y0 + 3, cardW - 8, RIBBON, 9, 9);
        g.setColor(Theme.INK);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(x0 + 4, y0 + 3, cardW - 9, RIBBON, 9, 9);
        g.setFont(Theme.display(10f));
        var metrics = g.getFontMetrics();
        String name = card.name().toUpperCase();
        g.drawString(name, x0 + (cardW - metrics.stringWidth(name)) / 2,
                y0 + 3 + (RIBBON + metrics.getAscent() - metrics.getDescent()) / 2 - 1);

        // Mini pattern grid, forward = up (the engine stamps the marks).
        int gridTop = y0 + RIBBON + PADDING;
        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                int px = x0 + PADDING + col * CELL;
                int py = gridTop + row * CELL;
                g.setColor(dimmed ? new Color(0xEFE9D6) : Theme.BOARD_LIGHT);
                g.fillRect(px, py, CELL, CELL);
                g.setColor(new Color(35, 31, 32, 70));
                g.drawRect(px, py, CELL, CELL);
            }
        }
        drawMark(g, x0 + PADDING + 2 * CELL, gridTop + 2 * CELL, "M", Theme.INK);
        Color markColor = dimmed ? new Color(0x5F8F7C) : Theme.TEAL;
        for (Square destination : card.destinationsFrom(new Square(2, 2), viewerColor)) {
            drawMark(g, x0 + PADDING + destination.x() * CELL,
                    gridTop + (Board.SIZE - 1 - destination.y()) * CELL,
                    "X", markColor);
        }

        // Stamp seal: the player color that decided the first move.
        int sealX = x0 + cardW - 13;
        int sealY = y0 + cardH - 13;
        g.setColor(dimmed ? new Color(0xA9A396)
                : card.stamp() == PlayerColor.BLUE ? Theme.SKY : Theme.CORAL);
        g.fillOval(sealX - 5, sealY - 5, 10, 10);
        g.setColor(Theme.INK);
        g.setStroke(new BasicStroke(1.5f));
        g.drawOval(sealX - 5, sealY - 5, 10, 10);

        if (dimmed) {
            // Veil pulls the opponent's cards back behind your own.
            g.setColor(new Color(15, 13, 14, 70));
            g.fillRoundRect(x0, y0, cardW, cardH, 12, 12);
        }
        g.dispose();
    }

    private void drawMark(Graphics2D g, int px, int py, String mark, Color color) {
        g.setFont(Theme.bold(11f));
        var metrics = g.getFontMetrics();
        g.drawString(mark, px + CELL / 2 - metrics.stringWidth(mark) / 2,
                py + CELL / 2 + metrics.getAscent() / 2 - 1);
    }
}
