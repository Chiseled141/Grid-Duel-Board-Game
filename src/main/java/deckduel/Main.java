package deckduel;

import deckduel.db.Database;
import deckduel.db.SqliteUserDao;
import deckduel.server.GameServer;
import deckduel.server.ServerConfig;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;

/** Starts the account/lobby shell; gameplay routes remain unavailable until the new engine exists. */
public final class Main {
    private static final Logger LOG = Logger.getLogger(Main.class.getName());
    private Main() { }

    /** Dispatches client/server; retired demo and bots fail clearly rather than running old rules. */
    public static void main(String[] args) {
        if (args.length == 0) { usage(); return; }
        try {
            Map<String, String> options = parseOptions(args);
            switch (args[0]) {
                case "server" -> server(options);
                case "client" -> client(options);
                case "demo", "bots" -> {
                    LOG.warning("New card gameplay is not ready. The old engine and bots were removed.");
                    System.exit(2);
                }
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
            throw new IllegalArgumentException("Legacy balance configuration was removed; the shell has no game rules.");
        try (var database = new Database(Path.of(options.getOrDefault("db", "data/deck-duel.db")))) {
            var server = new GameServer(ServerConfig.defaults(Integer.parseInt(options.getOrDefault("port", "5555"))),
                    new SqliteUserDao(database));
            Thread shutdown = new Thread(server::stop, "deckduel-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdown);
            try {
                LOG.info("Account/lobby server listening on port " + server.start());
                server.awaitShutdown();
            } finally {
                server.stop();
                try { Runtime.getRuntime().removeShutdownHook(shutdown); }
                catch (IllegalStateException ignored) { /* Shutdown is already running. */ }
            }
        }
    }

    private static void client(Map<String, String> options) {
        if (GraphicsEnvironment.isHeadless()) throw new IllegalStateException("Client needs a graphical environment");
        String host = options.get("host");
        Integer port = options.containsKey("port") ? Integer.valueOf(options.get("port")) : null;
        SwingUtilities.invokeLater(() -> new deckduel.client.ui.ClientFrame(host, port).setVisible(true));
    }

    private static void usage() {
        LOG.info("Usage: client [--host 127.0.0.1 --port 5555] | server [--port 5555 --db data/deck-duel.db]");
    }
}
