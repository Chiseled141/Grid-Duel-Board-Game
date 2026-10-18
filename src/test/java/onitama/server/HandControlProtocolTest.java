package onitama.server;

import java.nio.file.Path;
import java.util.*;
import onitama.config.RulesConfigLoader;
import onitama.core.PlayerColor;
import onitama.core.duel.*;
import onitama.db.*;
import onitama.net.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Private intel and random theft remain stable across duplicate requests and real reconnects. */
class HandControlProtocolTest {
    @TempDir Path directory;

    @ParameterizedTest @ValueSource(strings = {"spy", "steal"})
    void retriesAndReconnectPreservePrivateResultWithoutRecasting(String skill) throws Exception {
        var base = RulesConfigLoader.load(null);
        var definitions = base.skills().stream().map(s -> new SkillDefinition(s.id(), s.name(), s.category(),
                s.manaCost(), s.id().equals(skill) ? 4 : 0, s.parameters(), s.descriptionTemplate(), s.artworkPath())).toList();
        var rules = new RulesConfig(base.rulesVersion(), 0, 1, 4, 1, 5, 60, List.of(), definitions);
        try (var database = new Database(directory.resolve(skill+".db"))) {
            var server = new GameServer(new ServerConfig(0, 10, new Random(17), 90, 8, rules), new SqliteUserDao(database));
            int port = server.start();
            ScriptedClient blue = null, red = null, restored = null;
            try {
                blue = new ScriptedClient(port); red = new ScriptedClient(port);
                blue.send(new RegisterRequest("blue", "password")); var blueLogin = blue.expectLoginOk();
                red.send(new RegisterRequest("red", "password")); var redLogin = red.expectLoginOk();
                blue.send(new CreateMatchRequest()); String room = blue.expect(MatchCreated.class).roomCode();
                red.send(new JoinMatchRequest(room));
                var blueSync = blue.expect(MatchSync.class); var redSync = red.expect(MatchSync.class);
                var ready = ScriptedClient.keepOpening(blue, red, blueSync, redSync);
                boolean blueActs = ready[0].view().turn() == PlayerColor.BLUE;
                var actor = blueActs ? blue : red; var other = blueActs ? red : blue;
                var token = (blueActs ? blueLogin : redLogin).reconnectToken();
                var sync = blueActs ? ready[0] : ready[1];
                actor.send(new ActionRequest(sync.matchId(), 2, sync.view().revision(), new GameAction.SkipMovement()));
                actor.expect(GameUpdate.class); sync = actor.expect(ActionReply.class).current(); other.expect(GameUpdate.class);
                var request = new ActionRequest(sync.matchId(), 3, sync.view().revision(),
                        new GameAction.CastSkill(sync.view().ownSkillHand().get(0).instanceId(), new SkillTargets.None()));
                actor.send(request); actor.expect(GameUpdate.class); var accepted = actor.expect(ActionReply.class);
                assertTrue(accepted.receipt().accepted()); var opponent = other.expect(GameUpdate.class).current();
                assertTrue(opponent.view().revealedEnemySkills().isEmpty());
                assertEquals(skill.equals("spy") ? 1 : 0, accepted.current().view().revealedEnemySkills().size());
                assertEquals(skill.equals("steal") ? 0 : 1, accepted.current().view().opponentSkillCount());
                actor.send(request); var retry = actor.expect(ActionReply.class);
                assertEquals(accepted.current().view(), retry.current().view());
                actor.close(); other.expect(OpponentLeft.class); other.expect(MatchSync.class);
                restored = new ScriptedClient(port); restored.send(new ReconnectRequest(token));
                var reconnect = restored.expect(MatchSync.class); other.expect(MatchSync.class);
                assertEquals(accepted.current().view(), reconnect.view());
                restored.send(new ActionRequest(sync.matchId(), 4, reconnect.view().revision(), new GameAction.EndTurn()));
                var ended = restored.expect(GameUpdate.class); assertTrue(restored.expect(ActionReply.class).receipt().accepted());
                other.expect(GameUpdate.class);
                assertTrue(ended.current().view().revealedEnemySkills().isEmpty());
            } finally {
                if (restored != null) restored.close(); if (blue != null) blue.close(); if (red != null) red.close();
                server.stop();
            }
        }
    }
}
