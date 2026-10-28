package deckduel.client.ui;

import deckduel.client.state.HistoryFilter;
import java.awt.Color;
import java.awt.FlowLayout;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Search controls for the entries loaded into History; no request or database access. */
final class HistoryFiltersPanel extends JPanel {
    private final JTextField search = new JTextField(14);
    private final JComboBox<HistoryFilter.Result> result = new JComboBox<>(HistoryFilter.Result.values());
    private final JComboBox<HistoryFilter.Mode> mode = new JComboBox<>(HistoryFilter.Mode.values());
    private final JButton reset = UiKit.pill("Clear", UiKit.Pill.GOLD_OUTLINE);
    private final Consumer<HistoryFilter> changed;
    private boolean updating;

    HistoryFiltersPanel(Consumer<HistoryFilter> changed) {
        super(new FlowLayout(FlowLayout.LEFT, 8, 3));
        this.changed = changed;
        setOpaque(false); setName("historyFilters");
        JLabel caption = new JLabel("Opponent"); caption.setForeground(Theme.CREAM); caption.setFont(Theme.normal(12));
        search.setName("historySearch"); result.setName("historyResultFilter"); mode.setName("historyModeFilter");
        reset.setName("historyClearFilters");
        search.setFont(Theme.normal(13)); search.setForeground(Theme.CREAM); search.setCaretColor(Theme.CREAM);
        search.setBackground(Theme.IRON_DARK);
        search.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.BRASS),
                BorderFactory.createEmptyBorder(6, 7, 6, 7)));
        search.getAccessibleContext().setAccessibleName("Search opponent name");
        search.setToolTipText("Search the matches already loaded from the server");
        caption.setLabelFor(search);
        for (JComboBox<?> combo : new JComboBox<?>[] {result, mode}) {
            combo.setFont(Theme.normal(12)); combo.setForeground(Theme.CREAM); combo.setBackground(Theme.IRON_DARK);
            combo.addActionListener(event -> notifyChange());
        }
        result.getAccessibleContext().setAccessibleName("Filter match result");
        mode.getAccessibleContext().setAccessibleName("Filter online or practice matches");
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { notifyChange(); }
            public void removeUpdate(DocumentEvent event) { notifyChange(); }
            public void changedUpdate(DocumentEvent event) { notifyChange(); }
        });
        reset.addActionListener(event -> clear());
        add(caption); add(search); add(result); add(mode); add(reset);
        reset.setEnabled(false);
    }

    HistoryFilter filter() {
        return new HistoryFilter(search.getText(), (HistoryFilter.Result) result.getSelectedItem(),
                (HistoryFilter.Mode) mode.getSelectedItem());
    }

    private void notifyChange() {
        if (updating) return;
        HistoryFilter filter = filter();
        reset.setEnabled(filter.active());
        changed.accept(filter);
    }

    private void clear() {
        updating = true;
        try { search.setText(""); result.setSelectedIndex(0); mode.setSelectedIndex(0); }
        finally { updating = false; }
        notifyChange(); search.requestFocusInWindow();
    }
}
