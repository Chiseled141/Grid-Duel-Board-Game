package deckduel.server;

import deckduel.db.Database;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The headless server and its local operator tools ({@code deck-duel-server.jar}; also reachable
 * through {@code deck-duel.jar}). It never touches JavaFX, Swing or audio.
 * <ul>
 * <li>{@code server --config FILE}: the production server (docs/deployment.md); or
 * {@code server --port P --db FILE} for local development on 127.0.0.1 without TLS.</li>
 * <li>{@code ctl status|drain|resume|reload-tls|wait-safe [--socket S] [--timeout SECONDS]}: talks to a
 * running server over its local control socket.</li>
 * <li>{@code backup}, {@code restore}, {@code backup-keygen}: encrypted, consistent SQLite backups.</li>
 * <li>{@code probe --host H --port P [--trust-cert ca.pem] [--connect-address 127.0.0.1]}: checks TLS and protocol readiness without logging in.</li>
 * </ul>
 */
public final class ServerMain {
    private static final Logger LOG = Logger.getLogger(ServerMain.class.getName());

    private ServerMain() { }

    /** The jar's entry point. */
    public static void main(String[] args) { System.exit(run(args)); }

    /** Runs a command and returns its exit code (0 success, 1 failure, 2 usage, 3 not safe yet). */
    public static int run(String[] args) {
        if (args.length == 0) { usage(); return 2; }
        try {
            String command = args[0];
            if (command.equals("ctl")) return ControlClient.run(java.util.Arrays.copyOfRange(args, 1, args.length));
            Map<String, String> options = options(args);
            return switch (command) {
                case "server" -> server(options);
                case "backup" -> { System.out.println(Backups.backup(Path.of(required(options, "database")), Path.of(required(options, "out-dir")),
                        Path.of(required(options, "key-file")), Integer.parseInt(options.getOrDefault("keep", "14")))); yield 0; }
                case "restore" -> { Backups.restore(Path.of(required(options, "backup")), Path.of(required(options, "key-file")),
                        Path.of(required(options, "database")), Boolean.parseBoolean(options.getOrDefault("replace", "false"))); yield 0; }
                case "backup-keygen" -> { Backups.keygen(Path.of(required(options, "key-file"))); yield 0; }
                case "probe" -> ReadinessProbe.run(required(options, "host"), Integer.parseInt(required(options, "port")),
                        options.containsKey("trust-cert") ? Path.of(options.get("trust-cert")) : null,
                        Boolean.parseBoolean(options.getOrDefault("tls", "true")), options.get("connect-address"));
                default -> { usage(); yield 2; }
            };
        } catch (Exception failure) {
            LOG.log(Level.SEVERE, args[0] + " failed: " + failure.getMessage(), failure);
            return 1;
        }
    }

    private static int server(Map<String, String> options) throws Exception {
        ServerSettings settings;
        if (options.containsKey("config")) {
            settings = ServerSettings.load(Path.of(options.get("config")));
        } else {
            // Local development: this computer only, plain, no control socket.
            ServerConfig config = ServerConfig.defaults(Integer.parseInt(options.getOrDefault("port", "5555")));
            settings = new ServerSettings(config, Path.of(options.getOrDefault("db", "data/deck-duel.db")), null, null, null);
        }
        try (Database database = new Database(settings.database())) {
            GameServer server = GameServer.recording(database, settings.config());
            Thread shutdown = new Thread(() -> {
                // systemd stop or Ctrl+C: whatever is still in progress ends here. Printed directly,
                // because java.util.logging may already be shut down by its own hook.
                System.err.println("Deck Duel server stopping: " + server.status().describe());
                server.stop();
            }, "deckduel-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdown);
            ControlServer control = null;
            try {
                int port = server.start();
                if (settings.controlSocket() != null) control = ControlServer.start(settings.controlSocket(), server);
                LOG.info("Deck Duel server ready on port " + port + " (version " + version() + ", protocol " + deckduel.net.Message.PROTOCOL_VERSION
                        + (settings.config().minimumClientVersion() == null ? "" : ", minimum client " + settings.config().minimumClientVersion()) + ")");
                server.awaitShutdown();
            } finally {
                if (control != null) control.close();
                server.stop();
                try { Runtime.getRuntime().removeShutdownHook(shutdown); }
                catch (IllegalStateException running) { /* Shutdown is already under way. */ }
            }
        }
        return 0;
    }

    /** The release version stamped into the jar by the build. */
    static String version() {
        try (var in = ServerMain.class.getResourceAsStream("/deckduel/version.properties")) {
            var properties = new java.util.Properties();
            if (in != null) properties.load(in);
            return properties.getProperty("version", "unknown");
        } catch (java.io.IOException unreadable) {
            return "unknown";
        }
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 1; i < args.length; i += 2) {
            if (!args[i].startsWith("--") || i + 1 >= args.length) throw new IllegalArgumentException("Expected --option value");
            options.put(args[i].substring(2), args[i + 1]);
        }
        return options;
    }

    private static String required(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null) throw new IllegalArgumentException("Missing --" + name);
        return value;
    }

    private static void usage() {
        System.err.println("Usage: server --config FILE | server --port P --db FILE"
                + " | ctl status|drain|resume|reload-tls|wait-safe [--socket S] [--timeout SECONDS]"
                + " | backup --database DB --out-dir DIR --key-file KEY [--keep N]"
                + " | restore --backup FILE --key-file KEY --database DB [--replace true]"
                + " | backup-keygen --key-file KEY"
                + " | probe --host H --port P [--trust-cert ca.pem] [--connect-address 127.0.0.1] [--tls false (127.0.0.1 only)]");
    }
}
