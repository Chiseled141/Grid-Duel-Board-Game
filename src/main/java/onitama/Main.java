package onitama;

import onitama.config.RulesConfigLoader;
import onitama.core.PlayerColor;
import onitama.core.duel.CardInstance;
import onitama.core.duel.GameAction;
import onitama.core.duel.GameState;
import onitama.core.duel.PlayerView;
import onitama.core.duel.RulesConfig;
import onitama.core.duel.RulesEngine;
import onitama.core.duel.SkillDefinition;
import onitama.core.duel.Targeting;
import onitama.db.Database;
import onitama.db.MatchDao;
import onitama.db.SqliteMatchDao;
import onitama.db.SqliteUserDao;
import onitama.server.GameServer;
import onitama.server.MatchPersistence;
import onitama.server.ServerConfig;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.SwingUtilities;

/**
 * Command-line entry point (Factory Method): builds the right application
 * from the first argument.
 *
 * <pre>
 *   java -jar onitama.jar demo   [--seed n] [--config file] # print a board and legal moves
 *   java -jar onitama.jar server --port 5555 [--config file]# headless server
 *   java -jar onitama.jar client [--host &lt;ip&gt; --port 5555] # Swing client
 *   java -jar onitama.jar bots   --host 127.0.0.1 --port 5555 --bots 8 --games 4
 * </pre>
 */
public final class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    private Main() {
    }

    /** Program entry; dispatches on args[0]. */
    public static void main(String[] args) {
        if (args.length == 0) {
            usage();
            System.exit(1);
        }
        Map<String, String> options;
        try {
            options = parseOptions(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            usage();
            System.exit(1);
            return;
        }
        switch (args[0]) {
            case "demo" -> runDemo(options);
            case "server" -> runServer(options);
            case "client" -> launchClient(options);
            case "bots" -> runBots(options);
            default -> {
                usage();
                System.exit(1);
            }
        }
    }

    private static void usage() {
        System.err.println("usage: onitama demo [--seed n] [--config <path>]");
        System.err.println("       onitama server --port <port> [--config <path>] [--db <path>]");
        System.err.println("       onitama client [--host <host>] [--port <port>]");
        System.err.println("       onitama bots --host <host> --port <port> --bots <n> --games <g> [--seed n]");
    }

    /**
     * Parses "--key value" pairs after the mode argument into a map. Throws
     * {@link IllegalArgumentException} on a malformed option — never calls
     * {@code System.exit}, so tests and embedded callers stay in control; the
     * CLI entry point in {@link #main} turns it into usage + exit 1.
     */
    private static Map<String, String> parseOptions(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("missing value for " + args[i]);
                }
                options.put(args[i].substring(2), args[i + 1]);
                i++;
            }
        }
        return options;
    }

    // ------------------------------------------------------------------
    // demo
    // ------------------------------------------------------------------

    /** Prints the starting position, the dealt cards and all legal first moves. */
    private static void runDemo(Map<String, String> options) {
        long seed = Long.parseLong(options.getOrDefault("seed", "42"));
        Path configOverride = options.containsKey("config") ? Path.of(options.get("config")) : null;
        RulesConfig rules = RulesConfigLoader.load(configOverride);
        GameState state = RulesEngine.newOpeningGame(rules, seed);
        for (PlayerColor color : PlayerColor.values()) {
            state = ((onitama.core.duel.ActionResult.Accepted) RulesEngine.apply(state, color,
                    new GameAction.ConfirmOpening(List.of()))).nextState();
        }
        System.out.println("Grid Duel 7x7 Demo (Rules version: " + rules.rulesVersion() + ")");
        System.out.println("First to move: " + state.turn());
        System.out.println("Blue skills:   " + skillNames(rules, state.blueSkillHand()));
        System.out.println("Red skills:    " + skillNames(rules, state.redSkillHand()));
        System.out.println("Mana: Blue=" + state.blueMana() + ", Red=" + state.redMana());
        System.out.println();
        System.out.print(state.board());

        PlayerView moverView = RulesEngine.viewFor(state, state.turn());
        List<GameAction.MovePiece> legal = Targeting.legalMoves(moverView);
        System.out.println(legal.size() + " legal movement moves for " + state.turn() + ":");
        for (GameAction.MovePiece move : legal) {
            LOG.info(() -> "  piece#" + move.pieceId() + " -> " + move.to());
        }
    }

    private static String skillNames(RulesConfig rules, List<CardInstance> cards) {
        return cards.stream()
                .map(card -> {
                    SkillDefinition def = rules.skill(card.definitionId());
                    String name = def != null ? def.name() : card.definitionId();
                    int cost = def != null ? def.manaCost() : 0;
                    return name + " (" + cost + "m) [" + card.instanceId() + "]";
                })
                .collect(java.util.stream.Collectors.joining(", "));
    }

    // ------------------------------------------------------------------
    // server
    // ------------------------------------------------------------------

    private static void runServer(Map<String, String> options) {
        int port = Integer.parseInt(options.getOrDefault("port", "5555"));
        Path dbFile = Path.of(options.getOrDefault("db", "data/onitama.db"));
        Path configOverride = options.containsKey("config") ? Path.of(options.get("config")) : null;
        RulesConfig rules = RulesConfigLoader.load(configOverride);
        installFileLogging();
        try (Database database = new Database(dbFile)) {
            SqliteUserDao userDao = new SqliteUserDao(database);
            MatchDao matchDao = new SqliteMatchDao(database, userDao);
            GameServer server = new GameServer(ServerConfig.defaults(port, rules), userDao,
                    new MatchPersistence(database, matchDao));

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                server.stop();
                try {
                    database.close();
                } catch (SQLException e) {
                    LOG.log(Level.WARNING, "closing database failed", e);
                }
            }, "onitama-shutdown"));
            server.start();
            server.awaitShutdown();
        } catch (Exception e) {
            System.err.println("server failed: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Adds a file log handler (logs/onitama-server.log, 5 MB x 3 files) next
     * to the console handler the JUL default already provides. Truncates on
     * startup (append=false) so each server run starts with a fresh log;
     * rotation still keeps the last 15 MB within a run.
     */
    private static void installFileLogging() {
        try {
            java.nio.file.Files.createDirectories(Path.of("logs"));
            java.util.logging.FileHandler fileHandler = new java.util.logging.FileHandler(
                    "logs/onitama-server.log", 5_000_000, 3, false);
            fileHandler.setFormatter(new java.util.logging.SimpleFormatter());
            Logger rootLogger = Logger.getLogger("");
            rootLogger.addHandler(fileHandler);
        } catch (IOException e) {
            System.err.println("file logging unavailable: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // client
    // ------------------------------------------------------------------

    /** Launches the Swing client on the EDT; CLI host/port override settings. */
    private static void launchClient(Map<String, String> options) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("client mode needs a graphical environment");
            System.exit(1);
        }
        String host = options.get("host");
        Integer port = options.containsKey("port")
                ? Integer.valueOf(options.get("port")) : null;
        SwingUtilities.invokeLater(() -> new onitama.client.ui.ClientFrame(host, port).setVisible(true));
    }

    // ------------------------------------------------------------------
    // bots (load-test harness)
    // ------------------------------------------------------------------

    /** Runs the headless bot fleet and writes CSV results; exit 1 on failures. */
    private static void runBots(Map<String, String> options) {
        int bots = Integer.parseInt(options.getOrDefault("bots", "2"));
        int games = Integer.parseInt(options.getOrDefault("games", "1"));
        String host = options.getOrDefault("host", "127.0.0.1");
        int port = Integer.parseInt(options.getOrDefault("port", "5555"));
        Long seed = options.containsKey("seed") ? Long.valueOf(options.get("seed")) : null;
        if (bots % 2 != 0) {
            System.err.println("bots must be even (they pair up)");
            System.exit(1);
        }
        try {
            onitama.client.bot.BotHarness.Summary summary = new onitama.client.bot.BotHarness(
                    Path.of(options.getOrDefault("results", "results")))
                    .run(bots, games, host, port, seed);
            System.out.println("bots=" + summary.botCount()
                    + " gamesCompleted=" + summary.gamesCompleted() + "/" + summary.gamesRequested()
                    + " failedBots=" + summary.failedBots()
                    + " wallMillis=" + summary.wallMillis());
            System.out.println("games/minute=" + String.format("%.1f", summary.gamesPerMinute())
                    + " move RTT ms mean/p50/p95/max="
                    + String.format("%.1f/%.1f/%.1f/%.1f", summary.meanRttMillis(),
                            summary.p50RttMillis(), summary.p95RttMillis(), summary.maxRttMillis()));
            System.out.println("CSV written to " + options.getOrDefault("results", "results") + "/");
            System.exit(summary.failedBots() == 0
                    && summary.gamesCompleted() == summary.gamesRequested() ? 0 : 1);
        } catch (Exception e) {
            System.err.println("bot run failed: " + e.getMessage());
            System.exit(1);
        }
    }
}
