package onitama.client.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.Scrollable;
import javax.swing.text.View;
import onitama.config.SkillText;
import onitama.core.Card;
import onitama.core.PlayerColor;
import onitama.core.duel.CardInstance;
import onitama.core.duel.PlayerView;
import onitama.core.duel.SkillDefinition;
import onitama.core.duel.TurnPhase;

/** Read-only card reference with live text; it only receives permitted card identities. */
final class CardInspectorPanel extends JPanel {
    private final JToggleButton spentButton = new JToggleButton("SPENT SKILLS (0)");
    private final JLabel[] steps = new JLabel[3];
    private final JLabel title = UiKit.label("CARD DETAILS", 14f);
    private final JTextArea description = textArea();
    private final JTextArea guidance = textArea();
    private final JPanel imageHolder = new JPanel(new BorderLayout());
    private final JComponent illustration = new JComponent() {
        @Override protected void paintComponent(Graphics graphics) {
            if (face == null) return;
            var g = (Graphics2D) graphics.create();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            double scale = Math.min(getWidth() / (double) face.getWidth(), getHeight() / (double) face.getHeight());
            int width = (int) (face.getWidth() * scale), height = (int) (face.getHeight() * scale);
            g.drawImage(face, (getWidth() - width) / 2, 0, width, height, null);
            g.dispose();
        }
    };
    private final JScrollPane referenceScroll;
    private BufferedImage face;
    private Object shownKey;

    CardInspectorPanel(Runnable onBrowseChanged) {
        super(new BorderLayout(0, 10));
        setBackground(Theme.MIST);
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.INK, 2),
                BorderFactory.createEmptyBorder(12, 10, 12, 10)));
        JPanel header = new JPanel(new BorderLayout(0, 10));
        header.setOpaque(false);
        header.add(UiKit.caption("TURN FLOW", Theme.INK), BorderLayout.NORTH);
        JPanel flow = new JPanel(new GridLayout(1, 3, 4, 0));
        flow.setOpaque(false);
        String[] names = {"1 MOVE", "2 SKILL", "3 END"};
        for (int i = 0; i < steps.length; i++) {
            steps[i] = new JLabel(names[i], JLabel.CENTER);
            steps[i].setFont(Theme.bold(9f));
            steps[i].setOpaque(true);
            steps[i].setBorder(BorderFactory.createEmptyBorder(7, 0, 7, 0));
            flow.add(steps[i]);
        }
        header.add(flow, BorderLayout.CENTER);
        spentButton.setFont(Theme.bold(10f));
        spentButton.setForeground(Theme.CREAM);
        spentButton.setBackground(Theme.JADE);
        spentButton.setOpaque(true);
        spentButton.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 4));
        spentButton.setToolTipText("Publicly spent skill cards from both players");
        spentButton.addActionListener(event -> onBrowseChanged.run());
        header.add(spentButton, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setOpaque(false);
        title.setForeground(Theme.INK);
        content.add(title, BorderLayout.NORTH);
        imageHolder.setOpaque(false);
        illustration.setPreferredSize(new Dimension(208, 248));
        imageHolder.add(illustration, BorderLayout.CENTER);
        JPanel reference = new ReferencePanel();
        reference.setOpaque(false);
        reference.add(imageHolder, BorderLayout.NORTH);
        reference.add(description, BorderLayout.CENTER);
        referenceScroll = new JScrollPane(reference, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        referenceScroll.setBorder(null);
        referenceScroll.setOpaque(false);
        referenceScroll.getViewport().setOpaque(false);
        referenceScroll.getVerticalScrollBar().setUnitIncrement(18);
        referenceScroll.setMinimumSize(new Dimension(0, 0));
        content.add(referenceScroll, BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);
        guidance.setFont(Theme.bold(11f));
        guidance.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(177, 155, 113)));
        guidance.setRows(5);
        var hints = new JScrollPane(guidance, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        hints.setBorder(null);
        hints.setOpaque(false);
        hints.getViewport().setOpaque(false);
        add(hints, BorderLayout.SOUTH);
        idle();
    }

    /** Track viewport width and measure wrapped text before assigning the scroll extent. */
    private final class ReferencePanel extends JPanel implements Scrollable {
        ReferencePanel() { super(new BorderLayout(0, 8)); }

        @Override public Dimension getPreferredSize() {
            int width = referenceScroll == null ? 208 : referenceScroll.getViewport().getWidth();
            if (width <= 0) width = 208;
            var insets = description.getInsets();
            View text = description.getUI().getRootView(description);
            text.setSize(Math.max(1, width - insets.left - insets.right), Float.MAX_VALUE);
            int height = (int) Math.ceil(text.getPreferredSpan(View.Y_AXIS)) + insets.top + insets.bottom;
            if (imageHolder.isVisible()) height += illustration.getPreferredSize().height + 8;
            return new Dimension(width, height);
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return new Dimension(208, 400); }
        @Override public int getScrollableUnitIncrement(java.awt.Rectangle visible, int orientation, int direction) { return 18; }
        @Override public int getScrollableBlockIncrement(java.awt.Rectangle visible, int orientation, int direction) { return Math.max(18, visible.height - 18); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private static JTextArea textArea() {
        var text = new JTextArea();
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setOpaque(false);
        text.setForeground(Theme.INK);
        text.setFont(Theme.normal(12f));
        text.setColumns(15);
        return text;
    }

    void updateTurn(PlayerView view, String instruction, String progress, boolean paused, boolean finished) {
        spentButton.setText("SPENT SKILLS (" + view.spentSkills().size() + ")");
        int current = view.phase() == TurnPhase.MOVEMENT ? 0
                : view.phase() == TurnPhase.END ? 2 : 1;
        boolean active = view.turn() == view.viewer() && view.outcome() == null && !paused && !finished;
        for (int i = 0; i < steps.length; i++) {
            steps[i].setBackground(active && i == current ? Theme.AMBER : Theme.P1_LIGHT);
            steps[i].setForeground(Theme.INK);
        }
        String text = instruction + (progress.isEmpty() ? "" : "\n\n" + progress);
        if (!guidance.getText().equals(text)) {
            guidance.setText(text);
            guidance.setCaretPosition(0);
        }
    }

    void showMovement(Card card, PlayerColor owner) {
        Object key = java.util.List.of(card, owner);
        if (Objects.equals(shownKey, key)) return;
        var preview = new CardPanel(card, owner, false, null);
        preview.setReducedMotion(true);
        preview.setSize(212, 244);
        setReference(key, card.name().toUpperCase(), DeckDrawOverlay.snapshot(preview),
                "MOVEMENT - PUBLIC\n\nOffsets from the card owner's perspective:\n"
                + card.offsets().stream().map(o -> {
                    String horizontal = o.dx() == 0 ? "" : Math.abs(o.dx()) + (o.dx() > 0 ? " right" : " left");
                    String vertical = o.dy() == 0 ? "" : Math.abs(o.dy()) + (o.dy() > 0 ? " forward" : " backward");
                    return "- " + horizontal + (!horizontal.isEmpty() && !vertical.isEmpty() ? ", " : "") + vertical;
                }).collect(java.util.stream.Collectors.joining("\n"))
                + "\n\nJump to an allowed square. Friendly landing is illegal; enemy landing eliminates. "
                + "After use, return this card, shuffle the movement deck and draw.");
    }

    void showSkill(CardInstance card, PlayerView view) {
        SkillDefinition def = view.rules().skill(card.definitionId());
        if (def == null) { idle(); return; }
        Object key = java.util.List.of(card, def);
        if (Objects.equals(shownKey, key)) return;
        var preview = new SkillCardPanel(card, view.rules(), view.viewer(), def.manaCost(),
                TurnPhase.SKILL, true, false, null);
        preview.setReducedMotion(true);
        preview.setSize(208, 248);
        setReference(key, def.name().toUpperCase(), DeckDrawOverlay.snapshot(preview),
                def.category().name() + " - " + def.manaCost() + " MANA\n\n" + SkillText.render(def)
                + "\n\nSuccessfully cast skills are spent permanently. Draw a replacement if the skill deck has cards.");
    }

    boolean browsingSpent() {
        return spentButton.isSelected();
    }

    void showSpent(PlayerView view) {
        String text = view.spentSkills().isEmpty() ? "No skills have been spent yet."
                : view.spentSkills().stream().map(card -> {
                    SkillDefinition def = view.rules().skill(card.definitionId());
                    return def == null ? "Unknown skill" : def.name() + " - " + def.manaCost()
                            + " MANA\n" + SkillText.render(def);
                }).collect(java.util.stream.Collectors.joining("\n\n"));
        setReference(java.util.List.of("spent", view.spentSkills(), view.rules()), "SPENT SKILLS", null,
                "PUBLIC - BOTH PLAYERS\n\n" + text);
    }

    void idle() {
        setReference("idle", "YOUR NEXT ACTION", null,
                "Hover a card to inspect its artwork and full rules. The last preview stays here while you read or scroll. Click a card to choose targets."
                + "\n\n1. Move with a movement card, or skip.\n\n2. Cast up to one skill, or pass."
                + "\n\n3. End your turn.\n\nMovement hands are public. Opponent skill cards stay hidden.");
    }

    void clear() {
        spentButton.setSelected(false);
        shownKey = null;
        idle();
        guidance.setText("");
        for (JLabel step : steps) step.setBackground(Theme.P1_LIGHT);
    }

    private void setReference(Object key, String name, BufferedImage image, String text) {
        if (Objects.equals(shownKey, key)) return;
        shownKey = key;
        face = image;
        title.setText(name);
        title.setToolTipText(name);
        imageHolder.setVisible(image != null);
        description.setText(text);
        description.setCaretPosition(0);
        referenceScroll.getViewport().setViewPosition(new java.awt.Point());
        revalidate();
        repaint();
    }
}
