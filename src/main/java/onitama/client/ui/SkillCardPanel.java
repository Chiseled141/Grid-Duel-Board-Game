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
    private boolean phaseActive;
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
        this.phaseActive = isMyTurn && currentPhase == TurnPhase.SKILL;

        setPreferredSize(new Dimension(W, H));
        setMinimumSize(new Dimension(W, H));
        setOpaque(false);

        updateTooltip();

        if (onClick != null) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    onClick.run();
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
        this.phaseActive = isMyTurn && currentPhase == TurnPhase.SKILL;
        this.selected = selected;
        updateTooltip();
        repaint();
    }

    private void updateTooltip() {
        if (definition == null) {
            setToolTipText("Unknown Skill: " + instance.definitionId());
            return;
        }
        String renderedText = SkillText.render(definition);
        String category = definition.category() != null ? definition.category().name() : "SKILL";
        String html = "<html><body style='width: 220px; padding: 4px;'>"
                + "<b>" + escapeHtml(definition.name()) + "</b> "
                + "<span style='color: #60a5fa;'>[" + definition.manaCost() + " Mana]</span><br>"
                + "<i style='color: #aaa;'>" + escapeHtml(category) + "</i><br><br>"
                + escapeHtml(renderedText)
                + "</body></html>";
        setToolTipText(html);
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
        if (DeckDrawOverlay.isMasked(this)) return;
        Graphics2D g = UiKit.nice(graphics);
        int lift = selected ? 5 : (int) (hoverProgress * 4);
        int x = 5, y = 8 - lift, w = getWidth() - 12, h = getHeight() - 16;
        g.setColor(new Color(0, 0, 0, 80));
        g.fillRoundRect(x + 3, y + 4, w, h, 9, 9);
        g.setColor(new Color(237, 222, 183));
        g.fillRoundRect(x, y, w, h, 8, 8);
        g.setColor(selected ? Theme.AMBER : hovered ? new Color(186, 141, 62) : Theme.INK);
        g.setStroke(new BasicStroke(selected ? 3f : 2f));
        g.drawRoundRect(x, y, w, h, 8, 8);
        if (definition == null) { g.dispose(); return; }

        // Use the illustration region only; printed draft costs/rules are never authoritative.
        String artPath = definition.artworkPath();
        if (artPath.startsWith("assets/")) artPath = artPath.substring(7);
        Image face = AssetStore.optional(artPath);
        int artH = Math.max(50, (int) (h * .46));
        if (face != null) {
            int iw = face.getWidth(null), ih = face.getHeight(null);
            g.drawImage(face, x + 5, y + 5, x + w - 5, y + artH,
                    (int) (iw * .04), (int) (ih * .20), (int) (iw * .96), (int) (ih * .50), null);
        }
        int titleY = y + artH;
        g.setColor(Theme.INK);
        g.fillRect(x + 4, titleY, w - 8, 26);
        g.setColor(Theme.CREAM);
        float size = 12f;
        do { g.setFont(Theme.display(size)); size -= .5f; }
        while (g.getFontMetrics().stringWidth(definition.name().toUpperCase()) > w - 18 && size > 8);
        g.drawString(definition.name().toUpperCase(), x + 9, titleY + 17);

        // Opaque live-cost badge stays readable even out of phase.
        g.setColor(affordable ? new Color(34, 75, 90) : new Color(125, 58, 46));
        g.fillOval(x + 9, y + 9, 30, 30);
        g.setColor(Theme.CREAM);
        g.setStroke(new BasicStroke(1.5f));
        g.drawOval(x + 9, y + 9, 30, 30);
        g.setFont(Theme.display(16f));
        String cost = Integer.toString(definition.manaCost());
        g.drawString(cost, x + 24 - g.getFontMetrics().stringWidth(cost) / 2, y + 30);

        g.setColor(new Color(92, 68, 39));
        g.setFont(Theme.display(9f));
        g.drawString(definition.category().name() + " · SKILL", x + 9, titleY + 39);
        if (phaseActive && affordable) {
            g.setColor(new Color(34, 75, 90));
            g.fillOval(x + w - 15, titleY + 32, 5, 5);
        }
        g.setColor(Theme.INK);
        g.setFont(Theme.normal(11f));
        drawWrappedText(g, SkillText.render(definition), x + 9, titleY + 53, w - 18,
                Math.max(2, (y + h - titleY - 58) / 12 + 1));
        g.dispose();
    }

    private void drawWrappedText(Graphics2D g, String text, int x, int y, int width, int maxLines) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        String line = "";
        for (String word : text.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && g.getFontMetrics().stringWidth(next) > width) {
                lines.add(line);
                line = word;
            } else line = next;
        }
        if (!line.isEmpty()) lines.add(line);
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
            String value = lines.get(i);
            if (i == maxLines - 1 && lines.size() > maxLines) {
                while (!value.isEmpty() && g.getFontMetrics().stringWidth(value + "…") > width)
                    value = value.substring(0, value.length() - 1);
                value += "…";
            }
            g.drawString(value, x, y + i * 12);
        }
    }
}
