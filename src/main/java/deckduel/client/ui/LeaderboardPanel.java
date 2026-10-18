package deckduel.client.ui;

import deckduel.client.ClientSettings;
import deckduel.client.state.ClientModel;
import deckduel.client.state.ClientModelListener;
import deckduel.client.state.Screen;
import java.awt.*;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/** Compact cream-and-charcoal rankings over the animated main-menu scenery. */
public final class LeaderboardPanel extends BattlefieldMenuPanel {
    private static final Color IVORY = new Color(255, 238, 200);
    private static final Color DARK = new Color(25, 31, 29);
    private final ClientModel model;
    private final JPanel ledger;
    private final JLabel summary = menuLabel("", 13, false);
    private final JLabel ownRank = menuLabel("", 12, false);
    private final JPanel views = new JPanel(new CardLayout()) {
        @Override protected void paintComponent(Graphics graphics) {
            var g = UiKit.nice(graphics);
            g.setColor(new Color(20, 27, 26, 235));
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            g.setColor(new Color(255, 238, 200, 80));
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            g.dispose();
        }
    };
    private final DefaultTableModel rows = new DefaultTableModel(
            new Object[]{"RANK", "PLAYER", "ELO", "WINS", "LOSSES"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };

    /** Uses shared scene preferences and returns through the supplied menu action. */
    public LeaderboardPanel(ClientModel model, ClientSettings settings, Runnable backAction) {
        super(model, Screen.LEADERBOARD, settings);
        this.model = model;
        setLayout(null);
        ledger = new JPanel(new BorderLayout(0, 18));
        ledger.setName("rankingLedger"); ledger.setOpaque(false);
        JPanel heading = new JPanel(); heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        JButton back = LobbyPanel.smallButton("Back", backAction);
        back.setName("leaderboardBack");
        back.setMaximumSize(back.getPreferredSize());
        back.setAlignmentX(Component.LEFT_ALIGNMENT);
        heading.add(back); heading.add(Box.createVerticalStrut(12));
        heading.add(menuLabel("Leaderboard", 32, true));
        heading.add(Box.createVerticalStrut(6)); heading.add(summary);
        ledger.add(heading, BorderLayout.NORTH);
        JTable table = new JTable(rows);
        table.setName("leaderboardTable");
        table.setRowHeight(48); table.setFont(Theme.normal(15));
        table.setForeground(IVORY); table.setBackground(DARK); table.setOpaque(false);
        table.setShowGrid(false); table.setIntercellSpacing(new Dimension(0, 2));
        table.setFillsViewportHeight(false);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setPreferredSize(new Dimension(0, 36));
        table.getTableHeader().setDefaultRenderer((t, value, selected, focus, row, column) -> {
            JLabel label = new JLabel(String.valueOf(value), column == 1 ? SwingConstants.LEFT : SwingConstants.CENTER);
            label.setFont(Theme.bold(11)); label.setForeground(new Color(219, 204, 173));
            label.setOpaque(true); label.setBackground(DARK);
            label.setBorder(BorderFactory.createEmptyBorder(8, column == 1 ? 14 : 0, 8, 0));
            return label;
        });
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean selected, boolean focus, int row, int column) {
                super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                putClientProperty("html.disable", Boolean.TRUE);
                boolean own = model.me() != null && model.me().username().equalsIgnoreCase(String.valueOf(rows.getValueAt(row, 1)));
                setOpaque(true);
                setBackground(selected ? IVORY : own ? new Color(68, 59, 40)
                        : row % 2 == 0 ? new Color(36, 43, 39) : new Color(29, 36, 33));
                setForeground(selected ? new Color(25, 26, 23) : IVORY);
                setFont(column == 0 ? Theme.bold(17) : own || column == 2 ? Theme.bold(15) : Theme.normal(15));
                if (column == 0 && row < 3 && !selected) setForeground(new Color(245, 209, 139));
                setHorizontalAlignment(column == 1 ? SwingConstants.LEFT : SwingConstants.CENTER);
                var padding = BorderFactory.createEmptyBorder(8, column == 1 ? 14 : 0, 8, 0);
                setBorder(own && column == 0 ? BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 3, 0, 0, IVORY), padding) : padding);
                setToolTipText(null);
                return this;
            }
        });
        int[] widths = {62, 260, 90, 70, 70};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            if (i != 1) table.getColumnModel().getColumn(i).setMaxWidth(widths[i]);
        }
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(null); scroll.setOpaque(false); scroll.getViewport().setOpaque(false);
        scroll.setColumnHeaderView(table.getTableHeader());
        scroll.getColumnHeader().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(48);
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(7, 0));
        scroll.getVerticalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() { thumbColor = new Color(187, 168, 130); trackColor = DARK; }
            @Override protected JButton createDecreaseButton(int orientation) { return hiddenArrow(); }
            @Override protected JButton createIncreaseButton(int orientation) { return hiddenArrow(); }
            private JButton hiddenArrow() { var button = new JButton(); button.setPreferredSize(new Dimension(0, 0)); return button; }
        });
        JPanel corner = new JPanel(); corner.setBackground(DARK);
        scroll.setCorner(ScrollPaneConstants.UPPER_RIGHT_CORNER, corner);
        JPanel empty = new JPanel(new GridBagLayout()); empty.setOpaque(false);
        JPanel emptyCopy = new JPanel(); emptyCopy.setOpaque(false);
        emptyCopy.setLayout(new BoxLayout(emptyCopy, BoxLayout.Y_AXIS));
        emptyCopy.add(menuLabel("No players to show yet", 19, false));
        emptyCopy.add(Box.createVerticalStrut(8));
        emptyCopy.add(menuLabel("Play a match to start climbing the ranks.", 13, false));
        empty.add(emptyCopy);
        views.setOpaque(false); views.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        views.add(scroll, "table"); views.add(empty, "empty");
        ledger.add(views, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(16, 0)); footer.setOpaque(false);
        for (String key : java.util.List.of("ESCAPE", "BACK_SPACE"))
            getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(key), "menu.back");
        getActionMap().put("menu.back", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { backAction.run(); }
        });
        footer.add(ownRank, BorderLayout.EAST);
        ledger.add(footer, BorderLayout.SOUTH); add(ledger);
        model.addListener(new ClientModelListener() {
            @Override public void onLeaderboardChanged() { refresh(); }
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
        label.setFont(display ? Theme.display(size) : Theme.normal(size)); label.setForeground(IVORY);
        return label;
    }

    private void refresh() {
        rows.setRowCount(0); ownRank.setText("Top players on this server");
        int rank = 1;
        for (var player : model.leaderboard()) {
            rows.addRow(new Object[]{rank, player.username(), player.elo(), player.wins(), player.losses()});
            if (model.me() != null && model.me().username().equalsIgnoreCase(player.username()))
                ownRank.setText("You · #" + rank + " · " + player.elo() + " Elo");
            rank++;
        }
        summary.setText("Ranked by Elo · " + rows.getRowCount() + " players");
        ((CardLayout) views.getLayout()).show(views, rows.getRowCount() == 0 ? "empty" : "table");
        revalidate(); repaint();
    }

    @Override public void doLayout() {
        int width = Math.max(1, Math.min(720, getWidth() - 64));
        int visibleRows = Math.max(3, Math.min(rows.getRowCount(), 8));
        int height = Math.max(1, Math.min(222 + visibleRows * 48, getHeight() - 112));
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
