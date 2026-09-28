package onitama.client.ui;

import onitama.client.state.ClientModel;
import onitama.client.state.ClientModelListener;
import onitama.client.state.Screen;
import onitama.net.MatchSummary;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.Timer;

/**
 * The lobby, composed like the front page of an indie board game (§19-31):
 * the ONITAMA title with its tagline, the Master hero illustration, proper
 * CREATE/JOIN action cards, the room code as a major waiting object with a
 * copy button, and leaderboard/replays as secondary navigation cards. The
 * background carries faint dojo decoration instead of empty black.
 */
public final class LobbyPanel extends JPanel {

    private final ClientModel model;

    private final JLabel roomCodeLabel = new JLabel("· · · · ·");
    private final JLabel roomCaption = UiKit.label("CREATE A MATCH TO GET A CODE", 11f);
    private final JLabel waitingLabel = UiKit.label(" ", 12f);
    private final JTextField joinField = new JTextField(10);
    private final DefaultListModel<String> matchListModel = new DefaultListModel<>();
    private final JList<String> matchList = new JList<>(matchListModel);
    private final JLabel profileName = UiKit.inkLabel(" ", 15f);
    private final JLabel profileStats = UiKit.inkLabel(" ", 11f);
    private final JComponent profileAvatar = new JComponent() {
        {
            setPreferredSize(new java.awt.Dimension(36, 40));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            UiKit.drawFigurine(g, getWidth() / 2, getHeight() - 4,
                    getHeight() - 8, Theme.AMBER, true);
            g.dispose();
        }
    };
    private final JComponent onlineDot = new JComponent() {
        {
            setPreferredSize(new java.awt.Dimension(10, 10));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Theme.GREEN);
            g.fillOval(0, 0, 9, 9);
            g.setColor(Theme.INK);
            g.drawOval(0, 0, 9, 9);
            g.dispose();
        }
    };

    private int waitingDots;
    private Timer waitingTimer;
    private Runnable openLeaderboard;
    private Runnable openReplays;

    /** Builds the lobby and subscribes it to the model. */
    public LobbyPanel(ClientModel model, Runnable openLeaderboard, Runnable openReplays) {
        this.model = model;
        this.openLeaderboard = openLeaderboard;
        this.openReplays = openReplays;
        setLayout(new BorderLayout(16, 12));
        setBackground(Theme.BG);
        setBorder(BorderFactory.createEmptyBorder(18, 22, 14, 22));

        add(buildHeader(), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(24, 0));
        center.setOpaque(false);
        center.add(buildHero(), BorderLayout.CENTER);
        center.add(buildActionsColumn(), BorderLayout.EAST);
        add(center, BorderLayout.CENTER);

        model.addListener(new ClientModelListener() {
            @Override
            public void onScreenChanged(Screen screen) {
                if (screen == Screen.LOBBY) {
                    refreshProfile();
                    setWaitingVisible(false);
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
                setWaitingVisible(true);
            }
        });

        new Timer(5000, event -> {
            if (model.screen() == Screen.LOBBY) {
                model.refreshMatches();
            }
        }).start();
    }

    /** Atmospheric dojo background: soft ink washes and paper grain (§11). */
    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        // A couple of large, very faint ink washes for depth.
        for (java.awt.geom.Point2D.Double spot : new java.awt.geom.Point2D.Double[]{
                new java.awt.geom.Point2D.Double(getWidth() * 0.18, getHeight() * 0.3),
                new java.awt.geom.Point2D.Double(getWidth() * 0.8, getHeight() * 0.72)}) {
            g.setColor(new Color(249, 244, 218, 9));
            g.fillOval((int) (spot.x - 190), (int) (spot.y - 150), 380, 300);
            g.setColor(new Color(252, 186, 40, 6));
            g.fillOval((int) (spot.x - 120), (int) (spot.y - 90), 240, 180);
        }
        // Paper grain: sparse, tiny, low-contrast flecks (cached).
        if (grain == null) {
            grain = new java.awt.image.BufferedImage(220, 220,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.util.Random random = new java.util.Random(7);
            for (int i = 0; i < 500; i++) {
                grain.setRGB(random.nextInt(220), random.nextInt(220),
                        new Color(249, 244, 218, 14).getRGB());
            }
        }
        for (int x = 0; x < getWidth(); x += 220) {
            for (int y = 0; y < getHeight(); y += 220) {
                g.drawImage(grain, x, y, null);
            }
        }
        g.dispose();
    }

    private java.awt.image.BufferedImage grain;

    // ------------------------------------------------------------------
    // Header: title + profile
    // ------------------------------------------------------------------

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel titleBlock = new JPanel(new GridLayout(2, 1, 0, 0));
        titleBlock.setOpaque(false);
        JLabel title = new JLabel("ONITAMA");
        title.setFont(Theme.display(40f));
        title.setForeground(Theme.AMBER);
        JLabel tagline = UiKit.label("ONLINE · THE ANCIENT GAME OF MOVEMENT", 11f);
        titleBlock.add(title);
        titleBlock.add(tagline);
        header.add(titleBlock, BorderLayout.WEST);

        JPanel profile = UiKit.sticker(10);
        profile.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 2));
        profile.add(profileAvatar);
        JPanel profileText = new JPanel(new GridLayout(2, 1, 0, -3));
        profileText.setOpaque(false);
        profileText.add(profileName);
        profileText.add(profileStats);
        profile.add(profileText);
        profile.add(onlineDot);
        header.add(profile, BorderLayout.EAST);
        return header;
    }

    // ------------------------------------------------------------------
    // Hero illustration
    // ------------------------------------------------------------------

    private JComponent buildHero() {
        return new JComponent() {
            {
                setOpaque(false);
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(330, 380);
            }

            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                int cx = getWidth() / 2;
                // Halo + orbit ring.
                UiKit.starburst(g, cx, getHeight() / 2 - 30, 74);
                g.setColor(new Color(252, 186, 40, 60));
                g.setStroke(new java.awt.BasicStroke(3f, java.awt.BasicStroke.CAP_ROUND,
                        java.awt.BasicStroke.JOIN_ROUND, 0, new float[]{10, 10}, 0));
                g.drawOval(cx - 130, getHeight() / 2 - 120, 260, 220);
                // The Master figurine.
                UiKit.drawFigurine(g, cx, getHeight() / 2 + 120, 240, Theme.CREAM, true);
                // Flanking animal stickers.
                AnimalIcon.paint(g, "tiger", cx - 150, getHeight() / 2 - 140, 54,
                        Theme.CORAL, Theme.INK, Theme.CREAM);
                AnimalIcon.paint(g, "crane", cx + 96, getHeight() / 2 - 150, 54,
                        Theme.SKY, Theme.INK, Theme.CREAM);
                AnimalIcon.paint(g, "dragon", cx + 110, getHeight() / 2 + 40, 54,
                        Theme.PURPLE, Theme.INK, Theme.CREAM);
                g.dispose();
            }
        };
    }

    // ------------------------------------------------------------------
    // Actions column: create / join cards
    // ------------------------------------------------------------------

    private JPanel buildActionsColumn() {
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BorderLayout(0, 16));
        column.setPreferredSize(new java.awt.Dimension(430, 100));

        JPanel createCard = UiKit.sticker(18);
        createCard.setLayout(new BorderLayout(10, 10));
        createCard.add(UiKit.inkLabel("CREATE A MATCH", 17f), BorderLayout.NORTH);
        JPanel createBody = new JPanel(new BorderLayout(10, 10));
        createBody.setOpaque(false);
        createBody.add(UiKit.label("Challenge another master to a duel.", 13f),
                BorderLayout.NORTH);
        JButton create = UiKit.pill("Create match", UiKit.Pill.GOLD);
        create.addActionListener(event -> model.createMatch());
        JPanel createRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        createRow.setOpaque(false);
        createRow.add(create);
        createBody.add(createRow, BorderLayout.SOUTH);

        // The ticket: the room code becomes the major waiting object.
        ticket = new JPanel(new GridLayout(3, 1, 4, 4));
        ticket.setBackground(Theme.BOARD_LIGHT);
        ticket.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createDashedBorder(Theme.INK, 6, 4),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        roomCodeLabel.setFont(Theme.display(30f));
        roomCodeLabel.setForeground(Theme.INK);
        roomCodeLabel.setHorizontalAlignment(JLabel.CENTER);
        roomCaption.setHorizontalAlignment(JLabel.CENTER);
        roomCaption.setForeground(Theme.INK);
        JPanel copyRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        copyRow.setOpaque(false);
        JButton copy = UiKit.pill("Copy code", UiKit.Pill.GOLD_OUTLINE);
        copy.addActionListener(event -> Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(new StringSelection(roomCodeLabel.getText()), null));
        copyRow.add(copy);
        waitingLabel.setForeground(Theme.INK);
        copyRow.add(waitingLabel);
        ticket.add(roomCodeLabel);
        ticket.add(roomCaption);
        ticket.add(copyRow);
        ticket.setVisible(false);
        createBody.add(ticket, BorderLayout.CENTER);
        createCard.add(createBody, BorderLayout.CENTER);

        JPanel joinCard = buildJoinCard();

        JPanel nav = new JPanel(new GridLayout(1, 2, 14, 0));
        nav.setOpaque(false);
        nav.add(navCard("LEADERBOARD", "Top masters", () -> openLeaderboard.run()));
        nav.add(navCard("REPLAY VIEWER", "Watch matches", () -> openReplays.run()));

        column.add(createCard, BorderLayout.NORTH);
        column.add(joinCard, BorderLayout.CENTER);
        column.add(nav, BorderLayout.SOUTH);
        return column;
    }

    private JPanel ticket;
    private JPanel buildJoinCard() {
        JPanel card = UiKit.surface(16);
        card.setLayout(new BorderLayout(8, 8));

        JPanel joinHeader = new JPanel(new GridLayout(2, 1, 0, 0));
        joinHeader.setBackground(Theme.SURFACE);
        joinHeader.add(UiKit.boldLabel("JOIN A MATCH", 15f));
        joinHeader.add(UiKit.label("Enter your opponent's code.", 12f));
        card.add(joinHeader, BorderLayout.NORTH);

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

        // One CENTER child: join row on top, match list below.
        JPanel joinAndList = new JPanel(new BorderLayout(6, 6));
        joinAndList.setBackground(Theme.SURFACE);
        joinAndList.add(joinRow, BorderLayout.NORTH);

        matchList.setBackground(Theme.SURFACE);
        matchList.setForeground(Theme.CREAM);
        matchList.setSelectionBackground(Theme.AMBER);
        matchList.setSelectionForeground(Theme.INK);
        matchList.setFont(Theme.normal(13f));
        matchList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2) {
                    String entry = matchList.getSelectedValue();
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

    /** Adds a compact navigation card with a drawn icon. */
    private JComponent navCard(String title, String subtitle, Runnable action) {
        JPanel card = UiKit.surface(10);
        card.setLayout(new BorderLayout(10, 2));
        card.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        card.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                action.run();
            }
        });
        JComponent icon = new JComponent() {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(30, 30);
            }

            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                if (title.contains("LEADERBOARD")) {
                    UiKit.starburst(g, 15, 15, 13);
                } else {
                    g.setColor(Theme.AMBER);
                    g.fillPolygon(new int[]{9, 9, 24}, new int[]{7, 23, 15}, 3);
                    g.setColor(Theme.INK);
                    g.drawPolygon(new int[]{9, 9, 24}, new int[]{7, 23, 15}, 3);
                }
                g.dispose();
            }
        };
        card.add(icon, BorderLayout.WEST);
        JPanel text = new JPanel(new GridLayout(2, 1, 0, 0));
        text.setOpaque(false);
        text.add(UiKit.boldLabel(title, 13f));
        text.add(UiKit.label(subtitle, 11f));
        card.add(text, BorderLayout.CENTER);
        return card;
    }

    // ------------------------------------------------------------------
    // Waiting state + data refresh
    // ------------------------------------------------------------------

    private void setWaitingVisible(boolean visible) {
        ticket.setVisible(visible);
        if (visible) {
            waitingDots = 0;
            if (waitingTimer != null) {
                waitingTimer.stop();
            }
            waitingTimer = new Timer(500, event -> {
                waitingDots = (waitingDots + 1) % 4;
                waitingLabel.setText("WAITING FOR OPPONENT"
                        + ".".repeat(waitingDots));
            });
            waitingTimer.start();
        } else if (waitingTimer != null) {
            waitingTimer.stop();
        }
        revalidate();
        repaint();
    }

    private void refreshProfile() {
        if (model.me() != null) {
            profileName.setText(" " + model.me().username().toUpperCase() + " ");
            profileStats.setText("  ELO " + model.me().elo() + "   ·   "
                    + model.me().wins() + "W · " + model.me().losses() + "L  ");
        }
    }

    private void refreshMatches() {
        matchListModel.clear();
        String ownCode = model.pendingRoomCode();
        List<MatchSummary> others = model.openMatches().stream()
                .filter(summary -> !summary.roomCode().equals(ownCode))
                .toList();
        if (others.isEmpty()) {
            matchListModel.addElement("THE DOJO IS QUIET — CREATE A MATCH");
        } else {
            others.forEach(summary -> matchListModel.addElement(
                    summary.roomCode() + "  —  " + summary.hostUsername()));
        }
    }
}
