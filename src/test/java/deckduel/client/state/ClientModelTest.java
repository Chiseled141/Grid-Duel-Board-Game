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
            public void ensureConnected(String host, int port) { fail("Already connected"); }
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
            f.model.findMatch();
            var mine = update("M1", deckduel.core.card.Seat.FIRST);
            var waiting = mine.view().yourTurn() ? update("M1", deckduel.core.card.Seat.SECOND) : mine;
            var moving = mine.view().yourTurn() ? mine : update("M1", deckduel.core.card.Seat.SECOND);
            f.model.handleMessage(waiting);
            assertEquals(Screen.TABLE, f.model.screen()); assertFalse(f.model.isFindingMatch());
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
            public void ensureConnected(String host, int port) { connects.incrementAndGet(); }
        });
        SwingUtilities.invokeAndWait(() -> model.login("127.0.0.1", 5555, "alice", "secret"));
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

    @Test void searchErrorClearsSearchWithoutStartingAMatch() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var f = fixture(); List<String> errors = new ArrayList<>();
            f.model.addListener(new ClientModelListener() { public void onError(String text) { errors.add(text); } });
            f.model.handleMessage(new LoginResponse(true, null, new UserProfile("alice", 0, 0)));
            f.sent.clear(); f.model.findMatch(); f.model.findMatch();
            assertEquals(List.of(new FindMatchRequest(true)), f.sent);
            f.model.handleMessage(new MatchmakingStatus(false, "Matchmaking is unavailable"));
            assertFalse(f.model.isFindingMatch()); assertEquals(Screen.LOBBY, f.model.screen());
            assertEquals(List.of("Matchmaking is unavailable"), errors);
        });
    }

    @Test void disconnectClearsAccountRoomsAndPendingRequests() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var f = fixture();
            f.model.handleMessage(new LoginResponse(true, null, new UserProfile("alice", 0, 0)));
            f.model.handleMessage(new MatchCreated("ABCDE"));
            f.model.handleMessage(new ListMatchesResponse(List.of(new MatchSummary("ABCDE", "alice"))));
            f.model.findMatch(); f.model.handleConnectionLost();
            assertNull(f.model.me()); assertNull(f.model.pendingRoomCode());
            assertFalse(f.model.isFindingMatch()); assertTrue(f.model.openMatches().isEmpty());
            assertEquals(Screen.LOGIN, f.model.screen());
        });
    }
}
