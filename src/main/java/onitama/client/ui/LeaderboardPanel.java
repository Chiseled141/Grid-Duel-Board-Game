package onitama.client.ui;

import onitama.client.state.ClientModel;
import onitama.client.state.ClientModelListener;
import onitama.net.UserProfile;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * The leaderboard screen: a table of the top players by Elo with a back
 * button. Data arrives via {@link ClientModelListener#onLeaderboardChanged()}.
 */
public final class LeaderboardPanel extends JPanel {

    private final DefaultTableModel tableModel =
            new DefaultTableModel(new Object[]{"#", "PLAYER", "ELO", "W", "L"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };

    /** Builds the panel and subscribes it to the model. */
    public LeaderboardPanel(ClientModel model, Runnable backAction) {
        setLayout(new BorderLayout(10, 10));
        setBackground(Theme.BG);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        UiKit.HeadingLabel title = new UiKit.HeadingLabel(20f);
        title.setText("LEADERBOARD");
        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.CENTER));
        titleRow.setBackground(Theme.BG);
        titleRow.add(title);
        add(titleRow, BorderLayout.NORTH);

        JTable table = new JTable(tableModel);
        table.setBackground(Theme.SURFACE);
        table.setForeground(Theme.CREAM);
        table.setGridColor(new Color(0x3A3634));
        table.setRowHeight(28);
        table.setFont(Theme.normal(14f));
        table.setShowVerticalLines(false);
        table.getTableHeader().setBackground(Theme.AMBER);
        table.getTableHeader().setForeground(Theme.INK);
        table.getTableHeader().setFont(Theme.bold(13f));
        table.getTableHeader().setReorderingAllowed(false);
        DefaultTableCellRenderer centerer = new DefaultTableCellRenderer();
        centerer.setHorizontalAlignment(SwingConstants.CENTER);
        centerer.setBackground(Theme.SURFACE);
        centerer.setForeground(Theme.CREAM);
        for (int column = 0; column < tableModel.getColumnCount(); column++) {
            if (column != 1) {
                table.getColumnModel().getColumn(column).setCellRenderer(centerer);
            }
        }
        table.getColumnModel().getColumn(0).setMaxWidth(50);
        table.setFillsViewportHeight(true);
        javax.swing.JScrollPane leaderboardScroll = new javax.swing.JScrollPane(table);
        leaderboardScroll.setBorder(BorderFactory.createLineBorder(Theme.OUTLINE, 2));
        leaderboardScroll.getViewport().setBackground(Theme.SURFACE);
        add(leaderboardScroll, BorderLayout.CENTER);

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
            }
        });
    }
}
