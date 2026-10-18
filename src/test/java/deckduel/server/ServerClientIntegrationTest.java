package deckduel.server;

import deckduel.core.Elo;
import deckduel.core.PlayerColor;
import deckduel.core.duel.EndReason;
import deckduel.core.duel.GameAction;
import deckduel.core.duel.Targeting;
import deckduel.db.Database;
import deckduel.db.SqliteMatchDao;
import deckduel.db.SqliteUserDao;
import deckduel.net.ActionReply;
import deckduel.net.ActionRequest;
import deckduel.net.CreateMatchRequest;
import deckduel.net.GameOver;
import deckduel.net.GameUpdate;
import deckduel.net.JoinMatchRequest;
import deckduel.net.LeaderboardRequest;
import deckduel.net.LeaderboardResponse;
import deckduel.net.ListMatchesRequest;
import deckduel.net.ListMatchesResponse;
import deckduel.net.LoginResponse;
import deckduel.net.MatchCreated;
import deckduel.net.MatchSync;
import deckduel.net.RegisterRequest;
import deckduel.net.RematchAccept;
import deckduel.net.RematchRequest;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IT01 — two headless socket clients register, meet through the lobby and
 * play an authoritative Deck Duel match with retry sequencing, protocol verification,
 * Elo persistence, leaderboard reflection, and rematch.
 */
class ServerClientIntegrationTest {

    private static final long SEED = 20260927L;

    private GameServer server;
    private Database database;
    private int port;
    private ScriptedClient blue;
    private ScriptedClient red;

    @TempDir
    Path tempDir;

    @BeforeEach
    void startServer() throws IOException, SQLException {
        database = new Database(tempDir.resolve("it01.db"));
        SqliteUserDao userDao = new SqliteUserDao(database);
        server = new GameServer(new ServerConfig(0, 60, new Random(SEED)),
                userDao, new MatchPersistence(database, new SqliteMatchDao(database, userDao)));
        port = server.start();
    }

    @AfterEach
    void stopEverything() throws SQLException {
        if (blue != null) {
            blue.close();
        }
        if (red != null) {
            red.close();
        }
        server.stop();
        database.close();
    }

    @Test
    void twoClientsPlayGameWithProtocolAndRematch() throws Exception {
        // ---- register both players ----
        blue = new ScriptedClient(port);
        red = new ScriptedClient(port);
        blue.send(new RegisterRequest("alice", "alicepw"));
        LoginResponse blueLogin = blue.expectLoginOk();
        red.send(new RegisterRequest("bob", "bobpw"));
        LoginResponse redLogin = red.expectLoginOk();
        assertNotNull(blueLogin.reconnectToken());
        assertNotNull(redLogin.reconnectToken());
        assertEquals(1000, blueLogin.profile().elo());

        // ---- lobby: create, list, join ----
        blue.send(new CreateMatchRequest());
        String roomCode = blue.expect(MatchCreated.class).roomCode();
        assertEquals(5, roomCode.length());

        red.send(new ListMatchesRequest());
        ListMatchesResponse list = red.expect(ListMatchesResponse.class);
        assertEquals(1, list.openMatches().size());
        assertEquals(roomCode, list.openMatches().get(0).roomCode());
        assertEquals("alice", list.openMatches().get(0).hostUsername());

        red.send(new JoinMatchRequest(roomCode));
        MatchSync blueStart = blue.expect(MatchSync.class);
        MatchSync redStart = red.expect(MatchSync.class);
        var ready = ScriptedClient.keepOpening(blue, red, blueStart, redStart);
        blueStart = ready[0]; redStart = ready[1];
        assertEquals(PlayerColor.BLUE, blueStart.view().viewer());
        assertEquals(PlayerColor.RED, redStart.view().viewer());
        assertEquals(blueStart.matchId(), redStart.matchId());

        if (blueStart.view().turn() == PlayerColor.BLUE) {
            List<GameAction.MovePiece> legalMoves = Targeting.legalMoves(blueStart.view());
            assertFalse(legalMoves.isEmpty());
            GameAction.MovePiece firstMove = legalMoves.get(0);

            blue.send(new ActionRequest(blueStart.matchId(), 2L, blueStart.view().revision(), firstMove));
            GameUpdate blueUpdate = blue.expect(GameUpdate.class);
            ActionReply blueReply = blue.expect(ActionReply.class);
            GameUpdate redUpdate = red.expect(GameUpdate.class);

            assertTrue(blueReply.receipt().accepted());
            assertEquals(3L, blueReply.receipt().revision());

            blue.send(new ActionRequest(blueStart.matchId(), 3L, blueReply.current().view().revision(),
                    new GameAction.EndTurn()));
            GameUpdate blueEndUpdate = blue.expect(GameUpdate.class);
            ActionReply blueEndReply = blue.expect(ActionReply.class);
            GameUpdate redEndUpdate = red.expect(GameUpdate.class);

            assertTrue(blueEndReply.receipt().accepted());
            assertEquals(PlayerColor.RED, blueEndReply.current().view().turn());

            red.send(new ActionRequest(redStart.matchId(), 2L, redEndUpdate.current().view().revision(),
                    new GameAction.Resign()));
            GameUpdate redResignUpdate = red.expect(GameUpdate.class);
            ActionReply redResignReply = red.expect(ActionReply.class);
            GameUpdate blueResignUpdate = blue.expect(GameUpdate.class);

            assertTrue(redResignReply.receipt().accepted());
            assertEquals(PlayerColor.BLUE, redResignReply.current().view().outcome().winner());
            assertEquals(EndReason.FORFEIT, redResignReply.current().view().outcome().reason());
        } else {
            List<GameAction.MovePiece> legalMoves = Targeting.legalMoves(redStart.view());
            assertFalse(legalMoves.isEmpty());
            GameAction.MovePiece firstMove = legalMoves.get(0);

            red.send(new ActionRequest(redStart.matchId(), 2L, redStart.view().revision(), firstMove));
            GameUpdate redUpdate = red.expect(GameUpdate.class);
            ActionReply redReply = red.expect(ActionReply.class);
            GameUpdate blueUpdate = blue.expect(GameUpdate.class);

            assertTrue(redReply.receipt().accepted());
            assertEquals(3L, redReply.receipt().revision());

            red.send(new ActionRequest(redStart.matchId(), 3L, redReply.current().view().revision(),
                    new GameAction.EndTurn()));
            GameUpdate redEndUpdate = red.expect(GameUpdate.class);
            ActionReply redEndReply = red.expect(ActionReply.class);
            GameUpdate blueEndUpdate = blue.expect(GameUpdate.class);

            assertTrue(redEndReply.receipt().accepted());
            assertEquals(PlayerColor.BLUE, redEndReply.current().view().turn());

            red.send(new ActionRequest(redStart.matchId(), 4L, redEndReply.current().view().revision(),
                    new GameAction.Resign()));
            GameUpdate redResignUpdate = red.expect(GameUpdate.class);
            ActionReply redResignReply = red.expect(ActionReply.class);
            GameUpdate blueResignUpdate = blue.expect(GameUpdate.class);

            assertTrue(redResignReply.receipt().accepted());
            assertEquals(PlayerColor.BLUE, redResignReply.current().view().outcome().winner());
            assertEquals(EndReason.FORFEIT, redResignReply.current().view().outcome().reason());
        }

        // ---- Both receive GameOver with Elo update ----
        GameOver blueOver = blue.expect(GameOver.class);
        GameOver redOver = red.expect(GameOver.class);
        assertEquals(PlayerColor.BLUE, blueOver.winnerColor());
        assertEquals(EndReason.FORFEIT, blueOver.way());
        assertEquals(blueOver, redOver);
        Elo.Result expectedElo = Elo.update(1000, 1000, PlayerColor.BLUE);
        assertEquals(expectedElo.blue(), blueOver.blueEloAfter());
        assertEquals(expectedElo.red(), blueOver.redEloAfter());

        // ---- Leaderboard reflects the win ----
        blue.send(new LeaderboardRequest());
        LeaderboardResponse board = blue.expect(LeaderboardResponse.class);
        assertEquals("alice", board.topPlayers().get(0).username());
        assertEquals(1, board.topPlayers().get(0).wins());
        assertEquals("bob", board.topPlayers().get(1).username());
        assertEquals(1, board.topPlayers().get(1).losses());

        // ---- Rematch: offer is forwarded, second vote starts fresh game with new matchId ----
        blue.send(new RematchRequest());
        red.expect(RematchAccept.class);
        red.send(new RematchRequest());
        blue.expect(RematchAccept.class);

        MatchSync blueRematch = blue.expect(MatchSync.class);
        MatchSync redRematch = red.expect(MatchSync.class);
        assertNotEquals(blueStart.matchId(), blueRematch.matchId(), "rematch must assign new matchId");
        assertEquals(1L, blueRematch.nextSequence());
        assertEquals(1L, redRematch.nextSequence());
    }
}
