package deckduel;

import deckduel.cards.CatalogLoader;
import deckduel.client.bot.CardBot;
import deckduel.core.card.Catalog;
import deckduel.core.card.PlayerView;
import deckduel.core.card.RoundResult;
import deckduel.db.Database;
import deckduel.db.SqliteMatchDao;
import deckduel.db.SqliteUserDao;
import deckduel.net.MatchUpdate;
import deckduel.server.GameServer;
import deckduel.server.MatchPersistence;
import deckduel.server.ServerConfig;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;

/** Starts the server, the Swing client, a headless bot demo, or load-test bots. */
public final class Main {
    private static final Logger LOG = Logger.getLogger(Main.class.getName());
    private Main() { }

    /** Dispatches client, server, demo and bots. */
    public static void main(String[] args) {
        if (args.length == 0) { usage(); return; }
        try {
            Map<String, String> options = parseOptions(args);
            switch (args[0]) {
                case "server" -> server(options);
                case "client" -> client(options);
                case "demo" -> demo(options);
                case "bots" -> System.exit(bots(options) ? 0 : 1);
                default -> { usage(); System.exit(2); }
            }
        } catch (Exception exception) {
            LOG.log(Level.SEVERE, "Unable to start Deck Duel", exception);
            System.exit(1);
        }
    }

    private static Map<String, String> parseOptions(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 1; i < args.length; i += 2) {
            if (!args[i].startsWith("--") || i + 1 >= args.length)
                throw new IllegalArgumentException("Expected --option value");
            options.put(args[i].substring(2), args[i + 1]);
        }
        return options;
    }

    private static void server(Map<String, String> options) throws Exception {
        if (options.containsKey("config"))
            throw new IllegalArgumentException("Legacy balance configuration was removed; card content is in the bundled catalog.");
        try (var database = new Database(Path.of(options.getOrDefault("db", "data/deck-duel.db")))) {
            var server = newServer(database, ServerConfig.defaults(Integer.parseInt(options.getOrDefault("port", "5555"))));
            Thread shutdown = new Thread(server::stop, "deckduel-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdown);
            try {
                LOG.info("Deck Duel server listening on port " + server.start());
                server.awaitShutdown();
            } finally {
                server.stop();
                try { Runtime.getRuntime().removeShutdownHook(shutdown); }
                catch (IllegalStateException ignored) { /* Shutdown is already running. */ }
            }
        }
    }

    private static GameServer newServer(Database database, ServerConfig config) {
        var users = new SqliteUserDao(database);
        Catalog catalog = CatalogLoader.loadBundled();
        LOG.info("Card catalog " + catalog.version());
        var persistence = new MatchPersistence(database, new SqliteMatchDao(database, users));
        return new GameServer(config, users, catalog, persistence, persistence);
    }

    /** Runs two bots through a real local server and a throwaway database, then prints the results. */
    private static void demo(Map<String, String> options) throws Exception {
        int games = Integer.parseInt(options.getOrDefault("games", "1"));
        Path folder = Files.createTempDirectory("deck-duel-demo");
        try (var database = new Database(folder.resolve("demo.db"))) {
            var server = newServer(database, new ServerConfig(0, 90, 8, 60, 0));
            int port = server.start();
            try (var a = new CardBot("demo_north", "demo-pass", games);
                 var b = new CardBot("demo_south", "demo-pass", games)) {
                var first = a.start("127.0.0.1", port);
                b.start("127.0.0.1", port);
                for (MatchUpdate result : first.get(2, TimeUnit.MINUTES)) LOG.info(describe(result));
            } finally {
                server.stop();
            }
        }
    }

    /** Starts {@code --count} bots against an existing server; returns whether every bot finished. */
    private static boolean bots(Map<String, String> options) throws Exception {
        String host = options.getOrDefault("host", "127.0.0.1");
        int port = Integer.parseInt(options.getOrDefault("port", "5555"));
        int count = Integer.parseInt(options.getOrDefault("count", "2"));
        int games = Integer.parseInt(options.getOrDefault("games", "1"));
        List<CardBot> bots = new ArrayList<>();
        List<CompletableFuture<List<MatchUpdate>>> runs = new ArrayList<>();
        try {
            for (int i = 1; i <= count; i++) {
                CardBot bot = new CardBot("bot_" + i, options.getOrDefault("password", "bot-pass"), games);
                bots.add(bot);
                runs.add(bot.start(host, port));
            }
            boolean ok = true;
            for (int i = 0; i < runs.size(); i++) {
                try { runs.get(i).get(10, TimeUnit.MINUTES).forEach(result -> LOG.info(describe(result))); }
                catch (Exception failure) { ok = false; LOG.log(Level.WARNING, bots.get(i).username() + " did not finish", failure); }
            }
            return ok;
        } finally {
            bots.forEach(CardBot::close);
        }
    }

    private static String describe(MatchUpdate result) {
        PlayerView view = result.view();
        StringBuilder rounds = new StringBuilder();
        for (RoundResult round : view.rounds())
            rounds.append(" R").append(round.round()).append(' ').append(round.scoreOf(view.seat()))
                    .append('-').append(round.scoreOf(view.seat().other()));
        String verdict = view.outcome().winner() == null ? "draw" : view.outcome().winner() == view.seat() ? "won" : "lost";
        return result.yourName() + " " + verdict + " vs " + result.opponentName() + ": " + view.you().matchTotal()
                + "-" + view.opponent().matchTotal() + " (" + rounds.toString().trim() + ")";
    }

    private static void client(Map<String, String> options) {
        if (GraphicsEnvironment.isHeadless()) throw new IllegalStateException("Client needs a graphical environment");
        String host = options.get("host");
        Integer port = options.containsKey("port") ? Integer.valueOf(options.get("port")) : null;
        SwingUtilities.invokeLater(() -> new deckduel.client.ui.ClientFrame(host, port).setVisible(true));
    }

    private static void usage() {
        LOG.info("Usage: client [--host 127.0.0.1 --port 5555] | server [--port 5555 --db data/deck-duel.db]"
                + " | demo [--games 1] | bots [--host 127.0.0.1 --port 5555 --count 2 --games 1 --password bot-pass]");
    }
}
