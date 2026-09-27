package onitama.client.ui;

import onitama.core.Board;
import onitama.core.Card;
import onitama.core.PlayerColor;
import onitama.core.Square;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;

/**
 * One movement card rendered as its name plus a mini 5x5 pattern grid, drawn
 * from the same {@link Card} data the engine uses (single source of truth).
 * The pattern is always drawn from {@code viewerColor}'s perspective: the
 * opponent's cards are pre-rotated so the player can plan with what they will
 * receive. Clicking the panel fires the given callback (own cards only).
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
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
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

    /** Marks this card as the currently selected one (draws a highlight frame). */
    public void setSelected(boolean selected) {
        this.selected = selected;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Theme.BACKGROUND.brighter());
        g.fillRect(0, 0, getWidth(), getHeight());

        g.setColor(Theme.FOREGROUND);
        g.setFont(Theme.FONT_BOLD);
        g.drawString(card.name(), PADDING, NAME_AREA - 6);

        int gridTop = NAME_AREA + PADDING;
        var destinations = card.destinationsFrom(new Square(2, 2), viewerColor);
        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                int px = PADDING + col * CELL;
                int py = gridTop + row * CELL;
                g.setColor(Theme.BOARD_DARK);
                g.fillRect(px, py, CELL, CELL);
                g.setColor(Theme.BACKGROUND);
                g.drawRect(px, py, CELL, CELL);
            }
        }
        // The moving piece sits in the center; X marks every destination.
        drawMark(g, PADDING + 2 * CELL, gridTop + 2 * CELL, "M", Theme.FOREGROUND);
        for (Square destination : destinations) {
            drawMark(g, PADDING + destination.x() * CELL,
                    gridTop + (Board.SIZE - 1 - destination.y()) * CELL,
                    "X", Theme.TARGET_DOT);
        }
        if (selected) {
            g.setColor(Theme.SELECTION);
            for (int width = 1; width <= 3; width++) {
                g.drawRect(width, width, WIDTH - 2 * width, HEIGHT - 2 * width);
            }
        }
    }

    private void drawMark(Graphics2D g, int px, int py, String mark, Color color) {
        g.setColor(color);
        g.setFont(Theme.FONT_BOLD);
        var metrics = g.getFontMetrics();
        g.drawString(mark, px + CELL / 2 - metrics.stringWidth(mark) / 2,
                py + CELL / 2 + metrics.getAscent() / 2 - 1);
    }
}
