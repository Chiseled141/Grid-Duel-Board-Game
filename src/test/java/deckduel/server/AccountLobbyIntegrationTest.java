package deckduel.server;

import deckduel.client.DeckDuelClient;
import deckduel.db.Database;
import deckduel.db.SqliteUserDao;
import deckduel.net.*;
import java.io.*;
import java.net.Socket;
import java.nio.file.Path;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Real sockets and temporary SQLite cover the account/lobby boundary. */
class AccountLobbyIntegrationTest {
    @TempDir Path temporary;
    private Database database;
    private GameServer server;
    private int port;

    @BeforeEach void start() throws Exception {
        database = new Database(temporary.resolve("shell.db"));
        server = new GameServer(ServerConfig.defaults(0), new SqliteUserDao(database));
        port = server.start();
    }
    @AfterEach void close() throws Exception { server.stop(); database.close(); }

    private final class Peer implements AutoCloseable {
        final Socket socket;
        final ObjectOutputStream out;
        final ObjectInputStream in;
        Peer(boolean handshake) throws Exception {
            socket = new Socket("127.0.0.1", port); socket.setSoTimeout(5000);
            out = new ObjectOutputStream(socket.getOutputStream()); out.flush();
            in = new ObjectInputStream(socket.getInputStream()); in.setObjectInputFilter(Message.WIRE_FILTER);
            assertEquals(new ErrorMessage(Message.GREETING), expect(ErrorMessage.class));
            if (handshake) { send(new ProtocolHello(Message.PROTOCOL_VERSION, "1.0.0")); expect(ProtocolReady.class); }
        }
        void send(Object message) throws Exception { out.writeObject(message); out.flush(); out.reset(); }
        <T> T expect(Class<T> type) throws Exception { return type.cast(in.readObject()); }
        void register(String name) throws Exception {
            send(new RegisterRequest(name, "secret123")); assertTrue(expect(LoginResponse.class).ok());
        }
        public void close() throws IOException { socket.close(); }
    }

    @Test void registrationLoginRoomsHistoryAndHeartbeatsWork() throws Exception {
        try (var alice = new Peer(true); var bob = new Peer(true)) {
            alice.register("alice"); bob.register("bob");
            alice.send(new CreateMatchRequest()); var room = alice.expect(MatchCreated.class);
            assertTrue(room.roomCode().matches("[A-Z2-9]{5}"));
            bob.send(new ListMatchesRequest());
            assertEquals(java.util.List.of(new MatchSummary(room.roomCode(), "alice")), bob.expect(ListMatchesResponse.class).openMatches());
            bob.send(new HistoryRequest()); assertTrue(bob.expect(HistoryResponse.class).matches().isEmpty());
            bob.send(new Ping(77)); assertEquals(new Pong(77), bob.expect(Pong.class));
            bob.send(new LoginRequest("bob", "wrong")); assertFalse(bob.expect(LoginResponse.class).ok());
            bob.send(new LoginRequest("bob", "secret123")); assertTrue(bob.expect(LoginResponse.class).ok());
        }
    }

    @Test void unknownRoomCodesNeverConsumeRoomsOrCreateGames() throws Exception {
        try (var alice = new Peer(true); var bob = new Peer(true)) {
            alice.register("alice"); bob.register("bob");
            alice.send(new CreateMatchRequest()); alice.expect(MatchCreated.class);
            bob.send(new JoinMatchRequest("ZZZZZ"));
            assertTrue(bob.expect(ErrorMessage.class).text().contains("no longer open"));
            bob.send(new ListMatchesRequest()); assertEquals(1, bob.expect(ListMatchesResponse.class).openMatches().size());
            database.query(connection -> {
                try (var query = connection.prepareStatement("SELECT COUNT(*) FROM matches"); var rows = query.executeQuery()) {
                    assertTrue(rows.next()); assertEquals(0, rows.getInt(1));
                }
                return null;
            });
        }
    }

    @Test void authenticationIsRequiredForLobbyMatchAndHistoryRequests() throws Exception {
        try (var peer = new Peer(true)) {
            for (Object request : java.util.List.of(new CreateMatchRequest(), new ListMatchesRequest(),
                    new HistoryRequest(), new JoinMatchRequest("ABCDE"),
                    new PracticeRequest(false), new LeaveMatchRequest(), new RematchRequest(), new ReplayRequest(1), new CancelRoomRequest())) {
                peer.send(request); assertTrue(peer.expect(ErrorMessage.class).text().contains("log in"));
            }
        }
    }

    @Test void oldProtocolIsRejectedBeforeAccountCommands() throws Exception {
        try (var peer = new Peer(false)) {
            // Version 10 clients do not know SkipTurn.
            peer.send(new ProtocolHello(10, "1.0.0")); assertTrue(peer.expect(ErrorMessage.class).text().contains(Message.GREETING));
            assertThrows(IOException.class, peer.in::readObject);
        }
    }

    @Test void foreignSerializedObjectsCannotReachDispatch() throws Exception {
        try (var peer = new Peer(true)) {
            peer.send(new deckduel.SerializationProbe()); assertThrows(IOException.class, peer.in::readObject);
        }
    }

    @Test void duplicateLoginAndOldDisconnectCannotRemoveTheNewRoom() throws Exception {
        try (var old = new Peer(true); var current = new Peer(true); var observer = new Peer(true)) {
            old.register("alice"); observer.register("bob");
            old.send(new CreateMatchRequest()); old.expect(MatchCreated.class);
            current.send(new LoginRequest("alice", "secret123")); assertTrue(current.expect(LoginResponse.class).ok());
            assertTrue(old.expect(ErrorMessage.class).text().contains("another connection"));
            assertThrows(IOException.class, old.in::readObject);
            current.send(new CreateMatchRequest()); var room = current.expect(MatchCreated.class);
            observer.send(new ListMatchesRequest());
            assertEquals(java.util.List.of(new MatchSummary(room.roomCode(), "alice")), observer.expect(ListMatchesResponse.class).openMatches());
            current.send(new CreateMatchRequest()); current.expect(MatchCreated.class);
            observer.send(new ListMatchesRequest()); assertEquals(1, observer.expect(ListMatchesResponse.class).openMatches().size());
        }
    }

    @Test void productionClientSpeaksTheCurrentProtocol() throws Exception {
        var received = new LinkedBlockingQueue<Message>();
        try (var client = new DeckDuelClient(received::add, reason -> { })) {
            client.ensureConnected(new deckduel.client.startup.ServerRoute("127.0.0.1", port, false));
            client.send(new RegisterRequest("client", "secret123"));
            var response = received.poll(5, TimeUnit.SECONDS);
            assertInstanceOf(LoginResponse.class, response); assertTrue(((LoginResponse) response).ok());
            client.send(new Ping(88)); assertEquals(new Pong(88), received.poll(5, TimeUnit.SECONDS));
        }
    }
}
