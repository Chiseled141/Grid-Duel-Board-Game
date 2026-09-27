package onitama.server;

import onitama.GameStateAssert;
import onitama.core.CardDeck;
import onitama.core.Elo;
import onitama.core.GameState;
import onitama.core.Move;
import onitama.core.Piece;
import onitama.core.PlayerColor;
import onitama.core.RulesEngine;
import onitama.core.Square;
import onitama.core.WinCondition;
import onitama.db.Database;
import onitama.db.SqliteMatchDao;
import onitama.db.SqliteUserDao;
import onitama.net.CreateMatchRequest;
import onitama.net.GameOver;
import onitama.net.JoinMatchRequest;
import onitama.net.LeaderboardRequest;
import onitama.net.LeaderboardResponse;
import onitama.net.ListMatchesRequest;
import onitama.net.ListMatchesResponse;
import onitama.net.LoginResponse;
import onitama.net.MatchCreated;
import onitama.net.MatchStart;
import onitama.net.MoveApplied;
import onitama.net.MoveRejected;
import onitama.net.MoveRequest;
import onitama.net.PassTurn;
import onitama.net.RegisterRequest;
import onitama.net.RematchAccept;
import onitama.net.RematchRequest;
import onitama.replay.ReplayFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IT01 — two headless socket clients register, meet through the lobby and
 * play a full scripted game. The server's deal random is seeded, so the test
 * predicts the entire game with the local rules engine first and then asserts
 * the server produces byte-for-byte the same half-move sequence for both
 * players, ending in the predicted STONE win.
 */
class ServerClientIntegrationTest {

    /** Seed chosen so the scripted policy below ends in a quick STONE win. */
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
        Path replayDir = tempDir.resolve("replays");
        server = new GameServer(new ServerConfig(0, 60, new Random(SEED), replayDir),
                userDao, new MatchPersistence(database, new SqliteMatchDao(database, userDao),
                        replayDir));
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
    void twoClientsPlayAFullScriptedGameToAStoneWin() throws Exception {
        // ---- predict the whole game locally with the same seeded deal ----
        GameState initial = GameState.newGame(CardDeck.deal(new Random(SEED)));
        String initialTransitId = initial.transit().id();
        GameState sim = GameState.newGame(CardDeck.deal(new Random(SEED)));
        List<ScriptEntry> script = new ArrayList<>();
        List<String> boardAfterEachStep = new ArrayList<>();
        while (sim.isOngoing()) {
            if (RulesEngine.mustPass(sim)) {
                String discard = sim.hand(sim.turn()).get(0).id();
                script.add(new ScriptEntry(discard, null, null));
                RulesEngine.pass(sim, discard);
            } else {
                Move move = chooseMove(sim);
                script.add(new ScriptEntry(move.cardId(), move.from(), move.to()));
                RulesEngine.apply(sim, move);
            }
            boardAfterEachStep.add(sim.board().toString());
        }
        assertEquals(WinCondition.STONE, sim.way(),
                "seed/policy must produce a STONE win (adjust SEED if the deal changes)");
        assertTrue(sim.moveNumber() < 150, "scripted game should end quickly");

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
        MatchStart blueStart = blue.expect(MatchStart.class);
        MatchStart redStart = red.expect(MatchStart.class);
        assertEquals(PlayerColor.BLUE, blueStart.yourColor());
        assertEquals(PlayerColor.RED, redStart.yourColor());
        assertEquals("bob", blueStart.opponentUsername());
        assertEquals("alice", redStart.opponentUsername());
        GameStateAssert.assertSameGame(initial, blueStart.initialState());
        GameStateAssert.assertSameGame(initial, redStart.initialState());

        // ---- protocol violations are rejected without changing the state ----
        ScriptedClient firstClient = initial.turn() == PlayerColor.BLUE ? blue : red;
        ScriptedClient secondClient = initial.turn() == PlayerColor.BLUE ? red : blue;
        firstClient.send(new MoveRequest("unicorn", new Square(2, 2), new Square(2, 3)));
        assertEquals("card not in hand of " + initial.turn() + ": unicorn",
                firstClient.expect(MoveRejected.class).reason());
        secondClient.send(new MoveRequest("tiger", new Square(2, 2), new Square(2, 3)));
        assertEquals("it is not your turn", secondClient.expect(MoveRejected.class).reason());

        // ---- play the script; both clients must see the identical sequence ----
        PlayerColor mover = initial.turn();
        for (int i = 0; i < script.size(); i++) {
            ScriptedClient moverClient = mover == PlayerColor.BLUE ? blue : red;
            ScriptedClient otherClient = mover == PlayerColor.BLUE ? red : blue;
            ScriptEntry entry = script.get(i);
            if (entry.isPass()) {
                moverClient.send(new PassTurn(entry.cardId()));
            } else {
                moverClient.send(new MoveRequest(entry.cardId(), entry.from(), entry.to()));
            }
            MoveApplied moverView = moverClient.expect(MoveApplied.class);
            MoveApplied otherView = otherClient.expect(MoveApplied.class);

            assertEquals(i + 1, moverView.state().moveNumber());
            assertEquals(mover.opponent(), moverView.state().turn());
            assertEquals(boardAfterEachStep.get(i), moverView.state().board().toString());
            assertEquals(moverView.state().board().toString(), otherView.state().board().toString());
            assertEquals(moverView.state().moveNumber(), otherView.state().moveNumber());
            assertEquals(moverView.lastMove(), otherView.lastMove());
            mover = mover.opponent();
        }

        // ---- game over, identical for both, with the expected Elo update ----
        GameOver blueOver = blue.expect(GameOver.class);
        GameOver redOver = red.expect(GameOver.class);
        assertEquals(sim.winner(), blueOver.winnerColor());
        assertEquals(WinCondition.STONE, blueOver.way());
        assertEquals(blueOver, redOver);
        Elo.Result expectedElo = Elo.update(1000, 1000, sim.winner());
        assertEquals(expectedElo.blue(), blueOver.blueEloAfter());
        assertEquals(expectedElo.red(), blueOver.redEloAfter());

        // ---- leaderboard reflects the recorded win ----
        blue.send(new LeaderboardRequest());
        LeaderboardResponse board = blue.expect(LeaderboardResponse.class);
        String winnerName = sim.winner() == PlayerColor.BLUE ? "alice" : "bob";
        String loserName = sim.winner() == PlayerColor.BLUE ? "bob" : "alice";
        assertEquals(winnerName, board.topPlayers().get(0).username());
        assertEquals(1, board.topPlayers().get(0).wins());
        assertEquals(loserName, board.topPlayers().get(1).username());
        assertEquals(1, board.topPlayers().get(1).losses());

        // ---- the server persisted a valid replay file ----
        try (var replayFiles = Files.list(tempDir.resolve("replays"))) {
            Path replayFile = replayFiles.findFirst().orElseThrow();
            ReplayFile.Replay replay = ReplayFile.read(replayFile);
            assertEquals(sim.moveNumber(), replay.moves().size());
            assertEquals("alice", replay.blueUsername());
            assertEquals("bob", replay.redUsername());
            assertEquals(initialTransitId, replay.initialState().transit().id());
        }

        // ---- rematch: offer is forwarded, second vote starts a fresh game ----
        blue.send(new RematchRequest());
        red.expect(RematchAccept.class);
        red.send(new RematchRequest());
        blue.expect(RematchAccept.class);
        blue.expect(MatchStart.class);
        red.expect(MatchStart.class);
    }

    /** One scripted half-move: a pass has null squares. */
    private record ScriptEntry(String cardId, Square from, Square to) {
        boolean isPass() {
            return from == null;
        }
    }

    /**
     * Deterministic policy: capture the enemy Master if possible, else any
     * capture, else the move ending closest to the enemy Master (master
     * hunting), else the first legal move. Identical logic runs in the local
     * simulation and would run in a client, guaranteeing the same script.
     */
    private static Move chooseMove(GameState state) {
        List<Move> legal = RulesEngine.legalMoves(state);
        Square enemyMaster = findEnemyMaster(state);
        Move best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Move move : legal) {
            int score;
            if (isMasterCapture(state, move)) {
                score = 1_000_000;
            } else if (state.board().pieceAt(move.to()) != null) {
                score = 1_000;
            } else {
                score = -distance(move.to(), enemyMaster);
            }
            if (score > bestScore) {
                bestScore = score;
                best = move;
            }
        }
        return best;
    }

    private static boolean isMasterCapture(GameState state, Move move) {
        Piece target = state.board().pieceAt(move.to());
        return target != null && target.master() && target.color() != state.turn();
    }

    private static Square findEnemyMaster(GameState state) {
        for (Square square : state.board().occupiedBy(state.turn().opponent())) {
            if (state.board().pieceAt(square).master()) {
                return square;
            }
        }
        return new Square(2, 2);
    }

    private static int distance(Square a, Square b) {
        return Math.max(Math.abs(a.x() - b.x()), Math.abs(a.y() - b.y()));
    }
}
