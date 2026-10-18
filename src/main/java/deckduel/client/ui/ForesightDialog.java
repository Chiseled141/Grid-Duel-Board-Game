package deckduel.client.ui;

import deckduel.core.PlayerColor;
import deckduel.core.duel.CardInstance;
import deckduel.core.duel.RulesConfig;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * Non-cancellable deck preview; the player may still resign while choosing.
 */
public final class ForesightDialog extends JDialog {

    private final List<CardInstance> cards;
    private final RulesConfig rules;
    private final PlayerColor viewer;
    private final Consumer<List<String>> onConfirm;
    private final JPanel cardsRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 12));
    private final JButton confirm = UiKit.pill("Confirm Deck Order", UiKit.Pill.GOLD);
    private final JButton resign = UiKit.pill("Surrender", UiKit.Pill.DANGER);

    public ForesightDialog(Window parent,
                           List<CardInstance> initialPreview,
                           RulesConfig rules,
                           PlayerColor viewer,
                           Consumer<List<String>> onConfirm,
                           Runnable onResign) {
        super(parent, "Foresight — Deck Reordering", ModalityType.MODELESS);
        this.cards = new ArrayList<>(Objects.requireNonNull(initialPreview, "preview must not be null"));
        this.rules = Objects.requireNonNull(rules, "rules must not be null");
        this.viewer = viewer;
        this.onConfirm = Objects.requireNonNull(onConfirm, "onConfirm must not be null");

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE); // Cannot cancel pending choice
        setLayout(new BorderLayout(10, 10));
        getContentPane().setBackground(Theme.BG);

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(true);
        header.setBackground(Theme.BG);
        header.setBorder(BorderFactory.createEmptyBorder(18, 20, 10, 20));

        JLabel title = UiKit.boldLabel("FORESIGHT — REORDER SKILL DECK", 16f);
        title.setForeground(Theme.AMBER);
        title.setAlignmentX(CENTER_ALIGNMENT);
        header.add(title);
        header.add(Box.createVerticalStrut(6));

        JLabel subtitle = UiKit.label("Arrange the top " + cards.size()
                + " cards. The first card on the left is drawn next.", 12f);
        subtitle.setForeground(Theme.CREAM);
        subtitle.setAlignmentX(CENTER_ALIGNMENT);
        header.add(subtitle);

        add(header, BorderLayout.NORTH);

        cardsRow.setOpaque(false);
        add(cardsRow, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 16));
        footer.setOpaque(false);
        confirm.addActionListener(e -> {
            List<String> order = cards.stream().map(CardInstance::instanceId).toList();
            onConfirm.accept(order);
        });
        resign.addActionListener(e -> onResign.run());
        footer.add(resign);
        footer.add(confirm);
        add(footer, BorderLayout.SOUTH);

        rebuildCards();
        pack();
        setLocationRelativeTo(parent);
    }

    /** Keep the preview open until acceptance; paused matches still permit resignation. */
    public void setActionsEnabled(boolean canConfirm, boolean canResign) {
        confirm.setEnabled(canConfirm);
        resign.setEnabled(canResign);
    }

    private void rebuildCards() {
        cardsRow.removeAll();
        for (int i = 0; i < cards.size(); i++) {
            final int index = i;
            CardInstance instance = cards.get(i);

            JPanel slot = new JPanel(new BorderLayout(0, 6));
            slot.setOpaque(false);

            JLabel posLabel = UiKit.caption("#" + (i + 1) + (i == 0 ? " (TOP)" : ""), Theme.AMBER);
            posLabel.setHorizontalAlignment(SwingConstants.CENTER);
            slot.add(posLabel, BorderLayout.NORTH);

            SkillCardPanel cardPanel = new SkillCardPanel(instance, rules, viewer,
                    99, deckduel.core.duel.TurnPhase.SKILL, true, false, null);
            slot.add(cardPanel, BorderLayout.CENTER);

            JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
            controls.setOpaque(false);
            if (i > 0) {
                JButton leftBtn = UiKit.pill("Left", UiKit.Pill.CREAM_OUTLINE);
                leftBtn.addActionListener(e -> {
                    Collections.swap(cards, index, index - 1);
                    rebuildCards();
                });
                controls.add(leftBtn);
            }
            if (i < cards.size() - 1) {
                JButton rightBtn = UiKit.pill("Right", UiKit.Pill.CREAM_OUTLINE);
                rightBtn.addActionListener(e -> {
                    Collections.swap(cards, index, index + 1);
                    rebuildCards();
                });
                controls.add(rightBtn);
            }
            slot.add(controls, BorderLayout.SOUTH);

            cardsRow.add(slot);
        }
        cardsRow.revalidate();
        cardsRow.repaint();
    }
}
