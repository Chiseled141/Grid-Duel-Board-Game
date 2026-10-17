package onitama.client.ui;

import java.awt.Component;
import java.awt.Container;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import onitama.client.ClientSettings;
import onitama.config.RulesConfigLoader;
import onitama.core.duel.RulesConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real match preparation, gated entry, approved scenes and loading lifecycle. */
class LoadingPanelTest {
    private <T> T find(Container parent, Class<T> type) {
        for (Component child : parent.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container container) {
                T found = find(container, type); if (found != null) return found;
            }
        }
        return null;
    }

    private void awaitReady(LoadingPanel panel) throws Exception {
        long deadline = System.nanoTime() + 15_000_000_000L;
        while (System.nanoTime() < deadline) {
            var ready = new AtomicReference<Boolean>();
            SwingUtilities.invokeAndWait(() -> ready.set(panel.isReady()));
            if (ready.get()) return;
            Thread.sleep(20);
        }
        fail("Real match assets did not finish loading");
    }

    @Test void realLoadingGatesEntryAndDoesNotAutomaticallyLeaveAtCompletion() throws Exception {
        var ref = new AtomicReference<LoadingPanel>(); var enters = new AtomicInteger();
        SwingUtilities.invokeAndWait(() -> {
            var panel = new LoadingPanel(new ClientSettings(), RulesConfigLoader.load(null), enters::incrementAndGet); ref.set(panel);
            var button = find(panel, JButton.class);
            assertFalse(button.isVisible()); assertFalse(button.isEnabled()); button.doClick();
            assertEquals(0, enters.get());
        });
        try {
            awaitReady(ref.get());
            SwingUtilities.invokeAndWait(() -> {
                assertEquals(0, enters.get(), "readiness must wait for the user");
                var panel = ref.get(); var button = find(panel, JButton.class);
                assertEquals(100, find(panel, JProgressBar.class).getValue());
                assertEquals("ENTER MATCH", button.getText()); assertTrue(button.isVisible()); assertTrue(button.isEnabled());
                button.doClick(); button.doClick(); assertEquals(1, enters.get());
            });
        } finally { SwingUtilities.invokeAndWait(() -> ref.get().close()); }
    }

    @Test void onlyTheThreeApprovedScenesAreBundled() {
        assertEquals(List.of("loading/castle-passage.png", "loading/campaign-map.png", "loading/fortress-courtyard.png"), LoadingPanel.BACKGROUNDS);
        for (String asset : LoadingPanel.BACKGROUNDS) assertNotNull(getClass().getResource("/assets/" + asset));
        assertNull(getClass().getResource("/assets/loading/armourer-workshop.png"));
    }

    @Test void hintsUseTheActualConfiguredScoreAndManaLimits() {
        var base = RulesConfigLoader.load(null);
        var custom = new RulesConfig(base.rulesVersion(), 2, 3, 2, 1, 7, 60, base.movementCards(), base.skills(), 11);
        var tips = LoadingPanel.tips(custom);
        assertTrue(tips.stream().anyMatch(tip -> tip.contains("11 points")));
        assertTrue(tips.stream().anyMatch(tip -> tip.contains("7 mana")));
        assertTrue(tips.stream().anyMatch(tip -> tip.contains("turn’s start")));
    }

    @Test void backgroundsCycleAndReducedMotionKeepsTheChosenSceneStill() throws Exception {
        var ref = new AtomicReference<LoadingPanel>(); var settings = new ClientSettings();
        SwingUtilities.invokeAndWait(() -> ref.set(new LoadingPanel(settings, RulesConfigLoader.load(null), () -> { }, 0)));
        try {
            awaitReady(ref.get());
            SwingUtilities.invokeAndWait(() -> {
                var panel = ref.get(); assertEquals(0, panel.backgroundIndex());
                panel.nextBackground(); assertEquals(1, panel.backgroundIndex());
                panel.nextBackground(); assertEquals(2, panel.backgroundIndex());
                panel.nextBackground(); assertEquals(0, panel.backgroundIndex());
                settings.setReducedMotion(true); panel.nextBackground(); assertEquals(0, panel.backgroundIndex());
                var tip = namedHint(panel); String before = tip.getText();
                panel.nextHint(); assertNotEquals(before, tip.getText()); assertTrue(panel.isReady());
                panel.setSize(1280, 720); layout(panel);
                var button = find(panel, JButton.class);
                var point = SwingUtilities.convertPoint(button, 0, 0, panel);
                assertTrue(point.y > 500 && point.y + button.getHeight() <= 720);
                assertTrue(point.x >= 0 && point.x + button.getWidth() <= 1280);
            });
        } finally { SwingUtilities.invokeAndWait(() -> ref.get().close()); }
    }

    @Test void everyHintWrapsWithoutLosingWordsAtSupportedWidths() {
        var image = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics(); graphics.setFont(Theme.normal(16));
        try {
            var metrics = graphics.getFontMetrics();
            for (String tip : LoadingPanel.tips(RulesConfigLoader.load(null))) for (int width : new int[] {672, 736}) {
                var lines = LoadingPanel.wrapHint(tip, metrics, width);
                assertEquals(tip, String.join(" ", lines));
                assertTrue(lines.stream().allMatch(line -> metrics.stringWidth(line) <= width));
                assertTrue(lines.size() * metrics.getHeight() <= 70);
            }
        } finally { graphics.dispose(); }
    }

    @Test void closingDuringPreparationSuppressesLateReadinessAndEntry() throws Exception {
        var ref = new AtomicReference<LoadingPanel>(); var enters = new AtomicInteger();
        SwingUtilities.invokeAndWait(() -> {
            var panel = new LoadingPanel(new ClientSettings(), RulesConfigLoader.load(null), enters::incrementAndGet); ref.set(panel); panel.close();
        });
        Thread.sleep(100);
        SwingUtilities.invokeAndWait(() -> {
            assertFalse(ref.get().isReady()); find(ref.get(), JButton.class).doClick(); assertEquals(0, enters.get());
        });
    }

    @Test void startupOpensLoginAndPracticeEntryLoadsBeforeShowingTheBoard() throws Exception {
        var frameRef = new AtomicReference<ClientFrame>(); var loadingRef = new AtomicReference<LoadingPanel>();
        SwingUtilities.invokeAndWait(() -> {
            var frame = new ClientFrame(null, null); frameRef.set(frame); frame.setVisible(true);
            assertNull(find(frame.getContentPane(), LoadingPanel.class), "startup has no loading gate");
            assertTrue(find(frame.getContentPane(), LoginPanel.class).isShowing());
            var model = (onitama.client.state.ClientModel) field(frame, "model");
            model.startPracticeMatch(onitama.core.Difficulty.ROOKIE);
            var loading = find(frame.getContentPane(), LoadingPanel.class); loadingRef.set(loading);
            assertNotNull(loading); assertTrue(loading.isShowing());
            assertFalse(find(frame.getContentPane(), GamePanel.class).isShowing());
            assertSame(model.rules(), field(loading, "rules"));
        });
        try {
            awaitReady(loadingRef.get());
            SwingUtilities.invokeAndWait(() -> {
                var frame = frameRef.get(); var loading = loadingRef.get();
                Object music = field(frame, "menuMusic"); Object theme = field(music, "requestedTheme");
                assertTrue(((Timer) field(loading, "hints")).isRunning());
                find(loading, JButton.class).doClick();
                assertTrue(find(frame.getContentPane(), GamePanel.class).isShowing());
                assertNull(find(frame.getContentPane(), LoadingPanel.class));
                assertSame(music, field(frame, "menuMusic")); assertEquals(theme, field(music, "requestedTheme"));
                assertFalse(((Timer) field(loading, "hints")).isRunning());
                assertFalse(((Timer) field(loading, "scenes")).isRunning());
                var model = (onitama.client.state.ClientModel) field(frame, "model");
                model.leaveToLobby(); assertTrue(find(frame.getContentPane(), LobbyPanel.class).isShowing());
                model.returnToParkedMatch();
                assertNull(find(frame.getContentPane(), LoadingPanel.class), "return opens the existing board directly");
                assertTrue(find(frame.getContentPane(), GamePanel.class).isShowing());
                model.resign();
                model.startPracticeMatch(onitama.core.Difficulty.ROOKIE);
                var next = find(frame.getContentPane(), LoadingPanel.class);
                assertNotNull(next, "a fresh practice match still loads"); assertNotSame(loading, next);
                model.leaveToLobby(); assertNull(find(frame.getContentPane(), LoadingPanel.class));
                assertFalse(((Timer) field(next, "hints")).isRunning());
                model.resign();
            });
        } finally { SwingUtilities.invokeAndWait(() -> {
            ((onitama.client.state.ClientModel) field(frameRef.get(), "model")).resign(); frameRef.get().dispose();
        }); }
    }

    @Test void networkMatchEntryUsesItsRulesAndSyncUpdatesDoNotRestartLoading() throws Exception {
        var frameRef = new AtomicReference<ClientFrame>(); var loadingRef = new AtomicReference<LoadingPanel>();
        SwingUtilities.invokeAndWait(() -> {
            var frame = new ClientFrame(null, null); frameRef.set(frame); frame.setVisible(true);
            var model = (onitama.client.state.ClientModel) field(frame, "model");
            var base = RulesConfigLoader.load(null);
            var rules = new RulesConfig(base.rulesVersion(), 2, 3, 2, 1, 7, 60, base.movementCards(), base.skills(), 11);
            var game = onitama.core.duel.RulesEngine.newGame(rules, 42);
            var view = onitama.core.duel.RulesEngine.viewFor(game, onitama.core.PlayerColor.BLUE);
            var sync = new onitama.net.MatchSync("loading-online", "ROOM", view, false, 1L);
            model.handleMatchSync(sync);
            var loading = find(frame.getContentPane(), LoadingPanel.class); loadingRef.set(loading);
            assertNotNull(loading); assertSame(rules, field(loading, "rules"));
            model.handleMatchSync(sync); assertSame(loading, find(frame.getContentPane(), LoadingPanel.class));
        });
        try {
            awaitReady(loadingRef.get());
            SwingUtilities.invokeAndWait(() -> {
                var frame = frameRef.get(); assertTrue(namedHint(loadingRef.get()).getText().contains("11 points"));
                find(loadingRef.get(), JButton.class).doClick();
                assertTrue(find(frame.getContentPane(), GamePanel.class).isShowing());
                var model = (onitama.client.state.ClientModel) field(frame, "model");
                var before = model.view();
                model.leaveToLobby(); model.returnToParkedMatch();
                assertNull(find(frame.getContentPane(), LoadingPanel.class));
                assertTrue(find(frame.getContentPane(), GamePanel.class).isShowing());
                assertSame(before, model.view(), "return must preserve match state");

            });
        } finally { SwingUtilities.invokeAndWait(() -> frameRef.get().dispose()); }
    }

    private JLabel namedHint(Container parent) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JLabel label && "loadingHint".equals(label.getName())) return label;
            if (child instanceof Container container) { var result = namedHint(container); if (result != null) return result; }
        }
        return null;
    }

    private Object field(Object object, String name) {
        try { var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
        catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }

    private void layout(Container parent) {
        parent.doLayout(); for (Component child : parent.getComponents()) if (child instanceof Container c) layout(c);
    }
}
