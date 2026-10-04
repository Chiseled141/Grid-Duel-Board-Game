package onitama.client.ui;

import onitama.client.state.ClientModel;
import onitama.client.state.ClientModelListener;
import onitama.net.UserProfile;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JComponent;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * The leaderboard screen: the top players by Elo in a rounded surface —
 * drawn medals for the top three ranks, the logged-in player's row tinted
 * amber — with a themed empty state before the first ranked game. Data
 * arrives via {@link ClientModelListener#onLeaderboardChanged()}.
 */
public final class LeaderboardPanel extends JPanel {

    private final ClientModel model;

    private final DefaultTableModel tableModel =
            new DefaultTableModel(new Object[]{"#", "PLAYER", "ELO", "W", "L"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
    private final JPanel boardViews = new JPanel(new java.awt.CardLayout());

    /** Builds the panel and subscribes it to the model. */
    public LeaderboardPanel(ClientModel model, Runnable backAction) {
        this.model = model;
        setLayout(new BorderLayout(10, 10));
        setBackground(Theme.BG);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        UiKit.HeadingLabel title = new UiKit.HeadingLabel(20f);
        title.setText("LEADERBOARD");
        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.CENTER));
        titleRow.setBackground(Theme.BG);
        titleRow.add(title);
        add(titleRow, BorderLayout.NORTH);

        // The table sits in a rounded dark surface like every other card.
        JPanel tableCard = UiKit.surface(8);
        tableCard.setLayout(new BorderLayout());
        JTable table = new JTable(tableModel);
        table.setBackground(Theme.SURFACE);
        table.setForeground(Theme.CREAM);
        table.setGridColor(Theme.GRID_LINE);
        table.setRowHeight(28);
        table.setFont(Theme.normal(14f));
        table.setShowVerticalLines(false);
        table.getTableHeader().setBackground(Theme.AMBER);
        table.getTableHeader().setForeground(Theme.INK);
        table.getTableHeader().setFont(Theme.bold(13f));
        table.getTableHeader().setReorderingAllowed(false);
        LeaderboardRenderer renderer = new LeaderboardRenderer();
        table.setDefaultRenderer(Object.class, renderer);
        table.getColumnModel().getColumn(0).setMaxWidth(50);
        table.setFillsViewportHeight(true);
        javax.swing.JScrollPane leaderboardScroll = new javax.swing.JScrollPane(table);
        leaderboardScroll.setBorder(BorderFactory.createEmptyBorder());
        leaderboardScroll.getViewport().setBackground(Theme.SURFACE);
        tableCard.add(leaderboardScroll, BorderLayout.CENTER);

        boardViews.setOpaque(false);
        boardViews.add(tableCard, "table");
        boardViews.add(emptyState(), "empty");
        add(boardViews, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.setBackground(Theme.BG);
        var back = UiKit.pill("Back to lobby", UiKit.Pill.CREAM_OUTLINE);
        back.addActionListener(event -> backAction.run());
        bottom.add(back);
        add(bottom, BorderLayout.SOUTH);

        model.addListener(new ClientModelListener() {
            @Override
            public void onLeaderboardChanged() {
                tableModel.setRowCount(0);
                int rank = 1;
                for (UserProfile player : model.leaderboard()) {
                    tableModel.addRow(new Object[]{rank++, player.username(),
                            player.elo(), player.wins(), player.losses()});
                }
                showBoard(!model.leaderboard().isEmpty());
            }
        });
    }

    /** Swaps between the ranked table and the pre-first-game empty state. */
    private void showBoard(boolean hasPlayers) {
        ((java.awt.CardLayout) boardViews.getLayout()).show(boardViews,
                hasPlayers ? "table" : "empty");
    }

    /** Centered muted placeholder before any ranked game exists. */
    private JComponent emptyState() {
        JPanel empty = new JPanel(new GridBagLayout());
        empty.setBackground(Theme.BG);
        JLabel label = new JLabel("No masters yet — be the first!");
        label.setFont(Theme.normal(13f));
        label.setForeground(Theme.MUTED);
        label.setIcon(new ImageIcon(medalImage(4)));
        label.setHorizontalTextPosition(SwingConstants.RIGHT);
        empty.add(label);
        return empty;
    }

    /**
     * The tinted 18px starburst medal: gold/silver/bronze for ranks 1-3,
     * muted for the empty-state flourish.
     */
    private java.awt.Image medalImage(int rank) {
        Color tint = switch (rank) {
            case 1 -> Theme.AMBER;
            case 2 -> Theme.MEDAL_SILVER;
            case 3 -> Theme.MEDAL_BRONZE;
            default -> Theme.MUTED;
        };
        var image = new java.awt.image.BufferedImage(18, 18,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = (Graphics2D) image.getGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        UiKit.starburst(g, 9, 9, 8, tint);
        g.dispose();
        return image;
    }

    /**
     * Rank medals for the top three (plain numbers after); the logged-in
     * player's row is tinted amber and set in bold.
     */
    private final class LeaderboardRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            setHorizontalAlignment(column == 1 ? SwingConstants.LEFT : SwingConstants.CENTER);
            boolean mine = model.me() != null && model.me().username()
                    .equalsIgnoreCase(String.valueOf(tableModel.getValueAt(row, 1)));
            if (mine) {
                setBackground(new Color(Theme.AMBER.getRed(), Theme.AMBER.getGreen(),
                        Theme.AMBER.getBlue(), 102));
                setFont(Theme.bold(14f));
            } else {
                setBackground(Theme.SURFACE);
                setFont(Theme.normal(14f));
            }
            setForeground(Theme.CREAM);
            if (column == 0) {
                int rank = ((Number) value).intValue();
                if (rank >= 1 && rank <= 3) {
                    setIcon(new ImageIcon(medalImage(rank)));
                    setText("");
                } else {
                    setIcon(null);
                    setText(String.valueOf(rank));
                }
            } else {
                setIcon(null);
                setText(String.valueOf(value));
            }
            return this;
        }
    }
}
