package deckduel.client.ui;

import deckduel.client.ClientSettings;
import deckduel.client.startup.UpdateFlow;
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
    private Runnable gameSoundsChanged = () -> { };
    private final UpdateFlow updates;
    private final String fixture;
    private final boolean restarts;
    private final JPanel surface = new JPanel(new BorderLayout()) {
        @Override public Dimension getPreferredSize() {
            Dimension natural = super.getPreferredSize();
            return new Dimension(340, natural.height);
        }

    };
    private final JLabel profile = new JLabel(" ", SwingConstants.CENTER) {
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = UiKit.nice(graphics);
            UiKit.paintTile(g, getWidth(), getHeight(), false, false);
            g.dispose();
            super.paintComponent(graphics);
        }
    };
    private final JLabel roomCode = caption("· · · · ·");
    private final JLabel roomStatus = caption("Creating your room…");
    private final JLabel noRooms = caption("No open rooms yet.");
    private final JTextField joinField = new JTextField() {
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = UiKit.nice(graphics);
            UiKit.paintSlot(g, getWidth(), getHeight(), isFocusOwner());
            g.dispose();
            super.paintComponent(graphics);
            if (getText().isEmpty() && !isFocusOwner()) {
                g = UiKit.nice(graphics);
                g.setFont(Theme.normal(14f));
                g.setColor(Theme.FADED);
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
    private final JButton history = MenuSettingsOption.route("Match history", "Your recorded matches and their replays.", this::openHistory);
    // Gaps after Create match and Join a game, hidden with them in offline play.
    private final Component createGap = Box.createVerticalStrut(8), browseGap = Box.createVerticalStrut(8);
    private final JLabel playNotice = caption(" ");
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

    /**
     * Builds the menu; Quit uses the frame's normal save-and-close path. Settings checks for
     * updates through {@code updates}, labelling the result when {@code fixture} names a test fixture.
     */
    public LobbyPanel(ClientModel model, ClientSettings settings, Runnable quitGame, UpdateFlow updates, String fixture,
                      boolean restarts) {
        this(model, settings, quitGame, () -> {
            Thread writer = new Thread(() -> {
                synchronized (settings) { settings.save(); }
            }, "deck-duel-save-settings");
            writer.setDaemon(true);
            writer.start();
        }, updates, fixture, restarts);
    }

    // Inject persistence in UI tests so selecting a style never writes real user preferences.
    LobbyPanel(ClientModel model, ClientSettings settings, Runnable quitGame, Runnable saveSettings,
               UpdateFlow updates, String fixture, boolean restarts) {
        super(model, Screen.LOBBY, settings);
        this.updates = updates;
        this.fixture = fixture;
        this.restarts = restarts;
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
        profile.setFont(Theme.decorative(20f));
        profile.setForeground(Theme.BRASS_LIGHT);
        profile.setBorder(BorderFactory.createEmptyBorder(5, 18, 5, 18));
        profile.putClientProperty("html.disable", Boolean.TRUE);
        profile.setName("menuPlayerName");
        add(profile, anchor);
        pages.put("home", buildHome());
        pages.put("play", buildPlay());
        pages.put("create", buildCreate());
        pages.put("join", buildJoin());
        pages.put("practice", buildPractice());
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
                    roomStatus.setText(model.newMatchesBlocked() ? "Room closed: an update comes first."
                            : "Room creation failed. Go back to try again.");
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
        // Matching iron plates keep the account and music control legible against the sky.
        int right = getWidth() - 76;
        profile.setBounds(Math.max(0, right - width), 16, width, 40);
    }

    private JPanel buildHome() {
        JPanel page = page(null, null, null);
        JButton play = new MenuAction("Play", () -> showPage("play"), true);
        JButton preferences = new MenuAction("Settings", () -> showPage("settings"), false);
        JButton quit = new MenuAction("Quit game", this::confirmQuit, false);
        petalTargets.add(play); petalTargets.add(preferences); petalTargets.add(quit);
        line(page, play);
        line(page, preferences);
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
        line(page, create);
        page.add(createGap);
        line(page, browse);
        page.add(browseGap);
        line(page, practice);
        page.add(Box.createVerticalStrut(8));
        line(page, history);
        page.add(Box.createVerticalStrut(10));
        playNotice.setName("playNotice");
        playNotice.setForeground(Theme.BRASS_LIGHT);
        line(page, playNotice);
        return page;
    }

    private void openHistory() {
        if (history.isEnabled()) model.requestHistory();
    }

    private static JPanel roomSurface() {
        JPanel panel = new JPanel() {
            @Override protected void paintComponent(Graphics graphics) {
                var g = UiKit.nice(graphics);
                UiKit.paintPlate(g, getWidth() - 4, getHeight() - 4);
                g.dispose();
            }
        };
        panel.setOpaque(false);
        // The extra right and bottom room holds the plate's shadow.
        panel.setBorder(BorderFactory.createEmptyBorder(16, 20, 20, 22));
        return panel;
    }

    private JPanel buildCreate() {
        JPanel page = page("Create match", "Share your room code with a friend.", "play");
        roomCode.setFont(Theme.display(34f));
        roomCode.setForeground(Theme.BRASS_LIGHT);
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
        joinField.setCaretColor(Theme.BRASS_LIGHT);
        joinField.setSelectionColor(Theme.BRASS_DARK);
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
                Graphics2D tile = (Graphics2D) g.create();
                tile.translate(0, 2);
                UiKit.paintTile(tile, getWidth(), getHeight() - 4, false, Boolean.TRUE.equals(getClientProperty("room.selected")));
                tile.dispose();
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
                setForeground(selected || focus ? Theme.BRASS_LIGHT : Theme.CREAM);
                putClientProperty("room.selected", selected);
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
                thumbColor = Theme.BRASS;
                trackColor = Theme.SLOT;
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
            gameSoundsChanged.run();
            showSounds.run();
            saveSettings.run();
        });
        line(page, sounds);
        page.add(Box.createVerticalStrut(20));
        buildUpdates(page);
        page.add(Box.createVerticalStrut(12));
        line(page, caption("Changes saved automatically"));
        updateCardControls();
        return page;
    }

    /** Notifies the frame so muting releases the audio device and cancels queued cues. */
    void onGameSoundsChanged(Runnable listener) { gameSoundsChanged = listener; }

    /** The installed version, Check for updates, and Install update once a release is offered. */
    private void buildUpdates(JPanel page) {
        line(page, settingLabel("UPDATES"));
        page.add(Box.createVerticalStrut(7));
        JButton check = MenuSettingsOption.route("Check for updates",
                "Installed version " + updates.installedVersion() + ".", updates::check);
        check.setName("checkForUpdates");
        line(page, check);
        JLabel status = caption(" ");
        status.setName("updateStatus");
        line(page, status);
        JButton install = MenuSettingsOption.route("Install update", "Download and install the new release.", updates::install);
        install.setName("installUpdate");
        line(page, install);
        java.util.function.Consumer<UpdateFlow.State> show = state -> {
            status.setText((fixture == null ? "" : "Test fixture · ") + StartupPanel.summary(state, restarts));
            check.setEnabled(state.stage() != UpdateFlow.Stage.CHECKING && state.stage() != UpdateFlow.Stage.INSTALLING);
            install.setVisible(state.canInstall());
            install.setText(state.stage() == UpdateFlow.Stage.INSTALL_FAILED ? "Retry update" : "Install update");
            page.revalidate();
        };
        updates.addListener(show);
        show.accept(updates.state());
    }

    private static JLabel settingLabel(String text) {
        JLabel label = caption(text);
        label.setFont(Theme.bold(11f));
        label.setForeground(Theme.BRASS_LIGHT);
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
        // A required or installing update blocks new matches; offline play has no other players to host or join.
        boolean updateNeeded = model.newMatchesBlocked();
        create.setEnabled(!creatingRoom && !updateNeeded);
        browse.setEnabled(!updateNeeded);
        practice.setEnabled(!updateNeeded);
        for (Component online : new Component[] {create, createGap, browse, browseGap}) online.setVisible(!model.isOffline());
        playNotice.setText(model.requiredUpdate() != null
                ? "Version " + model.requiredUpdate() + " is required before your next match. Install it in Settings."
                : model.maintenance() != null ? "Server maintenance: new matches can start again after the restart."
                : updateNeeded ? "An update is installing. New matches can start after Deck Duel restarts."
                : model.isOffline() ? "Offline practice. History and replays last only for this session." : " ");
        join.setEnabled(joinField.getText().trim().toUpperCase(java.util.Locale.ROOT).matches("[A-Z2-9]{5}"));
        navigation.values().forEach(MenuNavigation::refresh);
    }

    private void refreshState() {
        if (model.me() != null) {
            // Offline play names the mode, not its throwaway local account.
            profile.setText(model.isOffline() ? "Offline play" : model.me().username());
            profile.setToolTipText(model.isOffline() ? "Playing offline against the practice bots" : model.me().username());
        }
        String code = model.pendingRoomCode();
        if (code != null) {
            roomCode.setText(code);
            roomStatus.setText("Waiting for an opponent…");
        } else if (model.newMatchesBlocked()) {
            // The model closed the room when the update block began.
            creatingRoom = false;
            roomCode.setText("· · · · ·");
            roomStatus.setText(model.maintenance() != null ? "Room closed for server maintenance." : "Room closed: an update comes first.");
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
        /**
         * The selected home entry, lettered in brass with a dark iron edge like the title, over a
         * fine gold rule that fades to the right (the table plates' groove).
         */
        private void paintBrass(Graphics2D g, int x, int y, FontMetrics metrics) {
            Shape text = new java.awt.font.TextLayout(getText(), g.getFont(), g.getFontRenderContext())
                    .getOutline(java.awt.geom.AffineTransform.getTranslateInstance(x, y));
            Rectangle box = text.getBounds();
            g.translate(0, 2); g.setColor(new Color(0, 0, 0, 120)); g.fill(text); g.translate(0, -2);
            g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(Theme.WOOD_LINE); g.draw(text);
            g.setPaint(new LinearGradientPaint(0, box.y, 0, box.y + box.height, new float[] {0f, .5f, 1f},
                    new Color[] {new Color(0xF4DE9E), Theme.BRASS_LIGHT, Theme.BRASS}));
            g.fill(text);
            int rule = y + metrics.getDescent() + 2;
            g.setPaint(new GradientPaint(x, 0, Theme.BRASS_LIGHT, x + box.width + 60, 0, new Color(217, 181, 106, 0)));
            g.fillRect(x, rule, box.width + 60, 2);
        }

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
                if (Boolean.TRUE.equals(getClientProperty("menu.compactBack")))
                    UiKit.paintTile(g, getWidth(), getHeight(), false, highlight);
                g.setFont(highlight && !managed ? Theme.bold(getFont().getSize2D()) : getFont());
                FontMetrics metrics = g.getFontMetrics();
                int x = getInsets().left;
                int y = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
                boolean home = Boolean.TRUE.equals(getClientProperty(MenuNavigation.HOME));
                if (home && highlight) paintBrass(g, x, y, metrics);
                else drawMenuText(g, getText(), x, y, !isEnabled() ? new Color(173, 175, 169)
                        : highlight ? Theme.BRASS_LIGHT : new Color(255, 248, 232));
            } finally { g.dispose(); }
        }
    }
}
