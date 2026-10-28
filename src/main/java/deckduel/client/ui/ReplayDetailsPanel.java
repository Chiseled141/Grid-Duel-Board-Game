package deckduel.client.ui;

import deckduel.client.state.ReplayIndex;
import deckduel.client.state.ReplayPlayback;
import deckduel.client.state.ReplayScoreChange;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;

/** Optional journal and score-change drawer. It can seek a replay but cannot send game actions. */
final class ReplayDetailsPanel extends JPanel {
    private final DefaultListModel<ReplayIndex.Entry> rows = new DefaultListModel<>();
    private final JList<ReplayIndex.Entry> journal = new JList<>(rows);
    private final JComboBox<String> filter = new JComboBox<>(new String[] {
            "All actions", "Spells", "Kings", "Round changes", "Result", "Recording gaps"});
    private final JCheckBox keyOnly = new JCheckBox("Key moments");
    private final JTextField search = new JTextField();
    private final JLabel count = new JLabel();
    private final JTextArea action = text(), changes = text(), lesson = text();
    private ReplayPlayback playback;
    private boolean updating;

    ReplayDetailsPanel() {
        super(new java.awt.CardLayout());
        setName("replayDetails"); setOpaque(false);
        setBorder(BorderFactory.createLineBorder(Theme.BRASS));
        var tabs = new JTabbedPane(); tabs.setName("replayDetailTabs");
        tabs.setUI(new javax.swing.plaf.basic.BasicTabbedPaneUI() {
            @Override protected void installDefaults() {
                super.installDefaults();
                highlight = Theme.BRASS; lightHighlight = Theme.BRASS;
                shadow = Theme.IRON_DARK; darkShadow = Theme.IRON_DARK;
                focus = Theme.BRASS_LIGHT;
            }
            @Override protected void paintTabBackground(java.awt.Graphics graphics, int placement, int index,
                    int x, int y, int width, int height, boolean selected) {
                graphics.setColor(selected ? Theme.BRASS : Theme.IRON_DARK);
                graphics.fillRect(x, y, width, height);
            }
        });
        tabs.setFont(Theme.bold(12)); tabs.setBackground(Theme.IRON_DARK); tabs.setForeground(Theme.CREAM);
        tabs.addTab("Actions", journalPage());
        tabs.addTab("Score changes", scroll(changes));
        add(tabs, "replay");
        lesson.setName("guideStepDetails"); add(scroll(lesson), "lesson");
        changes.setName("replayScoreChanges"); action.setName("replayJournalAction");
        journal.setName("replayJournal"); filter.setName("replayJournalFilter");
        keyOnly.setName("replayKeyMoments"); count.setName("replayJournalCount");
        search.setName("replayJournalSearch");
        journal.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        journal.setBackground(Theme.IRON_DARK); journal.setForeground(Theme.CREAM);
        journal.setFont(Theme.normal(12)); journal.setFixedCellHeight(42);
        journal.getAccessibleContext().setAccessibleName("Recorded actions; select a row to seek");
        journal.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                putClientProperty("html.disable", Boolean.TRUE);
                ReplayIndex.Entry entry = (ReplayIndex.Entry) value;
                setText(entry.caption()); setToolTipText(entry.caption());
                setBackground(selected ? Theme.BRASS : Theme.IRON_DARK);
                setForeground(selected ? Theme.INK : entry.keyMoment() ? Theme.BRASS_LIGHT : Theme.CREAM);
                setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                return this;
            }
        });
        filter.addActionListener(event -> rebuildJournal());
        keyOnly.addActionListener(event -> rebuildJournal());
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent event) { rebuildJournal(); }
            public void removeUpdate(javax.swing.event.DocumentEvent event) { rebuildJournal(); }
            public void changedUpdate(javax.swing.event.DocumentEvent event) { rebuildJournal(); }
        });
        journal.addListSelectionListener(event -> {
            if (updating || event.getValueIsAdjusting() || playback == null) return;
            ReplayIndex.Entry selected = journal.getSelectedValue();
            if (selected != null) playback.seek(selected.index());
        });
    }

    private JPanel journalPage() {
        JPanel page = new JPanel(new BorderLayout(0, 6)); page.setBackground(Theme.IRON_DARK);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4)); controls.setOpaque(false);
        filter.setFont(Theme.normal(11)); filter.setBackground(Theme.IRON_DARK); filter.setForeground(Theme.CREAM);
        keyOnly.setOpaque(false); keyOnly.setForeground(Theme.CREAM); keyOnly.setFont(Theme.normal(11));
        count.setForeground(Theme.FADED); count.setFont(Theme.normal(11));
        controls.add(filter); controls.add(keyOnly);
        JPanel heading = new JPanel(new BorderLayout()); heading.setOpaque(false);
        search.setFont(Theme.normal(12)); search.setForeground(Theme.CREAM); search.setBackground(Theme.IRON_DARK);
        search.setCaretColor(Theme.CREAM);
        search.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.BRASS),
                BorderFactory.createEmptyBorder(5, 6, 5, 6)));
        search.getAccessibleContext().setAccessibleName("Search recorded actions");
        search.setToolTipText("Search action text within this recording");
        heading.add(controls, BorderLayout.NORTH); heading.add(search, BorderLayout.CENTER);
        heading.add(count, BorderLayout.SOUTH);
        page.add(heading, BorderLayout.NORTH); page.add(scroll(journal), BorderLayout.CENTER);
        var description = scroll(action); description.setPreferredSize(new java.awt.Dimension(200, 100));
        page.add(description, BorderLayout.SOUTH);
        return page;
    }

    void attach(ReplayPlayback next) {
        playback = next;
        updating = true;
        try { filter.setSelectedIndex(0); keyOnly.setSelected(false); search.setText(""); }
        finally { updating = false; }
        rebuildJournal(); refresh();
    }

    private ReplayIndex.Kind kind() {
        return switch (filter.getSelectedIndex()) {
            case 1 -> ReplayIndex.Kind.SPELL;
            case 2 -> ReplayIndex.Kind.KING;
            case 3 -> ReplayIndex.Kind.ROUND;
            case 4 -> ReplayIndex.Kind.RESULT;
            case 5 -> ReplayIndex.Kind.GAP;
            default -> null;
        };
    }

    private void rebuildJournal() {
        if (updating) return;
        updating = true;
        try {
            rows.clear();
            if (playback != null) {
                List<ReplayIndex.Entry> visible = playback.navigation().matching(kind(), keyOnly.isSelected(), search.getText());
                rows.addAll(visible);
                count.setText(visible.size() + " of " + playback.length() + " recorded positions"
                        + (visible.isEmpty() ? " · No matching actions" : ""));
            } else count.setText("No recording");
        } finally { updating = false; }
        selectCurrent();
    }

    void refresh() {
        if (playback == null) { changes.setText(""); action.setText(""); return; }
        selectCurrent();
        action.setText(playback.navigation().at(playback.index()).caption()); action.setCaretPosition(0);
        changes.setText(scoreDescription(playback)); changes.setCaretPosition(0);
        lesson.setText(playback.action() + "\n\nYOU\n" + lessonScore(playback.frame().you())
                + "\n\nOPPONENT\n" + lessonScore(playback.frame().opponent()));
        lesson.setCaretPosition(0);
    }

    void training(boolean training) {
        ((java.awt.CardLayout) getLayout()).show(this, training ? "lesson" : "replay");
    }

    private static String lessonScore(deckduel.core.card.SideView side) {
        var score = side.score();
        return "Units: " + score.units() + "\nCombos: " + score.combos()
                + "\nFormation: " + (score.formation() == null ? "None" : score.formation().title())
                + "\nFormation bonus: +" + score.formationBonus() + " · ×" + score.multiplier()
                + "\nKings: +" + score.kingPoints() + "\nSeven nations: +" + score.bonus()
                + "\nRound score: " + score.total() + "\nMatch total: " + side.matchTotal();
    }

    private void selectCurrent() {
        updating = true;
        try {
            int selected = -1;
            if (playback != null) for (int i = 0; i < rows.size(); i++)
                if (rows.get(i).index() == playback.index()) { selected = i; break; }
            if (selected < 0) journal.clearSelection(); else journal.setSelectedIndex(selected);
            if (selected >= 0) journal.ensureIndexIsVisible(selected);
        } finally { updating = false; }
    }

    private static String scoreDescription(ReplayPlayback playback) {
        ReplayScoreChange change = playback.scoreChange();
        StringBuilder text = new StringBuilder(playback.position()).append("\n\n")
                .append(change.context()).append("\n\n");
        describeSide(text, "YOU", change.you());
        describeSide(text, playback.replay().opponent(), change.opponent());
        text.append("Multiplier effect = (Units + Combos + Formation bonus) × (multiplier - 1).\n")
                .append("Seven-nation and King points are added after multiplication.\n\n")
                .append("These are differences between stored snapshots. Round boundaries include clearing and dealing; ")
                .append("the recording may not contain a separate final pre-bank snapshot.\n\n")
                .append("Recorded action:\n").append(playback.action());
        return text.toString();
    }

    private static void describeSide(StringBuilder text, String name, ReplayScoreChange.Side side) {
        text.append(name).append("\nLive score: ").append(side.before()).append(" -> ").append(side.after())
                .append(" (").append(ReplayScoreChange.signed(side.delta())).append(")\n");
        text.append("Formation: ").append(side.formationBefore()).append(" -> ").append(side.formationAfter()).append('\n')
                .append("Multiplier: ×").append(side.multiplierBefore()).append(" -> ×").append(side.multiplierAfter()).append('\n');
        for (var component : side.components()) text.append(component.description()).append('\n');
        for (var king : side.kingChanges()) text.append("  ").append(king.description()).append('\n');
        text.append("Banked total: ").append(side.bankedBefore()).append(" -> ").append(side.bankedAfter())
                .append(" (").append(ReplayScoreChange.signed(side.bankedDelta())).append(")\n\n");
    }

    private static JTextArea text() {
        JTextArea area = new JTextArea(); area.setEditable(false);
        area.setLineWrap(true); area.setWrapStyleWord(true);
        area.setFont(Theme.normal(12)); area.setForeground(Theme.CREAM); area.setBackground(Theme.IRON_DARK);
        area.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        return area;
    }

    private static JScrollPane scroll(Component child) {
        JScrollPane scroll = new JScrollPane(child); scroll.setBorder(null);
        scroll.getViewport().setBackground(Theme.IRON_DARK);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setPreferredSize(new java.awt.Dimension(8, 0));
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.getVerticalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() {
                thumbColor = Theme.BRASS; trackColor = Theme.IRON_DARK;
            }
            @Override protected javax.swing.JButton createDecreaseButton(int direction) { return hiddenArrow(); }
            @Override protected javax.swing.JButton createIncreaseButton(int direction) { return hiddenArrow(); }
            private javax.swing.JButton hiddenArrow() {
                var button = new javax.swing.JButton(); button.setPreferredSize(new java.awt.Dimension(0, 0));
                return button;
            }
        });
        return scroll;
    }
}
