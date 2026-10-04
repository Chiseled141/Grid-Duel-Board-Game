package onitama.server;

import onitama.client.bot.BotHarness;
import onitama.db.Database;
import onitama.db.SqliteMatchDao;
import onitama.db.SqliteUserDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LT01 — smoke-level load test: 8 bots form 4 pairs and play 2 games each
 * (4 concurrent games at any time, 8 in total) against a real server with a
 * temporary database, without a single failed bot or rejected move.
 */
class BotSmokeTest {

    private GameServer server;
    private Database database;
    private int port;

    @TempDir
    Path tempDir;

    @BeforeEach
    void startServer() throws Exception {
        database = new Database(tempDir.resolve("lt01.db"));
        SqliteUserDao userDao = new SqliteUserDao(database);
        server = new GameServer(new ServerConfig(0, 60, new Random(99)),
                userDao, new MatchPersistence(database,
                        new SqliteMatchDao(database, userDao)));
        port = server.start();
    }

    @AfterEach
    void stopEverything() throws SQLException {
        server.stop();
        database.close();
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    void eightBotsPlayConcurrentGamesWithoutErrors() throws Exception {
        BotHarness.Summary summary = new BotHarness(tempDir.resolve("results"))
                .run(8, 2, "127.0.0.1", port, 7L);

        assertEquals(8, summary.gamesCompleted(), "all requested games must complete");
        assertEquals(0, summary.failedBots(), "no bot may fail");
        assertTrue(summary.movesMeasured() > 0, "round-trips must be measured");
        assertTrue(summary.gamesPerMinute() > 0, "throughput must be positive");
        assertEquals(2, Files.list(tempDir.resolve("results")).count(),
                "latencies.csv and summary.csv must exist");
    }
}
