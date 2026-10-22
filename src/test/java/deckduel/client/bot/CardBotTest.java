package deckduel.client.bot;

import static org.junit.jupiter.api.Assertions.*;

import deckduel.core.card.MatchPhase;
import deckduel.db.Database;
import deckduel.db.SqliteUserDao;
import deckduel.server.GameServer;
import deckduel.server.ServerConfig;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CardBotTest {
    @TempDir Path temporary;

    @Test void aHostAndJoinerPlayARoomMatchAndARematchThroughTheRealProtocol() throws Exception {
        try (var database = new Database(temporary.resolve("bots.db"))) {
            var server = new GameServer(new ServerConfig(0, 90, 8, 60, 0), new SqliteUserDao(database));
            int port = server.start();
            var room = new CompletableFuture<String>();
            try (var a = new CardBot("bot_a", "bot-pass", 2, true, room);
                 var b = new CardBot("bot_b", "bot-pass", 2, false, room)) {
                var first = a.start("127.0.0.1", port);
                var second = b.start("127.0.0.1", port);
                var aResults = first.get(1, TimeUnit.MINUTES);
                var bResults = second.get(1, TimeUnit.MINUTES);
                assertEquals(2, aResults.size());
                for (int i = 0; i < 2; i++) {
                    var mine = aResults.get(i).view();
                    var theirs = bResults.get(i).view();
                    assertEquals(MatchPhase.FINISHED, mine.phase());
                    assertEquals(3, mine.rounds().size());
                    assertEquals(mine.you().matchTotal(), theirs.opponent().matchTotal());
                }
            } finally {
                server.stop();
            }
        }
    }
}
