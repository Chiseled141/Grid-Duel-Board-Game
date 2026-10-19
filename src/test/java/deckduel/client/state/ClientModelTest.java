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
            var user = new UserProfile("alice", 1000, 0, 0);
            f.model.handleMessage(new LoginResponse(true, null, user));
            assertEquals(user, f.model.me()); assertEquals(Screen.LOBBY, f.model.screen());
            assertEquals(List.of(new ListMatchesRequest()), f.sent);
        });
    }

    @Test void deckAndTablePreviewNeedNoNetworkOrGameEngine() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var f = fixture(); f.model.openDeck(); assertEquals(Screen.DECK, f.model.screen());
            f.model.closeDeck(); assertEquals(Screen.LOBBY, f.model.screen());
            f.model.openTablePreview(); assertEquals(Screen.TABLE, f.model.screen());
            f.model.leaveToLobby(); assertEquals(Screen.LOBBY, f.model.screen());
            assertTrue(f.sent.isEmpty());
        });
    }

    @Test void gameplayUnavailableStatusClearsSearchWithoutStartingAMatch() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var f = fixture(); List<String> errors = new ArrayList<>();
            f.model.addListener(new ClientModelListener() { public void onError(String text) { errors.add(text); } });
            f.model.handleMessage(new LoginResponse(true, null, new UserProfile("alice", 1000, 0, 0)));
            f.sent.clear(); f.model.findMatch(); f.model.findMatch();
            assertEquals(List.of(new FindMatchRequest(true)), f.sent);
            f.model.handleMessage(new MatchmakingStatus(false, "Gameplay is not ready"));
            assertFalse(f.model.isFindingMatch()); assertEquals(Screen.LOBBY, f.model.screen());
            assertEquals(List.of("Gameplay is not ready"), errors);
        });
    }

    @Test void disconnectClearsAccountRoomsAndPendingRequests() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var f = fixture();
            f.model.handleMessage(new LoginResponse(true, null, new UserProfile("alice", 1000, 0, 0)));
            f.model.handleMessage(new MatchCreated("ABCDE"));
            f.model.handleMessage(new ListMatchesResponse(List.of(new MatchSummary("ABCDE", "alice"))));
            f.model.findMatch(); f.model.handleConnectionLost();
            assertNull(f.model.me()); assertNull(f.model.pendingRoomCode());
            assertFalse(f.model.isFindingMatch()); assertTrue(f.model.openMatches().isEmpty());
            assertEquals(Screen.LOGIN, f.model.screen());
        });
    }
}
