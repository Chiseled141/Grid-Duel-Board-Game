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
import java.util.List;

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
    private float popScale = 1f; // one-shot deal pop multiplier
    private Timer tween;
    private Timer pop;

    /** Scales the whole card (used to fit two cards in the side column). */
    public void setCardScale(double cardScale) {
        this.cardScale = cardScale;
        setPreferredSize(new Dimension((int) ((W + 12) * cardScale),
                (int) ((H + 14) * cardScale)));
        // The enclosing FlowLayout/GridLayout slots must re-layout to the new
        // size before the next click, or presses land on stale bounds.
        revalidate();
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
        setToolTipText("<html><b>" + card.name().toUpperCase()
                + "</b> — " + movementDescription()
                + "<br><i>" + CardArt.flavorFor(card.id())
                + (dimmed ? "</i> (opponent's card)" : "</i>") + "</html>");
        if (onSelect != null) {
            setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    // Press, not click: selection registers instantly and a
                    // press+release that ends outside the card still counts,
                    // so rapid card→piece tapping never loses an input.
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
        }
    }

    /** Marks this card as the currently selected one. */
    public void setSelected(boolean selected) {
        this.selected = selected;
        repaint();
    }

    /**
     * One-shot 180ms scale pop (1 → 1.12 → 1) played when the card enters
     * the hand after a swap — a quick, input-safe flourish via Swing timer.
     */
    public void playDealPop() {
        if (pop != null) {
            pop.stop();
        }
        long start = System.currentTimeMillis();
        pop = new Timer(16, event -> {
            float progress = Math.min(1f,
                    (System.currentTimeMillis() - start) / 180f);
            popScale = 1f + 0.12f * (float) Math.sin(Math.PI * progress);
            repaint();
            if (progress >= 1f) {
                popScale = 1f;
                ((Timer) event.getSource()).stop();
            }
        });
        pop.start();
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

        boolean interactive = !dimmed;
        // A selected card jumps out: higher lift, bigger scale, straight (no
        // tilt). Unselected cards keep the gentle 4px hover lift.
        double lift = interactive ? (selected ? 8 : 4 * hover) : 0;
        double tilt = Math.toRadians(-1.5 * hover * (interactive && !selected ? 1 : 0));
        double scale = (selected ? 1.07 : 1.0 + 0.02 * hover) * cardScale * popScale;
        g.translate(getWidth() / 2.0, getHeight() / 2.0);
        g.rotate(tilt);
        g.scale(scale, scale);
        g.translate(-W / 2.0, -H / 2.0 - lift);
        if (selected && interactive) {
            paintSelectionGlow(g);
        }
        paintCard(g);
        g.dispose();
    }

    /** A feathered gold halo painted behind a selected card (45% alpha). */
    private void paintSelectionGlow(Graphics2D g) {
        float radius = (float) (Math.max(W, H) / 2.0 + 8);
        var gradient = new java.awt.RadialGradientPaint(
                new java.awt.geom.Point2D.Float(W / 2f, H / 2f), radius,
                new float[]{0.65f, 1f},
                new Color[]{Theme.withAlpha(Theme.AMBER, 115),
                        Theme.withAlpha(Theme.AMBER, 0)});
        g.setPaint(gradient);
        g.fill(new java.awt.geom.Ellipse2D.Double(
                W / 2.0 - (W + 16) / 2.0, H / 2.0 - (H + 12) / 2.0, W + 16, H + 12));
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
            g.setColor(selected ? Theme.AMBER : Theme.INK);
            g.draw(clip);
            if (selected) {
                // Double frame on selection: gold outer ring + ink inner line.
                g.setColor(Theme.INK);
                g.setStroke(new BasicStroke(2f));
                g.draw(new java.awt.geom.RoundRectangle2D.Double(x0 + 4, y0 + 4,
                        W - 8, H - 8, Theme.RADIUS_MD - 4, Theme.RADIUS_MD - 4));
            }
        } else {
            paintFallbackCard(g, x0, y0);
        }

        if (dimmed) {
            g.setColor(Theme.withAlpha(Theme.BG, 70));
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

    /**
     * The card's moves in words, in the orientation the card is displayed in
     * (red-owned cards are shown rotated 180°, matching the artwork and the
     * engine). Feeds the hover tooltip.
     */
    private String movementDescription() {
        boolean flipped = faceColor == onitama.core.PlayerColor.RED;
        List<String> moves = new java.util.ArrayList<>();
        for (onitama.core.Offset offset : card.offsets()) {
            int dy = flipped ? -offset.dy() : offset.dy();
            int dx = flipped ? -offset.dx() : offset.dx();
            if (dy > 0) {
                moves.add("forward " + dy);
            } else if (dy < 0) {
                moves.add("back " + -dy);
            } else if (dx > 0) {
                moves.add("right " + dx);
            } else {
                moves.add("left " + -dx);
            }
        }
        return String.join(" · ", moves);
    }
}
