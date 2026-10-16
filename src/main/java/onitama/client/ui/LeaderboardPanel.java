package onitama.client.ui;

import onitama.client.ClientSettings;
import onitama.client.state.ClientModel;
import onitama.client.state.ClientModelListener;
import onitama.client.state.Screen;
import java.awt.*;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/** Parchment ranking ledger over the shared animated main-menu battlefield. */
public final class LeaderboardPanel extends BattlefieldMenuPanel {
    private final ClientModel model;
    private final JPanel ledger;
    private final JLabel summary = new JLabel();
    private final JPanel views = new JPanel(new CardLayout());
    private final DefaultTableModel rows = new DefaultTableModel(
            new Object[]{"RANK", "PLAYER", "ELO", "WINS", "LOSSES"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };

    /** Uses shared scene preferences and returns through the supplied menu action. */
    public LeaderboardPanel(ClientModel model, ClientSettings settings, Runnable backAction) {
        super(model, Screen.LEADERBOARD, settings);
        this.model = model;
        setLayout(null);
        ledger = new JPanel(new BorderLayout(0, 18)) {
            @Override protected void paintComponent(Graphics graphics) {
                var g = UiKit.nice(graphics);
                MatchStyle.parchment(g, getWidth(), getHeight());
                g.dispose();
            }
        };
        ledger.setName("rankingLedger");
        ledger.setOpaque(false);
        ledger.setBorder(BorderFactory.createEmptyBorder(27, 28, 28, 31));
        JPanel heading = new JPanel(new BorderLayout(0, 5));
        heading.setOpaque(false);
        JLabel title = new JLabel("Leaderboard");
        title.setFont(Theme.cardTitle(32)); title.setForeground(Theme.INK);
        heading.add(title, BorderLayout.NORTH);
        summary.setFont(Theme.normal(12)); summary.setForeground(new Color(116, 87, 48));
        heading.add(summary, BorderLayout.SOUTH);
        ledger.add(heading, BorderLayout.NORTH);
        JTable table = new JTable(rows);
        table.setName("leaderboardTable");
        table.setRowHeight(46);
        table.setFont(Theme.normal(14));
        table.setForeground(Theme.INK);
        table.setBackground(new Color(237, 223, 190));
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setGridColor(new Color(202, 181, 138));
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setFillsViewportHeight(false);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setPreferredSize(new Dimension(0, 34));
        table.getTableHeader().setDefaultRenderer((t, value, selected, focus, row, column) -> {
            JLabel label = new JLabel(String.valueOf(value), column == 1 ? SwingConstants.LEFT : SwingConstants.CENTER);
            label.setFont(Theme.bold(10)); label.setForeground(new Color(110, 76, 35));
            label.setOpaque(true); label.setBackground(new Color(218, 196, 151));
            label.setBorder(BorderFactory.createEmptyBorder(5, column == 1 ? 12 : 0, 5, 0));
            return label;
        });
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean selected, boolean focus, int row, int column) {
                super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                putClientProperty("html.disable", Boolean.TRUE);
                boolean own = model.me() != null && model.me().username().equalsIgnoreCase(String.valueOf(rows.getValueAt(row, 1)));
                setOpaque(true);
                setBackground(selected ? new Color(211, 185, 130) : own ? new Color(224, 202, 153)
                        : row % 2 == 0 ? new Color(243, 231, 202) : new Color(236, 220, 184));
                setForeground(Theme.INK);
                setFont(column == 0 ? Theme.cardTitle(17) : own ? Theme.bold(14) : Theme.normal(14));
                setHorizontalAlignment(column == 1 ? SwingConstants.LEFT : SwingConstants.CENTER);
                setBorder(BorderFactory.createEmptyBorder(7, column == 1 ? 12 : 0, 7, 0));
                setToolTipText(null);
                return this;
            }
        });
        int[] widths = {65, 260, 90, 75, 75};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            if (i != 1) table.getColumnModel().getColumn(i).setMaxWidth(widths[i]);
        }
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, new Color(165, 129, 75)));
        scroll.setOpaque(false); scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(46);
        JPanel empty = new JPanel(new GridBagLayout()); empty.setOpaque(false);
        JLabel emptyText = new JLabel("No ranked players yet. Play a match to begin.");
        emptyText.setFont(Theme.normal(14)); emptyText.setForeground(new Color(116, 87, 48));
        empty.add(emptyText);
        views.setOpaque(false); views.add(scroll, "table"); views.add(empty, "empty");
        ledger.add(views, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout()); footer.setOpaque(false);
        JButton back = UiKit.pill("Back to menu", UiKit.Pill.PARCHMENT);
        back.addActionListener(event -> backAction.run());
        footer.add(back, BorderLayout.WEST);
        ledger.add(footer, BorderLayout.SOUTH);
        add(ledger);
        model.addListener(new ClientModelListener() {
            @Override public void onLeaderboardChanged() { refresh(); }
        });
        refresh();
    }

    private void refresh() {
        rows.setRowCount(0);
        int rank = 1;
        for (var player : model.leaderboard()) rows.addRow(new Object[]{rank++, player.username(),
                player.elo(), player.wins(), player.losses()});
        summary.setText("Ranked by Elo · " + rows.getRowCount() + " players");
        ((CardLayout) views.getLayout()).show(views, rows.getRowCount() == 0 ? "empty" : "table");
    }

    @Override public void doLayout() {
        int width = Math.max(1, Math.min(760, getWidth() - 64));
        int height = Math.max(1, Math.min(720, getHeight() - 80));
        ledger.setBounds(Math.max(0, getWidth() - width - 40), Math.max(0, (getHeight() - height) / 2), width, height);
    }
}
