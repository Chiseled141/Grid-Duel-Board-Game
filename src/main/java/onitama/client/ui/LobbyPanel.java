package onitama.client.ui;

import onitama.client.state.ClientModel;
import onitama.client.state.ClientModelListener;
import onitama.client.state.Screen;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.Timer;

/**
 * The lobby: user info, create/join match controls, the open-match list
 * (auto-refreshed every 5 seconds) and the buttons to the leaderboard and
 * the replay viewer.
 */
public final class LobbyPanel extends JPanel {

    private final ClientModel model;

    private final JLabel profileLabel = new JLabel();
    private final JLabel roomCodeLabel = new JLabel(" ");
    private final JTextField joinField = new JTextField(8);
    private final DefaultListModel<String> matchListModel = new DefaultListModel<>();
    private final JList<String> matchList = new JList<>(matchListModel);

    /** Builds the panel and subscribes it to the model. */
    public LobbyPanel(ClientModel model, Runnable openLeaderboard, Runnable openReplays) {
        this.model = model;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        setBackground(Theme.BACKGROUND);

        profileLabel.setForeground(Theme.FOREGROUND);
        profileLabel.setFont(Theme.FONT_BOLD);
        add(profileLabel, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(1, 2, 12, 0));
        center.setBackground(Theme.BACKGROUND);
        center.add(buildCreateArea());
        center.add(buildJoinArea());
        add(center, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.setBackground(Theme.BACKGROUND);
        JButton leaderboard = new JButton("Leaderboard");
        leaderboard.addActionListener(event -> openLeaderboard.run());
        JButton replays = new JButton("Replay viewer");
        replays.addActionListener(event -> openReplays.run());
        bottom.add(leaderboard);
        bottom.add(replays);
        add(bottom, BorderLayout.SOUTH);

        model.addListener(new ClientModelListener() {
            @Override
            public void onScreenChanged(Screen screen) {
                if (screen == Screen.LOBBY) {
                    refreshProfile();
                    roomCodeLabel.setText(" ");
                    refreshMatches();
                }
            }

            @Override
            public void onLobbyChanged() {
                refreshProfile();
                refreshMatches();
            }

            @Override
            public void onMatchCreated(String roomCode) {
                roomCodeLabel.setText("Room code: " + roomCode
                        + "  - waiting for an opponent...");
            }
        });

        // Periodic refresh of the open-match list while the lobby is visible.
        new Timer(5000, event -> {
            if (model.screen() == Screen.LOBBY) {
                model.refreshMatches();
            }
        }).start();
    }

    private JPanel buildCreateArea() {
        JPanel panel = new JPanel(new GridLayout(3, 1, 6, 6));
        panel.setBackground(Theme.BACKGROUND);
        panel.setBorder(BorderFactory.createTitledBorder("Create a match"));
        JButton create = new JButton("Create match");
        create.addActionListener(event -> model.createMatch());
        roomCodeLabel.setForeground(Theme.SELECTION);
        roomCodeLabel.setFont(Theme.FONT_BOLD);
        panel.add(create);
        panel.add(roomCodeLabel);
        return panel;
    }

    private JPanel buildJoinArea() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBackground(Theme.BACKGROUND);
        panel.setBorder(BorderFactory.createTitledBorder("Join a match"));

        JPanel joinRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        joinRow.setBackground(Theme.BACKGROUND);
        joinRow.add(new JLabel("Code:"));
        joinRow.add(joinField);
        JButton join = new JButton("Join");
        join.addActionListener(event -> {
            String code = joinField.getText().trim().toUpperCase();
            if (!code.isEmpty()) {
                model.joinMatch(code);
            }
        });
        joinRow.add(join);
        panel.add(joinRow, BorderLayout.NORTH);

        panel.add(new JScrollPane(matchList), BorderLayout.CENTER);
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(event -> model.refreshMatches());
        panel.add(refresh, BorderLayout.SOUTH);

        // Double-click a listed room to join it.
        matchList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2) {
                    String entry = matchList.getSelectedValue();
                    if (entry != null) {
                        model.joinMatch(entry.split(" ")[0]);
                    }
                }
            }
        });
        return panel;
    }

    private void refreshProfile() {
        if (model.me() != null) {
            profileLabel.setText(" " + model.me().username()
                    + "   -   Elo " + model.me().elo()
                    + "   -   " + model.me().wins() + "W / " + model.me().losses() + "L");
        }
    }

    private void refreshMatches() {
        matchListModel.clear();
        String ownCode = model.pendingRoomCode();
        model.openMatches().stream()
                .filter(summary -> !summary.roomCode().equals(ownCode))
                .forEach(summary -> matchListModel.addElement(
                        summary.roomCode() + "  hosted by " + summary.hostUsername()));
    }
}
