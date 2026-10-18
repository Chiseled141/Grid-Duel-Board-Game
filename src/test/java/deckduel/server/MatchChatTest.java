package deckduel.server;

import deckduel.db.Database;
import deckduel.db.SqliteMatchDao;
import deckduel.db.SqliteUserDao;
import deckduel.net.*;
import deckduel.core.PlayerColor;
import deckduel.core.duel.GameAction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Socket-level checks of chat membership, validation, lifecycle and gameplay isolation. */
class MatchChatTest {
    @TempDir Path directory;
    private Database database;
    private GameServer server;
    private int port;
    private final List<ScriptedClient> clients = new ArrayList<>();

    @BeforeEach
    void start() throws Exception {
        database = new Database(directory.resolve("chat.db"));
        var users = new SqliteUserDao(database);
        server = new GameServer(new ServerConfig(0, 5, new Random(42)), users,
                new MatchPersistence(database, new SqliteMatchDao(database, users)));
        port = server.start();
    }

    @AfterEach
    void stop() throws Exception {
        clients.forEach(ScriptedClient::close);
        if (server != null) server.stop();
        if (database != null) database.close();
    }

    private ScriptedClient client(String name) throws Exception {
        var client = new ScriptedClient(port);
        clients.add(client);
        if (name != null) client.register(name, "password123");
        return client;
    }

    private MatchSync match(ScriptedClient blue, ScriptedClient red) {
        blue.send(new CreateMatchRequest());
        red.send(new JoinMatchRequest(blue.expect(MatchCreated.class).roomCode()));
        var sync = blue.expect(MatchSync.class);
        var redSync = red.expect(MatchSync.class);
        return ScriptedClient.keepOpening(blue, red, sync, redSync)[0];
    }

    @Test
    void echoesAuthenticatedIdentityOnlyWithinMatchAndDoesNotConsumeGameSequence() throws Exception {
        var blue = client("blue");
        var red = client("red");
        var outsider = client("outsider");
        var sync = match(blue, red);
        blue.send(new ChatRequest(sync.matchId(), "  Good luck 👋  "));
        var expected = new ChatMessage(sync.matchId(), "blue", "Good luck 👋");
        assertEquals(expected, blue.expect(ChatMessage.class));
        assertEquals(expected, red.expect(ChatMessage.class));
        outsider.send(new Ping(42));
        assertEquals(42, outsider.expect(Pong.class).timestamp());
        outsider.send(new ChatRequest(sync.matchId(), "intruder"));
        outsider.expect(ErrorMessage.class);
        var mover = sync.view().turn() == PlayerColor.BLUE ? blue : red;
        var other = mover == blue ? red : blue;
        mover.send(new ActionRequest(sync.matchId(), 2, sync.view().revision(), new GameAction.EndTurn()));
        var update = mover.expect(GameUpdate.class);
        assertEquals(sync.view().revision() + 1, update.current().view().revision());
        assertTrue(mover.expect(ActionReply.class).receipt().accepted());
        other.expect(GameUpdate.class);
    }

    @Test
    void rejectsUnauthenticatedStaleAndInvalidTextWithoutBroadcasting() throws Exception {
        var guest = client(null);
        guest.send(new ChatRequest("unknown", "hello"));
        guest.expect(ErrorMessage.class);
        var blue = client("blue");
        var red = client("red");
        var sync = match(blue, red);
        blue.send(new ChatRequest("stale", "hello"));
        blue.expect(ErrorMessage.class);
        for (String text : new String[]{null, "", "   ", "x".repeat(281), "a\nb", "a\tb", "a\u202Eb", "a\u2028b"}) {
            blue.send(new ChatRequest(sync.matchId(), text));
            blue.expect(ErrorMessage.class);
        }
        blue.send(new ChatRequest(sync.matchId(), "x".repeat(280)));
        assertEquals(280, blue.expect(ChatMessage.class).text().length());
        assertEquals(280, red.expect(ChatMessage.class).text().length());
    }

    @Test
    void neverRelaysToAnOpponentHandlerThatHasChangedAccounts() throws Exception {
        var blue = client("blue");
        var red = client("red");
        var sync = match(blue, red);
        red.send(new RegisterRequest("different", "password123"));
        red.expectLoginOk();
        blue.expect(OpponentLeft.class);
        assertTrue(blue.expect(MatchSync.class).paused());
        blue.send(new ChatRequest(sync.matchId(), "private"));
        blue.expect(ErrorMessage.class);
        red.send(new Ping(24));
        assertEquals(24, red.expect(Pong.class).timestamp());
    }

    @Test
    void throttlesOnePlayerWithoutBlockingOpponent() throws Exception {
        var blue = client("blue");
        var red = client("red");
        var sync = match(blue, red);
        blue.send(new ChatRequest(sync.matchId(), "one"));
        blue.send(new ChatRequest(sync.matchId(), "two"));
        blue.expect(ChatMessage.class);
        assertTrue(blue.expect(ErrorMessage.class).text().contains("wait"));
        red.expect(ChatMessage.class);
        red.send(new ChatRequest(sync.matchId(), "reply"));
        assertEquals("red", red.expect(ChatMessage.class).sender());
        assertEquals("reply", blue.expect(ChatMessage.class).text());
    }

    @Test
    void rejectsChatWhilePausedAndAfterGameOver() throws Exception {
        var blue = client("blue");
        var red = client("red");
        var sync = match(blue, red);
        red.close();
        blue.expect(OpponentLeft.class);
        var paused = blue.expect(MatchSync.class);
        assertTrue(paused.paused());
        blue.send(new ChatRequest(sync.matchId(), "hello"));
        blue.expect(ErrorMessage.class);
        blue.send(new ActionRequest(sync.matchId(), 2, sync.view().revision(), new GameAction.Resign()));
        blue.expect(GameUpdate.class);
        blue.expect(ActionReply.class);
        blue.expect(GameOver.class);
        blue.send(new ChatRequest(sync.matchId(), "too late"));
        blue.expect(ErrorMessage.class);
    }

    @Test
    void reconnectRestoresChatMembership() throws Exception {
        var blue = client("blue");
        var red = client("red");
        red.send(new LoginRequest("red", "password123"));
        String token = red.expectLoginOk().reconnectToken();
        var sync = match(blue, red);
        red.close();
        blue.expect(OpponentLeft.class);
        blue.expect(MatchSync.class);
        var replacement = client(null);
        replacement.send(new ReconnectRequest(token));
        replacement.expect(MatchSync.class);
        blue.expect(MatchSync.class);
        replacement.send(new ChatRequest(sync.matchId(), "back"));
        assertEquals("red", replacement.expect(ChatMessage.class).sender());
        assertEquals("back", blue.expect(ChatMessage.class).text());
    }
}
