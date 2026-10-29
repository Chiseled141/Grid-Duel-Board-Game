package deckduel.client.ui;

import deckduel.client.ClientSettings;
import deckduel.client.state.ClientModel;
import deckduel.client.state.ClientModelListener;
import deckduel.client.state.Screen;
import deckduel.client.state.HistoryFilter;
import deckduel.net.HistoryEntry;
import java.awt.*;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * The account's recorded matches, newest first, over the animated main-menu scenery. Selecting a
 * match with a recording and pressing Watch replay (or double-clicking it) opens its replay.
 */
public final class HistoryPanel extends BattlefieldMenuPanel {
    private final ClientModel model;
    private final JPanel ledger;
    private final JLabel summary = menuLabel("", 13, false);
    private final JLabel footnote = menuLabel("", 12, false);
    private final JPanel views = new JPanel(new CardLayout()) {
        @Override protected void paintComponent(Graphics graphics) {
            var g = UiKit.nice(graphics);
            UiKit.paintPlate(g, getWidth() - 4, getHeight() - 4);
            g.dispose();
        }
    };
    private final JTable table;
    private final JButton watch = UiKit.pill("Watch replay", UiKit.Pill.GOLD);
    private final JLabel replayStatus = menuLabel(" ", 12, false);
    private final JLabel emptyTitle = menuLabel("No recorded matches yet", 19, false);
    private final JLabel emptyDetail = menuLabel("Online and practice matches appear here.", 13, false);
    private final HistoryFiltersPanel filters;
    // The entries behind the table's rows, in the same order.
    private java.util.List<HistoryEntry> shown = java.util.List.of();
    private final DefaultTableModel rows = new DefaultTableModel(
            new Object[]{"DATE", "OPPONENT", "RESULT", "SCORE", "ROUNDS", "REPLAY"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };

    /** Uses shared scene preferences and returns through the supplied menu action. */
    public HistoryPanel(ClientModel model, ClientSettings settings, Runnable backAction) {
        super(model, Screen.HISTORY, settings);
        this.model = model;
        filters = new HistoryFiltersPanel(filter -> refresh());
        setLayout(null);
        ledger = new JPanel(new BorderLayout(0, 18));
        ledger.setName("historyLedger"); ledger.setOpaque(false);
        JPanel heading = new JPanel(); heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        JButton back = LobbyPanel.smallButton("Back", backAction);
        back.setName("historyBack");
        back.setMaximumSize(back.getPreferredSize());
        back.setAlignmentX(Component.LEFT_ALIGNMENT);
        heading.add(back); heading.add(Box.createVerticalStrut(12));
        heading.add(menuLabel("Match history", 32, true));
        heading.add(Box.createVerticalStrut(6)); heading.add(summary);
        filters.setAlignmentX(Component.LEFT_ALIGNMENT);
        filters.setMinimumSize(new Dimension(0, 60));
        filters.setPreferredSize(new Dimension(600, 60));
        filters.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        heading.add(Box.createVerticalStrut(8)); heading.add(filters);
        ledger.add(heading, BorderLayout.NORTH);
        table = new JTable(rows);
        table.setName("historyTable");
        table.setRowHeight(48); table.setFont(Theme.normal(15));
        table.setForeground(Theme.CREAM); table.setBackground(Theme.IRON_DARK); table.setOpaque(false);
        table.setShowGrid(false); table.setIntercellSpacing(new Dimension(0, 2));
        table.setFillsViewportHeight(false);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setPreferredSize(new Dimension(0, 36));
        // The header is see-through, so its gold labels sit straight on the iron plate.
        table.getTableHeader().setOpaque(false);
        table.getTableHeader().setDefaultRenderer((t, value, selected, focus, row, column) -> {
            JLabel label = new JLabel(String.valueOf(value), column == 1 || column == 4 ? SwingConstants.LEFT : SwingConstants.CENTER);
            label.setFont(Theme.bold(11)); label.setForeground(Theme.BRASS_LIGHT);
            label.setOpaque(false);
            label.setBorder(BorderFactory.createEmptyBorder(8, column == 1 ? 14 : 0, 8, 0));
            return label;
        });
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean selected, boolean focus, int row, int column) {
                super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                putClientProperty("html.disable", Boolean.TRUE);
                setOpaque(true);
                // Rows alternate two iron tones; the selected row is aged gold, like the menu's selected tile.
                setBackground(selected ? Theme.BRASS : row % 2 == 0 ? new Color(0x342F2C) : new Color(0x262220));
                setForeground(selected ? Theme.INK : column == 2 ? resultColor(String.valueOf(value)) : Theme.CREAM);
                setFont(column == 2 || column == 3 ? Theme.bold(14) : Theme.normal(14));
                boolean left = column == 1 || column == 4;
                setHorizontalAlignment(left ? SwingConstants.LEFT : SwingConstants.CENTER);
                setBorder(BorderFactory.createEmptyBorder(8, left ? 14 : 0, 8, 0));
                setToolTipText(column == 4 ? String.valueOf(value) : null);
                return this;
            }
        });
        int[] widths = {120, 130, 110, 90, 200, 110};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            if (i != 1 && i != 4) table.getColumnModel().getColumn(i).setMaxWidth(widths[i]);
        }
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(null); scroll.setOpaque(false); scroll.getViewport().setOpaque(false);
        scroll.setColumnHeaderView(table.getTableHeader());
        scroll.getColumnHeader().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(48);
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(7, 0));
        scroll.getVerticalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() { thumbColor = Theme.BRASS; trackColor = Theme.IRON_DARK; }
            @Override protected JButton createDecreaseButton(int orientation) { return hiddenArrow(); }
            @Override protected JButton createIncreaseButton(int orientation) { return hiddenArrow(); }
            private JButton hiddenArrow() { var button = new JButton(); button.setPreferredSize(new Dimension(0, 0)); return button; }
        });
        JPanel corner = new JPanel(); corner.setOpaque(false);
        scroll.setCorner(ScrollPaneConstants.UPPER_RIGHT_CORNER, corner);
        JPanel empty = new JPanel(new GridBagLayout()); empty.setOpaque(false);
        JPanel emptyCopy = new JPanel(); emptyCopy.setOpaque(false);
        emptyCopy.setLayout(new BoxLayout(emptyCopy, BoxLayout.Y_AXIS));
        emptyTitle.setName("historyEmptyTitle"); emptyDetail.setName("historyEmptyDetail");
        emptyCopy.add(emptyTitle);
        emptyCopy.add(Box.createVerticalStrut(8));
        emptyCopy.add(emptyDetail);
        empty.add(emptyCopy);
        views.setOpaque(false);
        // Room inside the plate's trim and studs, and for its shadow on the right and bottom.
        views.setBorder(BorderFactory.createEmptyBorder(16, 16, 20, 20));
        views.add(scroll, "table"); views.add(empty, "empty");
        ledger.add(views, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(16, 0)); footer.setOpaque(false);
        LobbyPanel.bindBack(this, backAction);
        // Extra right inset: the shadowed text paints slightly wider than the label measures.
        footnote.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 16));
        footer.add(footnote, BorderLayout.EAST);
        JPanel replayControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        replayControls.setOpaque(false);
        watch.setName("watchReplay");
        watch.addActionListener(event -> watchSelected());
        replayStatus.setName("replayStatus");
        replayControls.add(watch);
        replayControls.add(replayStatus);
        footer.add(replayControls, BorderLayout.WEST);
        table.getSelectionModel().addListSelectionListener(event -> updateReplayControls());
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2) watchSelected();
            }
        });
        ledger.add(footer, BorderLayout.SOUTH); add(ledger);
        model.addListener(new ClientModelListener() {
            @Override public void onHistoryChanged() { refresh(); }
            @Override public void onLobbyChanged() { updateReplayControls(); }
        });
        refresh();
    }

    private static JLabel menuLabel(String text, float size, boolean display) {
        JLabel label = new JLabel(text) {
            @Override protected void paintComponent(Graphics graphics) {
                var g = UiKit.nice(graphics); g.setFont(getFont());
                int y = (getHeight() - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent();
                g.setColor(new Color(10, 19, 22, 180)); g.drawString(getText(), 1, y + 2);
                g.setColor(getForeground()); g.drawString(getText(), 0, y); g.dispose();
            }
        };
        label.putClientProperty("html.disable", Boolean.TRUE);
        label.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 8));
        label.setFont(display ? Theme.display(size) : Theme.normal(size)); label.setForeground(Theme.CREAM);
        return label;
    }

    private void refresh() {
        HistoryEntry selected = selected();
        rows.setRowCount(0);
        HistoryFilter filter = filters.filter();
        shown = filter.apply(model.history());
        boolean legacy = false;
        for (HistoryEntry match : shown) {
            boolean scored = !match.yourRounds().isEmpty();
            legacy |= !scored;
            String result = label(match.result()) + ("FORFEIT".equals(match.endReason()) ? " (forfeit)" : "");
            rows.addRow(new Object[]{date(match.playedAt()), match.opponent(), result,
                    scored ? match.yourTotal() + "–" + match.opponentTotal() : "—", rounds(match), replayLabel(match.replay())});
        }
        summary.setText(HistoryFilter.summarize(shown).description()
                + (filter.active() ? " · " + shown.size() + " of " + model.history().size() + " loaded" : ""));
        emptyTitle.setText(filter.active() ? "No matches found" : "No recorded matches yet");
        emptyDetail.setText(filter.active() ? "Change the filters or press Clear to show loaded matches."
                : "Online and practice matches appear here.");
        for (int i = 0; selected != null && i < shown.size(); i++) {
            HistoryEntry entry = shown.get(i);
            if (entry.equals(selected) || entry.replayId() != null && entry.replayId().equals(selected.replayId())) {
                table.setRowSelectionInterval(i, i); break;
            }
        }
        footnote.setText(model.isOffline() ? "Offline: history and replays last only for this session."
                : legacy ? "Matches from the retired board game have no card scores." : "Round scores are banked into the match total.");
        updateReplayControls();
        ((CardLayout) views.getLayout()).show(views, rows.getRowCount() == 0 ? "empty" : "table");
        revalidate(); repaint();
    }

    private HistoryEntry selected() {
        int row = table.getSelectedRow();
        return row < 0 || row >= shown.size() ? null : shown.get(row);
    }

    private void updateReplayControls() {
        HistoryEntry match = selected();
        watch.setEnabled(match != null && match.replay() != HistoryEntry.Replay.UNAVAILABLE);
        replayStatus.setText(match == null ? "Select a match to watch it again."
                : switch (match.replay()) {
                    case AVAILABLE -> "Replay ready.";
                    case INCOMPLETE -> "Incomplete recording: some actions are missing.";
                    case UNAVAILABLE -> "Replay unavailable.";
                });
    }

    private void watchSelected() {
        HistoryEntry match = selected();
        if (match != null && match.replayId() != null) model.requestReplay(match.replayId());
    }

    private static String replayLabel(HistoryEntry.Replay replay) {
        return switch (replay) {
            case AVAILABLE -> "Watch";
            case INCOMPLETE -> "Incomplete";
            case UNAVAILABLE -> "Unavailable";
        };
    }

    private static String label(HistoryEntry.Result result) {
        return result.name().charAt(0) + result.name().substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    private static Color resultColor(String result) {
        return result.startsWith("Win") ? Theme.BRASS_LIGHT : result.startsWith("Loss") ? Theme.CORAL : Theme.CREAM;
    }

    /** ISO timestamps from the database, shown to the minute. */
    private static String date(String playedAt) {
        return playedAt.length() >= 16 ? playedAt.substring(0, 16).replace('T', ' ') : playedAt;
    }

    private static String rounds(HistoryEntry match) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < match.yourRounds().size(); i++)
            text.append(i == 0 ? "" : " · ").append(match.yourRounds().get(i)).append("–").append(match.opponentRounds().get(i));
        return text.toString();
    }

    @Override public void doLayout() {
        int width = Math.max(1, Math.min(820, getWidth() - 64));
        int visibleRows = Math.max(3, Math.min(rows.getRowCount(), 8));
        int height = Math.max(1, Math.min(286 + visibleRows * 48, getHeight() - 112));
        ledger.setBounds(Math.max(0, getWidth() - width - 40), Math.max(0, (getHeight() - height) / 2), width, height);
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        var g = (Graphics2D) graphics.create();
        float left = getWidth() * .48f;
        g.setPaint(new GradientPaint(left, 0, new Color(16, 23, 25, 0), getWidth(), 0, new Color(16, 23, 25, 180)));
        g.fillRect((int) left, 0, getWidth() - (int) left, getHeight()); g.dispose();
    }
}
