package deckduel.client.ui;

import deckduel.client.state.ClientModel;
import deckduel.client.state.Screen;
import deckduel.client.state.ServerConnection;
import deckduel.net.*;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HowToPlayPanelTest {
    private record Ui(ClientModel model, HowToPlayPanel panel, List<Message> sent) { }

    private static <T> T edt(Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work); SwingUtilities.invokeAndWait(task); return task.get();
    }
    private static Ui open() throws Exception {
        Ui ui = edt(() -> {
            List<Message> sent = new ArrayList<>();
            var model = new ClientModel(new ServerConnection() {
                public void send(Message message) { sent.add(message); }
                public boolean isConnected() { return true; }
                public void ensureConnected(deckduel.client.startup.ServerRoute route) { }
            });
            var panel = new HowToPlayPanel(model);
            model.handleMessage(new LoginResponse(true, null, new UserProfile("learner", 0, 0)));
            sent.clear(); model.showHowToPlay();
            return new Ui(model, panel, sent);
        });
        long deadline = System.currentTimeMillis() + 10_000;
        while (!edt(() -> named(ui.panel, JComboBox.class, "guideChapters").getItemCount() == 17)) {
            assertTrue(System.currentTimeMillis() < deadline, "bundled recordings load asynchronously"); Thread.sleep(20);
        }
        return ui;
    }
    private static <T extends Component> T named(Container parent, Class<T> type, String name) {
        for (Component child : parent.getComponents()) {
            if (type.isInstance(child) && name.equals(child.getName())) return type.cast(child);
            if (child instanceof Container container) { T found = named(container, type, name); if (found != null) return found; }
        }
        return null;
    }
    private static void layout(Container parent) {
        parent.doLayout(); for (Component child : parent.getComponents()) if (child instanceof Container container) layout(container);
    }

    @Test void lessonsPlaybackAndBackStayReadOnlyAndStopOnNavigation() throws Exception {
        Ui ui = open();
        try {
            String pausedPosition = edt(() -> {
                assertEquals(Screen.HOW_TO_PLAY, ui.model.screen());
                var chapters = named(ui.panel, JComboBox.class, "guideChapters");
                for (int i = 0; i < 17; i++) {
                    chapters.setSelectedIndex(i);
                    assertFalse(named(ui.panel, JTextArea.class, "guideRules").getText().isBlank());
                    assertTrue(named(ui.panel, JLabel.class, "replayPosition").getText().contains("Action 0"));
                    assertEquals(i == 0 || i == 5 ? "Opening deal" : "Start of recorded excerpt",
                            named(ui.panel, JLabel.class, "replayAction").getText());
                }
                chapters.setSelectedIndex(5);
                named(ui.panel, JButton.class, "replayNext").doClick();
                assertFalse(named(ui.panel, JLabel.class, "replayPosition").getText().contains("Action 0"));
                named(ui.panel, JButton.class, "replayPlay").doClick();
                assertEquals("PAUSE", named(ui.panel, JButton.class, "replayPlay").getText());
                ui.model.leaveToLobby();
                assertEquals("PLAY", named(ui.panel, JButton.class, "replayPlay").getText());
                assertTrue(ui.sent.isEmpty()); assertNull(ui.model.match()); assertNull(ui.model.replay());
                return named(ui.panel, JLabel.class, "replayPosition").getText();
            });
            Thread.sleep(1600);
            edt(() -> {
                assertEquals(pausedPosition, named(ui.panel, JLabel.class, "replayPosition").getText());
                ui.model.showHowToPlay();
                assertEquals(5, named(ui.panel, JComboBox.class, "guideChapters").getSelectedIndex());
                assertTrue(named(ui.panel, JLabel.class, "replayPosition").getText().contains("Action 0"));
                named(ui.panel, JButton.class, "guideNext").doClick();
                assertEquals(6, named(ui.panel, JComboBox.class, "guideChapters").getSelectedIndex());
                named(ui.panel, JButton.class, "guideBack").doClick();
                assertEquals(Screen.LOBBY, ui.model.screen()); assertTrue(ui.sent.isEmpty()); return null;
            });
        } finally { edt(() -> { ui.model.leaveToLobby(); return null; }); }
    }

    @Test void readableGuideAndRecordingsRenderAtSupportedWindowSizes() throws Exception {
        Ui ui = open();
        try {
            var artReady = new java.util.concurrent.CountDownLatch(1);
            KingArt.whenReady(artReady::countDown);
            assertTrue(artReady.await(10, java.util.concurrent.TimeUnit.SECONDS));
            for (int[] size : new int[][] {{1280, 720}, {1470, 923}, {1920, 1080}}) {
                for (int lesson : new int[] {0, 5, 6, 8, 14}) {
                    BufferedImage image = edt(() -> {
                        ui.panel.setSize(size[0], size[1]);
                        named(ui.panel, JComboBox.class, "guideChapters").setSelectedIndex(lesson);
                        layout(ui.panel);
                        // Show the final frame to include counters, effect results and the final three-round score.
                        JButton next = named(ui.panel, JButton.class, "replayNext");
                        while (next.isEnabled()) next.doClick(0);
                        layout(ui.panel);
                        assertTrue(named(ui.panel, JTextArea.class, "guideRules").getWidth() >= 280);
                        assertTrue(named(ui.panel, ReplayPanel.class, "guideRecording").getWidth() >= 850);
                        BufferedImage picture = new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_RGB);
                        var graphics = picture.createGraphics(); ui.panel.paint(graphics); graphics.dispose(); return picture;
                    });
                    Path path = Path.of("target/how-to-play-qa", "lesson-" + lesson + "-" + size[0] + ".png");
                    Files.createDirectories(path.getParent()); ImageIO.write(image, "png", path.toFile());
                    if (lesson == 5 || lesson == 14) {
                        BufferedImage privateChoice = edt(() -> {
                            named(ui.panel, JButton.class, "replayPrevious").doClick(0);
                            assertTrue(named(ui.panel, JTextArea.class, "guideObservation").getText().contains("only")
                                    || named(ui.panel, JTextArea.class, "guideObservation").getText().contains("Only"));
                            BufferedImage picture = new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_RGB);
                            var graphics = picture.createGraphics(); ui.panel.paint(graphics); graphics.dispose(); return picture;
                        });
                        ImageIO.write(privateChoice, "png", Path.of("target/how-to-play-qa",
                                "lesson-" + lesson + "-private-" + size[0] + ".png").toFile());
                    }
                }
            }
        } finally { edt(() -> { ui.model.leaveToLobby(); return null; }); }
    }
}
