package deckduel.client.state;

import deckduel.net.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientModelTest {
    private record Fixture(ClientModel model, List<Message> sent) { }
    private Fixture fixture() {
        List<Message> sent = new ArrayList<>();
        return new Fixture(new ClientModel(new ServerConnection() {
            public boolean isConnected() { return true; }
            public void send(Message message) { sent.add(message); }
            public void ensureConnected(deckduel.client.startup.ServerRoute route) { fail("Already connected"); }
        }), sent);
    }

    @Test void loginFailureStaysOnLoginAndSuccessRequestsRealRooms() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var f = fixture(); List<String> failures = new ArrayList<>();
            f.model.addListener(new ClientModelListener() {
                public void onLoginFailed(String reason) { failures.add(reason); }
            });
            f.model.handleMessage(new LoginResponse(false, "Wrong password", null));
            assertEquals(Screen.LOGIN, f.model.screen()); assertNull(f.model.me());
            assertEquals(List.of("Wrong password"), failures);
            var user = new UserProfile("alice", 0, 0);
            f.model.handleMessage(new LoginResponse(true, null, user));
            assertEquals(user, f.model.me()); assertEquals(Screen.LOBBY, f.model.screen());
            assertEquals(List.of(new ListMatchesRequest()), f.sent);
        });
    }

    @Test void startupOpensLoginOnceAndLoginUsesTheConnectionItMade() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var f = fixture(); List<Screen> screens = new ArrayList<>();
            f.model.addListener(new ClientModelListener() {
                public void onScreenChanged(Screen screen) { screens.add(screen); }
            });
            f.model.showStartup();
            assertEquals(Screen.STARTUP, f.model.screen());
            f.model.startupFinished();
            assertEquals(Screen.LOGIN, f.model.screen());
            f.model.startupFinished();
            assertEquals(List.of(Screen.STARTUP, Screen.LOGIN), screens, "a late finish changes nothing");
            f.model.login(deckduel.client.startup.ServerRoute.LOCAL_DEVELOPMENT, "alice", "secret");
            assertEquals(List.of(new LoginRequest("alice", "secret")), f.sent, "no second connection before login");
        });
    }

    /** The dealt opening of a match on the bundled catalog, as {@code seat} sees it. */
    static MatchUpdate update(String id, deckduel.core.card.Seat seat) {
        var catalog = deckduel.cards.CatalogLoader.loadBundled();
        var deck = deckduel.core.card.DeckRules.deck(catalog);
        var state = deckduel.core.card.MatchState.start(deck, deck, 1);
        return new MatchUpdate(id, 1, "alice", "bob", false, true, false, false, false, state.view(seat));
    }

    @Test void onlyOneMoveIsSentPerShownView() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var f = fixture();
            f.model.handleMessage(new LoginResponse(true, null, new UserProfile("alice", 0, 0)));
            var mine = update("M1", deckduel.core.card.Seat.FIRST);
            var moving = mine.view().yourTurn() ? mine : update("M1", deckduel.core.card.Seat.SECOND);
            f.model.handleMessage(moving);
            f.sent.clear();
            f.model.move(new deckduel.core.card.DrawCard());
            assertEquals(new deckduel.core.card.DrawCard(), ((MoveRequest) f.sent.get(0)).move());
            f.model.move(new deckduel.core.card.DrawCard());
            assertEquals(1, f.sent.size(), "one move per shown view");
        });
    }

    @Test void matchUpdatesOpenTheTableAndPlaysQuoteTheShownRevision() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var f = fixture();
            f.model.handleMessage(new LoginResponse(true, null, new UserProfile("alice", 0, 0)));
            f.model.handleMessage(new MatchCreated("ABCDE"));
            var mine = update("M1", deckduel.core.card.Seat.FIRST);
            var waiting = mine.view().yourTurn() ? update("M1", deckduel.core.card.Seat.SECOND) : mine;
            var moving = mine.view().yourTurn() ? mine : update("M1", deckduel.core.card.Seat.SECOND);
            f.model.handleMessage(waiting);
            assertEquals(Screen.TABLE, f.model.screen()); assertNull(f.model.pendingRoomCode());
            f.sent.clear();
            f.model.move(deckduel.core.card.PlayCard.place(1, 0));
            assertTrue(f.sent.isEmpty(), "no request when it is not your turn");
            f.model.handleMessage(moving);
            var play = moving.view().legalPlays().get(0);
            f.model.move(play); f.model.move(play);
            assertEquals(1, f.sent.size(), "a second click waits for the server's answer");
            f.model.handleMessage(new ActionRejected(((MoveRequest) f.sent.get(0)).requestId(), "Try again"));
            f.model.move(play);
            var first = (MoveRequest) f.sent.get(0); var second = (MoveRequest) f.sent.get(1);
            assertEquals(moving.view().revision(), first.revision());
            assertNotEquals(first.requestId(), second.requestId());
            f.model.leaveMatch();
            assertEquals(new LeaveMatchRequest(), f.sent.get(2));
            assertEquals(Screen.LOBBY, f.model.screen());
            f.model.handleMessage(moving);
            assertEquals(Screen.LOBBY, f.model.screen(), "a late update from the left match is ignored");
        });
    }

    @Test void losingTheConnectionMidMatchLogsBackInToTheHeldSeat() throws Exception {
        var sent = new java.util.concurrent.LinkedBlockingQueue<Message>();
        var connects = new java.util.concurrent.atomic.AtomicInteger();
        var model = new ClientModel(new ServerConnection() {
            public boolean isConnected() { return connects.get() > 0; }
            public void send(Message message) { sent.add(message); }
            public void ensureConnected(deckduel.client.startup.ServerRoute route) { connects.incrementAndGet(); }
        });
        SwingUtilities.invokeAndWait(() -> model.login(deckduel.client.startup.ServerRoute.LOCAL_DEVELOPMENT, "alice", "secret"));
        assertEquals(new LoginRequest("alice", "secret"), sent.poll(5, java.util.concurrent.TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {
            model.handleMessage(new LoginResponse(true, null, new UserProfile("alice", 0, 0)));
            model.handleMessage(update("M1", deckduel.core.card.Seat.FIRST));
            sent.clear();
            model.handleConnectionLost();
            assertTrue(model.isReconnecting()); assertEquals(Screen.TABLE, model.screen());
        });
        assertEquals(new LoginRequest("alice", "secret"), sent.poll(5, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(2, connects.get());
        SwingUtilities.invokeAndWait(() -> {
            model.handleMessage(new LoginResponse(true, null, new UserProfile("alice", 0, 0)));
            assertFalse(model.isReconnecting()); assertEquals(Screen.TABLE, model.screen());
        });
    }

    @Test void disconnectClearsAccountRoomsAndPendingRequests() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var f = fixture();
            f.model.handleMessage(new LoginResponse(true, null, new UserProfile("alice", 0, 0)));
            f.model.handleMessage(new MatchCreated("ABCDE"));
            f.model.handleMessage(new ListMatchesResponse(List.of(new MatchSummary("ABCDE", "alice"))));
            f.model.handleConnectionLost();
            assertNull(f.model.me()); assertNull(f.model.pendingRoomCode());
            assertTrue(f.model.openMatches().isEmpty());
            assertEquals(Screen.LOGIN, f.model.screen());
        });
    }

    @Test void aRequiredUpdateFoundDuringAMatchNeverInterruptsItButBlocksTheNextOne() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var f = fixture(); List<String> errors = new ArrayList<>(); List<Screen> screens = new ArrayList<>();
            f.model.addListener(new ClientModelListener() {
                public void onError(String text) { errors.add(text); }
                public void onScreenChanged(Screen screen) { screens.add(screen); }
            });
            f.model.handleMessage(new LoginResponse(true, null, new UserProfile("alice", 0, 0)));
            var mine = update("M1", deckduel.core.card.Seat.FIRST);
            var moving = mine.view().yourTurn() ? mine : update("M1", deckduel.core.card.Seat.SECOND);
            f.model.handleMessage(moving);
            screens.clear(); f.sent.clear();
            f.model.requireUpdate("2.0.0");
            assertEquals(Screen.TABLE, f.model.screen(), "the match stays on screen");
            assertTrue(screens.isEmpty());
            assertTrue(errors.isEmpty(), "nothing is shown over the table");
            f.model.move(new deckduel.core.card.DrawCard());
            assertTrue(f.sent.get(0) instanceof MoveRequest, "moves in the current match still go out");
            f.model.requireUpdate("3.0.0");
            assertEquals("2.0.0", f.model.requiredUpdate(), "the first requirement is remembered");

            f.sent.clear();
            f.model.requestRematch();
            f.model.leaveMatch();
            assertEquals(List.of(new LeaveMatchRequest()), f.sent, "no rematch; leaving still works");
            f.sent.clear();
            f.model.createMatch(); f.model.joinMatch("ABCDE"); f.model.startPractice(true);
            assertTrue(f.sent.isEmpty(), "no new match until the update is installed");
            assertEquals(4, errors.size());
            assertTrue(errors.get(0).contains("Version 2.0.0 is required"));
        });
    }

    @Test void replaysOpenOnlyWhenAskedForFromHistoryAndNeverTouchTheMatch() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var f = fixture();
            f.model.handleMessage(new LoginResponse(true, null, new UserProfile("alice", 0, 0)));
            var replay = ClientFixtures.replay(true);
            f.model.handleMessage(replay);
            assertNull(f.model.replay(), "an unrequested replay is ignored");
            f.model.requestReplay(5);
            assertTrue(f.sent.stream().noneMatch(m -> m instanceof ReplayRequest), "replays are opened from history");
            f.model.requestHistory();
            f.sent.clear();
            f.model.requestReplay(5);
            assertEquals(List.of(new ReplayRequest(5)), f.sent);
            f.model.handleMessage(new ReplayResponse(6, "carol", false, "2026-10-23T09:00:00", true, replay.frames()));
            assertNull(f.model.replay(), "a different replay's answer is ignored");
            f.model.handleMessage(replay);
            assertEquals(Screen.REPLAY, f.model.screen());
            assertNull(f.model.match());
            f.sent.clear();
            f.model.replay().next(); f.model.replay().play(); f.model.replay().setSpeed(2.0);
            f.model.move(new deckduel.core.card.DrawCard());
            assertTrue(f.sent.isEmpty(), "playback sends nothing, and there is no live match to move in");
            f.model.closeReplay();
            assertEquals(Screen.HISTORY, f.model.screen());
            assertNull(f.model.replay());
        });
    }

    @Test void offlinePlayIsFlaggedAndClearedWhenTheSessionEnds() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var f = fixture();
            f.model.beginOffline(new deckduel.client.startup.ServerRoute("127.0.0.1", 40404, false), "offline", "random-secret");
            assertTrue(f.model.isOffline());
            assertEquals(List.of(new RegisterRequest("offline", "random-secret")), f.sent);
            f.model.handleMessage(new LoginResponse(true, null, new UserProfile("offline", 0, 0)));
            assertEquals(Screen.LOBBY, f.model.screen());
            f.model.handleConnectionLost();
            assertFalse(f.model.isOffline());
            assertEquals(Screen.LOGIN, f.model.screen());
        });
    }

    @Test void aRoomOpenedBeforeARequiredUpdateIsClosedAndNeverStartsAMatch() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var f = fixture(); List<String> errors = new ArrayList<>();
            f.model.addListener(new ClientModelListener() { public void onError(String text) { errors.add(text); } });
            f.model.handleMessage(new LoginResponse(true, null, new UserProfile("alice", 0, 0)));
            f.model.createMatch();
            f.model.handleMessage(new MatchCreated("ABCDE"));
            f.sent.clear();
            f.model.requireUpdate("2.0.0");
            assertEquals(List.of(new CancelRoomRequest()), f.sent);
            assertNull(f.model.pendingRoomCode());
            // A room whose creation was already on its way is closed as soon as it arrives.
            f.sent.clear();
            f.model.handleMessage(new MatchCreated("FGHJK"));
            assertEquals(List.of(new CancelRoomRequest()), f.sent);
            assertNull(f.model.pendingRoomCode());
            assertFalse(errors.isEmpty());
        });
    }

    @Test void deferredWorkRunsOnceTheMatchFinishesOrIsLeft() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var f = fixture(); List<String> ran = new ArrayList<>();
            f.model.handleMessage(new LoginResponse(true, null, new UserProfile("alice", 0, 0)));
            f.model.whenNoMatchInProgress(() -> ran.add("now"));
            assertEquals(List.of("now"), ran, "no match: at once");
            f.model.handleMessage(update("M1", deckduel.core.card.Seat.FIRST));
            f.model.whenNoMatchInProgress(() -> ran.add("after"));
            assertEquals(List.of("now"), ran);
            f.model.setUpdating(true);
            f.model.move(new deckduel.core.card.DrawCard());
            assertEquals(Screen.TABLE, f.model.screen(), "installing never interrupts the match");
            f.model.leaveMatch();
            assertEquals(List.of("now", "after"), ran);
        });
    }

    @Test void maintenanceBlocksNewMatchesClosesTheRoomAndTellsAffectedPlayers() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var f = fixture(); List<String> errors = new ArrayList<>();
            f.model.addListener(new ClientModelListener() { public void onError(String text) { errors.add(text); } });
            f.model.handleMessage(new LoginResponse(true, null, new UserProfile("alice", 0, 0)));
            f.model.createMatch();
            f.model.handleMessage(new MatchCreated("ABCDE"));
            f.sent.clear();
            f.model.handleMessage(new MaintenanceNotice(true, "The server is about to restart for maintenance."));
            assertNull(f.model.pendingRoomCode(), "the server already closed the room");
            assertEquals(List.of("The server is about to restart for maintenance."), errors, "the host is told");
            assertTrue(f.model.newMatchesBlocked());
            f.model.startPractice(false);
            assertTrue(f.sent.isEmpty());
            assertEquals("The server is about to restart for maintenance.", errors.get(1));
            f.model.handleMessage(new MaintenanceNotice(false, "Maintenance is over."));
            assertFalse(f.model.newMatchesBlocked());
            f.model.startPractice(false);
            assertEquals(List.of(new PracticeRequest(false)), f.sent);
        });
    }
}
