package onitama.server;

import onitama.core.PlayerColor;
import onitama.core.duel.EndReason;
import onitama.core.duel.GameAction;
import onitama.core.duel.Targeting;
import onitama.db.Database;
import onitama.db.SqliteUserDao;
import onitama.net.ActionReply;
import onitama.net.ActionRequest;
import onitama.net.CreateMatchRequest;
import onitama.net.GameOver;
import onitama.net.GameUpdate;
import onitama.net.JoinMatchRequest;
import onitama.net.LoginResponse;
import onitama.net.MatchCreated;
import onitama.net.MatchSync;
import onitama.net.OpponentLeft;
import onitama.net.ReconnectRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IT02 — resilience: a player dropping mid-match triggers {@code OpponentLeft}
 * for the opponent; reconnecting with the token restores the exact game
 * state; staying away past the grace period forfeits the match. The server
 * runs with a 1-second grace period so the test is fast.
 */
class DisconnectReconnectTest {

    private GameServer server;
    private Database database;
    private ScriptedClient host;
    private ScriptedClient guest;

    @TempDir
    Path tempDir;

    @AfterEach
    void stopEverything() throws SQLException {
        if (host != null) {
            host.close();
        }
        if (guest != null) {
            guest.close();
        }
        if (server != null) {
            server.stop();
        }
        if (database != null) {
            database.close();
        }
    }

    @Test
    void reconnectRestoresStateAndGraceExpiryForfeits() throws Exception {
        database = new Database(tempDir.resolve("it02.db"));
        server = new GameServer(new ServerConfig(0, 1, new Random(7)),
                new SqliteUserDao(database));
        int port = server.start();

        // Register, meet in a lobby and start the match.
        host = new ScriptedClient(port);
        guest = new ScriptedClient(port);
        host.send(new onitama.net.RegisterRequest("carol", "carolpw"));
        LoginResponse hostLogin = host.expectLoginOk();
        guest.send(new onitama.net.RegisterRequest("dave", "davepw"));
        guest.expectLoginOk();

        host.send(new CreateMatchRequest());
        String roomCode = host.expect(MatchCreated.class).roomCode();
        guest.send(new JoinMatchRequest(roomCode));
        MatchSync hostSync = host.expect(MatchSync.class);
        MatchSync guestSync = guest.expect(MatchSync.class);

        assertEquals(PlayerColor.BLUE, hostSync.view().viewer());
        assertEquals(PlayerColor.RED, guestSync.view().viewer());
        assertEquals(PlayerColor.BLUE, hostSync.view().turn());

        // Blue plays a move
        List<GameAction.MovePiece> legal = Targeting.legalMoves(hostSync.view());
        assertFalse(legal.isEmpty());
        GameAction.MovePiece move = legal.get(0);
        host.send(new ActionRequest(hostSync.matchId(), 1L, hostSync.view().revision(), move));

        GameUpdate blueUpdate = host.expect(GameUpdate.class);
        ActionReply blueReply = host.expect(ActionReply.class);
        GameUpdate redUpdate = guest.expect(GameUpdate.class);

        assertTrue(blueReply.receipt().accepted());
        assertEquals(1L, blueReply.receipt().revision());

        // Host drops: guest is told about the grace period and sees paused=true.
        host.close();
        OpponentLeft left = guest.expect(OpponentLeft.class);
        assertEquals(1, left.graceSeconds());
        MatchSync guestPausedSync = guest.expect(MatchSync.class);
        assertTrue(guestPausedSync.paused());

        // Host reconnects within the grace period and receives the exact state.
        ScriptedClient restored = new ScriptedClient(port);
        restored.send(new ReconnectRequest(hostLogin.reconnectToken()));
        MatchSync restoredSync = restored.expect(MatchSync.class);
        assertEquals(hostSync.matchId(), restoredSync.matchId());
        assertEquals(blueUpdate.current().view().revision(), restoredSync.view().revision());
        assertFalse(restoredSync.paused());

        // Guest also receives unpaused sync
        MatchSync guestUnpausedSync = guest.expect(MatchSync.class);
        assertFalse(guestUnpausedSync.paused());
        host = restored;

        // Host drops again and does not come back: guest wins by forfeit.
        host.close();
        guest.expect(OpponentLeft.class);
        guest.expect(MatchSync.class); // paused sync
        GameUpdate forfeitUpdate = guest.expect(GameUpdate.class);
        assertEquals(EndReason.FORFEIT, forfeitUpdate.current().view().outcome().reason());
        assertEquals(PlayerColor.RED, forfeitUpdate.current().view().outcome().winner());

        GameOver over = guest.expect(GameOver.class);
        assertEquals(PlayerColor.RED, over.winnerColor());
        assertEquals(EndReason.FORFEIT, over.way());
    }

    @Test
    void silentClientIsDroppedAfterTheSilenceLimit() throws Exception {
        // 1-second heartbeat silence limit; the client read timeout is 10 s,
        // so the server's drop must arrive well before the test gives up.
        database = new Database(tempDir.resolve("ut-silence.db"));
        server = new GameServer(new ServerConfig(0, 60, new Random(3), 1, 32),
                new SqliteUserDao(database));
        int port = server.start();

        ScriptedClient idle = new ScriptedClient(port);
        idle.send(new onitama.net.RegisterRequest("idle", "idlepw"));
        idle.expectLoginOk();
        idle.expectClosed();
    }
}
