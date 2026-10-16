package onitama.client.ui;

import onitama.client.ClientSettings;
import onitama.client.MenuTheme;
import onitama.client.state.*;
import onitama.net.*;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import static org.junit.jupiter.api.Assertions.*;

class LobbyMenuTest {
    private record Ui(ClientModel model, ClientSettings settings, LobbyPanel panel,
                      List<Message> sent, AtomicInteger saved, AtomicInteger quit, AtomicInteger help, AtomicInteger about) { }

    private Ui ui() {
        List<Message> sent = new ArrayList<>();
        var model = new ClientModel(new ServerConnection() {
            public void send(Message message) { sent.add(message); }
            public boolean isConnected() { return true; }
            public void ensureConnected(String host, int port) { }
        });
        var settings = new ClientSettings();
        var saved = new AtomicInteger();
        var quit = new AtomicInteger();
        var help = new AtomicInteger();
        var about = new AtomicInteger();
        var panel = new LobbyPanel(model, settings, () -> { }, quit::incrementAndGet, saved::incrementAndGet,
                help::incrementAndGet, about::incrementAndGet);
        model.handleMessage(new LoginResponse(true, null, new UserProfile("alice", 1000, 0, 0), null));
        sent.clear();
        panel.setSize(1280, 660);
        layout(panel);
        return new Ui(model, settings, panel, sent, saved, quit, help, about);
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

    @Test void homeShowsOnlyPrimaryActionsAndLeavesBackgroundExposed() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = ui();
            var texts = find(ui.panel, JButton.class).stream().filter(Component::isVisible).map(JButton::getText).toList();
            assertEquals(List.of("Play", "Settings", "Quit game", "Leaderboard", "How to Play", "About"), texts);
            JPanel surface = find(ui.panel, JPanel.class).stream().filter(p -> "mainMenuSurface".equals(p.getName())).findFirst().orElseThrow();
            assertTrue(surface.getWidth() <= 340);
            assertTrue(surface.getHeight() < 450);
            assertTrue(surface.getX() > ui.panel.getWidth() / 2);
            var profile = find(ui.panel, JLabel.class).stream().filter(l -> l.getText().startsWith("alice")).findFirst().orElseThrow();
            assertEquals("alice", profile.getText());
            assertTrue(profile.getFont().getSize2D() >= 20, "account name is more prominent than the small links");
            var profilePoint = SwingUtilities.convertPoint(profile, 0, 0, ui.panel);
            var quitPoint = SwingUtilities.convertPoint(button(ui, "Quit game"), 0, 0, ui.panel);
            var leaderboardPoint = SwingUtilities.convertPoint(button(ui, "Leaderboard"), 0, 0, ui.panel);
            assertTrue(profilePoint.y > quitPoint.y + button(ui, "Quit game").getHeight());
            assertTrue(profilePoint.y + profile.getHeight() <= leaderboardPoint.y);
            assertTrue(profile.getHeight() >= profile.getFontMetrics(profile.getFont()).getHeight(),
                    "the compact account caption must not be clipped");
            button(ui, "How to Play").doClick();
            button(ui, "About").doClick();
            assertEquals(1, ui.help.get()); assertEquals(1, ui.about.get());
            button(ui, "Quit game").doClick();
            assertEquals(1, ui.quit.get());
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
            button(ui, "‹ Back").doClick();
            button(ui, "Create match").doClick();
            assertEquals(1, ui.sent.size());
            button(ui, "‹ Back").doClick();
            button(ui, "Join a game").doClick();
            assertTrue(ui.sent.get(1) instanceof ListMatchesRequest);
            var field = find(ui.panel, JTextField.class).get(0);
            field.setText("bad");
            assertFalse(button(ui, "Join").isEnabled());
            field.setText("abcd2");
            assertTrue(button(ui, "Join").isEnabled());
            field.postActionEvent();
            assertEquals(new JoinMatchRequest("ABCD2"), ui.sent.get(2));
            button(ui, "‹ Back").doClick();
            button(ui, "Practice").doClick();
            button(ui, "Rookie").doClick();
            assertTrue(ui.model.isPracticeMode());
            assertEquals(Screen.GAME, ui.model.screen());
            ui.model.resign();
        });
    }

    @Test void settingsAreHiddenAtHomeAndChoosingStylePersistsOnce() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = ui();
            assertTrue(find(ui.panel, JButton.class).stream().noneMatch(b -> b.getText().equals("Heraldic shields")));
            button(ui, "Settings").doClick();
            button(ui, "Heraldic shields").doClick();
            try {
                assertEquals(2, ui.settings.pieceStyle());
                assertEquals(1, ui.saved.get());
                assertTrue(button(ui, "Heraldic shields").isSelected());
                assertFalse(button(ui, "Steel helms").isSelected());
                assertTrue(ui.sent.isEmpty());
                button(ui, "‹ Back").doClick();
                assertNotNull(button(ui, "Play"));
            } finally { BoardPanel.setDefaultPieceStyle(1); }
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

    @Test void roomBrowserUsesTypedRoomsAndRetainsParkedMatchResume() throws Exception {
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
            button(ui, "‹ Back").doClick();
            button(ui, "Practice").doClick();
            button(ui, "Rookie").doClick();
            ui.model.leaveToLobby();
            assertTrue(ui.model.hasParkedMatch());
            assertTrue(button(ui, "Resume match").isVisible());
            button(ui, "Play").doClick();
            assertFalse(button(ui, "Create match").isEnabled());
            assertFalse(button(ui, "Join a game").isEnabled());
            assertFalse(button(ui, "Practice").isEnabled());
            button(ui, "‹ Back").doClick();
            button(ui, "Resume match").doClick();
            assertEquals(Screen.GAME, ui.model.screen());
            ui.model.resign();
        });
    }
}
