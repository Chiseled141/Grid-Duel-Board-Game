package onitama.client.ui;

import onitama.client.state.ClientModel;
import onitama.client.state.ClientModelListener;
import onitama.client.state.Screen;
import onitama.net.MatchSummary;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.List;

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
 * The lobby: profile badge, create/join match controls (the room code appears
 * as a ticket), the open-match list, and the leaderboard/replay buttons.
 */
public final class LobbyPanel extends JPanel {

    private final ClientModel model;

    private final JLabel profileLabel = UiKit.inkLabel(" ", 14f);
    private final JLabel roomCodeLabel = new JLabel("—");
    private final JLabel roomCaption = UiKit.inkLabel("CREATE A MATCH TO GET A CODE", 11f);
    private final JTextField joinField = new JTextField(8);
    private final DefaultListModel<String> matchListModel = new DefaultListModel<>();
    private final JList<String> matchList = new JList<>(matchListModel);
    private JPanel ticket;

    /** Builds the panel and subscribes it to the model. */
    public LobbyPanel(ClientModel model, Runnable openLeaderboard, Runnable openReplays) {
        this.model = model;
        setLayout(new BorderLayout(14, 14));
        setBackground(Theme.BG);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        // Profile badge (cream sticker, ink text).
        JPanel profileBadge = UiKit.sticker(10);
        profileBadge.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 2));
        profileBadge.add(profileLabel);
        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT));
        north.setBackground(Theme.BG);
        north.add(profileBadge);
        add(north, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(1, 2, 20, 0));
        center.setBackground(Theme.BG);
        center.add(buildCreateCard());
        center.add(buildJoinCard());
        add(center, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.setBackground(Theme.BG);
        JButton leaderboard = UiKit.pill("Leaderboard", UiKit.Pill.CREAM_OUTLINE);
        leaderboard.addActionListener(event -> openLeaderboard.run());
        bottom.add(leaderboard);
        JButton replays = UiKit.pill("Replay viewer", UiKit.Pill.CREAM_OUTLINE);
        replays.addActionListener(event -> openReplays.run());
        bottom.add(replays);
        add(bottom, BorderLayout.SOUTH);

        model.addListener(new ClientModelListener() {
            @Override
            public void onScreenChanged(Screen screen) {
                if (screen == Screen.LOBBY) {
                    refreshProfile();
                    roomCodeLabel.setText("—");
                    roomCaption.setText("CREATE A MATCH TO GET A CODE");
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
                roomCodeLabel.setText(roomCode);
                roomCaption.setText("SHARE THIS CODE WITH YOUR OPPONENT");
            }
        });

        // Periodic refresh of the open-match list while the lobby is visible.
        new Timer(5000, event -> {
            if (model.screen() == Screen.LOBBY) {
                model.refreshMatches();
            }
        }).start();
    }

    private JPanel buildCreateCard() {
        JPanel card = UiKit.sticker(20);
        card.setLayout(new GridLayout(4, 1, 10, 12));

        JButton create = UiKit.pill("Create match", UiKit.Pill.GOLD);
        create.addActionListener(event -> model.createMatch());

        // The room-code ticket: cream field, dashed inner border, big code.
        ticket = new JPanel(new GridLayout(2, 1, 4, 4));
        ticket.setBackground(Theme.BOARD_LIGHT);
        ticket.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createDashedBorder(Theme.INK, 6, 4),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        roomCodeLabel.setFont(Theme.display(26f));
        roomCodeLabel.setForeground(Theme.INK);
        roomCodeLabel.setHorizontalAlignment(JLabel.CENTER);
        roomCaption.setHorizontalAlignment(JLabel.CENTER);
        roomCaption.setForeground(Theme.INK);
        ticket.add(roomCodeLabel);
        ticket.add(roomCaption);

        card.add(UiKit.inkLabel("START A GAME", 16f));
        card.add(create);
        card.add(ticket);
        return card;
    }

    private JPanel buildJoinCard() {
        JPanel card = UiKit.surface(16);
        card.setLayout(new BorderLayout(8, 8));

        card.add(UiKit.boldLabel("JOIN A GAME", 16f), BorderLayout.NORTH);

        JPanel joinRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        joinRow.setBackground(Theme.SURFACE);
        joinField.setBackground(Theme.BOARD_LIGHT);
        joinField.setForeground(Theme.INK);
        joinField.setBorder(UiKit.fieldBorder());
        joinField.setFont(Theme.display(14f));
        joinRow.add(joinField);
        JButton join = UiKit.pill("Join", UiKit.Pill.GOLD);
        join.addActionListener(event -> {
            String code = joinField.getText().trim().toUpperCase();
            if (!code.isEmpty()) {
                model.joinMatch(code);
            }
        });
        joinRow.add(join);

        // One CENTER child: join row on top, match list below (two adds to
        // the same BorderLayout region would overwrite each other).
        JPanel joinAndList = new JPanel(new BorderLayout(6, 6));
        joinAndList.setBackground(Theme.SURFACE);
        joinAndList.add(joinRow, BorderLayout.NORTH);

        matchList.setBackground(Theme.SURFACE);
        matchList.setForeground(Theme.CREAM);
        matchList.setSelectionBackground(Theme.AMBER);
        matchList.setSelectionForeground(Theme.INK);
        matchList.setFont(Theme.normal(14f));
        // Double-click a listed room to join it.
        matchList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2) {
                    String entry = matchList.getSelectedValue();
                    // Real entries start with a 5-character room code; the
                    // empty-state placeholder is not joinable.
                    if (entry != null && entry.length() >= 5
                            && entry.matches("[A-Z2-9]{5}.*")) {
                        model.joinMatch(entry.substring(0, 5));
                    }
                }
            }
        });
        JScrollPane matchScroll = new JScrollPane(matchList);
        matchScroll.setBorder(BorderFactory.createEmptyBorder());
        matchScroll.getViewport().setBackground(Theme.SURFACE);
        joinAndList.add(matchScroll, BorderLayout.CENTER);
        card.add(joinAndList, BorderLayout.CENTER);

        JPanel southRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        southRow.setBackground(Theme.SURFACE);
        JButton refresh = UiKit.pill("Refresh", UiKit.Pill.CREAM_OUTLINE);
        refresh.addActionListener(event -> model.refreshMatches());
        southRow.add(refresh);
        card.add(southRow, BorderLayout.SOUTH);
        return card;
    }

    private void refreshProfile() {
        if (model.me() != null) {
            profileLabel.setText(" " + model.me().username().toUpperCase()
                    + "   ·   ELO " + model.me().elo()
                    + "   ·   " + model.me().wins() + "W / " + model.me().losses() + "L ");
        }
    }

    private void refreshMatches() {
        matchListModel.clear();
        String ownCode = model.pendingRoomCode();
        List<MatchSummary> others = model.openMatches().stream()
                .filter(summary -> !summary.roomCode().equals(ownCode))
                .toList();
        if (others.isEmpty()) {
            matchListModel.addElement("No open matches yet — create one!");
        } else {
            others.forEach(summary -> matchListModel.addElement(
                    summary.roomCode() + "  —  " + summary.hostUsername()));
        }
    }
}
