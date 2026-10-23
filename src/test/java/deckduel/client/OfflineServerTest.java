package deckduel.client;

import static org.junit.jupiter.api.Assertions.*;

import deckduel.core.card.GreedyBot;
import deckduel.core.card.MatchPhase;
import deckduel.net.*;
import deckduel.server.ServerConfig;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Offline play's private server: practice and its replay work there, and closing removes all of it. */
class OfflineServerTest {
    @TempDir Path temporary;

    /** A minimal protocol client for the offline account. */
    private static final class Peer implements AutoCloseable {
        final Socket socket;
        final ObjectOutputStream out;
        final LinkedBlockingQueue<Object> inbox = new LinkedBlockingQueue<>();

        Peer(int port, String password) throws Exception {
            socket = new Socket("127.0.0.1", port);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            var in = new ObjectInputStream(socket.getInputStream());
            in.setObjectInputFilter(Message.WIRE_FILTER);
            Thread reader = new Thread(() -> {
                try { while (true) inbox.add(in.readObject()); } catch (Exception closed) { inbox.add("closed"); }
            });
            reader.setDaemon(true);
            reader.start();
            expect(ErrorMessage.class, any -> true);
            send(new ProtocolHello(Message.PROTOCOL_VERSION, "1.0.0"));
            expect(ProtocolReady.class, any -> true);
            send(new RegisterRequest(OfflineServer.USERNAME, password));
            assertTrue(expect(LoginResponse.class, any -> true).ok());
        }

        void send(Message message) throws Exception { out.writeObject(message); out.flush(); out.reset(); }

        <T> T expect(Class<T> type, Predicate<T> wanted) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (System.nanoTime() < deadline) {
                Object next = inbox.poll(100, TimeUnit.MILLISECONDS);
                if (type.isInstance(next) && wanted.test(type.cast(next))) return type.cast(next);
            }
            throw new AssertionError("No " + type.getSimpleName() + " arrived");
        }

        @Override public void close() throws Exception { socket.close(); }
    }

    @Test void practiceIsReplayableOfflineAndClosingDeletesTheSessionDatabase() throws Exception {
        var offline = new OfflineServer(temporary, new ServerConfig(0, 90, 4, 1, 0));
        var route = offline.start();
        assertEquals(route, offline.start(), "one server per session");
        assertTrue(route.local());
        try (var peer = new Peer(route.port(), offline.password())) {
            peer.send(new PracticeRequest(false));
            MatchUpdate update = peer.expect(MatchUpdate.class, any -> true);
            long request = 1;
            while (update.view().phase() != MatchPhase.FINISHED) {
                if (update.view().yourTurn())
                    peer.send(new MoveRequest(request++, update.view().revision(), GreedyBot.choose(update.view())));
                int revision = update.view().revision();
                update = peer.expect(MatchUpdate.class, u -> u.view().revision() > revision);
            }
            peer.send(new HistoryRequest());
            HistoryEntry entry = peer.expect(HistoryResponse.class, any -> true).matches().get(0);
            assertTrue(entry.practice());
            assertEquals(HistoryEntry.Replay.AVAILABLE, entry.replay());
            peer.send(new ReplayRequest(entry.replayId()));
            ReplayResponse replay = peer.expect(ReplayResponse.class, any -> true);
            assertEquals(update.view(), replay.frames().get(replay.frames().size() - 1));
        }
        try (var folders = Files.list(temporary)) { assertEquals(1, folders.count(), "the session database folder"); }

        offline.close();
        offline.close();
        try (var folders = Files.list(temporary)) { assertEquals(0, folders.count(), "history and replays end with the session"); }
        assertThrows(java.io.IOException.class, () -> new Socket("127.0.0.1", route.port()).close(), "the server stopped");

        // The next session starts empty.
        var next = new OfflineServer(temporary, new ServerConfig(0, 90, 4, 1, 0));
        try (var peer = new Peer(next.start().port(), next.password())) {
            peer.send(new HistoryRequest());
            assertTrue(peer.expect(HistoryResponse.class, any -> true).matches().isEmpty());
        } finally { next.close(); }
    }

    @Test void offlinePlayNeverListensBeyondThisComputer() throws Exception {
        var exposed = new OfflineServer(temporary, new ServerConfig(0, 90, 4, 1, 0).withBind(java.net.InetAddress.getByName("0.0.0.0")));
        assertThrows(java.io.IOException.class, exposed::start);
        try (var folders = Files.list(temporary)) { assertEquals(0, folders.count(), "nothing is left behind"); }
        var offline = new OfflineServer(temporary, new ServerConfig(0, 90, 4, 1, 0));
        try {
            var route = offline.start();
            assertTrue(route.local());
            assertFalse(route.tls(), "offline play stays plain on loopback");
            // Every non-loopback address of this machine must refuse the offline port.
            for (var face : java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces()))
                for (var address : java.util.Collections.list(face.getInetAddresses()))
                    if (!address.isLoopbackAddress() && address instanceof java.net.Inet4Address && face.isUp())
                        assertThrows(java.io.IOException.class, () -> {
                            try (Socket probe = new Socket()) { probe.connect(new java.net.InetSocketAddress(address, route.port()), 1_000); }
                        }, "not reachable at " + address.getHostAddress());
        } finally { offline.close(); }
    }
}
