package deckduel.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import deckduel.client.ClientSettings;
import deckduel.client.startup.ServerRoute;
import deckduel.client.startup.StartupFlow;
import deckduel.client.startup.UpdateCheck;
import deckduel.client.startup.UpdateFlow;
import deckduel.client.startup.UpdateInstaller;
import deckduel.client.startup.UpdateService;
import deckduel.client.state.ClientModel;
import deckduel.client.state.ServerConnection;
import deckduel.net.Message;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** The startup screen shows each step's text and only the buttons that step allows. */
class StartupPanelTest {
    private record Ui(StartupPanel panel, StartupFlow flow, Queue<Runnable> worker, AtomicInteger connects, AtomicInteger quits) {
        void work() { while (!worker.isEmpty()) worker.remove().run(); }
    }

    private static Ui ui(UpdateService service, UpdateInstaller installer, int refusals, String fixture) {
        return ui(service, installer, refusals, fixture, false);
    }

    private static Ui ui(UpdateService service, UpdateInstaller installer, int refusals, String fixture, boolean restarts) {
        Queue<Runnable> worker = new ArrayDeque<>();
        var connects = new AtomicInteger();
        var quits = new AtomicInteger();
        var model = new ClientModel(new ServerConnection() {
            public void send(Message message) { fail("the startup screen sends nothing"); }
            public boolean isConnected() { return false; }
            public void ensureConnected(deckduel.client.startup.ServerRoute route) { fail("the startup flow owns the connection"); }
        });
        var updates = new UpdateFlow("1.0.0", service, installer, worker::add, Runnable::run);
        var flow = new StartupFlow(updates, route -> {
            if (connects.incrementAndGet() <= refusals) throw new java.io.IOException("Connection refused");
        }, () -> new ServerRoute("127.0.0.1", 40404), ServerRoute.LOCAL_DEVELOPMENT, worker::add, Runnable::run, 0);
        var panel = new StartupPanel(model, new ClientSettings(), flow, "1.0.0", fixture, restarts, quits::incrementAndGet);
        panel.setSize(1280, 720);
        return new Ui(panel, flow, worker, connects, quits);
    }

    private static <T> List<T> find(Container parent, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Component child : parent.getComponents()) {
            if (type.isInstance(child)) found.add(type.cast(child));
            if (child instanceof Container c) found.addAll(find(c, type));
        }
        return found;
    }

    private static void layout(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents()) if (child instanceof Container c) layout(c);
    }

    private static List<String> buttons(Ui ui) {
        return find(ui.panel, JButton.class).stream().filter(Component::isVisible).map(JButton::getText).toList();
    }

    private static JButton button(Ui ui, String text) {
        return find(ui.panel, JButton.class).stream().filter(b -> b.isVisible() && b.getText().equalsIgnoreCase(text)).findFirst().orElseThrow();
    }

    private static List<String> texts(Ui ui) {
        return find(ui.panel, JLabel.class).stream().map(JLabel::getText).toList();
    }

    @Test void checkingThenUpToDateThenConnectingShowNoButtons() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = ui(installed -> UpdateCheck.upToDate(installed), UpdateInstaller.notConfigured(), 0, null);
            ui.flow.start();
            assertTrue(texts(ui).contains("CHECKING FOR UPDATES"));
            assertEquals(List.of(), buttons(ui));
            ui.worker.remove().run();
            assertTrue(texts(ui).contains("UP TO DATE"));
            assertTrue(texts(ui).contains("Version 1.0.0 is the latest release."));
            ui.worker.remove().run();
            assertTrue(texts(ui).contains("CONNECTING"));
            assertTrue(texts(ui).contains("Connecting to the server…"));
            assertEquals(List.of(), buttons(ui));
            assertTrue(texts(ui).contains("VERSION 1.0.0  ·  LOCAL DEVELOPMENT SERVER 127.0.0.1:5555"));
            assertTrue(find(ui.panel, JLabel.class).stream().noneMatch(l -> "startupFixture".equals(l.getName())),
                    "real services carry no fixture label");
        });
    }

    @Test void anOptionalUpdateOffersUpdateAndContinue() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = ui(installed -> UpdateCheck.optional("1.1.0"), (update, progress) -> progress.accept(.4), 0, "optional-update");
            ui.flow.start(); ui.work();
            assertTrue(texts(ui).contains("UPDATE AVAILABLE"));
            assertEquals(List.of("CONTINUE", "UPDATE"), buttons(ui));
            assertTrue(texts(ui).contains("TEST FIXTURE · OPTIONAL-UPDATE · NO REAL UPDATE IS CHECKED OR INSTALLED"));
            button(ui, "Continue").doClick();
            ui.work();
            assertEquals(StartupFlow.Phase.CONNECTED, ui.flow.state().phase());
        });
    }

    @Test void aRequiredUpdateOffersUpdateOrQuitButNeverContinue() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = ui(installed -> UpdateCheck.required("2.0.0"), (update, report) -> report.accept(.45), 0, null);
            ui.flow.start(); ui.work();
            assertTrue(texts(ui).contains("UPDATE REQUIRED"));
            assertTrue(texts(ui).contains("Version 2.0.0 is required to play."));
            assertEquals(List.of("QUIT", "UPDATE"), buttons(ui));
            button(ui, "Update").doClick();
            assertTrue(texts(ui).contains("DOWNLOADING UPDATE"));
            assertEquals(List.of(), buttons(ui), "no way past an install in progress");
            ui.work();
            assertTrue(texts(ui).contains("UPDATE INSTALLED"));
            assertTrue(texts(ui).contains("Restart Deck Duel to finish updating to 2.0.0."));
            assertEquals(List.of("QUIT"), buttons(ui));
            button(ui, "Quit").doClick();
            assertEquals(1, ui.quits.get());
            assertEquals(0, ui.connects.get());
        });
    }

    @Test void installProgressIsShownAsAPercentage() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = ui(installed -> UpdateCheck.optional("1.1.0"), (update, report) -> {
                report.accept(.45);
                throw new java.io.IOException("download interrupted");
            }, 0, null);
            List<String> details = new ArrayList<>();
            ui.flow.addListener(state -> details.add(StartupPanel.describe(state, false)[1]));
            ui.flow.start(); ui.work();
            button(ui, "Update").doClick(); ui.work();
            assertTrue(details.contains("Downloading and verifying version 1.1.0…  45%"), details.toString());
            assertTrue(texts(ui).contains("UPDATE FAILED"));
            assertTrue(texts(ui).contains("download interrupted"));
            assertEquals(List.of("CONTINUE", "RETRY"), buttons(ui), "an optional update may still be skipped");
        });
    }

    @Test void aFailedConnectionOffersRetryAndRetryConnects() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = ui(UpdateService.notConfigured(), UpdateInstaller.notConfigured(), 1, null);
            ui.flow.start(); ui.work();
            assertTrue(texts(ui).contains("CONNECTION FAILED"));
            assertTrue(texts(ui).contains("Cannot connect to the server."));
            assertFalse(texts(ui).contains("Connection refused"), "the technical reason goes to the log only");
            assertEquals(List.of("QUIT", "PLAY OFFLINE", "RETRY"), buttons(ui));
            button(ui, "Retry").doClick();
            assertTrue(texts(ui).contains("CONNECTING"));
            ui.work();
            assertEquals(StartupFlow.Phase.CONNECTED, ui.flow.state().phase());
            assertEquals(2, ui.connects.get());
        });
    }

    @Test void playOfflineStartsTheLocalServerFromTheFailedConnection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = ui(UpdateService.notConfigured(), UpdateInstaller.notConfigured(), 1, null);
            ui.flow.start(); ui.work();
            layout(ui.panel);
            var row = button(ui, "Play offline").getParent();
            assertTrue(row.getWidth() > 0);
            for (var shown : find(row, JButton.class))
                assertTrue(!shown.isVisible() || shown.getX() >= 0 && shown.getX() + shown.getWidth() <= row.getWidth(),
                        shown.getText() + " fits the plate");
            button(ui, "Play offline").doClick();
            assertTrue(texts(ui).contains("STARTING OFFLINE PLAY"));
            assertEquals(List.of(), buttons(ui));
            assertTrue(texts(ui).contains("VERSION 1.0.0  ·  OFFLINE PLAY ON THIS COMPUTER"));
            ui.work();
            assertTrue(texts(ui).contains("OFFLINE PLAY"));
            assertEquals(new ServerRoute("127.0.0.1", 40404), ui.flow.state().route());
        });
    }

    @Test void aFailedCheckOffersRetryAndContinue() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = ui(installed -> { throw new java.io.IOException("update service unreachable"); },
                    UpdateInstaller.notConfigured(), 0, null);
            ui.flow.start(); ui.work();
            assertTrue(texts(ui).contains("UPDATE CHECK FAILED"));
            assertTrue(texts(ui).contains("update service unreachable"));
            assertEquals(List.of("CONTINUE", "RETRY"), buttons(ui));
            button(ui, "Retry").doClick();
            assertTrue(texts(ui).contains("CHECKING FOR UPDATES"));
        });
    }

    @Test void withoutAServiceTheScreenSaysNothingWasChecked() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = ui(UpdateService.notConfigured(), UpdateInstaller.notConfigured(), 0, null);
            ui.flow.start();
            ui.worker.remove().run();
            assertTrue(texts(ui).contains("UPDATES NOT SET UP"));
            assertTrue(texts(ui).contains("No update service is configured, so nothing was checked."));
            assertTrue(texts(ui).stream().noneMatch(text -> text.contains("UP TO DATE")));
        });
    }

    @Test void aVerifiedRealUpdateRestartsByItselfWithNoButtons() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = ui(installed -> UpdateCheck.required("2.0.0"), (update, report) -> report.accept(1), 0, null, true);
            ui.flow.start(); ui.work();
            button(ui, "Update").doClick(); ui.work();
            assertTrue(texts(ui).contains("RESTARTING"));
            assertTrue(texts(ui).contains("Version 2.0.0 is verified. Deck Duel closes, installs it and starts again."));
            assertEquals(List.of(), buttons(ui), "no Quit: the game closes and restarts on its own");
            assertEquals(0, ui.connects.get(), "a required update still never reaches the server");
        });
    }
}
