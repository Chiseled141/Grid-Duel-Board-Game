package deckduel.server;

import java.nio.file.Path;
import java.util.Random;
import deckduel.config.RulesConfigLoader;
import deckduel.core.PlayerColor;
import deckduel.core.duel.*;
import deckduel.db.*;
import deckduel.net.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Real-socket objective rewards survive retries and reconnect, then persist a SCORE win. */
class ObjectiveProtocolTest {
    @TempDir Path directory;

    @Test void centerRewardsAreOnceOnlyAcrossRetryAndReconnectAndScoreVictoryPersists() throws Exception {
        var base = RulesConfigLoader.load(null);
        var rules = new RulesConfig(base.rulesVersion(), 0, 3, 2, 1, 5, 60,
                base.movementCards(), base.skills(), 2);
        long seed = 0;
        while (true) {
            var candidate = RulesEngine.newGame(rules, seed);
            if (candidate.turn() == PlayerColor.BLUE) break;
            seed++;
            assertTrue(seed < 1000, "find a deterministic Blue opening");
        }
        final long openingSeed = seed;
        Random deals = new Random(1) {
            private static final long serialVersionUID = 1L;
            @Override public long nextLong() { return openingSeed; }
        };
        try (var database = new Database(directory.resolve("objectives.db"))) {
            var users = new SqliteUserDao(database);
            var matches = new SqliteMatchDao(database, users);
            var server = new GameServer(new ServerConfig(0, 10, deals, 90, 8, rules), users,
                    new MatchPersistence(database, matches));
            int port = server.start();
            ScriptedClient blue = null, red = null;
            try {
                blue = new ScriptedClient(port); red = new ScriptedClient(port);
                blue.send(new RegisterRequest("centerblue", "password"));
                var login = blue.expectLoginOk();
                red.register("centerred", "password");
                blue.send(new CreateMatchRequest());
                String room = blue.expect(MatchCreated.class).roomCode();
                red.send(new JoinMatchRequest(room));
                var sync = blue.expect(MatchSync.class); var redSync = red.expect(MatchSync.class);
                sync = ScriptedClient.keepOpening(blue, red, sync, redSync)[0];
                assertEquals(14, sync.view().pieces().size());
                assertEquals(2, sync.view().rules().scoreToWin());
                var scoringSquare = sync.view().objectives().scoring().get(0);
                int mover = sync.view().pieces().stream().filter(piece -> piece.square().y() == 0
                        && piece.square().x() == scoringSquare.x()).findFirst().orElseThrow().piece().id();
                var layout = sync.view().objectives();
                long blueSequence = 2, redSequence = 2;
                for (int rank = 1; rank <= 3; rank++) {
                    sync = act(blue, red, sync, blueSequence++, new GameAction.MovePiece(mover,
                            new deckduel.core.Square(scoringSquare.x(), rank)));
                    if (rank < 3) {
                        sync = act(blue, red, sync, blueSequence++, new GameAction.EndTurn());
                        sync = act(red, blue, sync, redSequence++, new GameAction.EndTurn());
                    }
                }
                assertEquals(0, sync.view().blueScore(), "landing alone earns no point");
                sync = act(blue, red, sync, blueSequence++, new GameAction.EndTurn());
                var request = new ActionRequest(sync.matchId(), redSequence++, sync.view().revision(), new GameAction.EndTurn());
                red.send(request);
                var firstReward = red.expect(GameUpdate.class);
                var receipt = red.expect(ActionReply.class);
                blue.expect(GameUpdate.class);
                assertEquals(1, firstReward.current().view().blueScore());
                red.send(request);
                assertEquals(1, red.expect(ActionReply.class).current().view().blueScore());

                blue.close(); red.expect(OpponentLeft.class);
                assertTrue(red.expect(MatchSync.class).paused());
                blue = new ScriptedClient(port);
                blue.send(new ReconnectRequest(login.reconnectToken()));
                sync = blue.expect(MatchSync.class); red.expect(MatchSync.class);
                assertEquals(1, sync.view().blueScore(), "reconnect must not repeat the reward");
                assertEquals(layout, sync.view().objectives(), "reconnect retains the match layout");
                assertEquals(receipt.current().view().blueMana(), sync.view().blueMana());
                sync = act(blue, red, sync, blueSequence++, new GameAction.EndTurn());
                red.send(new ActionRequest(sync.matchId(), redSequence++, sync.view().revision(), new GameAction.EndTurn()));
                var win = red.expect(GameUpdate.class); red.expect(ActionReply.class);
                var blueWin = blue.expect(GameUpdate.class);
                assertEquals(2, win.current().view().blueScore());
                assertEquals(14, win.current().view().pieces().size(), "score can win before any capture");
                assertEquals(EndReason.SCORE, red.expect(GameOver.class).way());
                assertEquals(EndReason.SCORE, blue.expect(GameOver.class).way());
                assertEquals(PlayerColor.BLUE, blueWin.current().view().outcome().winner());
                assertEquals("SCORE", matches.recentMatches(1).get(0).endReason());
                assertEquals(rules.rulesVersion(), matches.recentMatches(1).get(0).rulesVersion());
                assertEquals(1, users.profileOf("centerblue").wins());
                assertEquals(1, users.profileOf("centerred").losses());
            } finally {
                if (blue != null) blue.close();
                if (red != null) red.close();
                server.stop();
            }
        }
    }

    private MatchSync act(ScriptedClient actor, ScriptedClient other, MatchSync current,
                          long sequence, GameAction action) {
        actor.send(new ActionRequest(current.matchId(), sequence, current.view().revision(), action));
        actor.expect(GameUpdate.class);
        var reply = actor.expect(ActionReply.class);
        other.expect(GameUpdate.class);
        assertTrue(reply.receipt().accepted());
        return reply.current();
    }
}
