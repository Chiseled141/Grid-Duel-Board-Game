package onitama.client.ui;

import onitama.core.Card;
import onitama.core.PlayerColor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
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
 * A movement card rendered from the designed full-face artwork (one variant
 * per player color, see assets/cards/). The artwork already contains the
 * name, movement grid, kanji seal and flavor band, so this component only
 * composites the state: the player-colored selection frame, the hover
 * lift/tilt, and the dimmed veil for the opponent's cards. A drawn cream
 * card with the ink animal spirit is the fallback when a face is missing.
 */
public final class CardPanel extends JComponent {

    private static final int W = 200;
    private static final int H = 230;
    /** Hover animation length (§11: 150–250ms). */
    private static final int HOVER_MS = 180;

    private final Card card;
    private final PlayerColor faceColor;
    private final boolean dimmed;
    private boolean selected;

    private boolean hovered;
    private float hover; // 0..1 tween for lift/tilt
    private double cardScale = 1.0;
    private Timer tween;

    /** Scales the whole card (used to fit two cards in the side column). */
    public void setCardScale(double cardScale) {
        this.cardScale = cardScale;
        setPreferredSize(new Dimension((int) ((W + 12) * cardScale),
                (int) ((H + 14) * cardScale)));
        repaint();
    }

    /**
     * Creates the card panel.
     *
     * @param card the card to render
     * @param faceColor the player color whose artwork variant to use
     * @param dimmed true for the opponent's cards (muted, no interaction)
     * @param onSelect fired when the panel is clicked (null for read-only cards)
     */
    public CardPanel(Card card, PlayerColor faceColor, boolean dimmed, Runnable onSelect) {
        this.card = card;
        this.faceColor = faceColor;
        this.dimmed = dimmed;
        setPreferredSize(new Dimension((int) ((W + 12) * cardScale),
                (int) ((H + 14) * cardScale)));
        setOpaque(false);
        setToolTipText(card.name() + " — " + CardArt.flavorFor(card.id())
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
        double scale = (selected ? 1.04 : 1.0 + 0.02 * hover) * cardScale;
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
            g.fillRoundRect(x0 + shadow, y0 + shadow, W, H, Theme.RADIUS_MD, Theme.RADIUS_MD);
        }

        Image face = AssetStore.optional(facePath());
        if (face != null) {
            var clip = new java.awt.geom.RoundRectangle2D.Double(x0, y0, W, H,
                    Theme.RADIUS_MD, Theme.RADIUS_MD);
            g.setClip(clip);
            g.drawImage(face, x0, y0, W, H, null);
            g.setClip(null);
            g.setStroke(new BasicStroke(selected ? 3.5f : 2f));
            g.setColor(selected ? Theme.playerColor(faceColor) : Theme.INK);
            g.draw(clip);
        } else {
            paintFallbackCard(g, x0, y0);
        }

        if (dimmed) {
            g.setColor(new Color(15, 13, 14, 70));
            g.fillRoundRect(x0, y0, W, H, Theme.RADIUS_MD, Theme.RADIUS_MD);
        }
    }

    /** The designed face for this card and player color. */
    private String facePath() {
        return "cards/" + faceColor.name().toLowerCase() + "/"
                + card.id() + "-" + faceColor.name().toLowerCase() + ".png";
    }

    /** Minimal drawn fallback when the designed face file is missing. */
    private void paintFallbackCard(Graphics2D g, int x0, int y0) {
        g.setColor(Theme.CREAM);
        g.fillRoundRect(x0, y0, W, H, Theme.RADIUS_MD, Theme.RADIUS_MD);
        g.setColor(Theme.INK);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(x0, y0, W - 1, H - 1, Theme.RADIUS_MD, Theme.RADIUS_MD);
        AnimalIcon.paint(g, card.id(), x0 + (W - 90) / 2, y0 + 30, 90,
                Theme.INK, accentFor(), Theme.CREAM);
        g.setFont(Theme.display(15f));
        g.drawString(card.name().toUpperCase(), x0 + 16, y0 + 150);
        g.setFont(Theme.normal(9f));
        g.drawString(CardArt.flavorFor(card.id()), x0 + 16, y0 + 180);
        g.setColor(accentFor());
        g.fillRoundRect(x0 + 16, y0 + 195, W - 32, 20, 8, 8);
    }

    private Color accentFor() {
        return CardArt.of(card.id());
    }
}
