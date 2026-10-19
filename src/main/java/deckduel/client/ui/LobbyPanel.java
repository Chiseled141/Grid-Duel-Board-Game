package deckduel.client.ui;

import deckduel.client.ClientSettings;
import deckduel.client.state.ClientModel;
import deckduel.client.state.ClientModelListener;
import deckduel.client.state.Screen;
import deckduel.net.MatchSummary;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** A compact menu with focused Play and Settings pages over the medieval battlefield. */
public final class LobbyPanel extends BattlefieldMenuPanel {
    private final ClientModel model;
    private final ClientSettings settings;
    private final Runnable quitGame;
    private final Runnable saveSettings;
    private final JPanel surface = new JPanel(new BorderLayout()) {
        @Override public Dimension getPreferredSize() {
            Dimension natural = super.getPreferredSize();
            return new Dimension(340, natural.height);
        }

    };
    private final JLabel profile = caption(" ");
    private final JLabel roomCode = caption("· · · · ·");
    private final JLabel roomStatus = caption("Creating your room…");
    private final JLabel noRooms = caption("No open rooms yet.");
    private final JTextField joinField = new JTextField() {
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = UiKit.nice(graphics);
            g.setColor(new Color(20, 27, 26, 240));
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            g.setColor(isFocusOwner() ? new Color(255, 211, 133) : new Color(235, 228, 211, 120));
            g.drawLine(12, getHeight() - 2, getWidth() - 12, getHeight() - 2);
            g.dispose();
            super.paintComponent(graphics);
            if (getText().isEmpty() && !isFocusOwner()) {
                g = UiKit.nice(graphics);
                g.setFont(Theme.normal(14f));
                g.setColor(new Color(255, 238, 200));
                g.drawString("Enter room code", 13, (getHeight() - g.getFontMetrics().getHeight()) / 2
                        + g.getFontMetrics().getAscent());
                g.dispose();
            }
        }
    };
    private final DefaultListModel<MatchSummary> roomRows = new DefaultListModel<>();
    private final JList<MatchSummary> rooms = new JList<>(roomRows);
    private final JScrollPane roomScroll = new JScrollPane(rooms);
    private final JButton join = MenuSettingsOption.route("Join", "Open the room entered above.", this::joinRoom);
    private final JButton create = MenuSettingsOption.route("Create match", "Invite a friend with a room code.", this::createRoom);
    private final JButton practice = MenuSettingsOption.route("Practice", "Play against the practice bot.", () -> showPage("practice"));
    private final JButton browse = MenuSettingsOption.route("Join a game", "Enter a code or choose an open room.", () -> showPage("join"));
    private final JButton find = MenuSettingsOption.route("Find match", "Search for another online player.", this::findMatch);
    private final JLabel searchStatus = caption("Searching for an opponent…");
    private final JLabel searchDetail = caption("You can cancel while you wait.");
    private final JButton searchAgain = MenuSettingsOption.route("Search again", "Look for another online player.", this::findMatch);
    private final JButton cancelSearch = MenuSettingsOption.route("Cancel search", "Stop looking for an opponent.", this::cancelFindMatch);
    private final JButton[] cardControls = new JButton[2];
    private final java.util.Map<String, JPanel> pages = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, MenuNavigation> navigation = new java.util.HashMap<>();
    private String currentPage = "home";
    private boolean creatingRoom;
    private final Timer refreshTimer;
    private final java.util.List<JButton> petalTargets = new java.util.ArrayList<>();
    private long petalStartedAt;
    private final Timer petalTimer = new Timer(40, event -> {
        updatePetals();
        repaint();
    });

    /** Builds the menu; Quit uses the frame's normal save-and-close path. */
    public LobbyPanel(ClientModel model, ClientSettings settings, Runnable quitGame) {
        this(model, settings, quitGame, () -> {
            Thread writer = new Thread(() -> {
                synchronized (settings) { settings.save(); }
            }, "deck-duel-save-settings");
            writer.setDaemon(true);
            writer.start();
        });
    }

    // Inject persistence in UI tests so selecting a style never writes real user preferences.
    LobbyPanel(ClientModel model, ClientSettings settings, Runnable quitGame, Runnable saveSettings) {
        super(model, Screen.LOBBY, settings);
        this.model = model;
        this.refreshTimer = new Timer(5000, event -> model.refreshMatches());
        this.settings = settings;
        this.quitGame = quitGame;
        this.saveSettings = saveSettings;
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(32, 32, 32, 48));
        surface.setOpaque(false);
        surface.setName("mainMenuSurface");
        getAccessibleContext().setAccessibleName("Deck Duel main menu");
        getAccessibleContext().setAccessibleDescription("A Card-Based Strategy Game");
        surface.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
        var anchor = new GridBagConstraints();
        anchor.gridx = 0; anchor.gridy = 0;
        anchor.weightx = 1;
        anchor.weighty = 1;
        anchor.anchor = GridBagConstraints.LINE_END;
        add(surface, anchor);
        profile.setFont(Theme.bold(20f));
        profile.setName("menuPlayerName");
        add(profile, anchor);
        pages.put("home", buildHome());
        pages.put("play", buildPlay());
        pages.put("create", buildCreate());
        pages.put("join", buildJoin());
        pages.put("practice", buildPractice());
        pages.put("find", buildFindMatch());
        pages.put("settings", buildSettings());
        for (String name : java.util.List.of("home", "play", "practice")) {
            JPanel menu = pages.get(name);
            var buttons = new java.util.ArrayList<JButton>();
            for (Component child : menu.getComponents()) if (child instanceof JButton button) {
                buttons.add(button);
                if (name.equals("home")) button.putClientProperty(MenuNavigation.HOME, Boolean.TRUE);
            }
            navigation.put(name, new MenuNavigation(menu, buttons, name.equals("home") ? 0 : 1));
        }
        model.addListener(new ClientModelListener() {
            @Override public void onScreenChanged(Screen screen) {
                if (screen == Screen.LOBBY) {
                    creatingRoom = false;
                    refreshState();
                    showPage("home");
                }
                updatePolling();
            }
            @Override public void onLobbyChanged() { refreshState(); updatePolling(); }
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

    @Override public void doLayout() {
        super.doLayout();
        if (currentPage.equals("home")) {
            int offset = Math.min(16, Math.max(0,
                    getHeight() - getInsets().bottom - surface.getY() - surface.getHeight()));
            surface.setLocation(surface.getX(), surface.getY() + offset);
        }
        int width = Math.min(profile.getPreferredSize().width, Math.max(0, getWidth() - 122));
        // Align the account name with the floating sound button without a header bar.
        int right = getWidth() - 84;
        profile.setBounds(Math.max(0, right - width), 16, width, 36);
    }

    private JPanel buildHome() {
        JPanel page = page(null, null, null);
        JButton play = new MenuAction("Play", () -> showPage("play"), true);
        JButton preferences = new MenuAction("Settings", () -> showPage("settings"), false);
        JButton quit = new MenuAction("Quit game", this::confirmQuit, false);
        petalTargets.add(play); petalTargets.add(preferences); petalTargets.add(quit);
        line(page, play);
        line(page, preferences);
        line(page, new MenuAction("Atlas", model::openAtlas, false));
        line(page, quit);
        page.add(Box.createVerticalStrut(14));
        return page;
    }

    private void confirmQuit() {
        if (UiKit.confirmDialog(this, "Quit game", "Are you sure you want to quit?",
                "Quit game", UiKit.Pill.GOLD)) quitGame.run();
    }

    private JPanel buildPlay() {
        JPanel page = page("Play", "Choose your next match.", "home");
        line(page, find);
        page.add(Box.createVerticalStrut(8));
        line(page, create);
        page.add(Box.createVerticalStrut(8));
        line(page, browse);
        page.add(Box.createVerticalStrut(8));
        line(page, practice);
        return page;
    }

    private void findMatch() {
        showPage("find");
        model.findMatch();
    }

    private void cancelFindMatch() {
        model.cancelFindMatch();
    }

    private JPanel buildFindMatch() {
        JPanel page = page("Find match", null, "play");
        JPanel status = new JPanel() {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = UiKit.nice(graphics);
                g.setColor(new Color(20, 27, 26, 235));
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g.setColor(new Color(255, 238, 200, 95));
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g.dispose();
            }
        };
        status.setName("matchSearchStatus");
        status.setOpaque(false);
        status.setLayout(new BoxLayout(status, BoxLayout.Y_AXIS));
        status.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        status.setPreferredSize(new Dimension(330, 154));
        line(status, settingLabel("ONLINE MATCHMAKING"));
        status.add(Box.createVerticalStrut(10));
        searchStatus.setFont(Theme.bold(20f));
        searchStatus.setForeground(new Color(255, 246, 224));
        searchDetail.setFont(Theme.normal(13f));
        line(status, searchStatus);
        status.add(Box.createVerticalStrut(5));
        line(status, searchDetail);
        JLabel serverHint = caption("Players on this server can join you.");
        serverHint.setFont(Theme.normal(12f));
        line(status, serverHint);
        line(page, status);
        page.add(Box.createVerticalStrut(12));
        cancelSearch.setSelected(true);
        cancelSearch.setFont(Theme.bold(17f));
        searchAgain.setSelected(true);
        searchAgain.setFont(Theme.bold(17f));
        line(page, cancelSearch);
        line(page, searchAgain);
        return page;
    }

    private static JPanel roomSurface() {
        JPanel panel = new JPanel() {
            @Override protected void paintComponent(Graphics graphics) {
                var g = UiKit.nice(graphics);
                g.setColor(new Color(20, 27, 26, 235));
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g.setColor(new Color(255, 238, 200, 95));
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        return panel;
    }

    private JPanel buildCreate() {
        JPanel page = page("Create match", "Share your room code with a friend.", "play");
        roomCode.setFont(Theme.display(34f));
        roomCode.setHorizontalAlignment(SwingConstants.CENTER);
        roomCode.setPreferredSize(new Dimension(280, 64));
        JPanel codePanel = roomSurface();
        codePanel.setLayout(new BoxLayout(codePanel, BoxLayout.Y_AXIS));
        line(codePanel, settingLabel("YOUR ROOM CODE"));
        codePanel.add(Box.createVerticalStrut(8));
        line(codePanel, roomCode);
        codePanel.add(Box.createVerticalStrut(10));
        roomStatus.setFont(Theme.normal(14f));
        line(codePanel, roomStatus);
        line(page, codePanel);
        page.add(Box.createVerticalStrut(12));
        JButton copy = MenuSettingsOption.route("Copy code", "Share this code with a friend.", () -> {
            String code = model.pendingRoomCode();
            if (code != null) Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(code), null);
        });
        copy.setSelected(true);
        line(page, copy);
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
        page.add(Box.createVerticalStrut(8));
        join.setSelected(true);
        line(page, join);
        page.add(Box.createVerticalStrut(16));
        JPanel browser = roomSurface();
        browser.setLayout(new BoxLayout(browser, BoxLayout.Y_AXIS));
        line(browser, settingLabel("OPEN GAMES"));
        browser.add(Box.createVerticalStrut(8));
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
                setBackground(selected ? new Color(99, 77, 39, 240) : new Color(29, 36, 33, 245));
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
        noRooms.setFont(Theme.normal(14f));
        line(browser, noRooms);
        line(browser, roomScroll);
        line(page, browser);
        page.add(Box.createVerticalStrut(10));
        line(page, MenuSettingsOption.route("Refresh games", "Check for rooms waiting for a player.", model::refreshMatches));
        return page;
    }

    private JPanel buildPractice() {
        JPanel page = page("Practice", "An practice match against the bot.", "play");
        line(page, MenuSettingsOption.route("Easy bot", "Plays the best card for this turn only.", () -> model.startPractice(false)));
        page.add(Box.createVerticalStrut(8));
        line(page, MenuSettingsOption.route("Hard bot", "Plans ahead with its hand and saves its Spells.", () -> model.startPractice(true)));
        return page;
    }

    private JPanel buildSettings() {
        JPanel page = page("Settings", null, "home");
        line(page, settingLabel("CARD SELECTION"));
        page.add(Box.createVerticalStrut(7));
        String[] controls = {"Click or slide up", "Slide up only"};
        for (int i = 0; i < controls.length; i++) {
            boolean click = i == 0;
            cardControls[i] = MenuSettingsOption.choice(controls[i],
                    click ? "Click a card or drag it upward." : "Select only with an upward drag.", () -> {
                synchronized (settings) { settings.setClickToSelectCards(click); }
                updateCardControls();
                saveSettings.run();
            });
            line(page, cardControls[i]);
            if (i == 0) page.add(Box.createVerticalStrut(6));
        }
        page.add(Box.createVerticalStrut(20));
        line(page, settingLabel("ANIMATION & SOUND"));
        page.add(Box.createVerticalStrut(7));
        JButton motion = MenuSettingsOption.toggle("Reduced motion", "Limit background and card animation.");
        motion.setSelected(settings.reducedMotion());
        motion.setText("Reduced motion: " + (settings.reducedMotion() ? "On" : "Off"));
        motion.setName("reducedMotion");
        motion.addActionListener(event -> {
            settings.setReducedMotion(!settings.reducedMotion());
            model.setReducedMotion(settings.reducedMotion());
            motion.setText("Reduced motion: " + (settings.reducedMotion() ? "On" : "Off"));
            motion.setSelected(settings.reducedMotion());
            saveSettings.run();
        });
        line(page, motion);
        page.add(Box.createVerticalStrut(6));
        JButton sounds = MenuSettingsOption.toggle("Game sounds", "Card, combo and score sounds in matches.");
        sounds.setName("gameSounds");
        Runnable showSounds = () -> {
            sounds.setText("Game sounds: " + (settings.gameSounds() ? "On" : "Off"));
            sounds.setSelected(settings.gameSounds());
        };
        showSounds.run();
        sounds.addActionListener(event -> {
            settings.setGameSounds(!settings.gameSounds());
            showSounds.run();
            saveSettings.run();
        });
        line(page, sounds);
        page.add(Box.createVerticalStrut(12));
        line(page, caption("Changes saved automatically"));
        updateCardControls();
        return page;
    }

    private static JLabel settingLabel(String text) {
        JLabel label = caption(text);
        label.setFont(Theme.normal(11f));
        label.setForeground(new Color(240, 219, 180));
        return label;
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
            JButton backButton = smallButton("Back", () -> showPage(back));
            line(page, backButton);
            for (String key : java.util.List.of("ESCAPE", "BACK_SPACE"))
                page.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(key), "menu.back");
            page.getActionMap().put("menu.back", new AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent event) { backButton.doClick(0); }
            });
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
                int x = getHorizontalAlignment() == SwingConstants.CENTER
                        ? (getWidth() - g.getFontMetrics().stringWidth(getText())) / 2 : getInsets().left;
                int y = (getHeight() - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent();
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

    static JButton smallButton(String text, Runnable action) {
        JButton button = new MenuAction(text, action, false);
        button.putClientProperty("menu.compactBack", Boolean.TRUE);
        button.setFont(Theme.normal(13f));
        button.setPreferredSize(new Dimension(90, 36));
        button.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        return button;
    }

    private static void line(JPanel page, JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        int width = Boolean.TRUE.equals(component.getClientProperty("menu.compactBack"))
                ? component.getPreferredSize().width : Integer.MAX_VALUE;
        component.setMaximumSize(new Dimension(width, component.getPreferredSize().height));
        page.add(component);
    }

    private void showPage(String name) {
        if (currentPage.equals("find") && !name.equals("find") && model.isFindingMatch())
            model.cancelFindMatch();
        currentPage = name;
        surface.removeAll();
        surface.add(pages.get(name), BorderLayout.CENTER);
        if (name.equals("join")) model.refreshMatches();
        refreshAvailability();
        MenuNavigation controls = navigation.get(name);
        if (controls != null) {
            controls.reset();
            SwingUtilities.invokeLater(() -> {
                if (currentPage.equals(name) && model.screen() == Screen.LOBBY) controls.focusSelection();
            });
        } else {
            SwingUtilities.invokeLater(() -> {
                if (!currentPage.equals(name) || model.screen() != Screen.LOBBY) return;
                if (name.equals("join")) joinField.requestFocusInWindow();
                else for (Component child : pages.get(name).getComponents())
                    if (child instanceof JButton button && button.isEnabled() && button.isVisible()) {
                        button.requestFocusInWindow(); break;
                    }
            });
        }
        updatePolling();
        revalidate();
        repaint();
    }

    private void createRoom() {
        if (creatingRoom) return;
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
        boolean searching = model.isFindingMatch();
        find.setEnabled(!searching && !creatingRoom);
        searchStatus.setText(searching ? "Searching for a player…" : "Search stopped");
        searchDetail.setText(searching ? "You can cancel while you wait." : "Ready when you are.");
        cancelSearch.setVisible(searching);
        searchAgain.setVisible(!searching);
        searchAgain.setEnabled(true);
        create.setEnabled(!searching && !creatingRoom);
        browse.setEnabled(!searching);
        practice.setEnabled(!searching);
        join.setEnabled(!searching && joinField.getText().trim().toUpperCase(java.util.Locale.ROOT).matches("[A-Z2-9]{5}"));
        navigation.values().forEach(MenuNavigation::refresh);
    }

    private void refreshState() {
        if (model.me() != null) {
            profile.setText(model.me().username());
            profile.setToolTipText(model.me().username());
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

    private void updatePolling() {
        if (isShowing() && model.screen() == Screen.LOBBY && currentPage.equals("join")) refreshTimer.start();
        else refreshTimer.stop();
        updatePetals();
    }

    private void updatePetals() {
        boolean active = isShowing() && model.screen() == Screen.LOBBY
                && currentPage.equals("home") && !model.reducedMotion();
        if (active && !petalTimer.isRunning()) {
            petalStartedAt = System.nanoTime();
            petalTimer.start();
        } else if (!active) {
            petalTimer.stop(); petalStartedAt = 0;
        }
    }

    @Override public void removeNotify() {
        refreshTimer.stop();
        petalTimer.stop(); petalStartedAt = 0;
        super.removeNotify();
    }

    @Override protected void paintChildren(Graphics graphics) {
        super.paintChildren(graphics);
        if (currentPage.equals("home")) MenuTitle.paint((Graphics2D) graphics, getWidth(), getHeight());
        if (petalTimer.isRunning()) MenuPetals.paint(graphics, this, petalTargets,
                (System.nanoTime() - petalStartedAt) / 1_000_000_000.0);
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            // An edge-to-edge scrim follows the scenery, without creating a menu box.
            float left = getWidth() * .48f;
            g.setPaint(new GradientPaint(left, 0, new Color(16, 23, 25, 0),
                    getWidth(), 0, new Color(16, 23, 25, 180)));
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
        MenuAction(String text, Runnable action, boolean primary) {
            super(text);
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
                boolean managed = Boolean.TRUE.equals(getClientProperty(MenuNavigation.MANAGED));
                boolean highlight = isEnabled() && (managed
                        ? Boolean.TRUE.equals(getClientProperty(MenuNavigation.ACTIVE))
                        : getModel().isRollover() || isFocusOwner() || isSelected());
                if (Boolean.TRUE.equals(getClientProperty("menu.compactBack"))) {
                    g.setColor(new Color(25, 30, 27, highlight ? 170 : 115));
                    g.fillRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 8, 8);
                    g.setColor(highlight ? new Color(255, 216, 143, 220) : new Color(255, 238, 200, 85));
                    g.setStroke(new BasicStroke(1.2f));
                    g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 8, 8);
                }
                g.setFont(highlight && !managed ? Theme.bold(getFont().getSize2D()) : getFont());
                FontMetrics metrics = g.getFontMetrics();
                int x = getInsets().left;
                int y = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
                Color color = !isEnabled() ? new Color(173, 175, 169)
                        : highlight ? new Color(255, 216, 143) : new Color(255, 248, 232);
                drawMenuText(g, getText(), x, y, color);
            } finally { g.dispose(); }
        }
    }
}
