package deckduel.client.ui;

import deckduel.client.state.HistoryFilter;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;

/**
 * Search controls for the entries loaded into History, in the menu's iron and gold: a sunken
 * slot for the search, iron tiles with a brass arrow for the filters. No request or database access.
 */
final class HistoryFiltersPanel extends JPanel {
    private final JTextField search = new JTextField(14) {
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = UiKit.nice(graphics);
            UiKit.paintSlot(g, getWidth(), getHeight(), isFocusOwner());
            g.dispose();
            super.paintComponent(graphics);
        }
    };
    private final JComboBox<HistoryFilter.Result> result = new JComboBox<>(HistoryFilter.Result.values());
    private final JComboBox<HistoryFilter.Mode> mode = new JComboBox<>(HistoryFilter.Mode.values());
    private final JButton reset = UiKit.pill("Clear", UiKit.Pill.GOLD_OUTLINE);
    private final Consumer<HistoryFilter> changed;
    private boolean updating;

    HistoryFiltersPanel(Consumer<HistoryFilter> changed) {
        super(new FlowLayout(FlowLayout.LEFT, 8, 3));
        this.changed = changed;
        setOpaque(false); setName("historyFilters");
        JLabel caption = new JLabel("OPPONENT"); caption.setForeground(Theme.BRASS_LIGHT); caption.setFont(Theme.bold(11));
        search.setName("historySearch"); result.setName("historyResultFilter"); mode.setName("historyModeFilter");
        reset.setName("historyClearFilters");
        search.setFont(Theme.normal(14)); search.setForeground(Theme.CREAM); search.setCaretColor(Theme.BRASS_LIGHT);
        search.setOpaque(false);
        search.setSelectionColor(Theme.BRASS_DARK); search.setSelectedTextColor(Theme.CREAM);
        search.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        search.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent event) { search.repaint(); }
            @Override public void focusLost(java.awt.event.FocusEvent event) { search.repaint(); }
        });
        search.getAccessibleContext().setAccessibleName("Search opponent name");
        search.setToolTipText("Search the matches already loaded from the server");
        caption.setLabelFor(search);
        for (JComboBox<?> combo : new JComboBox<?>[] {result, mode}) {
            ironCombo(combo);
            combo.addActionListener(event -> notifyChange());
        }
        reset.setFont(Theme.display(12f));
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

    /** An iron tile with cream text and a brass arrow, whose list opens on the same dark iron. */
    private static void ironCombo(JComboBox<?> combo) {
        combo.setFont(Theme.normal(14)); combo.setForeground(Theme.CREAM); combo.setBackground(Theme.IRON_DARK);
        combo.setOpaque(false);
        combo.setBorder(BorderFactory.createEmptyBorder());
        combo.setPreferredSize(new Dimension(Math.max(132, combo.getPreferredSize().width + 16), 36));
        combo.setUI(new BasicComboBoxUI() {
            @Override public void paint(Graphics graphics, javax.swing.JComponent component) {
                Graphics2D g = UiKit.nice(graphics);
                UiKit.paintTile(g, component.getWidth(), component.getHeight(), false,
                        comboBox.isPopupVisible() || comboBox.hasFocus());
                int x = component.getWidth() - 18, y = component.getHeight() / 2 - 2;
                g.setColor(Theme.BRASS_LIGHT);
                g.fillPolygon(new int[] {x - 5, x + 5, x}, new int[] {y, y, y + 6}, 3);
                g.dispose();
                super.paint(graphics, component);
            }
            @Override public void paintCurrentValueBackground(Graphics graphics, java.awt.Rectangle bounds, boolean focus) { }
            @Override protected JButton createArrowButton() {
                JButton arrow = new JButton();
                arrow.setOpaque(false); arrow.setContentAreaFilled(false); arrow.setBorderPainted(false);
                arrow.setFocusable(false); arrow.setPreferredSize(new Dimension(28, 0));
                return arrow;
            }
            @Override protected ComboPopup createPopup() {
                BasicComboPopup popup = new BasicComboPopup(comboBox);
                popup.setBorder(BorderFactory.createLineBorder(Theme.BRASS));
                popup.getList().setBackground(Theme.IRON_DARK);
                return popup;
            }
        });
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                putClientProperty("html.disable", Boolean.TRUE);
                // index -1 is the closed box's own value, drawn over the iron tile.
                setOpaque(index >= 0);
                setBackground(selected ? Theme.BRASS : Theme.IRON_DARK);
                setForeground(index >= 0 && selected ? Theme.INK : Theme.CREAM);
                setFont(Theme.normal(14));
                setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
                return this;
            }
        });
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
