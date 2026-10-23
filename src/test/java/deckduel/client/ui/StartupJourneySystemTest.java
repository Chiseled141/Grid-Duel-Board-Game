package deckduel.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import deckduel.client.startup.StartupFixture;
import deckduel.client.startup.StartupServices;
import deckduel.db.Database;
import deckduel.db.SqliteUserDao;
import deckduel.server.GameServer;
import deckduel.server.ServerConfig;
import java.awt.Component;
import java.awt.Container;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The real client window and a real local server: Launch → update step → connection failure →
 * Retry → Login → Main menu; offline practice when no server answers; and a required update
 * that never reaches login.
 */
class StartupJourneySystemTest {
    @TempDir Path temporary;

    private static <T> T onEdt(Callable<T> read) throws Exception {
        Object[] result = new Object[1];
        Exception[] failure = new Exception[1];
        SwingUtilities.invokeAndWait(() -> {
            try { result[0] = read.call(); } catch (Exception e) { failure[0] = e; }
        });
        if (failure[0] != null) throw failure[0];
        @SuppressWarnings("unchecked") T value = (T) result[0];
        return value;
    }

    private static void waitFor(Callable<Boolean> condition, String what) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000;
        while (!onEdt(condition)) {
            if (System.currentTimeMillis() > deadline) fail("timed out waiting until " + what);
            Thread.sleep(25);
        }
    }

    private static <T> List<T> find(Container parent, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Component child : parent.getComponents()) {
            if (type.isInstance(child)) found.add(type.cast(child));
            if (child instanceof Container c) found.addAll(find(c, type));
        }
        return found;
    }

    private static boolean showing(ClientFrame frame, Class<?> page) {
        return find(frame.getContentPane(), Component.class).stream().anyMatch(c -> page.isInstance(c) && c.isShowing());
    }

    private static JButton visibleButton(Container parent, String text) {
        return find(parent, JButton.class).stream().filter(b -> b.isShowing() && b.getText().equalsIgnoreCase(text)).findFirst().orElse(null);
    }

    private static int freePort() throws Exception {
        try (var socket = new ServerSocket(0)) { return socket.getLocalPort(); }
    }

    @Test void launchRetriesAFailedConnectionThenLogsInToTheMainMenu() throws Exception {
        int port = freePort();
        ClientFrame[] frame = new ClientFrame[1];
        try (var database = new Database(temporary.resolve("startup.db"))) {
            GameServer server = null;
            try {
                SwingUtilities.invokeAndWait(() -> {
                    frame[0] = new ClientFrame("127.0.0.1", port, StartupServices.unconfigured());
                    frame[0].setVisible(true);
                });
                waitFor(() -> showing(frame[0], StartupPanel.class), "the startup screen opens first");
                // No server listens yet, so the real connection attempt fails.
                waitFor(() -> visibleButton(frame[0].getContentPane(), "Retry") != null, "the connection failure offers Retry");
                assertTrue(onEdt(() -> find(frame[0].getContentPane(), JLabel.class).stream()
                        .anyMatch(l -> l.isShowing() && l.getText().equals("CONNECTION FAILED"))));
                assertFalse(onEdt(() -> showing(frame[0], LoginPanel.class)));

                server = new GameServer(ServerConfig.defaults(port), new SqliteUserDao(database));
                server.start();
                SwingUtilities.invokeAndWait(() -> visibleButton(frame[0].getContentPane(), "Retry").doClick());
                waitFor(() -> showing(frame[0], LoginPanel.class), "Retry connects and opens login");

                SwingUtilities.invokeAndWait(() -> {
                    var login = find(frame[0].getContentPane(), LoginPanel.class).get(0);
                    find(login, JTextField.class).stream().filter(f -> !(f instanceof JPasswordField)).findFirst().orElseThrow().setText("journey");
                    find(login, JPasswordField.class).get(0).setText("secret123");
                    visibleButton(login, "Register").doClick();
                });
                waitFor(() -> showing(frame[0], LobbyPanel.class), "registration opens the main menu");

                SwingUtilities.invokeAndWait(() -> {
                    visibleButton(frame[0].getContentPane(), "Settings").doClick();
                    visibleButton(frame[0].getContentPane(), "Check for updates").doClick();
                });
                waitFor(() -> find(frame[0].getContentPane(), JLabel.class).stream().anyMatch(l -> l.isShowing()
                        && l.getText().equals("No update service is configured; nothing was checked.")),
                        "Settings reports that no real check exists");
            } finally {
                if (frame[0] != null) SwingUtilities.invokeAndWait(frame[0]::dispose);
                if (server != null) server.stop();
            }
        }
    }

    @Test void aRequiredUpdateKeepsTheClientOffTheServer() throws Exception {
        ClientFrame[] frame = new ClientFrame[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame[0] = new ClientFrame("127.0.0.1", 1, StartupFixture.REQUIRED_UPDATE.services(0));
                frame[0].setVisible(true);
            });
            waitFor(() -> visibleButton(frame[0].getContentPane(), "Update") != null, "the required update is offered");
            Thread.sleep(300);
            assertNull(onEdt(() -> visibleButton(frame[0].getContentPane(), "Continue")));
            assertNotNull(onEdt(() -> visibleButton(frame[0].getContentPane(), "Quit")));
            assertTrue(onEdt(() -> showing(frame[0], StartupPanel.class)));
            assertTrue(onEdt(() -> find(frame[0].getContentPane(), JLabel.class).stream()
                    .anyMatch(l -> l.isShowing() && l.getText().startsWith("TEST FIXTURE · REQUIRED-UPDATE"))));
        } finally {
            if (frame[0] != null) SwingUtilities.invokeAndWait(frame[0]::dispose);
        }
    }

    @Test void withNoServerPlayOfflineOpensTheMenuAndAPracticeMatch() throws Exception {
        int port = freePort();
        ClientFrame[] frame = new ClientFrame[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame[0] = new ClientFrame("127.0.0.1", port, StartupServices.unconfigured());
                frame[0].setVisible(true);
            });
            waitFor(() -> visibleButton(frame[0].getContentPane(), "Play offline") != null, "the failed connection offers offline play");
            SwingUtilities.invokeAndWait(() -> visibleButton(frame[0].getContentPane(), "Play offline").doClick());
            waitFor(() -> showing(frame[0], LobbyPanel.class), "offline play signs in and opens the main menu");
            assertTrue(onEdt(() -> find(frame[0].getContentPane(), JLabel.class).stream()
                    .anyMatch(l -> l.isShowing() && l.getText().equals("Offline play"))), "the menu names offline play");
            SwingUtilities.invokeAndWait(() -> visibleButton(frame[0].getContentPane(), "Play").doClick());
            assertNull(onEdt(() -> visibleButton(frame[0].getContentPane(), "Create match")), "no hosting offline");
            assertNull(onEdt(() -> visibleButton(frame[0].getContentPane(), "Join a game")), "no joining offline");
            assertNotNull(onEdt(() -> visibleButton(frame[0].getContentPane(), "Match history")));
            SwingUtilities.invokeAndWait(() -> {
                visibleButton(frame[0].getContentPane(), "Practice").doClick();
                visibleButton(frame[0].getContentPane(), "Easy bot").doClick();
            });
            // The JavaFX table replaces the Swing menu window once the match arrives.
            waitFor(() -> !frame[0].isVisible(), "the offline practice match opens the table");
        } finally {
            if (frame[0] != null) SwingUtilities.invokeAndWait(frame[0]::dispose);
        }
    }
}
