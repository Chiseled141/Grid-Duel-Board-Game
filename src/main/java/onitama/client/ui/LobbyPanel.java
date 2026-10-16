package onitama.client.ui;

import onitama.client.ClientSettings;
import onitama.client.state.ClientModel;
import onitama.client.state.ClientModelListener;
import onitama.client.state.Screen;
import onitama.core.Difficulty;
import onitama.net.MatchSummary;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** A compact menu with focused Play and Settings pages over the medieval battlefield. */
public final class LobbyPanel extends BattlefieldMenuPanel {
    private final ClientModel model;
    private final ClientSettings settings;
    private final Runnable openLeaderboard;
    private final Runnable quitGame;
    private final Runnable openHelp;
    private final Runnable openAbout;
    private final Runnable saveSettings;
    private final JPanel surface = new JPanel(new BorderLayout()) {
        @Override public Dimension getPreferredSize() {
            Dimension natural = super.getPreferredSize();
            return new Dimension(340, natural.height);
        }

    };
    private final JLabel profile = caption(" ");
    private final JLabel rating = caption(" ");
    private final JLabel roomCode = caption("· · · · ·");
    private final JLabel roomStatus = caption("Creating your room…");
    private final JLabel noRooms = caption("No open rooms yet.");
    private final JTextField joinField = new JTextField() {
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = UiKit.nice(graphics);
            g.setColor(new Color(13, 25, 27, 100));
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            g.setColor(isFocusOwner() ? new Color(255, 211, 133) : new Color(235, 228, 211, 120));
            g.drawLine(12, getHeight() - 2, getWidth() - 12, getHeight() - 2);
            g.dispose();
            super.paintComponent(graphics);
            if (getText().isEmpty() && !isFocusOwner()) {
                g = UiKit.nice(graphics);
                g.setFont(Theme.normal(14f));
                g.setColor(new Color(235, 228, 211, 185));
                g.drawString("Enter room code", 13, (getHeight() - g.getFontMetrics().getHeight()) / 2
                        + g.getFontMetrics().getAscent());
                g.dispose();
            }
        }
    };
    private final DefaultListModel<MatchSummary> roomRows = new DefaultListModel<>();
    private final JList<MatchSummary> rooms = new JList<>(roomRows);
    private final JScrollPane roomScroll = new JScrollPane(rooms);
    private final JButton join = new MenuAction("Join", this::joinRoom, true);
    private final JButton create = new MenuAction("Create match", this::createRoom, false);
    private final JButton practice = new MenuAction("Practice", () -> showPage("practice"), false);
    private final JButton browse = new MenuAction("Join a game", () -> showPage("join"), false);
    private final JButton resume;
    private final JButton[] cardControls = new JButton[2];
    private final JButton[] styles = new JButton[3];
    private final java.util.Map<String, JPanel> pages = new java.util.LinkedHashMap<>();
    private String currentPage = "home";
    private boolean creatingRoom;
    private final Timer refreshTimer;

    /** Builds the menu; Quit uses the frame's normal save-and-close path. */
    public LobbyPanel(ClientModel model, ClientSettings settings, Runnable openLeaderboard, Runnable quitGame,
                      Runnable openHelp, Runnable openAbout) {
        this(model, settings, openLeaderboard, quitGame, () -> {
            Thread writer = new Thread(() -> {
                synchronized (settings) { settings.save(); }
            }, "grid-duel-save-settings");
            writer.setDaemon(true);
            writer.start();
        }, openHelp, openAbout);
    }

    // Inject persistence in UI tests so selecting a style never writes real user preferences.
    LobbyPanel(ClientModel model, ClientSettings settings, Runnable openLeaderboard,
               Runnable quitGame, Runnable saveSettings, Runnable openHelp, Runnable openAbout) {
        super(model, Screen.LOBBY, settings);
        this.model = model;
        this.resume = new MenuAction("Resume match", model::returnToParkedMatch, true);
        this.refreshTimer = new Timer(5000, event -> model.refreshMatches());
        this.settings = settings;
        this.openLeaderboard = openLeaderboard;
        this.quitGame = quitGame;
        this.openHelp = openHelp;
        this.openAbout = openAbout;
        this.saveSettings = saveSettings;
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(32, 32, 32, 48));
        surface.setOpaque(false);
        surface.setName("mainMenuSurface");
        surface.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
        var anchor = new GridBagConstraints();
        anchor.weightx = 1;
        anchor.weighty = 1;
        anchor.anchor = GridBagConstraints.LINE_END;
        add(surface, anchor);
        pages.put("home", buildHome());
        pages.put("play", buildPlay());
        pages.put("create", buildCreate());
        pages.put("join", buildJoin());
        pages.put("practice", buildPractice());
        pages.put("settings", buildSettings());
        model.addListener(new ClientModelListener() {
            @Override public void onScreenChanged(Screen screen) {
                if (screen == Screen.LOBBY) {
                    creatingRoom = false;
                    refreshState();
                    showPage("home");
                }
                updatePolling();
            }
            @Override public void onLobbyChanged() { refreshState(); }
            @Override public void onMatchCreated(String code) {
                creatingRoom = false;
                refreshState();
                showPage("create");
            }
            @Override public void onError(String text) {
                if (creatingRoom) {
                    creatingRoom = false;
                    roomStatus.setText("Room creation failed. Go back to try again.");
                    refreshAvailability();
                }
            }
        });
        addHierarchyListener(event -> updatePolling());
        refreshState();
        showPage("home");
    }

    private JPanel buildHome() {
        JPanel page = page(null, null, null);
        line(page, new MenuAction("Play", () -> showPage("play"), true));
        line(page, new MenuAction("Settings", () -> showPage("settings"), false));
        line(page, new MenuAction("Quit game", quitGame, false));
        page.add(Box.createVerticalStrut(14));
        JPanel footer = new JPanel();
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        profile.setFont(Theme.cardTitle(21f));
        profile.setName("menuPlayerName");
        rating.setName("menuPlayerRating");
        line(footer, profile);
        line(footer, rating);
        footer.add(Box.createVerticalStrut(5));
        line(footer, smallButton("Leaderboard", openLeaderboard));
        line(footer, smallButton("How to Play", openHelp));
        line(footer, smallButton("About", openAbout));
        line(page, footer);
        line(page, resume);
        return page;
    }

    private JPanel buildPlay() {
        JPanel page = page("Play", "Choose your next match.", "home");
        line(page, create);
        line(page, browse);
        line(page, practice);
        return page;
    }

    private JPanel buildCreate() {
        JPanel page = page("Create match", "Share your room code with a friend.", "play");
        roomCode.setFont(Theme.display(34f));
        roomCode.setHorizontalAlignment(SwingConstants.CENTER);
        roomCode.setPreferredSize(new Dimension(280, 64));
        line(page, roomCode);
        line(page, roomStatus);
        line(page, smallButton("Copy code", () -> {
            String code = model.pendingRoomCode();
            if (code != null) Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(code), null);
        }));
        return page;
    }

    private JPanel buildJoin() {
        JPanel page = page("Join a game", "Enter a room code or pick an open game.", "play");
        joinField.setName("joinCode");
        joinField.setOpaque(false);
        joinField.setForeground(Theme.CREAM);
        joinField.setCaretColor(Theme.AMBER);
        joinField.setSelectionColor(new Color(111, 94, 53));
        joinField.setSelectedTextColor(Theme.CREAM);
        joinField.setFont(Theme.bold(20f));
        joinField.setBorder(BorderFactory.createEmptyBorder(12, 13, 12, 13));
        joinField.getAccessibleContext().setAccessibleName("Room code");
        joinField.setToolTipText("Five-character room code");
        joinField.addActionListener(event -> { if (join.isEnabled()) joinRoom(); });
        joinField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { refreshAvailability(); }
            @Override public void removeUpdate(DocumentEvent event) { refreshAvailability(); }
            @Override public void changedUpdate(DocumentEvent event) { refreshAvailability(); }
        });
        line(page, joinField);
        join.setFont(Theme.normal(23f));
        join.setPreferredSize(new Dimension(292, 48));
        line(page, join);
        page.add(Box.createVerticalStrut(16));
        line(page, caption("OPEN GAMES"));
        rooms.setName("openRooms");
        rooms.setOpaque(false);
        rooms.setForeground(Theme.CREAM);
        rooms.setFont(Theme.normal(14f));
        rooms.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        rooms.setCellRenderer(new DefaultListCellRenderer() {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = UiKit.nice(graphics);
                g.setColor(getBackground());
                g.fillRoundRect(0, 2, getWidth(), getHeight() - 4, 7, 7);
                g.setFont(getFont());
                drawMenuText(g, getText(), 12, (getHeight() - g.getFontMetrics().getHeight()) / 2
                        + g.getFontMetrics().getAscent(), getForeground());
                g.dispose();
            }
            @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                putClientProperty("html.disable", Boolean.TRUE);
                MatchSummary room = (MatchSummary) value;
                setText(room.roomCode() + "  ·  " + room.hostUsername());
                setOpaque(false);
                setForeground(selected || focus ? new Color(255, 211, 133) : Theme.CREAM);
                setBackground(selected ? new Color(99, 77, 39, 180) : new Color(13, 25, 27, 100));
                setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
                return this;
            }
        });
        rooms.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && rooms.getSelectedValue() != null)
                joinField.setText(rooms.getSelectedValue().roomCode());
        });
        rooms.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2 && join.isEnabled()) joinRoom();
            }
        });
        roomScroll.setName("roomBrowser");
        roomScroll.setPreferredSize(new Dimension(280, 120));
        roomScroll.setOpaque(false);
        roomScroll.getViewport().setOpaque(false);
        roomScroll.setBorder(null);
        roomScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        roomScroll.getVerticalScrollBar().setPreferredSize(new Dimension(6, 0));
        roomScroll.getVerticalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() {
                thumbColor = new Color(213, 182, 123);
                trackColor = new Color(25, 34, 35);
            }
            @Override protected JButton createDecreaseButton(int orientation) { return hiddenArrow(); }
            @Override protected JButton createIncreaseButton(int orientation) { return hiddenArrow(); }
            private JButton hiddenArrow() {
                JButton button = new JButton();
                button.setPreferredSize(new Dimension(0, 0));
                return button;
            }
        });
        line(page, noRooms);
        line(page, roomScroll);
        line(page, smallButton("Refresh games", model::refreshMatches));
        return page;
    }

    private JPanel buildPractice() {
        JPanel page = page("Practice", "Choose your opponent.", "play");
        for (Difficulty difficulty : Difficulty.values()) {
            String label = switch (difficulty) {
                case ROOKIE -> "Rookie";
                case SENIOR -> "Senior";
                case LEGEND -> "Legend";
            };
            line(page, new MenuAction(label, () -> model.startPracticeMatch(difficulty), false));
        }
        return page;
    }

    private JPanel buildSettings() {
        JPanel page = page("Settings", "Piece style", "home");
        String[] names = {"Steel helms", "Heraldic shields", "Carved pawns"};
        for (int i = 0; i < names.length; i++) {
            int style = i + 1;
            styles[i] = new MenuAction(names[i], () -> {
                synchronized (settings) { settings.setPieceStyle(style); }
                BoardPanel.setDefaultPieceStyle(style);
                updateStyles();
                saveSettings.run();
            }, false);
            line(page, styles[i]);
        }
        page.add(Box.createVerticalStrut(10));
        line(page, caption("Card selection"));
        String[] controls = {"Click or slide up", "Slide up only"};
        for (int i = 0; i < controls.length; i++) {
            boolean click = i == 0;
            cardControls[i] = new MenuAction(controls[i], () -> {
                synchronized (settings) { settings.setClickToSelectCards(click); }
                updateCardControls();
                saveSettings.run();
            }, false);
            line(page, cardControls[i]);
        }
        page.add(Box.createVerticalStrut(10));
        JButton motion = smallButton("Reduced motion: " + (settings.reducedMotion() ? "On" : "Off"), () -> { });
        motion.setName("reducedMotion");
        motion.addActionListener(event -> {
            settings.setReducedMotion(!settings.reducedMotion());
            model.setReducedMotion(settings.reducedMotion());
            motion.setText("Reduced motion: " + (settings.reducedMotion() ? "On" : "Off"));
            saveSettings.run();
        });
        line(page, motion);
        line(page, caption("Saved automatically"));
        updateCardControls();
        updateStyles();
        return page;
    }

    private void updateCardControls() {
        cardControls[0].setSelected(settings.clickToSelectCards());
        cardControls[1].setSelected(!settings.clickToSelectCards());
    }

    private JPanel page(String title, String subtitle, String back) {
        JPanel page = new JPanel();
        page.setName("menu-" + (title == null ? "home" : title.toLowerCase(java.util.Locale.ROOT).replace(' ', '-')));
        page.setOpaque(false);
        page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
        if (back != null) {
            line(page, smallButton("‹ Back", () -> showPage(back)));
            page.add(Box.createVerticalStrut(8));
        }
        if (title != null) {
            JLabel heading = caption(title);
            heading.setFont(Theme.display(25f));
            line(page, heading);
        }
        if (subtitle != null) line(page, caption(subtitle));
        page.add(Box.createVerticalStrut(16));
        return page;
    }

    private static JLabel caption(String text) {
        JLabel label = new JLabel(text) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = UiKit.nice(graphics);
                g.setFont(getFont());
                int x = getInsets().left;
                int y = getInsets().top + g.getFontMetrics().getAscent();
                drawMenuText(g, getText(), x, y, getForeground());
                g.dispose();
            }
        };
        label.putClientProperty("html.disable", Boolean.TRUE);
        label.setFont(Theme.normal(11f));
        label.setForeground(new Color(235, 228, 211));
        label.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        return label;
    }

    private static JButton smallButton(String text, Runnable action) {
        JButton button = new MenuAction(text, action, false);
        button.setFont(Theme.normal(13f));
        button.setPreferredSize(new Dimension(292, 30));
        button.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        return button;
    }

    private static void line(JPanel page, JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
        page.add(component);
    }

    private void showPage(String name) {
        currentPage = name;
        surface.removeAll();
        surface.add(pages.get(name), BorderLayout.CENTER);
        if (name.equals("join")) model.refreshMatches();
        refreshAvailability();
        updatePolling();
        revalidate();
        repaint();
    }

    private void createRoom() {
        if (model.hasParkedMatch() || creatingRoom) return;
        if (model.pendingRoomCode() == null) {
            creatingRoom = true;
            roomStatus.setText("Creating your room…");
            model.createMatch();
        }
        showPage("create");
    }

    private void joinRoom() {
        if (!join.isEnabled()) return;
        model.joinMatch(joinField.getText().trim().toUpperCase(java.util.Locale.ROOT));
    }

    private void refreshAvailability() {
        boolean parked = model.hasParkedMatch();
        create.setEnabled(!parked && !creatingRoom);
        browse.setEnabled(!parked);
        practice.setEnabled(!parked);
        join.setEnabled(!parked && joinField.getText().trim().toUpperCase(java.util.Locale.ROOT).matches("[A-Z2-9]{5}"));
        resume.setVisible(parked);
    }

    private void refreshState() {
        if (model.me() != null) {
            profile.setText(model.me().username());
            profile.setToolTipText(model.me().username());
            rating.setText("ELO " + model.me().elo());
        }
        String code = model.pendingRoomCode();
        if (code != null) {
            roomCode.setText(code);
            roomStatus.setText("Waiting for an opponent…");
        } else if (!creatingRoom) roomCode.setText("· · · · ·");
        roomRows.clear();
        for (MatchSummary room : model.openMatches())
            if (!room.roomCode().equals(code)) roomRows.addElement(room);
        noRooms.setVisible(roomRows.isEmpty());
        roomScroll.setVisible(!roomRows.isEmpty());
        refreshAvailability();
    }

    private void updateStyles() {
        for (int i = 0; i < styles.length; i++) {
            if (styles[i] != null) styles[i].setSelected(i + 1 == settings.pieceStyle());
        }
    }

    private void updatePolling() {
        if (isShowing() && model.screen() == Screen.LOBBY && currentPage.equals("join")) refreshTimer.start();
        else refreshTimer.stop();
    }

    @Override public void removeNotify() {
        refreshTimer.stop();
        super.removeNotify();
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            // An edge-to-edge scrim follows the scenery, without creating a menu box.
            float left = getWidth() * .48f;
            g.setPaint(new GradientPaint(left, 0, new Color(16, 23, 25, 0),
                    getWidth(), 0, new Color(16, 23, 25, 160)));
            g.fillRect((int) left, 0, getWidth() - (int) left, getHeight());
        } finally { g.dispose(); }
    }

    private static void drawMenuText(Graphics2D g, String text, int x, int y, Color color) {
        g.setColor(new Color(10, 19, 22, 180));
        g.drawString(text, x + 1, y + 2);
        g.setColor(color);
        g.drawString(text, x, y);
    }

    private static final class MenuAction extends JButton {
        private final boolean primary;

        MenuAction(String text, Runnable action, boolean primary) {
            super(text);
            this.primary = primary;
            setPreferredSize(new Dimension(330, primary ? 65 : 53));
            setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
            setHorizontalAlignment(SwingConstants.LEFT);
            setFont(primary ? Theme.display(34f) : Theme.normal(25f));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addActionListener(event -> action.run());
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = UiKit.nice(graphics);
            try {
                boolean highlight = isEnabled() && (getModel().isRollover() || isFocusOwner());
                Color color = !isEnabled() ? new Color(173, 175, 169)
                        : highlight || isSelected() ? new Color(255, 211, 133)
                        : primary ? new Color(255, 232, 184) : new Color(255, 248, 232);
                g.setFont(getFont());
                int x = getInsets().left + (highlight ? 5 : 0);
                int y = (getHeight() - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent();
                drawMenuText(g, getText(), x, y, color);
                if (highlight || isSelected()) {
                    g.setColor(color);
                    int width = Math.min(g.getFontMetrics().stringWidth(getText()), getWidth() - x - 4);
                    g.setStroke(new BasicStroke(1.2f));
                    g.drawLine(x, y + 6, x + width, y + 6);
                }
            } finally { g.dispose(); }
        }
    }
}
