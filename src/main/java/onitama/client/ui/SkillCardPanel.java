package onitama.client.ui;

import onitama.config.SkillText;
import onitama.core.PlayerColor;
import onitama.core.duel.CardInstance;
import onitama.core.duel.RulesConfig;
import onitama.core.duel.SkillDefinition;
import onitama.core.duel.TurnPhase;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * A skill card component rendering the live card definition, mana cost,
 * and current availability state.
 */
public final class SkillCardPanel extends JComponent {

    private static final int W = 176;
    private static final int H = 210;

    private final CardInstance instance;
    private final SkillDefinition definition;

    private boolean selected;
    private boolean affordable;
    private boolean hovered;
    private float hoverProgress;
    private Timer hoverTimer;

    private boolean reducedMotion = false;

    /** Creates an illustrated card using live balance text and cost. */
    public SkillCardPanel(CardInstance instance,
                          RulesConfig rules,
                          PlayerColor viewer,
                          int availableMana,
                          TurnPhase currentPhase,
                          boolean isMyTurn,
                          boolean selected,
                          Runnable onClick) {
        this.instance = Objects.requireNonNull(instance, "instance must not be null");
        Objects.requireNonNull(rules, "rules must not be null");
        this.definition = rules.skill(instance.definitionId());
        this.selected = selected;

        int cost = definition != null ? definition.manaCost() : 0;
        this.affordable = availableMana >= cost;

        setPreferredSize(new Dimension(W, H));
        setMinimumSize(new Dimension(W, H));
        setOpaque(false);

        updateTooltip();
        CardDetails.bind(this);

        {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (onClick != null) onClick.run();
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    hovered = true;
                    startHoverAnim(1f);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = false;
                    startHoverAnim(0f);
                }
            });
        }
    }

    /** Returns the own-hand instance represented by this component. */
    public CardInstance instance() {
        return instance;
    }

    public boolean isSelected() {
        return selected;
    }

    /** Applies the preference to this existing card immediately. */
    public void setReducedMotion(boolean reducedMotion) {
        if (this.reducedMotion == reducedMotion) return;
        this.reducedMotion = reducedMotion;
        if (reducedMotion) cancelAnimations();
    }

    /** Stops hover animation when this presentation is discarded. */
    public void cancelAnimations() {
        if (hoverTimer != null) { hoverTimer.stop(); hoverTimer = null; }
        hoverProgress = 0f;
        repaint();
    }

    @Override public void removeNotify() {
        cancelAnimations();
        super.removeNotify();
    }

    /** Refreshes selection and availability without recreating the card or hover animation. */
    public void updateState(int availableMana, TurnPhase currentPhase, boolean isMyTurn, boolean selected) {
        int cost = definition != null ? definition.manaCost() : 0;
        this.affordable = availableMana >= cost;
        this.selected = selected;
        updateTooltip();
        repaint();
    }

    private void updateTooltip() {
        if (definition == null) {
            setToolTipText("Unknown Skill: " + instance.definitionId());
            javax.swing.ToolTipManager.sharedInstance().unregisterComponent(this);
            return;
        }
        String renderedText = SkillText.render(definition);
        String category = definition.category() != null ? definition.category().name() : "SKILL";
        String html = "<html><body style='width: 220px; padding: 4px;'>"
                + "<b>" + escapeHtml(definition.name()) + "</b> "
                + "<span style='color: #76532e;'>[" + definition.manaCost() + " Mana]</span><br>"
                + "<i style='color: #76532e;'>" + escapeHtml(category) + "</i><br><br>"
                + escapeHtml(renderedText)
                + "</body></html>";
        setToolTipText(html);
        javax.swing.ToolTipManager.sharedInstance().unregisterComponent(this);
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private void startHoverAnim(float target) {
        if (reducedMotion) {
            hoverProgress = target;
            repaint();
            return;
        }
        if (hoverTimer != null) {
            hoverTimer.stop();
        }
        hoverTimer = new Timer(20, event -> {
            if (Math.abs(hoverProgress - target) < 0.1f) {
                hoverProgress = target;
                ((Timer) event.getSource()).stop();
                hoverTimer = null;
            } else {
                hoverProgress += (target - hoverProgress) * 0.35f;
            }
            repaint();
        });
        hoverTimer.start();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        if (Boolean.TRUE.equals(getClientProperty("openingHidden"))
                || DeckDrawOverlay.isMasked(this) || CardPlayOverlay.isMasked(this)) return;
        Graphics2D g = UiKit.nice(graphics);
        int lift = selected ? 5 : (int) (hoverProgress * 4);
        int x = 5, y = 8 - lift, w = getWidth() - 12, h = getHeight() - 16;
        g.setColor(new Color(0, 0, 0, 80));
        g.fillRoundRect(x + 3, y + 4, w, h, 9, 9);
        if (definition != null) {
            g.drawImage(CardArt.skillFace(definition), x, y, w, h, null);
            if (selected || hovered) {
                g.setColor(selected ? Theme.AMBER : CardArt.categoryColor(definition.category()));
                g.setStroke(new BasicStroke(selected ? 3f : 2f));
                g.drawRoundRect(x, y, w, h, 8, 8);
            }
            if (!affordable) {
                g.setColor(new Color(48, 34, 24, 45));
                g.fillRoundRect(x, y, w, h, 8, 8);
            }
        }
        g.dispose();
    }

}
