package deckduel.client.ui;

import deckduel.client.ClientSettings;
import deckduel.client.MenuTheme;
import deckduel.client.startup.UpdateCheck;
import deckduel.client.startup.UpdateFlow;
import deckduel.client.startup.UpdateInstaller;
import deckduel.client.startup.UpdateService;
import deckduel.client.state.*;
import deckduel.net.*;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import static org.junit.jupiter.api.Assertions.*;

class LobbyMenuTest {
    @Test void gameSoundToggleNotifiesPlaybackAfterChangingThePreference() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = ui();
            var observed = new ArrayList<Boolean>();
            ui.panel.onGameSoundsChanged(() -> observed.add(ui.settings.gameSounds()));
            button(ui, "Settings").doClick();
            button(ui, "Game sounds: On").doClick();
            button(ui, "Game sounds: Off").doClick();
            assertEquals(List.of(false, true), observed, "the audio owner sees the new mute state");
            assertEquals(2, ui.saved.get(), "both choices remain persisted");
        });
    }

    @Test void petalTimerOnlyRunsOnTheVisibleHomeMenuAndHonorsReducedMotion() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = ui(); var frame = new JFrame(); frame.add(ui.panel); frame.setSize(1280, 720);
            try {
                frame.setVisible(true);
                var field = LobbyPanel.class.getDeclaredField("petalTimer"); field.setAccessible(true);
                Timer timer = (Timer) field.get(ui.panel);
                assertTrue(timer.isRunning());
                button(ui, "Settings").doClick(); assertFalse(timer.isRunning());
                button(ui, "Back").doClick(); assertTrue(timer.isRunning());
                ui.model.setReducedMotion(true); assertFalse(timer.isRunning());
                ui.model.setReducedMotion(false); assertTrue(timer.isRunning());
                button(ui, "Play").doClick(); assertFalse(timer.isRunning());
                button(ui, "Back").doClick(); assertTrue(timer.isRunning());
                ui.model.requestHistory(); assertFalse(timer.isRunning());
                ui.model.leaveToLobby(); assertTrue(timer.isRunning());
                frame.setVisible(false); assertFalse(timer.isRunning());
                frame.setVisible(true); assertTrue(timer.isRunning());
                frame.dispose(); assertFalse(timer.isRunning());
            } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
            finally { frame.dispose(); }
        });
    }

    private record Ui(ClientModel model, ClientSettings settings, LobbyPanel panel,
                      List<Message> sent, AtomicInteger saved, AtomicInteger quit) { }

    private Ui ui() { return ui(UpdateService.notConfigured(), UpdateInstaller.notConfigured(), null); }

    /** Update work runs inline on the EDT here, so each click's result is visible at once. */
    private Ui ui(UpdateService service, UpdateInstaller installer, String fixture) {
        List<Message> sent = new ArrayList<>();
        var model = new ClientModel(new ServerConnection() {
            public void send(Message message) { sent.add(message); }
            public boolean isConnected() { return true; }
            public void ensureConnected(deckduel.client.startup.ServerRoute route) { }
        });
        var settings = new ClientSettings();
        var saved = new AtomicInteger();
        var quit = new AtomicInteger();
        var updates = new UpdateFlow("1.0.0", service, installer, Runnable::run, Runnable::run);
        var panel = new LobbyPanel(model, settings, quit::incrementAndGet, saved::incrementAndGet, updates, fixture, false);
        model.handleMessage(new LoginResponse(true, null, new UserProfile("alice", 0, 0)));
        sent.clear();
        panel.setSize(1280, 660);
        layout(panel);
        return new Ui(model, settings, panel, sent, saved, quit);
    }

    private void layout(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents()) if (child instanceof Container c) layout(c);
    }

    private <T> List<T> find(Container parent, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Component child : parent.getComponents()) {
            if (type.isInstance(child)) result.add(type.cast(child));
            if (child instanceof Container c) result.addAll(find(c, type));
        }
        return result;
    }

    private JButton button(Ui ui, String text) {
        return find(ui.panel, JButton.class).stream().filter(b -> b.getText().equals(text)).findFirst().orElseThrow();
    }

    private void answerQuit(Ui ui, String answer) {
        var answered = new java.util.concurrent.atomic.AtomicBoolean();
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
        Timer timer = new Timer(20, null);
        timer.addActionListener(event -> {
            for (Window window : Window.getWindows()) if (window instanceof JDialog dialog && dialog.isShowing()) {
                if (System.nanoTime() > deadline) { dialog.dispose(); timer.stop(); return; }
                if (!dialog.getTitle().equals("Quit game")) continue;
                for (JButton candidate : find(dialog, JButton.class)) if (candidate.getText().equalsIgnoreCase(answer)) {
                    answered.set(true); timer.stop(); candidate.doClick(); return;
                }
            }
        });
        timer.start();
        try { button(ui, "Quit game").doClick(); } finally { timer.stop(); }
        assertTrue(answered.get(), "quit confirmation must appear before closing");
    }

    @Test void homeShowsOnlyPrimaryActionsAndLeavesBackgroundExposed() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = ui();
            var texts = find(ui.panel, JButton.class).stream().filter(Component::isVisible).map(JButton::getText).toList();
            assertEquals(List.of("Play", "Settings", "Quit game"), texts);
            JPanel surface = find(ui.panel, JPanel.class).stream().filter(p -> "mainMenuSurface".equals(p.getName())).findFirst().orElseThrow();
            assertTrue(surface.getWidth() <= 340);
            assertTrue(surface.getHeight() < 450);
            assertTrue(surface.getX() > ui.panel.getWidth() / 2);
            var profile = find(ui.panel, JLabel.class).stream().filter(l -> l.getText().startsWith("alice")).findFirst().orElseThrow();
            assertEquals("alice", profile.getText());
            assertTrue(profile.getFont().getSize2D() >= 20, "account name is more prominent than the small links");
            var profilePoint = SwingUtilities.convertPoint(profile, 0, 0, ui.panel);
            assertTrue(profilePoint.y < 60, "account name belongs beside the sound icon");
            assertTrue(profilePoint.x > ui.panel.getWidth() / 2);
            assertTrue(profilePoint.x + profile.getWidth() <= ui.panel.getWidth() - 76);
            assertTrue(profile.getHeight() >= profile.getFontMetrics(profile.getFont()).getHeight());
            assertTrue(texts.stream().noneMatch(text -> text.equals("How to Play") || text.equals("About")));
            assertTrue(find(ui.panel, JLabel.class).stream()
                    .noneMatch(label -> label.getText().contains("ELO")), "no rating in the menu");
            answerQuit(ui, "No"); assertEquals(0, ui.quit.get());
            answerQuit(ui, "Quit game"); assertEquals(1, ui.quit.get());
            assertTrue(ui.sent.isEmpty());
        });
    }

    @Test void playRoutesCreationJoiningAndPracticeWithoutDuplicateCreation() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = ui();
            button(ui, "Play").doClick();
            assertNotNull(button(ui, "Create match"));
            assertNotNull(button(ui, "Join a game"));
            assertNotNull(button(ui, "Practice"));
            button(ui, "Create match").doClick();
            assertEquals(List.of(new CreateMatchRequest()), ui.sent);
            ui.model.handleMessage(new MatchCreated("ABCDE"));
            assertTrue(find(ui.panel, JLabel.class).stream().anyMatch(l -> l.getText().equals("ABCDE")));
            button(ui, "Back").doClick();
            button(ui, "Create match").doClick();
            assertEquals(1, ui.sent.size());
            button(ui, "Back").doClick();
            button(ui, "Join a game").doClick();
            assertTrue(ui.sent.get(1) instanceof ListMatchesRequest);
            var field = find(ui.panel, JTextField.class).get(0);
            field.setText("bad");
            assertFalse(button(ui, "Join").isEnabled());
            field.setText("abcd2");
            assertTrue(button(ui, "Join").isEnabled());
            field.postActionEvent();
            assertEquals(new JoinMatchRequest("ABCD2"), ui.sent.get(2));
            button(ui, "Back").doClick();
            button(ui, "Practice").doClick();
            button(ui, "Hard bot").doClick();
            assertEquals(new PracticeRequest(true), ui.sent.get(ui.sent.size() - 1));
        });
    }

    private void menuKey(Ui ui, String pageName, String key) {
        JPanel page = find(ui.panel, JPanel.class).stream().filter(panel -> pageName.equals(panel.getName())).findFirst().orElseThrow();
        Object id = page.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(KeyStroke.getKeyStroke(key));
        assertNotNull(id, "key must be installed on the visible button-only page");
        page.getActionMap().get(id).actionPerformed(new java.awt.event.ActionEvent(page, 0, key));
    }

    private void selected(Ui ui, String text) {
        var actions = find(ui.panel, JButton.class).stream().filter(button -> Boolean.TRUE.equals(button.getClientProperty(MenuNavigation.ACTIVE))).toList();
        assertEquals(1, actions.size(), "mouse and keyboard share one highlight");
        assertEquals(text, actions.get(0).getText());
    }

    @Test void mouseAndDirectionalKeysShareSelectionAndActivatePlayAndPractice() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = ui(); selected(ui, "Play");
            assertEquals(34f, button(ui, "Play").getFont().getSize2D());
            JButton settings = button(ui, "Settings");
            settings.dispatchEvent(new java.awt.event.MouseEvent(settings, java.awt.event.MouseEvent.MOUSE_ENTERED,
                    System.currentTimeMillis(), 0, 5, 5, 0, false));
            selected(ui, "Settings");
            assertEquals(34f, settings.getFont().getSize2D());
            assertEquals(25f, button(ui, "Play").getFont().getSize2D());
            menuKey(ui, "menu-home", "S"); selected(ui, "Quit game");
            menuKey(ui, "menu-home", "W"); selected(ui, "Settings");
            menuKey(ui, "menu-home", "LEFT"); selected(ui, "Play");
            menuKey(ui, "menu-home", "ENTER"); selected(ui, "Create match");
            assertFalse(button(ui, "Create match").isSelected(), "Create match has no permanent highlight");
            JButton highlighted = button(ui, "Create match"); highlighted.setSize(330, 56);
            var painted = new java.awt.image.BufferedImage(330, 56, java.awt.image.BufferedImage.TYPE_INT_RGB);
            var graphics = painted.createGraphics(); highlighted.paint(graphics); graphics.dispose();
            int readableDetail = 0;
            for (int y = 31; y < 45; y++) for (int x = 14; x < 270; x++)
                if (new Color(painted.getRGB(x, y)).getRed() < 140) readableDetail++;
            assertTrue(readableDetail > 30, "description must use dark text on the gold selection");
            menuKey(ui, "menu-play", "D"); selected(ui, "Join a game");
            menuKey(ui, "menu-play", "RIGHT"); selected(ui, "Practice");
            menuKey(ui, "menu-play", "ENTER"); selected(ui, "Easy bot");
            menuKey(ui, "menu-practice", "DOWN"); selected(ui, "Hard bot");
            menuKey(ui, "menu-practice", "A"); selected(ui, "Easy bot");
            menuKey(ui, "menu-practice", "ESCAPE"); selected(ui, "Create match");
            menuKey(ui, "menu-play", "ESCAPE"); selected(ui, "Play");
            menuKey(ui, "menu-home", "UP"); selected(ui, "Quit game"); // hidden Resume is skipped
            menuKey(ui, "menu-home", "DOWN"); menuKey(ui, "menu-home", "ENTER");
            button(ui, "Practice").doClick();
            menuKey(ui, "menu-practice", "ENTER");
            assertEquals(List.of(new PracticeRequest(false)), ui.sent);
        });
    }

    @Test void navigationSkipsDisabledActionsAndNeverCapturesRoomCodeTyping() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = ui(); button(ui, "Play").doClick(); button(ui, "Create match").doClick();
            button(ui, "Back").doClick(); // The room is still being created.
            selected(ui, "Back"); menuKey(ui, "menu-play", "DOWN"); selected(ui, "Join a game");
            menuKey(ui, "menu-play", "UP"); selected(ui, "Back");
            ui.model.handleMessage(new ErrorMessage("Room creation failed"));
            menuKey(ui, "menu-play", "S"); selected(ui, "Create match");
            button(ui, "Join a game").doClick();
            JPanel joinPage = find(ui.panel, JPanel.class).stream().filter(panel -> "menu-join-a-game".equals(panel.getName())).findFirst().orElseThrow();
            assertNull(joinPage.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(KeyStroke.getKeyStroke("W")));
            var field = find(ui.panel, JTextField.class).get(0); field.setText("WASD2");
            assertEquals("WASD2", field.getText());
        });
    }

    @Test void escapeAndBackspaceReturnFromRoomAndSettingsPagesWithoutBreakingEditing() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = ui(); button(ui, "Play").doClick();
            button(ui, "Create match").doClick();
            ui.model.handleMessage(new MatchCreated("ABCDE"));
            menuKey(ui, "menu-create-match", "BACK_SPACE");
            assertNotNull(button(ui, "Create match"));
            button(ui, "Join a game").doClick();
            var field = find(ui.panel, JTextField.class).get(0);
            field.setText("ABCDE"); field.setCaretPosition(5);
            Object delete = field.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("BACK_SPACE"));
            assertNotNull(delete);
            field.getActionMap().get(delete).actionPerformed(new java.awt.event.ActionEvent(field, 0, "delete"));
            assertEquals("ABCD", field.getText());
            assertNotNull(button(ui, "Join"));
            menuKey(ui, "menu-join-a-game", "ESCAPE");
            menuKey(ui, "menu-play", "BACK_SPACE");
            button(ui, "Settings").doClick();
            menuKey(ui, "menu-settings", "BACK_SPACE");
            assertNotNull(button(ui, "Play"));
        });
    }


    @Test void cardControlPickerSavesEitherModeWithoutNetworkTraffic() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = ui(); button(ui, "Settings").doClick();
            assertTrue(button(ui, "Click or slide up").isSelected());
            button(ui, "Slide up only").doClick();
            assertFalse(ui.settings.clickToSelectCards());
            assertTrue(button(ui, "Slide up only").isSelected());
            assertFalse(button(ui, "Click or slide up").isSelected());
            button(ui, "Click or slide up").doClick();
            assertTrue(ui.settings.clickToSelectCards());
            assertTrue(button(ui, "Click or slide up").isSelected());
            assertEquals(2, ui.saved.get()); assertTrue(ui.sent.isEmpty());
            layout(ui.panel);
            for (String name : List.of("Click or slide up", "Slide up only")) {
                var button = button(ui, name);
                var point = SwingUtilities.convertPoint(button, 0, 0, ui.panel);
                assertTrue(point.y >= 0 && point.y + button.getHeight() <= ui.panel.getHeight());
            }
        });
    }

    @Test void reducedMotionRemainsAvailableWithoutTheGameMenu() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = ui(); button(ui, "Settings").doClick();
            button(ui, "Reduced motion: Off").doClick();
            assertTrue(ui.settings.reducedMotion()); assertTrue(ui.model.reducedMotion());
            button(ui, "Reduced motion: On").doClick();
            assertFalse(ui.settings.reducedMotion()); assertFalse(ui.model.reducedMotion());
            assertEquals(2, ui.saved.get()); assertTrue(ui.sent.isEmpty());
            layout(ui.panel);
            for (var button : find(ui.panel, JButton.class)) {
                var point = SwingUtilities.convertPoint(button, 0, 0, ui.panel);
                assertTrue(point.y >= 0 && point.y + button.getHeight() <= ui.panel.getHeight());
            }
        });
    }

    @Test void retiredThemeCannotBeSelectedFromTheMenu() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = ui();
            assertTrue(find(ui.panel, JButton.class).stream().noneMatch(button ->
                    List.of("Change theme", "Mountain valley").contains(button.getText())));
            assertEquals(List.of(MenuTheme.GOLDEN_FIELDS), List.of(MenuTheme.values()));
            assertTrue(ui.sent.isEmpty());
        });
    }

    @Test void roomBrowserUsesTypedRoomsAndPracticeAsksTheServer() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = ui();
            button(ui, "Play").doClick();
            button(ui, "Join a game").doClick();
            var scroll = find(ui.panel, JScrollPane.class).get(0);
            assertFalse(scroll.isVisible(), "empty browser must not leave a blank box");
            assertFalse(scroll.isOpaque());
            assertFalse(scroll.getViewport().isOpaque());
            assertFalse(find(ui.panel, JTextField.class).get(0).isOpaque());
            var room = new MatchSummary("FGHJK", "opponent");
            ui.model.handleMessage(new ListMatchesResponse(List.of(room)));
            assertTrue(scroll.isVisible());
            var browser = find(ui.panel, JList.class).get(0);
            assertEquals(room, browser.getModel().getElementAt(0));
            browser.setSelectedIndex(0);
            assertEquals("FGHJK", find(ui.panel, JTextField.class).get(0).getText());
            ui.model.handleMessage(new ListMatchesResponse(List.of()));
            assertFalse(scroll.isVisible(), "browser collapses again when last room disappears");
            button(ui, "Back").doClick();
            button(ui, "Practice").doClick();
            button(ui, "Easy bot").doClick();
            assertTrue(ui.sent.get(ui.sent.size() - 1) instanceof PracticeRequest);
            assertEquals(Screen.LOBBY, ui.model.screen(), "the table opens only when the server sends a match");
        });
    }

    private JLabel named(Ui ui, String name) {
        return find(ui.panel, JLabel.class).stream().filter(l -> name.equals(l.getName())).findFirst().orElseThrow();
    }

    @Test void settingsShowsTheInstalledVersionAndAnHonestCheckWhenNoServiceExists() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = ui(); button(ui, "Settings").doClick();
            JButton check = button(ui, "Check for updates");
            assertEquals("Installed version 1.0.0.", check.getAccessibleContext().getAccessibleDescription());
            assertEquals("Not checked yet.", named(ui, "updateStatus").getText());
            assertFalse(button(ui, "Install update").isVisible());
            check.doClick();
            assertEquals("No update service is configured; nothing was checked.", named(ui, "updateStatus").getText());
            assertFalse(button(ui, "Install update").isVisible());
            assertTrue(ui.sent.isEmpty(), "update checks never use the game connection");
        });
    }

    @Test void settingsOffersAFoundUpdateAndShowsInstallFailureRetryAndRestart() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var attempts = new AtomicInteger();
            var ui = ui(installed -> UpdateCheck.optional("1.1.0"), (update, progress) -> {
                progress.accept(.5);
                if (attempts.getAndIncrement() == 0) throw new java.io.IOException("disk full");
                progress.accept(1);
            }, "optional-update");
            button(ui, "Settings").doClick();
            button(ui, "Check for updates").doClick();
            assertEquals("Test fixture · Version 1.1.0 is available.", named(ui, "updateStatus").getText());
            button(ui, "Install update").doClick();
            assertEquals("Test fixture · Update failed: disk full", named(ui, "updateStatus").getText());
            button(ui, "Retry update").doClick();
            assertEquals("Test fixture · Installed 1.1.0. Restart Deck Duel to finish.", named(ui, "updateStatus").getText());
            assertEquals(2, attempts.get());
            assertFalse(find(ui.panel, JButton.class).stream().anyMatch(b -> b.isVisible() && b.getText().endsWith("update")));
        });
    }

    @Test void settingsStaysOnScreenWithAnUpdateOffered() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = ui(installed -> UpdateCheck.required("2.0.0"), UpdateInstaller.notConfigured(), null);
            button(ui, "Settings").doClick();
            button(ui, "Check for updates").doClick();
            assertTrue(button(ui, "Install update").isVisible());
            layout(ui.panel);
            for (var button : find(ui.panel, JButton.class)) {
                if (!button.isVisible()) continue;
                var point = SwingUtilities.convertPoint(button, 0, 0, ui.panel);
                assertTrue(point.y >= 0 && point.y + button.getHeight() <= ui.panel.getHeight(), button.getText() + " fits");
            }
        });
    }

    private List<String> visibleButtons(Ui ui) {
        return find(ui.panel, JButton.class).stream().filter(Component::isVisible).map(JButton::getText).toList();
    }

    @Test void offlinePlayShowsPracticeAndHistoryButNoHostingOrJoining() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<Message> sent = new ArrayList<>();
            var model = new ClientModel(new ServerConnection() {
                public void send(Message message) { sent.add(message); }
                public boolean isConnected() { return true; }
                public void ensureConnected(deckduel.client.startup.ServerRoute route) { }
            });
            var updates = new UpdateFlow("1.0.0", UpdateService.notConfigured(), UpdateInstaller.notConfigured(), Runnable::run, Runnable::run);
            var panel = new LobbyPanel(model, new ClientSettings(), () -> { }, () -> { }, updates, null, false);
            model.beginOffline(new deckduel.client.startup.ServerRoute("127.0.0.1", 40404, false), "offline", "random-secret");
            model.handleMessage(new LoginResponse(true, null, new UserProfile("offline", 0, 0)));
            var ui = new Ui(model, new ClientSettings(), panel, sent, new AtomicInteger(), new AtomicInteger());
            assertTrue(find(panel, JLabel.class).stream().anyMatch(l -> l.getText().equals("Offline play")), "the mode is named");
            button(ui, "Play").doClick();
            List<String> shown = visibleButtons(ui);
            assertTrue(shown.containsAll(List.of("Practice", "Match history")));
            assertFalse(shown.contains("Create match"));
            assertFalse(shown.contains("Join a game"));
            assertEquals("Offline practice. History and replays last only for this session.", named(ui, "playNotice").getText());
            sent.clear();
            button(ui, "Match history").doClick();
            assertEquals(Screen.HISTORY, model.screen());
            assertEquals(List.of(new HistoryRequest()), sent);
        });
    }

    @Test void onlineMenuOffersHistoryAndARequiredUpdateDisablesNewMatches() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = ui();
            button(ui, "Play").doClick();
            assertTrue(visibleButtons(ui).containsAll(List.of("Create match", "Join a game", "Practice", "Match history")));
            assertEquals(" ", named(ui, "playNotice").getText());
            ui.model.requireUpdate("2.0.0");
            for (String name : List.of("Create match", "Join a game", "Practice")) assertFalse(button(ui, name).isEnabled(), name);
            assertTrue(button(ui, "Match history").isEnabled(), "history and replays stay available");
            assertEquals("Version 2.0.0 is required before your next match. Install it in Settings.", named(ui, "playNotice").getText());
            button(ui, "Create match").doClick();
            assertTrue(ui.sent.isEmpty());
        });
    }
}
