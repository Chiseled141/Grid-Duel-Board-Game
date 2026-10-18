package deckduel.server;

import deckduel.core.duel.*;
import deckduel.db.*;
import deckduel.net.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OpeningProtocolTest {
    @TempDir Path directory;
    private Database database;
    private GameServer server;
    private int port;
    private final List<ScriptedClient> clients = new ArrayList<>();
    @BeforeEach void start() throws Exception {
        database = new Database(directory.resolve("opening.db"));
        var users = new SqliteUserDao(database);
        server = new GameServer(new ServerConfig(0, 5, new Random(42)), users,
                new MatchPersistence(database, new SqliteMatchDao(database, users)));
        port = server.start();
    }
    @AfterEach void stop() throws Exception {
        clients.forEach(ScriptedClient::close);
        if (server != null) server.stop();
        if (database != null) database.close();
    }
    private ScriptedClient client() throws Exception {
        var result = new ScriptedClient(port); clients.add(result); return result;
    }
    @Test void privateChoicesRetryAndIndependentStaleConfirmationStartExactlyOnce() throws Exception {
        var blue = client(); var red = client();
        blue.register("blue", "password123"); red.register("red", "password123");
        blue.send(new CreateMatchRequest()); red.send(new JoinMatchRequest(blue.expect(MatchCreated.class).roomCode()));
        var b = blue.expect(MatchSync.class); var r = red.expect(MatchSync.class);
        assertEquals(TurnPhase.OPENING, b.view().phase()); assertEquals(0, b.view().startedTurns());
        var old = b.view().ownSkillHand().get(1);
        var request = new ActionRequest(b.matchId(), 1, 0, new GameAction.ConfirmOpening(List.of(old.instanceId())));
        blue.send(request);
        var updated = blue.expect(GameUpdate.class); var receipt = blue.expect(ActionReply.class);
        var other = red.expect(GameUpdate.class);
        assertTrue(receipt.receipt().accepted()); assertNotEquals(old, updated.current().view().ownSkillHand().get(1));
        assertEquals(r.view().ownSkillHand(), other.current().view().ownSkillHand());
        assertTrue(other.current().view().opponentOpeningConfirmed());
        assertTrue(updated.events().isEmpty(), "private replacements never emit card identities");
        blue.send(request); var retry = blue.expect(ActionReply.class);
        assertEquals(receipt.receipt(), retry.receipt());
        assertEquals(updated.current().view().ownSkillHand(), retry.current().view().ownSkillHand());
        // Red clicked against revision 0 while Blue's independent choice was in flight.
        red.send(new ActionRequest(r.matchId(), 1, 0, new GameAction.ConfirmOpening(List.of())));
        var ready = red.expect(GameUpdate.class); assertTrue(red.expect(ActionReply.class).receipt().accepted());
        blue.expect(GameUpdate.class);
        assertEquals(TurnPhase.MOVEMENT, ready.current().view().phase()); assertEquals(1, ready.current().view().startedTurns());
        assertEquals(2, ready.current().view().revision());
        blue.send(request); var afterRetry = blue.expect(ActionReply.class);
        assertEquals(2, afterRetry.current().view().revision()); assertEquals(1, afterRetry.current().view().startedTurns());
    }
    @Test void reconnectRestoresConfirmedOpeningWithoutNewDrawOrMana() throws Exception {
        var blue = client(); var red = client();
        blue.send(new RegisterRequest("blue", "password123")); var login = blue.expectLoginOk();
        red.register("red", "password123");
        blue.send(new CreateMatchRequest()); red.send(new JoinMatchRequest(blue.expect(MatchCreated.class).roomCode()));
        var b = blue.expect(MatchSync.class); red.expect(MatchSync.class);
        blue.send(new ActionRequest(b.matchId(), 1, 0, new GameAction.ConfirmOpening(List.of())));
        var selected = blue.expect(GameUpdate.class).current(); blue.expect(ActionReply.class); red.expect(GameUpdate.class);
        blue.close(); red.expect(OpponentLeft.class); red.expect(MatchSync.class);
        var restored = client(); restored.send(new ReconnectRequest(login.reconnectToken()));
        var sync = restored.expect(MatchSync.class); red.expect(MatchSync.class);
        assertEquals(selected.view(), sync.view()); assertEquals(2, sync.nextSequence());
        assertEquals(2, sync.view().blueMana()); assertTrue(sync.view().openingConfirmed());
        red.send(new ActionRequest(sync.matchId(), 1, sync.view().revision(), new GameAction.ConfirmOpening(List.of())));
        red.expect(GameUpdate.class); assertTrue(red.expect(ActionReply.class).receipt().accepted());
        assertEquals(TurnPhase.MOVEMENT, restored.expect(GameUpdate.class).current().view().phase());
    }
}
