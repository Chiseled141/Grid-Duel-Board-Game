package onitama.server;

import onitama.core.PlayerColor;
import onitama.core.duel.ActionError;
import onitama.core.duel.CardInstance;
import onitama.core.duel.EndReason;
import onitama.core.duel.GameAction;
import onitama.core.duel.Targeting;
import onitama.core.duel.TurnPhase;
import onitama.db.Database;
import onitama.db.SqliteMatchDao;
import onitama.db.SqliteUserDao;
import onitama.net.ActionReply;
import onitama.net.ActionRequest;
import onitama.net.CreateMatchRequest;
import onitama.net.ErrorMessage;
import onitama.net.GameOver;
import onitama.net.GameUpdate;
import onitama.net.JoinMatchRequest;
import onitama.net.LoginRequest;
import onitama.net.LoginResponse;
import onitama.net.MatchCreated;
import onitama.net.MatchSync;
import onitama.net.ProtocolHello;
import onitama.net.ProtocolReady;
import onitama.net.ReconnectRequest;
import onitama.net.RegisterRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Acceptance tests for Milestone D: wire handshake, sequence retry semantics,
 * revision matching, reconnect during CHOICE, paused behavior, and DB persistence.
 */
class GridDuelProtocolTest {

    private GameServer server;
    private Database database;
    private int port;
    private ScriptedClient client1;
    private ScriptedClient client2;

    @TempDir
    Path tempDir;

    @BeforeEach
    void startServer() throws IOException, SQLException {
        database = new Database(tempDir.resolve("protocol-test.db"));
        SqliteUserDao userDao = new SqliteUserDao(database);
        server = new GameServer(new ServerConfig(0, 5, new Random(12345L)),
                userDao, new MatchPersistence(database, new SqliteMatchDao(database, userDao)));
        port = server.start();
    }

    @AfterEach
    void stopServer() throws SQLException {
        if (client1 != null) client1.close();
        if (client2 != null) client2.close();
        if (server != null) server.stop();
        if (database != null) database.close();
    }

    @Test
    void handshakeNegotiatesProtocolVersion4() throws Exception {
        client1 = new ScriptedClient(port, false);
        ErrorMessage greeting = client1.expect(ErrorMessage.class);
        assertEquals("GRID_DUEL_PROTOCOL:4", greeting.text());

        client1.send(new ProtocolHello(4));
        ProtocolReady ready = client1.expect(ProtocolReady.class);
        assertEquals(4, ready.version());

        client1.send(new RegisterRequest("user101", "password123"));
        LoginResponse response = client1.expectLoginOk();
        assertTrue(response.ok());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {2, 3, 99})
    void handshakeRejectsMismatchedVersion(int version) throws Exception {
        client1 = new ScriptedClient(port, false);
        ErrorMessage greeting = client1.expect(ErrorMessage.class);
        assertEquals("GRID_DUEL_PROTOCOL:4", greeting.text());

        client1.send(new ProtocolHello(version));
        ErrorMessage err = client1.expect(ErrorMessage.class);
        assertTrue(err.text().contains("incompatible protocol version"));
        client1.expectClosed();
    }

    @Test
    void handshakeRejectsPreHandshakeCommands() throws Exception {
        client1 = new ScriptedClient(port, false);
        ErrorMessage greeting = client1.expect(ErrorMessage.class);
        assertEquals("GRID_DUEL_PROTOCOL:4", greeting.text());

        // Attempting to log in before handshake
        client1.send(new LoginRequest("user1", "pw1"));
        ErrorMessage err = client1.expect(ErrorMessage.class);
        assertTrue(err.text().contains("protocol handshake required"));
        client1.expectClosed();
    }

    @Test
    void sequenceRetryAndConflictHandling() throws Exception {
        client1 = new ScriptedClient(port);
        client2 = new ScriptedClient(port);

        client1.send(new RegisterRequest("playerOne", "pass1234"));
        client1.expectLoginOk();
        client2.send(new RegisterRequest("playerTwo", "pass5678"));
        client2.expectLoginOk();

        client1.send(new CreateMatchRequest());
        String room = client1.expect(MatchCreated.class).roomCode();
        client2.send(new JoinMatchRequest(room));

        MatchSync sync1 = client1.expect(MatchSync.class);
        MatchSync sync2 = client2.expect(MatchSync.class);
        String matchId = sync1.matchId();

        ScriptedClient moverClient = sync1.view().turn() == PlayerColor.BLUE ? client1 : client2;
        MatchSync moverSync = sync1.view().turn() == PlayerColor.BLUE ? sync1 : sync2;
        ScriptedClient otherClient = moverClient == client1 ? client2 : client1;

        // 1. SEQUENCE GAP: Send sequence 2 instead of expected 1
        List<GameAction.MovePiece> legalMoves = Targeting.legalMoves(moverSync.view());
        assertFalse(legalMoves.isEmpty());
        GameAction.MovePiece move1 = legalMoves.get(0);

        moverClient.send(new ActionRequest(matchId, 2L, moverSync.view().revision(), move1));
        ActionReply gapReply = moverClient.expect(ActionReply.class);
        assertFalse(gapReply.receipt().accepted());
        assertEquals(ActionError.SEQUENCE_GAP, gapReply.receipt().error());
        assertEquals(1L, gapReply.current().nextSequence());

        // 2. STALE REVISION: Send sequence 1 with revision + 1
        moverClient.send(new ActionRequest(matchId, 1L, moverSync.view().revision() + 10, move1));
        ActionReply staleReply = moverClient.expect(ActionReply.class);
        assertFalse(staleReply.receipt().accepted());
        assertEquals(ActionError.STALE_REVISION, staleReply.receipt().error());
        // Stale revision consumed sequence 1
        assertEquals(2L, staleReply.current().nextSequence());

        // 3. RETRY OF REJECTED SEQUENCE: Retransmit identical payload for sequence 1
        moverClient.send(new ActionRequest(matchId, 1L, moverSync.view().revision() + 10, move1));
        ActionReply retryStale = moverClient.expect(ActionReply.class);
        assertFalse(retryStale.receipt().accepted());
        assertEquals(ActionError.STALE_REVISION, retryStale.receipt().error());

        // 4. SEQUENCE CONFLICT: Retransmit sequence 1 with different payload
        moverClient.send(new ActionRequest(matchId, 1L, moverSync.view().revision(), new GameAction.SkipMovement()));
        ActionReply conflictReply = moverClient.expect(ActionReply.class);
        assertFalse(conflictReply.receipt().accepted());
        assertEquals(ActionError.SEQUENCE_CONFLICT, conflictReply.receipt().error());

        // 5. SUCCESSFUL ACTION on sequence 2
        moverClient.send(new ActionRequest(matchId, 2L, moverSync.view().revision(), move1));
        GameUpdate update1 = moverClient.expect(GameUpdate.class);
        ActionReply okReply = moverClient.expect(ActionReply.class);
        otherClient.expect(GameUpdate.class);
        assertTrue(okReply.receipt().accepted());
        assertEquals(1L, okReply.receipt().revision());
        assertEquals(3L, okReply.current().nextSequence());

        // 6. IDEMPOTENT RETRY OF ACCEPTED ACTION: Retransmit sequence 2 with exact same move
        moverClient.send(new ActionRequest(matchId, 2L, moverSync.view().revision(), move1));
        ActionReply idempReply = moverClient.expect(ActionReply.class);
        assertTrue(idempReply.receipt().accepted());
        assertEquals(1L, idempReply.receipt().revision());
        assertEquals(3L, idempReply.current().nextSequence());

        // Same action with a changed revision is a different request, not a retry.
        moverClient.send(new ActionRequest(matchId, 2L, moverSync.view().revision() + 99, move1));
        ActionReply changedRevision = moverClient.expect(ActionReply.class);
        assertFalse(changedRevision.receipt().accepted());
        assertEquals(ActionError.SEQUENCE_CONFLICT, changedRevision.receipt().error());
        assertEquals(1L, changedRevision.current().view().revision());
        assertEquals(3L, changedRevision.current().nextSequence());

        // 7. OLD SEQUENCE: sequence 0 (older than nextSequence - 1)
        moverClient.send(new ActionRequest(matchId, 0L, 0L, move1));
        ActionReply oldReply = moverClient.expect(ActionReply.class);
        assertFalse(oldReply.receipt().accepted());
        assertEquals(ActionError.OLD_SEQUENCE, oldReply.receipt().error());
    }

    @Test
    void pausedStateRejectsNormalActionsAllowsResign() throws Exception {
        client1 = new ScriptedClient(port);
        client2 = new ScriptedClient(port);

        client1.send(new RegisterRequest("alice", "alicepw"));
        client1.expectLoginOk();
        client2.send(new RegisterRequest("bob", "bobpw"));
        client2.expectLoginOk();

        client1.send(new CreateMatchRequest());
        String room = client1.expect(MatchCreated.class).roomCode();
        client2.send(new JoinMatchRequest(room));

        MatchSync sync1 = client1.expect(MatchSync.class);
        MatchSync sync2 = client2.expect(MatchSync.class);
        String matchId = sync1.matchId();

        // client2 disconnects -> match pauses
        client2.close();
        client1.expect(onitama.net.OpponentLeft.class);
        MatchSync pausedSync = client1.expect(MatchSync.class);
        assertTrue(pausedSync.paused());

        // client1 tries to move while paused -> MATCH_PAUSED
        client1.send(new ActionRequest(matchId, 1L, sync1.view().revision(), new GameAction.SkipMovement()));
        ActionReply pausedReply = client1.expect(ActionReply.class);
        assertFalse(pausedReply.receipt().accepted());
        assertEquals(ActionError.MATCH_PAUSED, pausedReply.receipt().error());

        // Resign is permitted even while paused
        client1.send(new ActionRequest(matchId, 2L, sync1.view().revision(), new GameAction.Resign()));
        client1.expect(GameUpdate.class);
        ActionReply resignReply = client1.expect(ActionReply.class);
        assertTrue(resignReply.receipt().accepted());

        GameOver over = client1.expect(GameOver.class);
        assertEquals(PlayerColor.RED, over.winnerColor());
        assertEquals(EndReason.FORFEIT, over.way());
    }

    @Test
    void reconnectDuringChoiceRestoresPendingChoiceWithoutExtraMana() throws Exception {
        // Build a rules config where Foresight is enabled and guaranteed in hands
        onitama.core.duel.RulesConfig defaults = onitama.config.RulesConfigLoader.load(null);
        java.util.List<onitama.core.duel.SkillDefinition> customSkills = defaults.skills().stream()
                .map(s -> "foresight".equals(s.id())
                        ? new onitama.core.duel.SkillDefinition(s.id(), s.name(), s.category(), s.manaCost(),
                                4, s.parameters(), s.descriptionTemplate(), s.artworkPath())
                        : new onitama.core.duel.SkillDefinition(s.id(), s.name(), s.category(), s.manaCost(),
                                0, s.parameters(), s.descriptionTemplate(), s.artworkPath()))
                .toList();
        onitama.core.duel.RulesConfig customConfig = new onitama.core.duel.RulesConfig(
                defaults.rulesVersion(), defaults.movementHandSize(), 1,
                defaults.initialMana(), defaults.manaPerTurn(), defaults.manaCap(),
                defaults.turnLimit(), defaults.movementCards(), customSkills);

        SqliteUserDao userDao = (SqliteUserDao) server.userDao();
        GameServer foresightServer = new GameServer(
                new ServerConfig(0, 5, new Random(42L), 90, 32, customConfig),
                userDao, new MatchPersistence(database, new SqliteMatchDao(database, userDao)));
        int fPort = foresightServer.start();

        try {
            ScriptedClient c1 = new ScriptedClient(fPort);
            ScriptedClient c2 = new ScriptedClient(fPort);
            c1.send(new RegisterRequest("caster", "pass1234"));
            LoginResponse c1Login = c1.expectLoginOk();
            c2.send(new RegisterRequest("watcher", "pass5678"));
            LoginResponse c2Login = c2.expectLoginOk();

            c1.send(new CreateMatchRequest());
            String room = c1.expect(MatchCreated.class).roomCode();
            c2.send(new JoinMatchRequest(room));

            MatchSync s1 = c1.expect(MatchSync.class);
            MatchSync s2 = c2.expect(MatchSync.class);
            String matchId = s1.matchId();

            ScriptedClient moverClient = s1.view().turn() == PlayerColor.BLUE ? c1 : c2;
            MatchSync moverSync = s1.view().turn() == PlayerColor.BLUE ? s1 : s2;
            ScriptedClient otherClient = moverClient == c1 ? c2 : c1;
            LoginResponse moverLogin = moverClient == c1 ? c1Login : c2Login;

            // Skip movement to enter SKILL phase
            moverClient.send(new ActionRequest(matchId, 1L, moverSync.view().revision(), new GameAction.SkipMovement()));
            GameUpdate up1 = moverClient.expect(GameUpdate.class);
            ActionReply rep1 = moverClient.expect(ActionReply.class);
            otherClient.expect(GameUpdate.class);

            assertEquals(TurnPhase.SKILL, rep1.current().view().phase());
            int manaBeforeCast = rep1.current().view().viewer() == PlayerColor.BLUE
                    ? rep1.current().view().blueMana() : rep1.current().view().redMana();

            // Cast Foresight
            String foresightId = rep1.current().view().ownSkillHand().get(0).instanceId();
            moverClient.send(new ActionRequest(matchId, 2L, rep1.current().view().revision(),
                    new GameAction.CastSkill(foresightId, new onitama.core.duel.SkillTargets.None())));
            GameUpdate up2 = moverClient.expect(GameUpdate.class);
            ActionReply rep2 = moverClient.expect(ActionReply.class);
            GameUpdate otherUp = otherClient.expect(GameUpdate.class);

            // Mover receives private ownChoice preview
            assertNotNull(rep2.current().view().ownChoice());
            assertEquals(2, rep2.current().view().ownChoice().preview().size());
            assertEquals(TurnPhase.CHOICE, rep2.current().view().phase());

            // Other player sees CHOICE phase, but ownChoice is NULL (privacy preserved!)
            assertEquals(TurnPhase.CHOICE, otherUp.current().view().phase());
            assertNull(otherUp.current().view().ownChoice());

            // Mover drops while CHOICE is pending
            moverClient.close();
            otherClient.expect(onitama.net.OpponentLeft.class);
            otherClient.expect(MatchSync.class);

            // Mover reconnects with token
            ScriptedClient restoredMover = new ScriptedClient(fPort);
            restoredMover.send(new ReconnectRequest(moverLogin.reconnectToken()));
            MatchSync restoredSync = restoredMover.expect(MatchSync.class);

            // Restored view preserves CHOICE, choiceId, preview, and does not grant extra mana
            assertEquals(TurnPhase.CHOICE, restoredSync.view().phase());
            assertNotNull(restoredSync.view().ownChoice());
            assertEquals(rep2.current().view().ownChoice().choiceId(), restoredSync.view().ownChoice().choiceId());
            assertEquals(2, restoredSync.view().ownChoice().preview().size());
            int manaAfterReconnect = restoredSync.view().viewer() == PlayerColor.BLUE
                    ? restoredSync.view().blueMana() : restoredSync.view().redMana();
            assertEquals(manaBeforeCast, manaAfterReconnect, "no turn-start mana granted on reconnect");

            // Complete Foresight resolution
            List<String> order = restoredSync.view().ownChoice().preview().stream()
                    .map(CardInstance::instanceId)
                    .toList();
            restoredMover.send(new ActionRequest(matchId, 3L, restoredSync.view().revision(),
                    new GameAction.ResolveForesight(restoredSync.view().ownChoice().choiceId(), order)));
            GameUpdate resolveUp = restoredMover.expect(GameUpdate.class);
            ActionReply resolveRep = restoredMover.expect(ActionReply.class);
            assertTrue(resolveRep.receipt().accepted());
            assertEquals(TurnPhase.END, resolveRep.current().view().phase());

            restoredMover.close();
            c2.close();
        } finally {
            foresightServer.stop();
        }
    }
}
