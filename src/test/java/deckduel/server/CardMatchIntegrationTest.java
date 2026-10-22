package deckduel.server;

import static org.junit.jupiter.api.Assertions.*;

import deckduel.cards.CatalogLoader;
import deckduel.core.card.Catalog;
import deckduel.core.card.GreedyBot;
import deckduel.core.card.MatchPhase;
import deckduel.core.card.PlayCard;
import deckduel.db.Database;
import deckduel.db.SqliteMatchDao;
import deckduel.db.SqliteUserDao;
import deckduel.net.*;
import java.io.*;
import java.net.Socket;
import java.nio.file.Path;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** Real sockets, real matches and temporary SQLite cover the card-game server boundary. */
class CardMatchIntegrationTest {
    @TempDir Path temporary;
    private final Catalog catalog = CatalogLoader.loadBundled();
    private Database database;
    private SqliteMatchDao matchDao;
    private GameServer server;
    private int port;

    @BeforeEach void start() throws Exception {
        database = new Database(temporary.resolve("cards.db"));
        var users = new SqliteUserDao(database);
        matchDao = new SqliteMatchDao(database, users);
        var persistence = new MatchPersistence(database, matchDao);
        server = new GameServer(new ServerConfig(0, 90, 32, 1, 0), users, catalog, persistence, persistence);
        port = server.start();
    }
    @AfterEach void close() throws Exception { server.stop(); database.close(); }

    /** A raw protocol peer whose reader thread queues every message. */
    private final class Peer implements AutoCloseable {
        final Socket socket;
        final ObjectOutputStream out;
        final LinkedBlockingQueue<Object> inbox = new LinkedBlockingQueue<>();
        long requests = 1;

        Peer(String name, boolean register) throws Exception {
            socket = new Socket("127.0.0.1", port);
            out = new ObjectOutputStream(socket.getOutputStream()); out.flush();
            var in = new ObjectInputStream(socket.getInputStream()); in.setObjectInputFilter(Message.WIRE_FILTER);
            Thread reader = new Thread(() -> {
                try { while (true) inbox.add(in.readObject()); } catch (Exception closed) { inbox.add("closed"); }
            });
            reader.setDaemon(true); reader.start();
            expect(ErrorMessage.class, any -> true);
            send(new ProtocolHello(Message.PROTOCOL_VERSION)); expect(ProtocolReady.class, any -> true);
            send(register ? new RegisterRequest(name, "secret123") : new LoginRequest(name, "secret123"));
            assertTrue(expect(LoginResponse.class, any -> true).ok());
        }
        void send(Object message) throws IOException {
            synchronized (out) { out.writeObject(message); out.flush(); out.reset(); }
        }
        <T> T expect(Class<T> type, Predicate<T> wanted) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline) {
                Object next = inbox.poll(100, TimeUnit.MILLISECONDS);
                if (type.isInstance(next) && wanted.test(type.cast(next))) return type.cast(next);
            }
            throw new AssertionError("No " + type.getSimpleName() + " arrived");
        }
        MatchUpdate update(Predicate<MatchUpdate> wanted) throws Exception { return expect(MatchUpdate.class, wanted); }
        /** Sends the greedy bot's move for a view in which this player is waited on. */
        void move(MatchUpdate update) throws IOException {
            send(new MoveRequest(requests++, update.view().revision(), GreedyBot.choose(update.view())));
        }
        public void close() throws IOException { socket.close(); }
    }

    private int matchRows() throws Exception {
        return database.query(connection -> {
            try (var query = connection.prepareStatement("SELECT COUNT(*) FROM matches"); var rows = query.executeQuery()) {
                rows.next(); return rows.getInt(1);
            }
        });
    }

    /** Opens a hosted room and joins it; both views start with round one dealt. */
    private MatchUpdate[] hostAndJoin(Peer host, Peer joiner) throws Exception {
        host.send(new CreateMatchRequest());
        String code = host.expect(MatchCreated.class, any -> true).roomCode();
        joiner.send(new JoinMatchRequest(code.toLowerCase()));
        return new MatchUpdate[] {host.update(any -> true), joiner.update(any -> true)};
    }

    private static boolean waitedOn(MatchUpdate update) { return update.view().yourTurn(); }

    /** Drives both peers with the greedy bot until each sees the finished view. */
    private MatchUpdate[] playOut(Peer a, Peer b, MatchUpdate first, MatchUpdate second) throws Exception {
        MatchUpdate ua = first, ub = second;
        while (ua.view().phase() != MatchPhase.FINISHED) {
            int revision = Math.max(ua.view().revision(), ub.view().revision());
            if (waitedOn(ua)) a.move(ua); else b.move(ub);
            ua = a.update(u -> u.view().revision() > revision);
            ub = b.update(u -> u.view().revision() > revision);
        }
        return new MatchUpdate[] {ua, ub};
    }

    @Test void hostedRoomPlaysThreeRoundsAndRecordsTheResultAtomically() throws Exception {
        try (var alice = new Peer("alice", true); var bob = new Peer("bob", true)) {
            MatchUpdate[] opened = hostAndJoin(alice, bob);
            assertEquals("bob", opened[0].opponentName()); assertEquals("alice", opened[1].opponentName());
            assertEquals(MatchPhase.IN_PROGRESS, opened[0].view().phase(), "round one is dealt at once");
            alice.send(new ListMatchesRequest());
            assertTrue(alice.expect(ListMatchesResponse.class, any -> true).openMatches().isEmpty(), "the room was consumed");

            MatchUpdate[] start = opened;
            assertEquals(10, start[0].view().hand().size()); assertEquals(10, start[0].view().opponent().handCount());
            assertEquals(45 - 10, start[0].view().you().deckCount(), "every Unit and Spell");
            assertNotEquals(start[0].view().hand(), start[1].view().hand(), "each player only sees their own hand");

            MatchUpdate[] end = playOut(alice, bob, start[0], start[1]);
            assertEquals(3, end[0].view().rounds().size());
            assertEquals(end[0].view().you().matchTotal(), end[1].view().opponent().matchTotal());
            UserProfile aliceAfter = alice.expect(ProfileUpdate.class, any -> true).profile();
            UserProfile bobAfter = bob.expect(ProfileUpdate.class, any -> true).profile();
            assertEquals(aliceAfter.wins(), bobAfter.losses());
            assertEquals(aliceAfter.losses(), bobAfter.wins());
            var history = matchDao.recentMatches(1).get(0);
            assertEquals("cards-neutral-4", history.rulesVersion());
            assertEquals(3, matchDao.roundsOf(history.id()).size());
            assertEquals(end[0].view().rounds().get(2).firstScore(), matchDao.roundsOf(history.id()).get(2).firstScore());
            bob.send(new HistoryRequest());
            HistoryEntry seen = bob.expect(HistoryResponse.class, any -> true).matches().get(0);
            assertEquals("alice", seen.opponent());
            assertEquals(end[1].view().you().matchTotal(), seen.yourTotal(), "history is from the requester's side");
        }
    }

    @Test void staleIllegalAndRetriedPlaysChangeNothing() throws Exception {
        try (var alice = new Peer("alice", true); var bob = new Peer("bob", true)) {
            MatchUpdate[] opened = hostAndJoin(alice, bob);
            MatchUpdate[] start = opened;
            Peer mover = start[0].view().yourTurn() ? alice : bob, waiter = mover == alice ? bob : alice;
            MatchUpdate view = mover == alice ? start[0] : start[1];
            MatchUpdate waiting = mover == alice ? start[1] : start[0];
            PlayCard play = view.view().legalPlays().get(0);

            waiter.send(new MoveRequest(101, waiting.view().revision(), PlayCard.place(waiting.view().hand().get(0).id(), 0)));
            assertTrue(waiter.expect(ActionRejected.class, any -> true).reason().contains("not your turn"));
            mover.send(new MoveRequest(102, view.view().revision() + 5, play));
            assertEquals(102, mover.expect(ActionRejected.class, any -> true).requestId());
            mover.send(new MoveRequest(103, view.view().revision(), PlayCard.place(999_999, 0)));
            mover.expect(ActionRejected.class, rejected -> rejected.requestId() == 103);

            mover.send(new MoveRequest(104, view.view().revision(), play));
            MatchUpdate accepted = mover.update(u -> u.view().revision() == view.view().revision() + 1);
            mover.send(new MoveRequest(104, view.view().revision(), play));
            MatchUpdate retried = mover.update(any -> true);
            assertEquals(accepted.view(), retried.view(), "a retried accepted play is not applied twice");
            assertEquals(1, retried.view().you().playsUsed());
        }
    }

    @Test void skippingIsBroadcastAndStaleOutOfTurnAndRetriedSkipsDoNotConsumeAnotherPlay() throws Exception {
        try (var alice = new Peer("alice", true); var bob = new Peer("bob", true)) {
            MatchUpdate[] opened = hostAndJoin(alice, bob);
            MatchUpdate[] start = opened;
            Peer mover = start[0].view().yourTurn() ? alice : bob, waiter = mover == alice ? bob : alice;
            MatchUpdate view = mover == alice ? start[0] : start[1];
            int revision = view.view().revision();
            var skip = new deckduel.core.card.SkipTurn();
            waiter.send(new MoveRequest(101, revision, skip));
            assertTrue(waiter.expect(ActionRejected.class, any -> true).reason().contains("not your turn"));
            mover.send(new MoveRequest(102, revision - 1, skip));
            mover.expect(ActionRejected.class, r -> r.requestId() == 102);
            var request = new MoveRequest(103, revision, skip);
            mover.send(request);
            MatchUpdate accepted = mover.update(u -> u.view().revision() == revision + 1);
            MatchUpdate opponent = waiter.update(u -> u.view().revision() == revision + 1);
            assertEquals(view.view().hand(), accepted.view().hand());
            assertEquals(1, accepted.view().you().playsUsed());
            assertEquals(accepted.view().you(), opponent.view().opponent());
            assertTrue(opponent.view().yourTurn());
            mover.send(request);
            assertEquals(accepted.view(), mover.update(any -> true).view(), "retry changes nothing");
        }
    }

    @Test void aPlayerAlreadyInAMatchCannotHostAnotherRoom() throws Exception {
        try (var alice = new Peer("alice", true); var bob = new Peer("bob", true)) {
            hostAndJoin(alice, bob);
            bob.send(new CreateMatchRequest());
            assertEquals(ClientHandler.BUSY, bob.expect(ErrorMessage.class, any -> true).text());
        }
    }

    @Test void aDroppedPlayerReconnectsToTheSameSeatWithinTheGracePeriod() throws Exception {
        try (var alice = new Peer("alice", true); var bob = new Peer("bob", true)) {
            MatchUpdate[] opened = hostAndJoin(alice, bob);
            MatchUpdate[] start = opened;
            bob.close();
            assertFalse(alice.update(u -> !u.opponentConnected()).opponentLeft());
            try (var bobAgain = new Peer("bob", false)) {
                MatchUpdate restored = bobAgain.update(any -> true);
                assertEquals(start[1].matchId(), restored.matchId());
                assertEquals(start[1].view().hand(), restored.view().hand());
                assertEquals(start[1].view().you().row(), restored.view().you().row(), "the row survives the reconnect");
                assertTrue(alice.update(MatchUpdate::opponentConnected).opponentConnected());
            }
        }
    }

    @Test void anAbsentPlayerForfeitsWhenTheGracePeriodEnds() throws Exception {
        try (var alice = new Peer("alice", true); var bob = new Peer("bob", true)) {
            hostAndJoin(alice, bob);
            bob.close();
            MatchUpdate finished = alice.update(u -> u.view().phase() == MatchPhase.FINISHED);
            assertTrue(finished.view().outcome().forfeit());
            assertEquals(finished.view().seat(), finished.view().outcome().winner());
            alice.expect(ProfileUpdate.class, any -> true);
            assertEquals(1, matchRows());
        }
    }

    @Test void rematchStartsAFreshGame() throws Exception {
        try (var alice = new Peer("alice", true); var bob = new Peer("bob", true)) {
            MatchUpdate[] start = hostAndJoin(alice, bob);
            playOut(alice, bob, start[0], start[1]);
            alice.send(new RematchRequest());
            assertTrue(bob.update(MatchUpdate::opponentWantsRematch).opponentWantsRematch());
            bob.send(new RematchRequest());
            MatchUpdate second = alice.update(u -> u.game() == 2);
            assertEquals(MatchPhase.IN_PROGRESS, second.view().phase(), "a rematch deals a fresh round one");
            assertEquals(1, second.view().round());
            assertEquals(10, second.view().hand().size());
            assertEquals(0, second.view().you().matchTotal());
        }
    }

    @Test void leavingForfeitsAndFreesThePlayerForANewMatch() throws Exception {
        try (var alice = new Peer("alice", true); var bob = new Peer("bob", true)) {
            hostAndJoin(alice, bob);
            alice.send(new LeaveMatchRequest());
            MatchUpdate finished = bob.update(u -> u.view().phase() == MatchPhase.FINISHED);
            assertTrue(finished.opponentLeft()); assertTrue(finished.view().outcome().forfeit());
            alice.send(new CreateMatchRequest());
            assertNotNull(alice.expect(MatchCreated.class, any -> true).roomCode());
        }
    }

    @Test void practiceAgainstTheBotFinishesWithoutRecordingAMatch() throws Exception {
        try (var alice = new Peer("alice", true)) {
            alice.send(new PracticeRequest(true));
            MatchUpdate update = alice.update(any -> true);
            assertTrue(update.practice()); assertEquals(MatchSession.HARD_BOT, update.opponentName());
            while (update.view().phase() != MatchPhase.FINISHED) {
                if (waitedOn(update)) alice.move(update);
                int revision = update.view().revision();
                update = alice.update(u -> u.view().revision() > revision);
            }
            assertEquals(3, update.view().rounds().size());
            assertEquals(0, matchRows());
        }
    }
}
