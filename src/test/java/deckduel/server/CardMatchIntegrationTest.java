package deckduel.server;

import static org.junit.jupiter.api.Assertions.*;

import deckduel.cards.CatalogLoader;
import deckduel.core.card.Catalog;
import deckduel.core.card.GreedyBot;
import deckduel.core.card.MatchPhase;
import deckduel.core.card.PlayCard;
import deckduel.core.card.PlayerView;
import deckduel.core.card.Seat;
import deckduel.db.Database;
import deckduel.db.SqliteMatchDao;
import deckduel.db.SqliteUserDao;
import deckduel.net.*;
import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
        var persistence = new MatchPersistence(database, matchDao, new deckduel.db.SqliteReplayDao(database));
        server = new GameServer(new ServerConfig(0, 90, 32, 1, 0), users, catalog, persistence, persistence, persistence);
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
            send(new ProtocolHello(Message.PROTOCOL_VERSION, "1.0.0")); expect(ProtocolReady.class, any -> true);
            send(register ? new RegisterRequest(name, "secret123") : new LoginRequest(name, "secret123"));
            assertTrue(expect(LoginResponse.class, any -> true).ok());
        }
        void send(Object message) throws IOException {
            synchronized (out) { out.writeObject(message); out.flush(); out.reset(); }
        }
        <T> T expect(Class<T> type, Predicate<T> wanted) throws Exception {
            return expect(type, wanted, 10);
        }
        <T> T expect(Class<T> type, Predicate<T> wanted, int seconds) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
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

    private MatchUpdate[] confirmKings(Peer a, Peer b, MatchUpdate[] opened) throws Exception {
        MatchUpdate ua = opened[0], ub = opened[1];
        while (ua.view().phase() == MatchPhase.KING_SELECTION) {
            int revision = ua.view().revision();
            if (ua.view().choosingKing()) a.move(ua); else b.move(ub);
            ua = a.update(u -> u.view().revision() > revision);
            ub = b.update(u -> u.view().revision() > revision);
        }
        return new MatchUpdate[] {ua, ub};
    }

    private static boolean waitedOn(MatchUpdate update) { return update.view().needsAction(); }

    @Test void realThirtySecondDeadlineChoosesLeftmostForIdleSeatsAndPreservesConfirmedChoices() throws Exception {
        try (var alice = new Peer("timer_a", true); var bob = new Peer("timer_b", true);
             var carol = new Peer("timer_c", true); var dan = new Peer("timer_d", true)) {
            MatchUpdate[] idle = hostAndJoin(alice, bob), mixed = hostAndJoin(carol, dan);
            long idleDeadline = idle[0].kingChoiceDeadlineMillis(), mixedDeadline = mixed[0].kingChoiceDeadlineMillis();
            assertEquals(idleDeadline, idle[1].kingChoiceDeadlineMillis());
            assertTrue(idleDeadline > System.currentTimeMillis());
            assertTrue(idleDeadline <= System.currentTimeMillis() + 30_000);
            var chosen = mixed[0].view().kingOffer().get(2);
            MoveRequest confirmation = new MoveRequest(500, mixed[0].view().revision(), new deckduel.core.card.ChooseKing(chosen));
            carol.send(confirmation);
            MatchUpdate waiting = carol.update(u -> u.view().selectedKing() == chosen);
            MatchUpdate hidden = dan.update(u -> u.view().revision() == waiting.view().revision());
            assertEquals(mixedDeadline, waiting.kingChoiceDeadlineMillis());
            assertTrue(hidden.view().opponent().kings().kings().isEmpty());
            dan.close(); carol.update(u -> !u.opponentConnected());
            try (var again = new Peer("timer_d", false)) {
                MatchUpdate restored = again.update(any -> true);
                assertEquals(mixedDeadline, restored.kingChoiceDeadlineMillis(), "reconnect does not restart the clock");
                carol.update(MatchUpdate::opponentConnected);
                MatchUpdate auto = alice.expect(MatchUpdate.class, u -> u.view().phase() == MatchPhase.IN_PROGRESS, 40);
                assertTrue(System.currentTimeMillis() >= idleDeadline);
                var firstKing = idle[0].view().kingOffer().get(0);
                assertEquals(List.of(firstKing), auto.view().you().kings().kings());
                assertEquals(List.of(firstKing), auto.view().opponent().kings().kings());
                assertEquals(0, auto.kingChoiceDeadlineMillis());
                assertEquals(0, auto.view().you().playsUsed()); assertEquals(0, auto.view().you().drawsUsed());
                assertEquals(idle[0].view().turn(), auto.view().turn());
                MatchUpdate kept = carol.expect(MatchUpdate.class, u -> u.view().phase() == MatchPhase.IN_PROGRESS, 10);
                assertEquals(List.of(chosen), kept.view().you().kings().kings());
                assertEquals(List.of(mixed[0].view().kingOffer().get(0)), kept.view().opponent().kings().kings());
                carol.send(confirmation);
                assertEquals(kept.view(), carol.update(any -> true).view(), "retry of the original confirmation changes nothing");
                again.send(new LeaveMatchRequest()); carol.update(u -> u.view().phase() == MatchPhase.FINISHED);
                ReplayResponse replay = replayOf(carol);
                assertTrue(replay.frames().stream().anyMatch(f -> f.selectedKing() == chosen));
                assertEquals(List.of(chosen), replay.frames().get(replay.frames().size() - 1).you().kings().kings());
                bob.send(new LeaveMatchRequest()); alice.update(u -> u.view().phase() == MatchPhase.FINISHED);
            }
        }
    }

    /** Drives both peers with the greedy bot until each sees the finished view. */
    private MatchUpdate[] playOut(Peer a, Peer b, MatchUpdate first, MatchUpdate second) throws Exception {
        MatchUpdate ua = first, ub = second;
        while (ua.view().phase() != MatchPhase.FINISHED) {
            int rounds = ua.view().rounds().size();
            int revision = Math.max(ua.view().revision(), ub.view().revision());
            if (waitedOn(ua)) a.move(ua); else b.move(ub);
            ua = a.update(u -> u.view().revision() > revision);
            ub = b.update(u -> u.view().revision() > revision);
            if (ua.view().rounds().size() > rounds) {
                for (MatchUpdate update : List.of(ua, ub)) {
                    var closing = update.closingRoundView();
                    assertNotNull(closing, "the final pre-bank view crosses the real socket");
                    var banked = update.view().rounds().get(rounds);
                    assertEquals(banked.scoreOf(closing.seat()), closing.you().score().total());
                    assertEquals(closing.you().matchTotal() + closing.you().score().total(), update.view().you().matchTotal());
                    assertFalse(closing.yourTurn());
                }
                assertEquals(ua.closingRoundView().you().handCount(), ub.closingRoundView().opponent().handCount());
                assertEquals(ub.closingRoundView().you().handCount(), ua.closingRoundView().opponent().handCount());
            }
        }
        return new MatchUpdate[] {ua, ub};
    }

    @Test void hostedRoomPlaysThreeRoundsAndRecordsTheResultAtomically() throws Exception {
        try (var alice = new Peer("alice", true); var bob = new Peer("bob", true)) {
            MatchUpdate[] opened = hostAndJoin(alice, bob);
            assertEquals("bob", opened[0].opponentName()); assertEquals("alice", opened[1].opponentName());
            assertEquals(MatchPhase.KING_SELECTION, opened[0].view().phase(), "round one is dealt at once");
            alice.send(new ListMatchesRequest());
            assertTrue(alice.expect(ListMatchesResponse.class, any -> true).openMatches().isEmpty(), "the room was consumed");

            MatchUpdate[] start = confirmKings(alice, bob, opened);
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
            assertEquals("cards-neutral-5", history.rulesVersion());
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
            MatchUpdate[] start = confirmKings(alice, bob, opened);
            Peer mover = start[0].view().yourTurn() ? alice : bob, waiter = mover == alice ? bob : alice;
            MatchUpdate view = mover == alice ? start[0] : start[1];
            MatchUpdate waiting = mover == alice ? start[1] : start[0];
            PlayCard play = view.view().legalPlays().stream().filter(p -> view.view().hand().stream()
                    .anyMatch(c -> c.id() == p.cardId() && c.def().kind() == deckduel.core.card.CardKind.SPELL))
                    .findFirst().orElse(view.view().legalPlays().get(0));

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
            assertEquals(accepted.view().you().kings().spellsPlayed(), retried.view().you().kings().spellsPlayed());
        }
    }

    @Test void skippingIsBroadcastAndStaleOutOfTurnAndRetriedSkipsDoNotConsumeAnotherPlay() throws Exception {
        try (var alice = new Peer("alice", true); var bob = new Peer("bob", true)) {
            MatchUpdate[] opened = hostAndJoin(alice, bob);
            MatchUpdate[] start = confirmKings(alice, bob, opened);
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
            MatchUpdate[] start = confirmKings(alice, bob, opened);
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
            assertEquals(MatchPhase.KING_SELECTION, second.view().phase(), "a rematch deals a fresh round one");
            assertEquals(1, second.view().round());
            assertEquals(10, second.view().hand().size());
            assertEquals(0, second.view().you().matchTotal());
            assertTrue(second.kingChoiceDeadlineMillis() > System.currentTimeMillis());
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

    private static ReplayResponse replayOf(Peer peer) throws Exception {
        peer.send(new HistoryRequest());
        HistoryEntry entry = peer.expect(HistoryResponse.class, any -> true).matches().get(0);
        assertEquals(HistoryEntry.Replay.AVAILABLE, entry.replay());
        peer.send(new ReplayRequest(entry.replayId()));
        return peer.expect(ReplayResponse.class, any -> true);
    }

    /** Revisions must run without gaps or repeats from the opening deal to the finished view. */
    private static void assertContiguous(List<PlayerView> frames) {
        for (int i = 1; i < frames.size(); i++)
            assertEquals(frames.get(i - 1).revision() + 1, frames.get(i).revision(), "frame " + i + " follows the one before it");
        assertEquals(MatchPhase.FINISHED, frames.get(frames.size() - 1).phase(), "the replay ends with the result");
    }

    @Test void eachPlayerReplaysTheirOwnViewsOnceInOrderAcrossARetryAndAReconnect() throws Exception {
        Map<Integer, PlayerView> aliceSaw = new TreeMap<>();
        try (var alice = new Peer("alice", true)) {
            Peer bob = new Peer("bob", true);
            MatchUpdate[] opened = hostAndJoin(alice, bob);
            MatchUpdate ua = opened[0], ub = opened[1];
            aliceSaw.put(ua.view().revision(), ua.view());
            boolean retried = false, reconnected = false;
            while (ua.view().phase() != MatchPhase.FINISHED) {
                int revision = Math.max(ua.view().revision(), ub.view().revision());
                if (!reconnected && revision >= 8 && waitedOn(ua)) {
                    // Bob drops while Alice moves, then logs back in to the held seat.
                    bob.close();
                    alice.move(ua);
                    ua = alice.update(u -> u.view().revision() > revision);
                    aliceSaw.put(ua.view().revision(), ua.view());
                    bob = new Peer("bob", false);
                    int now = ua.view().revision();
                    ub = bob.update(u -> u.view().revision() == now);
                    // The reconnect re-sends the same revision to Alice; it must not become a second frame.
                    ua = alice.update(u -> u.view().revision() == now);
                    reconnected = true;
                    continue;
                }
                if (waitedOn(ua)) {
                    alice.move(ua);
                    if (!retried) {
                        // The same request id again: answered with the current view, applied once.
                        alice.send(new MoveRequest(alice.requests - 1, ua.view().revision(), GreedyBot.choose(ua.view())));
                        retried = true;
                    }
                } else bob.move(ub);
                ua = alice.update(u -> u.view().revision() > revision);
                ub = bob.update(u -> u.view().revision() > revision);
                aliceSaw.put(ua.view().revision(), ua.view());
            }
            assertTrue(retried && reconnected);

            ReplayResponse mine = replayOf(alice), theirs = replayOf(bob);
            assertEquals("bob", mine.opponent()); assertFalse(mine.practice()); assertTrue(mine.complete());
            assertContiguous(mine.frames());
            assertContiguous(theirs.frames());
            assertEquals(mine.frames().size(), theirs.frames().size(), "Bob's recording kept going while he was away");
            for (PlayerView frame : mine.frames()) {
                assertEquals(Seat.FIRST, frame.seat());
                PlayerView live = aliceSaw.get(frame.revision());
                if (live != null) assertEquals(live, frame, "the replay holds exactly what Alice was sent");
            }
            for (int i = 0; i < mine.frames().size(); i++) {
                PlayerView own = mine.frames().get(i), other = theirs.frames().get(i);
                assertEquals(Seat.SECOND, other.seat());
                var bobHand = other.hand().stream().map(c -> c.id()).toList();
                assertTrue(own.hand().stream().noneMatch(c -> bobHand.contains(c.id())), "no opponent hand cards in Alice's frames");
                assertTrue(own.revealed().stream().allMatch(c -> bobHand.contains(c.id())), "only Spy-revealed cards of Bob's hand");
            }

            // Ownership: Bob cannot open Alice's recording, and unknown ids look the same.
            bob.send(new ReplayRequest(mine.replayId()));
            assertEquals(ReplayStore.UNAVAILABLE, bob.expect(ErrorMessage.class, any -> true).text());
            bob.send(new ReplayRequest(987_654));
            assertEquals(ReplayStore.UNAVAILABLE, bob.expect(ErrorMessage.class, any -> true).text());
            bob.close();
        }
    }

    @Test void drainClosesRoomsRefusesNewMatchesAndLetsTheRunningMatchFinish() throws Exception {
        try (var alice = new Peer("alice", true); var bob = new Peer("bob", true);
             var carol = new Peer("carol", true); var dave = new Peer("dave", true)) {
            MatchUpdate[] opened = hostAndJoin(alice, bob);
            carol.send(new CreateMatchRequest());
            String code = carol.expect(MatchCreated.class, any -> true).roomCode();
            server.drain();
            MaintenanceNotice notice = carol.expect(MaintenanceNotice.class, any -> true);
            assertTrue(notice.active());
            assertTrue(notice.text().contains("matches in progress finish normally"));
            assertTrue(alice.expect(MaintenanceNotice.class, any -> true).active(), "players in a match are told too");
            assertEquals(0, server.status().openRooms(), "waiting rooms are closed");
            dave.send(new JoinMatchRequest(code));
            assertEquals(ClientHandler.MAINTENANCE, dave.expect(ErrorMessage.class, any -> true).text());
            carol.send(new CreateMatchRequest());
            assertEquals(ClientHandler.MAINTENANCE, carol.expect(ErrorMessage.class, any -> true).text());
            dave.send(new PracticeRequest(false));
            assertEquals(ClientHandler.MAINTENANCE, dave.expect(ErrorMessage.class, any -> true).text());
            ServerStatus during = server.status();
            assertEquals(1, during.activeMatches());
            assertFalse(during.safeToRestart(), "a match is still being played");

            MatchUpdate[] end = playOut(alice, bob, opened[0], opened[1]);
            assertEquals(3, end[0].view().rounds().size(), "the running match finished normally");
            alice.send(new RematchRequest());
            assertEquals(ClientHandler.MAINTENANCE, alice.expect(ErrorMessage.class, any -> true).text());
            long deadline = System.currentTimeMillis() + 5_000;
            while (!server.status().safeToRestart() && System.currentTimeMillis() < deadline) Thread.sleep(25);
            assertTrue(server.status().safeToRestart(), server.status().describe());
            assertTrue(server.status().describe().contains("safeToRestart=true"));

            server.resume();
            assertFalse(carol.expect(MaintenanceNotice.class, n -> !n.active()).active());
            carol.send(new CreateMatchRequest());
            assertNotNull(carol.expect(MatchCreated.class, any -> true).roomCode(), "new matches are admitted again");
        }
    }

    @Test void maintenanceWaitsForFinalResultWrites() throws Exception {
        checkPendingWrites(false);
    }

    @Test void maintenanceTracksDepartedPlayersUntilTheirReplayIsSaved() throws Exception {
        checkPendingWrites(true);
    }

    private void checkPendingWrites(boolean blockReplay) throws Exception {
        server.stop();
        var users = new SqliteUserDao(database);
        var persistence = new MatchPersistence(database, matchDao, new deckduel.db.SqliteReplayDao(database));
        var writing = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        Runnable pause = () -> {
            writing.countDown();
            try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
        };
        ReplayStore replay = new ReplayStore() {
            public long begin(String owner, String key, String opponent, boolean practice, String rules) {
                return persistence.begin(owner, key, opponent, practice, rules);
            }
            public boolean frame(long id, PlayerView view) { return persistence.frame(id, view); }
            public void finish(long id, PlayerView view, boolean complete) {
                if (blockReplay) pause.run();
                persistence.finish(id, view, complete);
            }
            public Message replay(String owner, long id) { return persistence.replay(owner, id); }
        };
        server = new GameServer(new ServerConfig(0, 90, 32, 1, 0), users, catalog, (result, rounds) -> {
            if (!blockReplay) pause.run();
            persistence.record(result, rounds);
        }, persistence, replay);
        port = server.start();
        try (var alice = new Peer("alice", true); var bob = new Peer("bob", true)) {
            MatchUpdate[] opened = hostAndJoin(alice, bob);
            server.drain();
            if (blockReplay) {
                // Both seats disappear while their forfeit's replay is still being saved.
                alice.close();
                bob.close();
            } else playOut(alice, bob, opened[0], opened[1]);
            assertTrue(writing.await(5, TimeUnit.SECONDS), "the final write was reached");
            assertEquals(1, server.status().activeMatches());
            assertFalse(server.status().safeToRestart(), "pending persistence must block a restart");
            release.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!server.status().safeToRestart() && System.nanoTime() < deadline) Thread.sleep(10);
            assertTrue(server.status().safeToRestart(), server.status().describe());
            assertEquals(1, matchRows(), "the result is durable before restart is allowed");
        } finally { release.countDown(); }
    }

    @Test void drainCannotOvertakeAMatchBeingRegistered() throws Exception {
        try (var alice = new Peer("alice", true)) {
            alice.send(new CreateMatchRequest());
            String code = alice.expect(MatchCreated.class, any -> true).roomCode();
            Entrant entrant = server.lobby().claim(code);
            var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
            var drained = new java.util.concurrent.CountDownLatch(1);
            Thread joining = new Thread(() -> {
                try { assertTrue(server.startPractice(entrant, false)); }
                catch (Throwable e) { failure.set(e); }
            });
            Thread maintenance = new Thread(() -> { server.drain(); drained.countDown(); });
            try {
                synchronized (server.matches()) {
                    // Pause admission in bind, after the maintenance check but before registration.
                    joining.start();
                    awaitBlocked(joining);
                    maintenance.start();
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                    while (maintenance.getState() != Thread.State.BLOCKED && drained.getCount() != 0
                            && System.nanoTime() < deadline) Thread.sleep(1);
                    assertEquals(1, drained.getCount(), "drain must wait for the admitted match to be visible");
                    assertEquals(Thread.State.BLOCKED, maintenance.getState());
                }
            } finally { joining.join(5_000); maintenance.join(5_000); }
            assertNull(failure.get());
            assertTrue(server.draining());
            assertEquals(1, server.status().activeMatches());
            assertFalse(server.status().safeToRestart());
        }
    }

    private static void awaitBlocked(Thread thread) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.getState() != Thread.State.BLOCKED && thread.isAlive() && System.nanoTime() < deadline) Thread.sleep(1);
        assertEquals(Thread.State.BLOCKED, thread.getState());
    }

    @Test void aReservedRematchBlocksRestartAndDrainRefusesLaterReservations() throws Exception {
        try (var alice = new Peer("alice", true); var bob = new Peer("bob", true)) {
            MatchUpdate[] opened = hostAndJoin(alice, bob);
            playOut(alice, bob, opened[0], opened[1]);
            // A request on the same match executor completes after final persistence.
            alice.send(new RematchRequest());
            alice.update(u -> u.rematchRequested());
            MatchSession session = server.matches().sessionFor("alice");
            assertFalse(session.inProgress());
            assertTrue(server.reserveRematch(session));
            server.drain();
            assertFalse(server.status().safeToRestart(), "the next game was reserved before drain");
            assertFalse(server.reserveRematch(session), "no reservation may cross the drain barrier");
        }
    }

    @Test void aHeldSeatKeepsARestartUnsafeUntilItsGraceEnds() throws Exception {
        try (var alice = new Peer("alice", true)) {
            Peer bob = new Peer("bob", true);
            hostAndJoin(alice, bob);
            server.drain();
            bob.close();
            long deadline = System.currentTimeMillis() + 5_000;
            while (server.status().heldSeats() == 0 && System.currentTimeMillis() < deadline) Thread.sleep(10);
            ServerStatus held = server.status();
            assertEquals(1, held.heldSeats(), held.describe());
            assertFalse(held.safeToRestart());
            // The one-second grace ends, Bob forfeits, the match finishes and nothing is left to lose.
            deadline = System.currentTimeMillis() + 10_000;
            while (!server.status().safeToRestart() && System.currentTimeMillis() < deadline) Thread.sleep(50);
            assertTrue(server.status().safeToRestart(), server.status().describe());
            assertTrue(alice.update(u -> u.view().phase() == MatchPhase.FINISHED).view().outcome().forfeit());
        }
    }

    /** Creates the control-socket folder in a short system location; Unix addresses are length-limited. */
    private static Path shortSocketFolder() throws IOException {
        // Unix socket paths are limited to about 100 characters on macOS and 108 on Windows,
        // so prefer the short system folder on Unix and the JVM temp folder elsewhere.
        Path unixTmp = Path.of("/tmp");
        Path base = File.separatorChar != '\\' && Files.isDirectory(unixTmp)
                ? unixTmp : Path.of(System.getProperty("java.io.tmpdir"));
        return Files.createTempDirectory(base, "dd");
    }

    @Test void theControlSocketReportsDrainsAndResumes() throws Exception {
        Path folder = shortSocketFolder();
        Path socket = folder.resolve("control.sock");
        try (ControlServer control = ControlServer.start(socket, server)) {
            if (Files.getFileStore(socket).supportsFileAttributeView("posix"))
                assertEquals("rw-rw----", java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(socket)));
            assertTrue(ControlClient.send(socket, "status").startsWith("ok state=open"));
            assertTrue(ControlClient.send(socket, "drain").contains("state=draining"));
            assertTrue(server.draining());
            assertTrue(ControlClient.send(socket, "status").contains("safeToRestart=true"));
            assertTrue(ControlClient.send(socket, "reload-tls").startsWith("error TLS is not configured"));
            assertTrue(ControlClient.send(socket, "shutdown now").startsWith("error unknown command"));
            assertTrue(ControlClient.send(socket, "resume").contains("state=open"));
            assertEquals(0, ControlClient.run(new String[] {"status", "--socket", socket.toString()}));
            assertEquals(3, ControlClient.run(new String[] {"wait-safe", "--socket", socket.toString(), "--timeout", "1"}),
                    "wait-safe refuses while the server is not draining");
        } finally {
            Files.deleteIfExists(socket);
            Files.deleteIfExists(folder);
        }
        assertFalse(Files.exists(socket));
    }

    @Test void aCancelledRoomCanNoLongerBeJoined() throws Exception {
        try (var alice = new Peer("alice", true); var bob = new Peer("bob", true)) {
            alice.send(new CreateMatchRequest());
            String code = alice.expect(MatchCreated.class, any -> true).roomCode();
            alice.send(new CancelRoomRequest());
            assertTrue(alice.expect(ListMatchesResponse.class, any -> true).openMatches().isEmpty());
            bob.send(new JoinMatchRequest(code));
            assertNotNull(bob.expect(ErrorMessage.class, any -> true));
            Thread.sleep(200);
            assertTrue(alice.inbox.stream().noneMatch(m -> m instanceof MatchUpdate), "no match started");
            assertTrue(bob.inbox.stream().noneMatch(m -> m instanceof MatchUpdate));
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
            // The game is kept only as Alice's replay: listed as practice, with no change to wins or losses.
            alice.send(new HistoryRequest());
            HistoryEntry entry = alice.expect(HistoryResponse.class, any -> true).matches().get(0);
            assertTrue(entry.practice());
            assertEquals(MatchSession.HARD_BOT, entry.opponent());
            assertEquals(update.view().you().matchTotal(), entry.yourTotal());
            ReplayResponse replay = replayOf(alice);
            assertTrue(replay.practice());
            assertContiguous(replay.frames());
            assertEquals(update.view(), replay.frames().get(replay.frames().size() - 1), "the final result is the last frame");
            var profile = server.userDao().profileOf("alice");
            assertEquals(0, profile.wins() + profile.losses());
        }
    }

    @Test void kingConfirmationIsPrivateIdempotentAndRestoredAfterDisconnect() throws Exception {
        try (var alice = new Peer("alice", true); var bob = new Peer("bob", true)) {
            MatchUpdate[] opened = hostAndJoin(alice, bob);
            var offer = opened[0].view().kingOffer();
            assertEquals(offer, opened[1].view().kingOffer());
            var chosen = offer.get(0);
            var request = new MoveRequest(alice.requests++, opened[0].view().revision(), new deckduel.core.card.ChooseKing(chosen));
            alice.send(request);
            MatchUpdate privateChoice = alice.update(u -> u.view().selectedKing() != null);
            MatchUpdate hidden = bob.update(u -> u.view().revision() == privateChoice.view().revision());
            assertNull(hidden.view().selectedKing());
            assertTrue(hidden.view().opponent().kings().kings().isEmpty());
            alice.send(request);
            assertEquals(privateChoice.view(), alice.update(any -> true).view());
            bob.send(new MoveRequest(bob.requests++, opened[1].view().revision(), new deckduel.core.card.ChooseKing(chosen)));
            bob.expect(ActionRejected.class, any -> true);
            MatchUpdate fresh = bob.update(u -> u.view().revision() == hidden.view().revision());
            alice.close();
            bob.update(u -> !u.opponentConnected());
            try (var again = new Peer("alice", false)) {
                MatchUpdate restored = again.update(any -> true);
                assertEquals(privateChoice.view(), restored.view());
                MatchUpdate connected = bob.update(MatchUpdate::opponentConnected);
                bob.send(new MoveRequest(bob.requests++, fresh.view().revision(), new deckduel.core.card.ChooseKing(chosen)));
                MatchUpdate ready = again.update(u -> u.view().phase() == MatchPhase.IN_PROGRESS);
                assertEquals(List.of(chosen), ready.view().you().kings().kings());
                assertEquals(List.of(chosen), ready.view().opponent().kings().kings());
                assertEquals(0, ready.view().you().playsUsed());
                assertEquals(opened[0].view().turn(), ready.view().turn());
                bob.send(new LeaveMatchRequest());
                again.update(u -> u.view().phase() == MatchPhase.FINISHED);
                ReplayResponse replay = replayOf(again);
                assertTrue(replay.frames().contains(privateChoice.view()), "owner replay retains its unannounced confirmation");
            }
        }
    }

    @Test void secretEdictPendingChoiceSurvivesRetryReconnectAndPrivateReplay() throws Exception {
        try (var alice = new Peer("alice", true); var bob = new Peer("bob", true)) {
            MatchUpdate[] opened = hostAndJoin(alice, bob);
            // Offers use live secure randomness. New games are requested through the real rematch route.
            for (int attempts = 0; !opened[0].view().kingOffer().contains(deckduel.core.card.King.NETHRION); attempts++) {
                assertTrue(attempts < 40, "Nethrion should appear in a shared offer");
                int game = opened[0].game();
                alice.send(new LeaveMatchRequest()); bob.update(u -> u.view().phase() == MatchPhase.FINISHED);
                bob.send(new LeaveMatchRequest());
                opened = hostAndJoin(alice, bob);
            }
            var king = deckduel.core.card.King.NETHRION;
            alice.send(new MoveRequest(alice.requests++, opened[0].view().revision(), new deckduel.core.card.ChooseKing(king)));
            MatchUpdate confirmed = alice.update(u -> u.view().selectedKing() == king);
            MatchUpdate bobChoice = bob.update(u -> u.view().revision() == confirmed.view().revision());
            bob.send(new MoveRequest(bob.requests++, bobChoice.view().revision(), new deckduel.core.card.ChooseKing(king)));
            MatchUpdate mine = alice.update(u -> u.view().phase() == MatchPhase.IN_PROGRESS);
            int openedRevision = mine.view().revision();
            MatchUpdate theirs = bob.update(u -> u.view().revision() == openedRevision);
            if (!mine.view().yourTurn()) {
                bob.send(new MoveRequest(bob.requests++, theirs.view().revision(), new deckduel.core.card.SkipTurn()));
                mine = alice.update(u -> u.view().yourTurn());
                int rev = mine.view().revision(); bob.update(u -> u.view().revision() == rev);
            }
            var draw = new MoveRequest(alice.requests++, mine.view().revision(), new deckduel.core.card.DrawCard());
            alice.send(draw);
            MatchUpdate pending = alice.update(u -> u.view().choiceKind() == deckduel.core.card.ChoiceKind.SECRET_EDICT);
            MatchUpdate hidden = bob.update(u -> u.view().revision() == pending.view().revision());
            assertTrue(hidden.view().looking().isEmpty()); assertEquals(10, pending.view().hand().size());
            assertEquals(1, pending.view().you().drawsUsed());
            alice.send(draw); assertEquals(pending.view(), alice.update(any -> true).view());
            alice.close(); bob.update(u -> !u.opponentConnected());
            try (var again = new Peer("alice", false)) {
                MatchUpdate restored = again.update(any -> true);
                assertEquals(pending.view(), restored.view());
                bob.update(MatchUpdate::opponentConnected);
                var keep = new MoveRequest(700, restored.view().revision(), new deckduel.core.card.KeepEdictCard(restored.view().looking().get(0).id()));
                again.send(keep);
                MatchUpdate kept = again.update(u -> u.view().revision() > restored.view().revision());
                bob.update(u -> u.view().revision() == kept.view().revision());
                assertTrue(kept.view().yourTurn()); assertEquals(11, kept.view().hand().size());
                assertEquals(1, kept.view().you().drawsUsed()); assertEquals(0, kept.view().you().playsUsed());
                again.send(keep); assertEquals(kept.view(), again.update(any -> true).view());
                bob.send(new LeaveMatchRequest()); again.update(u -> u.view().phase() == MatchPhase.FINISHED);
                ReplayResponse owner = replayOf(again), opponent = replayOf(bob);
                assertTrue(owner.frames().contains(pending.view()));
                assertTrue(opponent.frames().stream().allMatch(f -> f.looking().isEmpty()), "opponent recording contains no Edict cards");
            }
        }
    }
}
