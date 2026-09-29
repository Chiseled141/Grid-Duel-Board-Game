package onitama.client.ui;

import onitama.client.state.ClientModel;
import onitama.client.state.ClientModelListener;
import onitama.client.state.Screen;
import onitama.net.MatchSummary;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Component;
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
import javax.swing.BoxLayout;
import javax.swing.Box;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.Timer;
import java.awt.Dimension;

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
    private JComponent heroPanel;
    private JPanel actionsColumn;
    private JPanel actionsWrap;
    private boolean wideLayout = true;
    private final onitama.client.ClientSettings settings;
    private final UiKit.PillButton[] styleButtons = new UiKit.PillButton[3];
    private Runnable openLeaderboard;
    private Runnable openReplays;
    private JButton returnPill;
    private JPanel returnWrap;

    /** Builds the lobby and subscribes it to the model. */
    public LobbyPanel(ClientModel model, onitama.client.ClientSettings settings,
                      Runnable openLeaderboard, Runnable openReplays) {
        this.model = model;
        this.settings = settings;
        this.openLeaderboard = openLeaderboard;
        this.openReplays = openReplays;
        setLayout(new BorderLayout(16, 12));
        setBackground(Theme.BG);
        setBorder(BorderFactory.createEmptyBorder(18, 22, 14, 22));

        add(buildHeader(), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(24, 0));
        center.setOpaque(false);
        center.add(buildActionsColumn(), BorderLayout.EAST);
        add(center, BorderLayout.CENTER);

        model.addListener(new ClientModelListener() {
            @Override
            public void onScreenChanged(Screen screen) {
                if (screen == Screen.LOBBY) {
                    refreshProfile();
                    setWaitingVisible(false);
                    refreshMatches();
                    if (model.hasParkedMatch()) {
                        returnPill.setText("RETURN TO MATCH · " + model.parkedRoomCode());
                        returnPill.setVisible(true);
                        returnWrap.setVisible(true);
                    } else {
                        returnPill.setVisible(false);
                        returnWrap.setVisible(false);
                    }
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

        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent event) {
                relayout();
            }

            @Override
            public void componentShown(java.awt.event.ComponentEvent event) {
                relayout();
            }
        });
    }

    /**
     * Responsive layout (§31): wide windows show the hero beside the action
     * cards; narrow ones stack the hero band on top so nothing collapses.
     */
    private void relayout() {
        boolean wide = getWidth() >= 950;
        if (wide == wideLayout) {
            return;
        }
        wideLayout = wide;
        remove(heroPanel);
        remove(actionsColumn);
        if (wide) {
            heroPanel.setVisible(true);
            actionsColumn.setPreferredSize(
                    new Dimension(440, actionsColumn.getPreferredSize().height));
            remove(actionsWrap);
            add(heroPanel, BorderLayout.CENTER);
            add(actionsWrap, BorderLayout.EAST);
        } else {
            // Narrow: the painting is the backdrop, so the hero band would
            // only squeeze — hide it and center a capped actions column.
            heroPanel.setVisible(false);
            int capped = Math.min(440, Math.max(320, getWidth() - 36));
            actionsColumn.setPreferredSize(
                    new Dimension(capped, actionsColumn.getPreferredSize().height));
            remove(heroPanel);
            remove(actionsWrap);
            add(actionsWrap, BorderLayout.CENTER);
        }
        revalidate();
        repaint();
    }

    /** The sumi-e temple painting as the lobby background (cover, left-anchored). */
    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        java.awt.Image background = AssetStore.optional("menu-background.png");
        if (background == null) {
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        // Cover-fit, anchored left: the temple and mountains stay on screen.
        double scaleFactor = Math.max(getWidth() / (double) background.getWidth(null),
                getHeight() / (double) background.getHeight(null));
        int drawW = (int) (background.getWidth(null) * scaleFactor);
        int drawH = (int) (background.getHeight(null) * scaleFactor);
        g.drawImage(background, 0, (getHeight() - drawH) / 2, drawW, drawH, null);
        // Subtle right-side scrim so the action cards keep their contrast.
        g.setColor(new Color(15, 13, 14, 40));
        g.fillRect(getWidth() - 480, 0, 480, getHeight());
        g.dispose();
    }

    // ------------------------------------------------------------------
    // Header: title + profile
    // ------------------------------------------------------------------

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel titleBlock = UiKit.sticker(12);
        titleBlock.setLayout(new GridLayout(2, 1, 0, 0));
        JLabel title = new JLabel("ONITAMA");
        title.setFont(Theme.display(38f));
        title.setForeground(Theme.INK);
        JLabel tagline = UiKit.inkLabel("ONLINE · THE ANCIENT GAME OF MOVEMENT", 11f);
        titleBlock.add(title);
        titleBlock.add(tagline);
        header.add(titleBlock, BorderLayout.WEST);

        JPanel east = new JPanel(new BorderLayout(0, 10));
        east.setOpaque(false);

        JPanel profile = UiKit.sticker(10);
        profile.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 2));
        profile.add(profileAvatar);
        JPanel profileText = new JPanel(new GridLayout(2, 1, 0, -3));
        profileText.setOpaque(false);
        profileText.add(profileName);
        profileText.add(profileStats);
        profile.add(profileText);
        profile.add(onlineDot);
        east.add(profile, BorderLayout.NORTH);

        east.add(buildSettingsCard(), BorderLayout.CENTER);
        header.add(east, BorderLayout.EAST);
        return header;
    }

    /** The settings card: choose the board-piece style (§: user preference). */
    private JPanel buildSettingsCard() {
        JPanel card = UiKit.surface(10);
        card.setLayout(new BorderLayout(6, 6));

        card.add(UiKit.boldLabel("SETTINGS", 12f), BorderLayout.NORTH);

        JPanel styleBlock = new JPanel(new BorderLayout(4, 4));
        styleBlock.setBackground(Theme.SURFACE);
        styleBlock.add(UiKit.label("PIECE STYLE", 10f), BorderLayout.NORTH);

        JPanel styleRow = new JPanel(new GridLayout(3, 1, 4, 4));
        styleRow.setBackground(Theme.SURFACE);
        String[] names = {"1 · INK TOKENS", "2 · SEAL STONES", "3 · INK SILHOUETTES"};
        for (int i = 0; i < 3; i++) {
            UiKit.PillButton button = UiKit.miniPill(names[i],
                    i + 1 == settings.pieceStyle() ? UiKit.Pill.GOLD : UiKit.Pill.CREAM_OUTLINE);
            int style = i + 1;
            button.addActionListener(event -> {
                settings.setPieceStyle(style);
                settings.save();
                BoardPanel.setDefaultPieceStyle(style);
                updateStyleSelection();
            });
            styleButtons[i] = button;
            styleRow.add(button);
        }
        styleBlock.add(styleRow, BorderLayout.CENTER);
        card.add(styleBlock, BorderLayout.CENTER);
        return card;
    }

    /** Highlights the style button matching the current preference. */
    private void updateStyleSelection() {
        for (int i = 0; i < styleButtons.length; i++) {
            boolean selected = i + 1 == settings.pieceStyle();
            styleButtons[i].setVariant(selected ? UiKit.Pill.GOLD : UiKit.Pill.CREAM_OUTLINE);
        }
    }

    // ------------------------------------------------------------------
    // Hero illustration
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // Actions column: create / join cards
    // ------------------------------------------------------------------

    private JPanel buildActionsColumn() {
        JPanel column = new JPanel(new BorderLayout(0, 14));
        column.setOpaque(false);
        column.setPreferredSize(new java.awt.Dimension(440, 100));

        // RETURN TO MATCH: shown while a live match is parked in the background.
        returnPill = UiKit.pill("RETURN TO MATCH", UiKit.Pill.GOLD);
        returnPill.addActionListener(event -> model.returnToParkedMatch());
        returnPill.setVisible(false);
        returnWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        returnWrap.setOpaque(false);
        returnWrap.add(returnPill);
        returnWrap.setVisible(false);

        // CREATE card: title, subtitle, button, and the waiting ticket.
        JPanel createCard = UiKit.sticker(16);
        createCard.setLayout(new BorderLayout(10, 12));
        JPanel createHead = new JPanel(new BorderLayout(8, 2));
        createHead.setOpaque(false);
        createHead.add(UiKit.inkLabel("CREATE A MATCH", 15f), BorderLayout.NORTH);
        createHead.add(UiKit.label("Challenge another master to a duel.", 12f),
                BorderLayout.SOUTH);
        createCard.add(createHead, BorderLayout.NORTH);

        JPanel createCenter = new JPanel(new BorderLayout(10, 12));
        createCenter.setOpaque(false);
        JButton create = UiKit.pill("Create match", UiKit.Pill.GOLD);
        create.addActionListener(event -> model.createMatch());
        JPanel createRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 2));
        createRow.setOpaque(false);
        createRow.add(create);
        createCenter.add(createRow, BorderLayout.NORTH);

        // The ticket: the room code becomes the major waiting object.
        ticket = new JPanel(new BorderLayout(6, 8));
        ticket.setBackground(Theme.BOARD_LIGHT);
        ticket.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createDashedBorder(Theme.INK, 6, 4),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        roomCodeLabel.setFont(Theme.display(32f));
        roomCodeLabel.setForeground(Theme.INK);
        roomCodeLabel.setHorizontalAlignment(JLabel.CENTER);
        roomCaption.setHorizontalAlignment(JLabel.CENTER);
        roomCaption.setForeground(Theme.INK);
        ticket.add(roomCodeLabel, BorderLayout.CENTER);
        JPanel ticketRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        ticketRow.setOpaque(false);
        JButton copy = UiKit.pill("Copy code", UiKit.Pill.GOLD_OUTLINE);
        copy.addActionListener(event -> Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(new StringSelection(roomCodeLabel.getText()), null));
        ticketRow.add(copy);
        waitingLabel.setHorizontalAlignment(JLabel.CENTER);
        waitingLabel.setForeground(Theme.INK);
        waitingLabel.setFont(Theme.normal(10f));
        ticketRow.add(waitingLabel);
        ticket.add(ticketRow, BorderLayout.SOUTH);
        ticket.setVisible(false);
        createCenter.add(ticket, BorderLayout.CENTER);
        createCard.add(createCenter, BorderLayout.CENTER);

        // JOIN card: unified input row, list, refresh.
        JPanel joinCard = buildJoinCard();

        // Nav row: two equal secondary cards.
        JPanel nav = new JPanel(new GridLayout(1, 2, 14, 0));
        nav.setOpaque(false);
        nav.add(navCard("LEADERBOARD", "Top masters", () -> openLeaderboard.run()));
        nav.add(navCard("REPLAY VIEWER", "Watch matches", () -> openReplays.run()));

        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        // Each section sits in a centering wrapper at its natural size, so
        // tall or short windows never stretch the cards into sparse boxes.
        stack.add(wrap(returnWrap));
        stack.add(Box.createVerticalStrut(14));
        stack.add(wrap(createCard));
        stack.add(Box.createVerticalStrut(14));
        stack.add(wrap(joinCard));
        stack.add(Box.createVerticalStrut(14));
        stack.add(wrap(nav));
        column.add(stack, BorderLayout.NORTH);
        return column;
    }

    /** Centers a component at its preferred size inside the column. */
    private JPanel wrap(JComponent inner) {
        JPanel holder = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        holder.setOpaque(false);
        holder.add(inner);
        return holder;
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
