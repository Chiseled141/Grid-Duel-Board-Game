package onitama.server;

import onitama.GameStateAssert;
import onitama.core.GameState;
import onitama.core.Move;
import onitama.core.RulesEngine;
import onitama.db.Database;
import onitama.db.SqliteUserDao;
import onitama.net.CreateMatchRequest;
import onitama.net.GameOver;
import onitama.net.JoinMatchRequest;
import onitama.net.LoginResponse;
import onitama.net.MatchCreated;
import onitama.net.MatchStart;
import onitama.net.MoveApplied;
import onitama.net.MoveRequest;
import onitama.net.OpponentLeft;
import onitama.net.ReconnectRequest;
import onitama.core.WinCondition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        server = new GameServer(new ServerConfig(0, 1, new Random(7), null),
                new SqliteUserDao(database));
        int port = server.start();

        // Register, meet in a lobby and play three half-moves.
        host = new ScriptedClient(port);
        guest = new ScriptedClient(port);
        host.send(new onitama.net.RegisterRequest("carol", "carolpw"));
        LoginResponse hostLogin = host.expectLoginOk();
        guest.send(new onitama.net.RegisterRequest("dave", "davepw"));
        guest.expectLoginOk();

        host.send(new CreateMatchRequest());
        String roomCode = host.expect(MatchCreated.class).roomCode();
        guest.send(new JoinMatchRequest(roomCode));
        MatchStart hostStart = host.expect(MatchStart.class);
        guest.expect(MatchStart.class);

        GameState sim = hostStart.initialState();
        for (int i = 0; i < 3 && sim.isOngoing(); i++) {
            ScriptedClient moverClient = sim.turn() == hostStart.yourColor() ? host : guest;
            Move move = RulesEngine.legalMoves(sim).get(0);
            moverClient.send(new MoveRequest(move.cardId(), move.from(), move.to()));
            RulesEngine.apply(sim, move);
            moverClient.expect(MoveApplied.class);
            (moverClient == host ? guest : host).expect(MoveApplied.class);
        }

        // Host drops: guest is told about the grace period.
        host.close();
        OpponentLeft left = guest.expect(OpponentLeft.class);
        assertEquals(1, left.graceSeconds());

        // Host reconnects within the grace period and receives the exact state.
        ScriptedClient restored = new ScriptedClient(port);
        restored.send(new ReconnectRequest(hostLogin.reconnectToken()));
        MoveApplied restoredState = restored.expect(MoveApplied.class);
        GameStateAssert.assertSameGame(sim, restoredState.state());
        host = restored; // from now on the new connection plays host's side

        // Host drops again and does not come back: guest wins by forfeit.
        host.close();
        guest.expect(OpponentLeft.class);
        GameOver over = guest.expect(GameOver.class);
        assertEquals(hostStart.yourColor().opponent(), over.winnerColor());
        assertEquals(WinCondition.FORFEIT, over.way());
    }

    @Test
    void silentClientIsDroppedAfterTheSilenceLimit() throws Exception {
        // 1-second heartbeat silence limit; the client read timeout is 10 s,
        // so the server's drop must arrive well before the test gives up.
        database = new Database(tempDir.resolve("ut-silence.db"));
        server = new GameServer(new ServerConfig(0, 60, new Random(3), null, 1),
                new SqliteUserDao(database));
        int port = server.start();

        ScriptedClient idle = new ScriptedClient(port);
        idle.send(new onitama.net.RegisterRequest("idle", "idlepw"));
        idle.expectLoginOk();
        idle.expectClosed();
    }
}
