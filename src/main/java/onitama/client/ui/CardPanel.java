package onitama.client.ui;

import onitama.core.Board;
import onitama.core.Card;
import onitama.core.PlayerColor;
import onitama.core.Square;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * A premium tabletop movement card, in the retro-pop style: original animal
 * illustration on top, name, the 5x5 movement grid filled in the card's own
 * accent color, the flavor-quote band and the stamp seal. The pattern always
 * comes from the engine's {@link Card} data (single source of truth), drawn
 * from {@code viewerColor}'s perspective. Own cards hover-lift and tilt;
 * the selected card scales up with the player-colored border; opponent cards
 * are dimmed and flat.
 */
public final class CardPanel extends JComponent {

    private static final int CELL = 15;
    private static final int GRID = Board.SIZE * CELL;
    private static final int W = 200;
    private static final int H = 192;
    /** Hover animation length (§11: 150–250ms). */
    private static final int HOVER_MS = 180;

    private final Card card;
    private final PlayerColor viewerColor;
    private final boolean dimmed;
    private boolean selected;

    private boolean hovered;
    private float hover; // 0..1 tween for lift/tilt
    private Timer tween;

    /**
     * Creates the card panel.
     *
     * @param card the card to render
     * @param viewerColor the side whose orientation the pattern is drawn in
     * @param dimmed true for the opponent's cards (muted, no interaction)
     * @param onSelect fired when the panel is clicked (null for read-only cards)
     */
    public CardPanel(Card card, PlayerColor viewerColor, boolean dimmed, Runnable onSelect) {
        this.card = card;
        this.viewerColor = viewerColor;
        this.dimmed = dimmed;
        setPreferredSize(new Dimension(W + 12, H + 14));
        setOpaque(false);
        setToolTipText(card.name() + " — " + quote()
                + (dimmed ? " (opponent's card)" : ""));
        if (onSelect != null) {
            setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    onSelect.run();
                }

                @Override
                public void mouseEntered(MouseEvent event) {
                    hovered = true;
                    startTween(1f);
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    hovered = false;
                    startTween(0f);
                }
            });
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent event) {
                    repaint();
                }
            });
        }
    }

    /** Marks this card as the currently selected one. */
    public void setSelected(boolean selected) {
        this.selected = selected;
        repaint();
    }

    /** Tweens the hover lift/tilt toward the target (1 = lifted). */
    private void startTween(float target) {
        if (tween != null) {
            tween.stop();
        }
        float from = hover;
        long start = System.currentTimeMillis();
        tween = new Timer(16, event -> {
            float progress = Math.min(1f, (System.currentTimeMillis() - start) / (float) HOVER_MS);
            hover = from + (target - from) * progress;
            repaint();
            if (progress >= 1f) {
                ((Timer) event.getSource()).stop();
            }
        });
        tween.start();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Theme.BG);
        g.fillRect(0, 0, getWidth(), getHeight());

        double lift = hover * 6 * (dimmed ? 0 : 1);
        double tilt = Math.toRadians(-1.5 * hover * (dimmed ? 0 : 1));
        double scale = selected ? 1.04 : 1.0 + 0.02 * hover;
        g.translate(getWidth() / 2.0, getHeight() / 2.0);
        g.rotate(tilt);
        g.scale(scale, scale);
        g.translate(-W / 2.0, -H / 2.0 - lift);
        paintCard(g);
        g.dispose();
    }

    private void paintCard(Graphics2D g) {
        int x0 = 0;
        int y0 = 0;
        int shadow = selected || hover > 0.2f ? Theme.SHADOW_OFFSET + 2 : Theme.SHADOW_OFFSET;

        if (!dimmed) {
            g.setColor(Theme.SHADOW);
            g.fillRoundRect(x0 + shadow, y0 + shadow, W, H, 14, 14);
        }

        Image face = AssetStore.optional("card-face-template.png");
        Color cardFill = dimmed ? Theme.blend(Theme.CREAM, Theme.SURFACE) : Theme.CREAM;
        g.setColor(cardFill);
        g.fillRoundRect(x0, y0, W, H, Theme.RADIUS_MD, Theme.RADIUS_MD);
        if (face != null) {
            g.drawImage(face, x0, y0, W, H, null);
        }
        Color accent = dimmed ? Theme.blend(accent(), Theme.SURFACE) : accent();
        g.setColor(selected ? Theme.playerColor(viewerColor) : accent);
        g.setStroke(new BasicStroke(selected ? 3f : 2f));
        g.drawRoundRect(x0, y0, W - 1, H - 1, Theme.RADIUS_MD, Theme.RADIUS_MD);

        // TOP: original animal illustration (sticker style).
        AnimalIcon.paint(g, card.id(), x0 + (W - 56) / 2, y0 + 8, 56,
                Theme.INK, accent(), Theme.CREAM);
        // Small kanji keeps the dojo flavor next to the illustration.
        g.setFont(Theme.display(16f));
        g.setColor(Theme.blend(accent(), cardFill));
        var kanjiMetrics = g.getFontMetrics();
        String kanji = kanjiFor(card.id());
        g.drawString(kanji, x0 + W - 22 - kanjiMetrics.stringWidth(kanji) / 2, y0 + 46);

        // CENTER: name + movement grid (engine data, viewer perspective).
        g.setFont(Theme.display(14f));
        g.setColor(Theme.INK);
        var nameMetrics = g.getFontMetrics();
        String name = card.name().toUpperCase();
        g.drawString(name, x0 + (W - nameMetrics.stringWidth(name)) / 2, y0 + 86);

        int gridX = x0 + (W - GRID) / 2;
        int gridY = y0 + 94;
        Color fill = accent();
        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                int px = gridX + col * CELL;
                int py = gridY + row * CELL;
                g.setColor(dimmed ? Theme.blend(Theme.BOARD_LIGHT, Theme.SURFACE)
                        : Theme.BOARD_LIGHT);
                g.fillRect(px, py, CELL, CELL);
                g.setColor(new Color(35, 31, 32, 60));
                g.drawRect(px, py, CELL, CELL);
            }
        }
        g.setColor(Theme.INK);
        g.setStroke(new BasicStroke(2.5f));
        g.drawOval(gridX + 2 * CELL + 3, gridY + 2 * CELL + 3, CELL - 6, CELL - 6);
        for (Square destination : card.destinationsFrom(new Square(2, 2), viewerColor)) {
            int px = gridX + destination.x() * CELL;
            int py = gridY + (Board.SIZE - 1 - destination.y()) * CELL;
            g.setColor(fill);
            g.fillRect(px + 1, py + 1, CELL - 2, CELL - 2);
            g.setColor(Theme.INK);
            g.setStroke(new BasicStroke(1.5f));
            g.drawRect(px + 1, py + 1, CELL - 3, CELL - 3);
        }

        // BOTTOM: flavor-quote band + stamp seal.
        int bandY = y0 + H - 30;
        g.setColor(dimmed ? Theme.blend(Theme.AMBER, Theme.SURFACE) : Theme.AMBER);
        g.fillRoundRect(x0 + 6, bandY, W - 12, 24, Theme.RADIUS_SM, Theme.RADIUS_SM);
        g.setColor(Theme.INK);
        g.setFont(Theme.normal(8f));
        var metrics = g.getFontMetrics();
        String quote = quote();
        String first = quote;
        String second = "";
        int space = quote.lastIndexOf(' ', quote.length() / 2 + 4);
        if (space > 0 && metrics.stringWidth(quote) > W - 30) {
            first = quote.substring(0, space);
            second = quote.substring(space + 1);
        }
        g.drawString(first, x0 + 12, bandY + 10);
        if (!second.isEmpty()) {
            g.drawString(second, x0 + 12, bandY + 20);
        }
        int sealX = x0 + W - 16;
        int sealY = bandY + 12;
        g.setColor(dimmed ? Theme.blend(sealColor(), Theme.SURFACE) : sealColor());
        g.fillOval(sealX - 6, sealY - 6, 12, 12);
        g.setColor(Theme.INK);
        g.setStroke(new BasicStroke(1.5f));
        g.drawOval(sealX - 6, sealY - 6, 12, 12);
    }

    private Color sealColor() {
        return card.stamp() == PlayerColor.BLUE ? Theme.SKY : Theme.CORAL;
    }

    private Color accent() {
        return CardArt.of(card.id());
    }

    private String quote() {
        return CardArt.flavorFor(card.id());
    }

    private String kanjiFor(String id) {
        return CardArt.kanjiFor(id);
    }
}
