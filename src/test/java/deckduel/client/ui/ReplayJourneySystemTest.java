package deckduel.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import deckduel.client.startup.StartupServices;
import deckduel.core.card.GreedyBot;
import deckduel.core.card.MatchPhase;
import deckduel.db.Database;
import deckduel.net.*;
import deckduel.server.GameServer;
import deckduel.server.ServerConfig;
import java.awt.Component;
import java.awt.Container;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A practice game recorded by a real server, then watched in the real client window:
 * Login → Play → Match history → select → Watch replay → step → Back.
 */
class ReplayJourneySystemTest {
    @TempDir Path temporary;

    /** Plays one practice game as {@code alice} over the raw protocol. */
    private static void playPractice(int port) throws Exception {
        try (var socket = new Socket("127.0.0.1", port)) {
            var out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            var in = new ObjectInputStream(socket.getInputStream());
            in.setObjectInputFilter(Message.WIRE_FILTER);
            var inbox = new LinkedBlockingQueue<Object>();
            Thread reader = new Thread(() -> { try { while (true) inbox.add(in.readObject()); } catch (Exception closed) { } });
            reader.setDaemon(true);
            reader.start();
            inbox.poll(10, TimeUnit.SECONDS);
            for (Message message : List.of(new ProtocolHello(Message.PROTOCOL_VERSION, "1.0.0"), new RegisterRequest("alice", "secret123"),
                    new PracticeRequest(false))) {
                out.writeObject(message); out.flush(); out.reset();
            }
            long request = 1;
            int shown = -1;
            long deadline = System.currentTimeMillis() + 60_000;
            while (System.currentTimeMillis() < deadline) {
                if (!(inbox.poll(10, TimeUnit.SECONDS) instanceof MatchUpdate update) || update.view().revision() <= shown) continue;
                shown = update.view().revision();
                if (update.view().phase() == MatchPhase.FINISHED) return;
                if (update.view().yourTurn()) {
                    out.writeObject(new MoveRequest(request++, shown, GreedyBot.choose(update.view())));
                    out.flush(); out.reset();
                }
            }
            fail("the practice game did not finish");
        }
    }

    private static <T> T onEdt(Callable<T> read) throws Exception {
        Object[] result = new Object[1];
        Exception[] failure = new Exception[1];
        SwingUtilities.invokeAndWait(() -> { try { result[0] = read.call(); } catch (Exception e) { failure[0] = e; } });
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

    private static JButton button(ClientFrame frame, String text) {
        return find(frame.getContentPane(), JButton.class).stream()
                .filter(b -> b.isShowing() && b.getText().equalsIgnoreCase(text)).findFirst().orElse(null);
    }

    private static String label(ClientFrame frame, String name) {
        return find(frame.getContentPane(), JLabel.class).stream().filter(l -> name.equals(l.getName()))
                .findFirst().map(JLabel::getText).orElse(null);
    }

    @Test void aRecordedPracticeGameIsWatchedFromMatchHistoryAndBackAgain() throws Exception {
        try (var database = new Database(temporary.resolve("replays.db"))) {
            GameServer server = GameServer.recording(database, new ServerConfig(0, 90, 8, 60, 0));
            int port = server.start();
            ClientFrame[] frame = new ClientFrame[1];
            try {
                playPractice(port);
                SwingUtilities.invokeAndWait(() -> {
                    frame[0] = new ClientFrame("127.0.0.1", port, StartupServices.unconfigured());
                    frame[0].setVisible(true);
                });
                waitFor(() -> showing(frame[0], LoginPanel.class), "startup connects and opens login");
                SwingUtilities.invokeAndWait(() -> {
                    var login = find(frame[0].getContentPane(), LoginPanel.class).get(0);
                    find(login, JTextField.class).stream().filter(f -> !(f instanceof JPasswordField)).findFirst().orElseThrow().setText("alice");
                    find(login, JPasswordField.class).get(0).setText("secret123");
                    find(login, JButton.class).stream().filter(b -> b.getText().equalsIgnoreCase("Login")).findFirst().orElseThrow().doClick();
                });
                waitFor(() -> showing(frame[0], LobbyPanel.class), "login opens the main menu");
                SwingUtilities.invokeAndWait(() -> {
                    button(frame[0], "Play").doClick();
                    button(frame[0], "Match history").doClick();
                });
                waitFor(() -> find(frame[0].getContentPane(), JTable.class).stream().anyMatch(t -> t.isShowing() && t.getRowCount() == 1),
                        "history lists the practice game");
                SwingUtilities.invokeAndWait(() -> {
                    JTable table = find(frame[0].getContentPane(), JTable.class).get(0);
                    assertEquals("Practice bot (easy)", table.getValueAt(0, 1));
                    assertEquals("Watch", table.getValueAt(0, 5));
                    table.setRowSelectionInterval(0, 0);
                    button(frame[0], "Watch replay").doClick();
                });
                waitFor(() -> showing(frame[0], ReplayPanel.class), "the replay opens");
                assertTrue(onEdt(() -> label(frame[0], "replayPosition")).startsWith("Round 1 · Action 0 of"));
                SwingUtilities.invokeAndWait(() -> { button(frame[0], "Next").doClick(); button(frame[0], "Next").doClick(); });
                assertTrue(onEdt(() -> label(frame[0], "replayPosition")).contains("Action 2 of"));
                SwingUtilities.invokeAndWait(() -> button(frame[0], "Back").doClick());
                waitFor(() -> showing(frame[0], HistoryPanel.class), "Back returns to match history");
            } finally {
                if (frame[0] != null) SwingUtilities.invokeAndWait(frame[0]::dispose);
                server.stop();
            }
        }
    }
}
