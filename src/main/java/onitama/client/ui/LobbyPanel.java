package onitama.client.ui;

import onitama.client.state.ClientModel;
import onitama.client.state.ClientModelListener;
import onitama.client.state.Screen;
import onitama.core.Difficulty;
import onitama.net.MatchSummary;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
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
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.JTextField;
import javax.swing.Timer;

/**
 * The lobby, composed like the front page of an indie board game (§19-31):
 * the ONITAMA title with its tagline, the Master hero illustration, proper
 * CREATE/JOIN action cards, the room code as a major waiting object with a
 * copy button, and the leaderboard as a secondary navigation card. The
 * background carries faint dojo decoration instead of empty black.
 */
public final class LobbyPanel extends JPanel {

    private final ClientModel model;

    private final JLabel roomCodeLabel = new JLabel("· · · · ·");
    private final JLabel roomCaption = UiKit.label("CREATE A MATCH TO GET A CODE", 11f);
    private final JLabel waitingLabel = UiKit.label(" ", 12f);
    /** Code input with a muted "ENTER CODE" placeholder when empty/unfocused. */
    private final JTextField joinField = new JTextField(10) {
        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (getText().isEmpty() && !isFocusOwner()) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setColor(Theme.MUTED);
                g.setFont(Theme.normal(11f));
                FontMetrics metrics = g.getFontMetrics();
                g.drawString("ENTER CODE", 12,
                        (getHeight() + metrics.getAscent() - metrics.getDescent()) / 2 - 1);
                g.dispose();
            }
        }
    };
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
    private final JComponent onlineDot = UiKit.statusDot(10, () -> Theme.GREEN);

    private int waitingDots;
    private Timer waitingTimer;
    private JPanel actionsColumn;
    private JScrollPane actionsScroll;
    private JPanel centerColumn;
    private boolean wideLayout = true;
    private final onitama.client.ClientSettings settings;
    private final UiKit.PillButton[] styleButtons = new UiKit.PillButton[3];
    private Runnable openLeaderboard;
    private UiKit.PillButton returnPill;
    private JPanel returnWrap;
    private UiKit.PillButton playButton;
    private UiKit.PillButton joinButton;
    private UiKit.PillButton createButton;
    private javax.swing.JLabel difficultyHint;
    private JPanel difficultyRow;
    private JPanel difficultySlot;
    /**
     * The right column's fixed card width. 446px is the settings pill row's
     * natural width ("1 · INK TOKENS" … at 9f plus padding), so the header's
     * profile + piece-style cards and the action cards below all share one
     * width line — same left/right edges, no misaligned column.
     */
    private static final int COLUMN_WIDTH = 446;

    /** Builds the lobby and subscribes it to the model. */
    public LobbyPanel(ClientModel model, onitama.client.ClientSettings settings,
                      Runnable openLeaderboard) {
        this.model = model;
        this.settings = settings;
        this.openLeaderboard = openLeaderboard;
        setLayout(new BorderLayout());
        setBackground(Theme.BG);
        // Slim vertical page padding (sides keep the 16px rule): every pixel
        // of height counts toward the scrollbar-free 720px target.
        setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));

        // The header (title chip, profile, settings) and the action cards
        // share one page column — the header is NORTH, actions EAST.
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(false);
        page.add(buildHeader(), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(24, 0));
        center.setOpaque(false);
        center.add(buildActionsColumn(), BorderLayout.EAST);
        page.add(center, BorderLayout.CENTER);
        add(page, BorderLayout.CENTER);
        this.centerColumn = center;

        model.addListener(new ClientModelListener() {
            @Override
            public void onScreenChanged(Screen screen) {
                if (screen == Screen.LOBBY) {
                    refreshProfile();
                    setWaitingVisible(false);
                    hideDifficultyRow();
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
     * Responsive layout (§31): wide windows show the action cards beside the
     * painting on the right; narrow ones center a capped column over the
     * backdrop so nothing collapses.
     */
    private void relayout() {
        if (centerColumn == null || actionsColumn == null) {
            return;
        }
        // The scrollbar is an emergency fallback: hidden at 720px and up,
        // armed only when the window is too short to hold the stack.
        if (actionsScroll != null) {
            actionsScroll.setVerticalScrollBarPolicy(getHeight() < 650
                    ? ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
                    : ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        }
        boolean wide = getWidth() >= 800;
        if (wide == wideLayout) {
            return;
        }
        wideLayout = wide;
        centerColumn.remove(actionsColumn);
        if (wide) {
            actionsColumn.setPreferredSize(
                    new Dimension(COLUMN_WIDTH, actionsColumn.getPreferredSize().height));
            centerColumn.add(actionsColumn, BorderLayout.EAST);
        } else {
            int capped = Math.min(COLUMN_WIDTH, Math.max(280, getWidth() - 32));
            actionsColumn.setPreferredSize(
                    new Dimension(capped, actionsColumn.getPreferredSize().height));
            centerColumn.add(actionsColumn, BorderLayout.CENTER);
        }
        centerColumn.revalidate();
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
        g.setColor(Theme.withAlpha(Theme.BG, 40));
        g.fillRect(getWidth() - COLUMN_WIDTH - 48, 0, COLUMN_WIDTH + 48, getHeight());
        g.dispose();
    }

    // ------------------------------------------------------------------
    // Header: title + profile
    // ------------------------------------------------------------------

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        // Small breathing room under the header so the action column below
        // starts at a constant offset whatever the profile/settings height.
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));

        // The title sticker: brush-weight ONITAMA wordmark, tracked ONLINE
        // subtitle and a red hanko seal — readable without blocking the art.
        // It hugs its content (FlowLayout wrapper) so a tall profile/settings
        // column can't stretch it into a sparse box.
        JPanel titleBlock = UiKit.sticker(12);
        titleBlock.setLayout(new BorderLayout(14, 0));
        JPanel titleText = new JPanel(new GridLayout(2, 1, 0, 0));
        titleText.setOpaque(false);
        JLabel title = new JLabel("ONITAMA");
        title.setFont(Theme.display(44f));
        title.setForeground(Theme.INK);
        JLabel tagline = new JLabel("O N L I N E   ·   T H E   A N C I E N T   G A M E");
        tagline.setFont(Theme.bold(11f));
        tagline.setForeground(Theme.P2_DARK);
        titleText.add(title);
        titleText.add(tagline);
        titleBlock.add(titleText, BorderLayout.CENTER);
        titleBlock.add(sealStamp(), BorderLayout.EAST);
        JPanel titleWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        titleWrap.setOpaque(false);
        titleWrap.add(titleBlock);
        header.add(titleWrap, BorderLayout.WEST);

        JPanel east = new JPanel(new BorderLayout(0, 10));
        east.setOpaque(false);

        // Both header cards share one width line: the piece-style pill row
        // drives the settings card's natural width, and the profile sticker
        // is pinned to exactly that width, so the two always keep identical
        // left/right edges (right alignment is BorderLayout EAST's job).
        JPanel settingsCard = buildSettingsCard();
        int headerCardWidth = settingsCard.getPreferredSize().width;

        JPanel profile = UiKit.sticker(10);
        profile.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 2));
        profile.add(profileAvatar);
        JPanel profileText = new JPanel(new GridLayout(2, 1, 0, -3));
        profileText.setOpaque(false);
        profileText.add(profileName);
        profileText.add(profileStats);
        profile.add(profileText);
        profile.add(onlineDot);
        profile.setPreferredSize(new Dimension(headerCardWidth,
                profile.getPreferredSize().height));
        east.add(profile, BorderLayout.NORTH);
        east.add(settingsCard, BorderLayout.CENTER);
        header.add(east, BorderLayout.EAST);
        return header;
    }

    /** A small red hanko seal (drawn square stamp) for the title sticker. */
    private JComponent sealStamp() {
        return new JComponent() {
            {
                setPreferredSize(new java.awt.Dimension(34, 34));
                setOpaque(false);
            }

            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(Theme.P2);
                g.fillRoundRect(2, 2, 30, 30, 8, 8);
                g.setColor(Theme.INK);
                g.setStroke(new java.awt.BasicStroke(2f));
                g.drawRoundRect(2, 2, 30, 30, 8, 8);
                g.setColor(Theme.CREAM);
                g.setStroke(new java.awt.BasicStroke(2f));
                g.drawRoundRect(7, 7, 20, 20, 4, 4);
                g.drawLine(11, 11, 23, 23);
                g.drawLine(23, 11, 11, 23);
                g.dispose();
            }
        };
    }

    /**
     * The settings card: piece style as one compact horizontal pill row.
     * Three side-by-side pills instead of a 3-row stack keeps the header
     * short enough that the lobby fits a 720px window without scrolling.
     */
    private JPanel buildSettingsCard() {
        JPanel card = UiKit.surface(10);
        card.setLayout(new BorderLayout(4, 4));

        card.add(UiKit.label("PIECE STYLE", 10f), BorderLayout.NORTH);

        JPanel styleRow = new JPanel(new GridLayout(1, 3, 6, 0));
        styleRow.setBackground(Theme.SURFACE);
        String[] names = {"1 · INK TOKENS", "2 · SEAL STONES", "3 · INK SILHOUETTES"};
        for (int i = 0; i < 3; i++) {
            UiKit.PillButton button = UiKit.miniPill(names[i],
                    i + 1 == settings.pieceStyle() ? UiKit.Pill.GOLD : UiKit.Pill.CREAM_OUTLINE);
            // The 9f labels antialias wider than their font metrics (worse on
            // macOS), so the grid cell can ellipsize the longest one — pad
            // every pill's preferred width to keep the full label visible.
            Dimension pillSize = button.getPreferredSize();
            button.setPreferredSize(new Dimension(pillSize.width + 10, pillSize.height));
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
        card.add(styleRow, BorderLayout.CENTER);
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
        JPanel column = new JPanel(new BorderLayout());
        // The field twin of the local: relayout() moves this column between
        // the wide and narrow anchors when the window crosses 800px.
        actionsColumn = column;
        column.setOpaque(false);
        column.setPreferredSize(new java.awt.Dimension(COLUMN_WIDTH, 100));

        // RETURN TO MATCH: shown while a live match is parked in the background.
        returnPill = UiKit.pill("RETURN TO MATCH", UiKit.Pill.GOLD);
        returnPill.addActionListener(event -> model.returnToParkedMatch());
        returnPill.setVisible(false);
        returnWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        returnWrap.setOpaque(false);
        returnWrap.add(returnPill);
        returnWrap.setVisible(false);

        // CREATE card: title, subtitle, button, and the waiting ticket.
        JPanel createCard = UiKit.sticker(14);
        createCard.setLayout(new BorderLayout(10, 10));
        JPanel createHead = new JPanel(new BorderLayout(8, 2));
        createHead.setOpaque(false);
        createHead.add(UiKit.inkLabel("CREATE A MATCH", 13f), BorderLayout.NORTH);
        createHead.add(UiKit.label("Challenge another master to a duel.", 12f),
                BorderLayout.SOUTH);
        createCard.add(createHead, BorderLayout.NORTH);

        JPanel createCenter = new JPanel(new BorderLayout(10, 10));
        createCenter.setOpaque(false);
        createButton = UiKit.pill("Create match", UiKit.Pill.GOLD);
        createButton.addActionListener(event -> model.createMatch());
        JPanel createRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 2));
        createRow.setOpaque(false);
        createRow.add(createButton);
        createCenter.add(createRow, BorderLayout.NORTH);

        // The ticket: the room code becomes the major waiting object.
        ticket = new JPanel(new BorderLayout(6, 8));
        ticket.setBackground(Theme.BOARD_LIGHT);
        ticket.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createDashedBorder(Theme.INK, 6, 4),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        roomCodeLabel.setFont(Theme.display(30f));
        roomCodeLabel.setForeground(Theme.INK);
        roomCodeLabel.setHorizontalAlignment(JLabel.CENTER);
        roomCaption.setHorizontalAlignment(JLabel.CENTER);
        roomCaption.setForeground(Theme.INK);
        ticket.add(roomCodeLabel, BorderLayout.CENTER);
        JPanel ticketRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        ticketRow.setOpaque(false);
        JButton copy = UiKit.miniPill("Copy code", UiKit.Pill.GOLD_OUTLINE);
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

        // SINGLE PLAY card: one Play button, then pick the bot level.
        JPanel singlePlayCard = buildSinglePlayCard();

        // JOIN card: unified input row, list, refresh.
        JPanel joinCard = buildJoinCard();

        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        // Tight 4px rhythm: with the leaderboard folded into the SINGLE PLAY
        // header and the compact settings row, the three cards fit a 720px
        // window without a scrollbar.
        stack.add(wrap(returnWrap));
        stack.add(Box.createVerticalStrut(4));
        stack.add(wrap(createCard));
        stack.add(Box.createVerticalStrut(4));
        stack.add(wrap(singlePlayCard));
        stack.add(Box.createVerticalStrut(4));
        stack.add(wrap(joinCard));

        // The column scrolls only as an emergency fallback on very short
        // windows (<650px, armed in relayout()); from 720px up the bar stays
        // hidden and the content is sized to fit.
        actionsScroll = new JScrollPane(stack,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        actionsScroll.setBorder(BorderFactory.createEmptyBorder());
        actionsScroll.setOpaque(false);
        actionsScroll.getViewport().setOpaque(false);
        actionsScroll.getVerticalScrollBar().setUnitIncrement(16);
        column.add(actionsScroll, BorderLayout.CENTER);
        return column;
    }

    /** Holds a section stretched to the column width (uniform edges). */
    private JPanel wrap(JComponent inner) {
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(inner, BorderLayout.CENTER);
        return holder;
    }

    /** The single-play card: a Play button that reveals the difficulty pick. */
    private JPanel buildSinglePlayCard() {
        JPanel card = UiKit.surface(14);
        card.setLayout(new BorderLayout(8, 8));

        // Header row: title left, leaderboard shortcut inline right — the
        // leaderboard used to be a full card of its own, one row too many
        // for a 720px window.
        JPanel head = new JPanel(new BorderLayout(8, 0));
        head.setOpaque(false);
        head.add(UiKit.boldLabel("SINGLE PLAY", 13f), BorderLayout.WEST);
        UiKit.PillButton leaderboardPill =
                UiKit.miniPill("LEADERBOARD", UiKit.Pill.CREAM_OUTLINE);
        leaderboardPill.addActionListener(event -> openLeaderboard.run());
        head.add(leaderboardPill, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setBackground(Theme.SURFACE);

        JPanel playRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 2));
        playRow.setBackground(Theme.SURFACE);
        playButton = UiKit.pill("Play", UiKit.Pill.GOLD);
        playButton.addActionListener(event -> toggleDifficultyRow());
        playRow.add(playButton);
        center.add(playRow, BorderLayout.NORTH);

        // Hidden until the player presses Play: the three bot levels, each
        // with a one-line character description shown on hover/selection.
        // The fixed-height slot keeps the reveal at a stable 36px so the
        // card's rhythm does not jump as the row appears or hides.
        difficultyRow = new JPanel(new GridLayout(1, 3, 8, 0));
        difficultyRow.setBackground(Theme.SURFACE);
        difficultyRow.add(difficultyPill("ROOKIE", Difficulty.ROOKIE,
                "Forgiving — the dojo holds back."));
        difficultyRow.add(difficultyPill("SENIOR", Difficulty.SENIOR,
                "Sharp — two moves ahead."));
        difficultyRow.add(difficultyPill("LEGEND", Difficulty.LEGEND,
                "Merciless — five moves of iron sight."));
        // Assign the FIELD: a local declaration here would shadow it and
        // leave toggleDifficultyRow() dereferencing null on the first click.
        difficultySlot = new JPanel(new BorderLayout());
        difficultySlot.setBackground(Theme.SURFACE);
        difficultySlot.setPreferredSize(new Dimension(10, 36));
        difficultySlot.add(difficultyRow, BorderLayout.CENTER);
        difficultySlot.setVisible(false);
        JPanel difficultyBlock = new JPanel(new BorderLayout(0, 6));
        difficultyBlock.setBackground(Theme.SURFACE);
        difficultyBlock.add(difficultySlot, BorderLayout.NORTH);
        difficultyHint = new JLabel("Pick your opponent.", SwingConstants.CENTER);
        difficultyHint.setFont(Theme.normal(11f).deriveFont(java.awt.Font.ITALIC));
        difficultyHint.setForeground(Theme.MUTED);
        difficultyBlock.add(difficultyHint, BorderLayout.SOUTH);
        center.add(difficultyBlock, BorderLayout.CENTER);

        card.add(center, BorderLayout.CENTER);
        return card;
    }

    /** Shows or hides the difficulty choice under the Play button. */
    private void toggleDifficultyRow() {
        difficultySlot.setVisible(!difficultySlot.isVisible());
        revalidate();
        repaint();
    }

    /** Hides the difficulty choice again (used when returning to the lobby). */
    private void hideDifficultyRow() {
        if (difficultySlot != null) {
            difficultySlot.setVisible(false);
        }
    }

    /** One difficulty pill; describes itself on hover and starts the match. */
    private UiKit.PillButton difficultyPill(String label, Difficulty difficulty,
                                            String description) {
        UiKit.PillButton pill = UiKit.miniPill(label, UiKit.Pill.GOLD);
        pill.addActionListener(event -> {
            difficultyHint.setText(description);
            model.startPracticeMatch(difficulty);
        });
        pill.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent event) {
                difficultyHint.setText(description);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent event) {
                difficultyHint.setText("Pick your opponent.");
            }
        });
        return pill;
    }

    private JPanel ticket;

    /** The join card: code input + join button, open-match list, refresh. */
    private JPanel buildJoinCard() {
        JPanel card = UiKit.surface(14);
        card.setLayout(new BorderLayout(8, 8));

        JPanel joinHeader = new JPanel(new GridLayout(2, 1, 0, 0));
        joinHeader.setBackground(Theme.SURFACE);
        joinHeader.add(UiKit.boldLabel("JOIN A MATCH", 13f));
        joinHeader.add(UiKit.label("Enter your opponent's code.", 12f));
        card.add(joinHeader, BorderLayout.NORTH);

        joinField.setPreferredSize(new Dimension(200, 36));
        joinField.setToolTipText("The 5-character room code");
        // Enter submits the join (§16).
        joinField.addActionListener(event -> joinButton.doClick());
        // The Join button is only live while a plausible code is entered.
        joinButton = UiKit.pill("Join", UiKit.Pill.GOLD);
        joinButton.setEnabled(false);
        joinButton.addActionListener(event -> {
            String code = joinField.getText().trim().toUpperCase();
            if (!code.isEmpty()) {
                model.joinMatch(code);
            }
        });
        joinField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void update() {
                joinButton.setEnabled(joinField.getText().trim().length() >= 4);
            }

            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent event) {
                update();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent event) {
                update();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent event) {
                update();
            }
        });
        JPanel joinRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        joinRow.setBackground(Theme.SURFACE);
        joinField.setBackground(Theme.BOARD_LIGHT);
        joinField.setForeground(Theme.INK);
        joinField.setBorder(UiKit.fieldBorder());
        joinField.setFont(Theme.display(14f));
        joinRow.add(joinField);
        joinRow.add(joinButton);

        // One CENTER child: join row on top, match list below (capped height
        // so the column keeps its compact, uniform rhythm).
        JPanel joinAndList = new JPanel(new BorderLayout(8, 8));
        joinAndList.setBackground(Theme.SURFACE);
        joinAndList.add(joinRow, BorderLayout.NORTH);

        matchList.setBackground(Theme.SURFACE);
        matchList.setForeground(Theme.CREAM);
        matchList.setSelectionBackground(Theme.AMBER);
        matchList.setSelectionForeground(Theme.INK);
        matchList.setFont(Theme.normal(13f));
        matchList.setCellRenderer(new MatchRowRenderer());
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
        // Two visible rows: the open-match list is usually the quiet dojo
        // placeholder, and the shaved height keeps the lobby scrollbar-free
        // at 720px (the list itself still scrolls by wheel when fuller).
        matchScroll.setPreferredSize(new Dimension(10, 56));
        joinAndList.add(matchScroll, BorderLayout.CENTER);
        card.add(joinAndList, BorderLayout.CENTER);

        JPanel southRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 2));
        southRow.setBackground(Theme.SURFACE);
        JButton refresh = UiKit.miniPill("Refresh", UiKit.Pill.CREAM_OUTLINE);
        refresh.addActionListener(event -> model.refreshMatches());
        southRow.add(refresh);
        card.add(southRow, BorderLayout.SOUTH);
        return card;
    }

    /** Open-match rows in body text; the quiet placeholder in muted small caps. */
    private final class MatchRowRenderer extends javax.swing.DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean selected, boolean focused) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list,
                    value, index, selected, focused);
            String entry = String.valueOf(value);
            if (entry.startsWith("THE DOJO")) {
                setFont(Theme.normal(11f));
                setForeground(selected ? Theme.INK : Theme.MUTED);
            } else {
                setFont(Theme.normal(13f));
                setForeground(Theme.CREAM);
            }
            setBackground(selected ? Theme.AMBER : Theme.SURFACE);
            setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            return label;
        }
    }

    // ------------------------------------------------------------------
    // Waiting state + data refresh
    // ------------------------------------------------------------------

    private void setWaitingVisible(boolean visible) {
        ticket.setVisible(visible);
        // While waiting for an opponent you already host a match — creating
        // another would just fail server-side, so the button greys out.
        createButton.setEnabled(!visible);
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
