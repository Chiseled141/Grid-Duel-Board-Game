package onitama.client.ui;

import onitama.client.state.ClientModel;
import onitama.client.state.ClientModelListener;
import onitama.net.UserProfile;

import java.awt.BorderLayout;
import java.awt.FlowLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

/**
 * The leaderboard screen: a table of the top players by Elo with a back
 * button. Data arrives via {@link ClientModelListener#onLeaderboardChanged()}.
 */
public final class LeaderboardPanel extends JPanel {

    private final DefaultTableModel tableModel =
            new DefaultTableModel(new Object[]{"#", "Player", "Elo", "Wins", "Losses"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };

    /** Builds the panel and subscribes it to the model. */
    public LeaderboardPanel(ClientModel model, Runnable backAction) {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        setBackground(Theme.BACKGROUND);

        JLabel title = new JLabel("Leaderboard - top players by Elo");
        title.setFont(Theme.FONT_TITLE);
        title.setForeground(Theme.ACCENT);
        add(title, BorderLayout.NORTH);

        JTable table = new JTable(tableModel);
        table.setBackground(Theme.BACKGROUND.brighter());
        table.setForeground(Theme.FOREGROUND);
        table.setRowHeight(24);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.setBackground(Theme.BACKGROUND);
        JButton back = new JButton("Back to lobby");
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
