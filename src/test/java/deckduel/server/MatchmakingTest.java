package deckduel.server;

import deckduel.db.*;
import deckduel.net.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real-socket checks for queue admission, cancellation and lobby races. */
class MatchmakingTest {
    @TempDir Path directory;
    private Database database;
    private GameServer server;
    private BlockingUsers users;
    private int port;
    private final List<ScriptedClient> clients = new ArrayList<>();

    @BeforeEach void start() throws Exception {
        database = new Database(directory.resolve("queue.db"));
        users = new BlockingUsers(new SqliteUserDao(database));
        server = new GameServer(new ServerConfig(0, 5, new Random(42)), users);
        port = server.start();
    }

    @AfterEach void stop() throws Exception {
        clients.forEach(ScriptedClient::close);
        if (server != null) server.stop();
        if (database != null) database.close();
    }

    private ScriptedClient client(String name) throws Exception {
        var client = new ScriptedClient(port); clients.add(client);
        if (name != null) client.register(name, "password123");
        return client;
    }

    private void search(ScriptedClient client) {
        client.send(new FindMatchRequest(true));
        assertEquals(new MatchmakingStatus(true, null), client.expect(MatchmakingStatus.class));
    }

    private MatchSync paired(ScriptedClient client) {
        assertEquals(new MatchmakingStatus(false, null), client.expect(MatchmakingStatus.class));
        return client.expect(MatchSync.class);
    }

    @Test void duplicateSearchQueuesOnceAndReturnsPrivateViewsForOneMatch() throws Exception {
        var first = client("first"); var second = client("second"); var third = client("third");
        search(first); search(first);
        second.send(new FindMatchRequest(true));
        var blue = paired(first); var red = paired(second);
        assertEquals(blue.matchId(), red.matchId());
        assertNotEquals(blue.view().viewer(), red.view().viewer());
        assertEquals(3, blue.view().ownSkillHand().size());
        assertEquals(3, red.view().ownSkillHand().size());
        first.send(new FindMatchRequest(true));
        assertNotNull(first.expect(MatchmakingStatus.class).error());
        search(third); // No duplicate first player remains in the waiting queue.
        third.send(new FindMatchRequest(false));
        assertFalse(third.expect(MatchmakingStatus.class).searching());
    }

    @Test void cancellationIsIdempotentAndNextWaitingPlayerCanPair() throws Exception {
        var first = client("first"); var second = client("second"); var third = client("third");
        search(first);
        first.send(new FindMatchRequest(false)); first.send(new FindMatchRequest(false));
        assertFalse(first.expect(MatchmakingStatus.class).searching());
        assertFalse(first.expect(MatchmakingStatus.class).searching());
        search(second);
        third.send(new FindMatchRequest(true));
        assertEquals(paired(second).matchId(), paired(third).matchId());
        first.send(new Ping(47)); assertEquals(47, first.expect(Pong.class).timestamp());
    }

    @Test void queueClosesHostedRoomAndRejectsRoomAdmissionUntilCancelled() throws Exception {
        var first = client("first"); var second = client("second");
        first.send(new CreateMatchRequest()); String oldCode = first.expect(MatchCreated.class).roomCode();
        search(first);
        second.send(new ListMatchesRequest());
        assertTrue(second.expect(ListMatchesResponse.class).openMatches().isEmpty());
        first.send(new CreateMatchRequest()); assertTrue(first.expect(ErrorMessage.class).text().contains("Cancel"));
        first.send(new JoinMatchRequest(oldCode)); assertTrue(first.expect(ErrorMessage.class).text().contains("Cancel"));
        second.send(new JoinMatchRequest(oldCode)); second.expect(ErrorMessage.class);
        first.send(new FindMatchRequest(false)); first.expect(MatchmakingStatus.class);
        first.send(new CreateMatchRequest()); assertNotNull(first.expect(MatchCreated.class).roomCode());
    }

    @Test void disconnectAndReplacementLoginRemoveWaitingIdentity() throws Exception {
        var old = client("old"); search(old);
        var replacement = client(null);
        replacement.send(new LoginRequest("old", "password123")); replacement.expectLoginOk();
        old.expect(ErrorMessage.class); old.expectClosed();
        var next = client("next"); search(next);
        replacement.send(new FindMatchRequest(true));
        assertEquals(paired(next).matchId(), paired(replacement).matchId());
    }

    @Test void simultaneousSearchesPairOnceAndCancelCannotUndoLiveMatch() throws Exception {
        var first = client("first"); var second = client("second");
        var latch = new CountDownLatch(1);
        var a = CompletableFuture.runAsync(() -> { await(latch); first.send(new FindMatchRequest(true)); });
        var b = CompletableFuture.runAsync(() -> { await(latch); second.send(new FindMatchRequest(true)); });
        latch.countDown(); a.get(3, TimeUnit.SECONDS); b.get(3, TimeUnit.SECONDS);
        var firstSync = afterConcurrentSearch(first); var secondSync = afterConcurrentSearch(second);
        assertEquals(firstSync.matchId(), secondSync.matchId());
        first.send(new FindMatchRequest(false)); assertFalse(first.expect(MatchmakingStatus.class).searching());
        first.send(new CreateMatchRequest()); assertTrue(first.expect(ErrorMessage.class).text().contains("already"));
    }

    @Test void disconnectedWaiterIsRemovedBeforeNextSearch() throws Exception {
        var gone = client(null);
        gone.send(new RegisterRequest("gone", "password123"));
        String token = gone.expectLoginOk().reconnectToken();
        search(gone); gone.close();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (server.registry().byToken(token) != null && System.nanoTime() < deadline) Thread.sleep(10);
        assertNull(server.registry().byToken(token));
        search(client("next"));
    }

    @Test void newSearchWithdrawsFinishedGameRematch() throws Exception {
        var first = client("first"); var second = client("second"); var next = client("next");
        search(first); second.send(new FindMatchRequest(true));
        var sync = paired(first); paired(second);
        first.send(new ActionRequest(sync.matchId(), 1, sync.view().revision(), new deckduel.core.duel.GameAction.Resign()));
        first.expect(GameUpdate.class); first.expect(ActionReply.class); first.expect(GameOver.class);
        second.expect(GameUpdate.class); second.expect(GameOver.class);
        search(first);
        second.send(new RematchRequest());
        assertTrue(second.expect(ErrorMessage.class).text().contains("left"));
        next.send(new FindMatchRequest(true));
        assertEquals(paired(first).matchId(), paired(next).matchId());
    }

    @Test void reconnectCancelsWaitingSearchRatherThanRestoringAPhantomQueueEntry() throws Exception {
        var old = client(null);
        old.send(new RegisterRequest("old", "password123"));
        String token = old.expectLoginOk().reconnectToken();
        search(old);
        var restored = client(null);
        restored.send(new ReconnectRequest(token)); restored.expectLoginOk();
        old.expectClosed();
        var next = client("next"); search(next);
        restored.send(new FindMatchRequest(true));
        assertEquals(paired(next).matchId(), paired(restored).matchId());
    }

    @Test void changingAccountsCancelsSearchBeforeAuthenticationCompletes() throws Exception {
        var first = client("first"); search(first);
        first.send(new RegisterRequest("changed", "password123"));
        assertFalse(first.expect(MatchmakingStatus.class).searching());
        assertEquals("changed", first.expectLoginOk().profile().username());
        var second = client("second"); search(second);
        first.send(new FindMatchRequest(true));
        var secondSync = paired(second); var changedSync = paired(first);
        assertEquals(secondSync.matchId(), changedSync.matchId());
    }

    @Test void changingAccountsAfterPairingNotifiesTheReservedOpponent() throws Exception {
        var first = client("first"); var second = client("second");
        search(first); second.send(new FindMatchRequest(true)); paired(first); paired(second);
        first.send(new RegisterRequest("changed", "password123"));
        first.expectLoginOk();
        assertTrue(second.expect(OpponentLeft.class).graceSeconds() > 0);
        first.send(new FindMatchRequest(true));
        assertTrue(first.expect(MatchmakingStatus.class).searching());
    }

    @Test void queueIsAlreadyCancelledWhileAuthenticationIsBlocked() throws Exception {
        var first = client("first"); var second = client("second"); search(first);
        users.blockLogin = true;
        first.send(new LoginRequest("first", "password123"));
        try {
            assertFalse(first.expect(MatchmakingStatus.class).searching());
            assertTrue(users.entered.await(3, TimeUnit.SECONDS));
            search(second); // Cannot claim first while its login is in progress.
        } finally { users.release.countDown(); }
        first.expectLoginOk();
        first.send(new FindMatchRequest(true));
        assertEquals(paired(second).matchId(), paired(first).matchId());
    }

    @Test void roomClaimedDuringBlockedAuthenticationGetsDisconnectRecovery() throws Exception {
        var first = client("first"); var second = client("second");
        first.send(new CreateMatchRequest()); String code = first.expect(MatchCreated.class).roomCode();
        users.blockLogin = true;
        first.send(new LoginRequest("first", "password123"));
        try {
            assertTrue(users.entered.await(3, TimeUnit.SECONDS));
            second.send(new JoinMatchRequest(code));
            first.expect(MatchSync.class); second.expect(MatchSync.class);
        } finally { users.release.countDown(); }
        first.expectLoginOk();
        assertTrue(second.expect(OpponentLeft.class).graceSeconds() > 0);
        assertTrue(second.expect(MatchSync.class).paused());
    }

    @Test void abandonedMatchCannotSendIntoNewMatchOrClearItsReconnectToken() throws Exception {
        server.stop();
        server = new GameServer(new ServerConfig(0, 1, new Random(42)), users);
        port = server.start();
        var first = client("first"); var oldPeer = client("oldpeer"); var next = client("next");
        search(first); oldPeer.send(new FindMatchRequest(true)); paired(first); paired(oldPeer);
        first.send(new RegisterRequest("changed", "password123"));
        String newToken = first.expectLoginOk().reconnectToken();
        oldPeer.expect(OpponentLeft.class); oldPeer.expect(MatchSync.class);
        search(first); next.send(new FindMatchRequest(true));
        paired(first); paired(next);
        oldPeer.expect(GameUpdate.class); oldPeer.expect(GameOver.class);
        first.send(new Ping(77)); assertEquals(77, first.expect(Pong.class).timestamp());
        next.send(new Ping(78)); assertEquals(78, next.expect(Pong.class).timestamp());
        assertNotNull(server.registry().byToken(newToken));
    }

    @Test void requiresAuthentication() throws Exception {
        var guest = client(null); guest.send(new FindMatchRequest(true));
        var status = guest.expect(MatchmakingStatus.class);
        assertFalse(status.searching()); assertNotNull(status.error());
    }

    private MatchSync afterConcurrentSearch(ScriptedClient client) {
        if (client.expect(MatchmakingStatus.class).searching()) return paired(client);
        return client.expect(MatchSync.class);
    }

    private static final class BlockingUsers implements UserDao {
        private final UserDao delegate;
        volatile boolean blockLogin;
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        BlockingUsers(UserDao delegate) { this.delegate = delegate; }
        public UserProfile register(String name, String password) { return delegate.register(name, password); }
        public UserProfile login(String name, String password) {
            if (blockLogin) { entered.countDown(); await(release); }
            return delegate.login(name, password);
        }
        public UserProfile profileOf(String name) { return delegate.profileOf(name); }
        public void updateStats(String name, int elo, int wins, int losses) { delegate.updateStats(name, elo, wins, losses); }
        public List<UserProfile> leaderboard(int limit) { return delegate.leaderboard(limit); }
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
}
