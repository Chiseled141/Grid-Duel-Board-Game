package deckduel;

import deckduel.client.bot.CardBot;
import deckduel.client.startup.StartupFixture;
import deckduel.client.startup.StartupServices;
import deckduel.client.ClientSettings;
import deckduel.client.ClientTls;
import deckduel.client.startup.Endpoints;
import deckduel.client.startup.ServerRoute;
import java.io.IOException;
import javax.net.ssl.SSLContext;
import deckduel.client.update.ReleaseChannel;
import deckduel.launcher.InstallLayout;
import deckduel.core.card.PlayerView;
import deckduel.core.card.RoundResult;
import deckduel.db.Database;
import deckduel.net.MatchUpdate;
import deckduel.server.GameServer;
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

/** Starts the server, the Swing client, a headless bot demo, load-test bots, or the release tooling. */
public final class Main {
    private static final Logger LOG = Logger.getLogger(Main.class.getName());
    /** A fixture's simulated network step, slow enough to watch each startup state. */
    private static final long FIXTURE_STEP_MILLIS = 100;
    private Main() { }

    /** Dispatches client, server, demo and bots. */
    public static void main(String[] args) {
        if (args.length == 0) { usage(); return; }
        try {
            // Release tooling has sub-commands of its own.
            if (args[0].equals("release")) { System.exit(deckduel.release.ReleaseTool.run(java.util.Arrays.copyOfRange(args, 1, args.length))); }
            // The server and its operator tools (docs/README.md#deployment) share the headless server jar's entry point.
            if (java.util.Set.of("server", "ctl", "backup", "backup-keygen", "restore", "probe").contains(args[0])) System.exit(deckduel.server.ServerMain.run(args));
            Map<String, String> options = parseOptions(args);
            switch (args[0]) {
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

    /** Runs two bots through a real local server and a throwaway database, then prints the results. */
    private static void demo(Map<String, String> options) throws Exception {
        int games = Integer.parseInt(options.getOrDefault("games", "1"));
        Path folder = Files.createTempDirectory("deck-duel-demo");
        try (var database = new Database(folder.resolve("demo.db"))) {
            var server = GameServer.recording(database, new ServerConfig(0, 90, 8, 60, 0));
            int port = server.start();
            var room = new CompletableFuture<String>();
            try (var a = new CardBot("demo_north", "demo-pass", games, true, room);
                 var b = new CardBot("demo_south", "demo-pass", games, false, room)) {
                var first = a.start(new ServerRoute("127.0.0.1", port, false));
                b.start(new ServerRoute("127.0.0.1", port, false));
                for (MatchUpdate result : first.get(2, TimeUnit.MINUTES)) LOG.info(describe(result));
            } finally {
                server.stop();
            }
        }
    }

    /** Starts {@code --count} bots in host/joiner pairs against an existing server; returns whether every bot finished. */
    private static boolean bots(Map<String, String> options) throws Exception {
        ServerRoute route = Endpoints.resolve(options.getOrDefault("host", "127.0.0.1"), Integer.parseInt(options.getOrDefault("port", "5555")),
                tlsOption(options), java.util.Optional.empty(), "127.0.0.1", 5555).route();
        SSLContext trust = trust(options);
        int count = Integer.parseInt(options.getOrDefault("count", "2"));
        if (count % 2 != 0) throw new IllegalArgumentException("--count must be even: bots play in host/joiner pairs");
        int games = Integer.parseInt(options.getOrDefault("games", "1"));
        List<CardBot> bots = new ArrayList<>();
        List<CompletableFuture<List<MatchUpdate>>> runs = new ArrayList<>();
        try {
            CompletableFuture<String> room = null;
            for (int i = 1; i <= count; i++) {
                // Odd bots host a fresh room; each even bot joins the room of the bot before it.
                boolean hosting = i % 2 == 1;
                if (hosting) room = new CompletableFuture<>();
                CardBot bot = new CardBot("bot_" + i, options.getOrDefault("password", "bot-pass"), games, hosting, room, trust);
                bots.add(bot);
                runs.add(bot.start(route));
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
        Endpoints.Choice server;
        SSLContext trust;
        try {
            ClientSettings saved = ClientSettings.load();
            server = Endpoints.resolve(host, port, tlsOption(options), Endpoints.packaged(), saved.host(), saved.port());
            trust = trust(options);
        } catch (IOException | RuntimeException invalid) {
            throw new IllegalArgumentException("Cannot choose a server: " + invalid.getMessage(), invalid);
        }
        // The launcher passes --install-root; a copy started straight from a jar cannot update itself.
        InstallLayout installation = options.containsKey("install-root") ? new InstallLayout(Path.of(options.get("install-root"))) : null;
        // Only an explicit --startup-fixture replaces the real release channel with a labelled local stand-in.
        StartupServices services = options.containsKey("startup-fixture")
                ? StartupFixture.fromId(options.get("startup-fixture")).services(FIXTURE_STEP_MILLIS)
                : ReleaseChannel.services(installation, deckduel.net.Message.PROTOCOL_VERSION);
        SwingUtilities.invokeLater(() -> new deckduel.client.ui.ClientFrame(server, trust, services, installation).setVisible(true));
    }

    /** {@code --tls true|false}, or null to use TLS for every host but this computer. */
    private static Boolean tlsOption(Map<String, String> options) {
        return options.containsKey("tls") ? Boolean.valueOf(options.get("tls")) : null;
    }

    /** The Java runtime's trusted authorities, or only those in {@code --trust-cert <pem>} for local test servers. */
    private static SSLContext trust(Map<String, String> options) throws IOException {
        return options.containsKey("trust-cert") ? ClientTls.trusting(Path.of(options.get("trust-cert"))) : ClientTls.systemTrust();
    }

    private static void usage() {
        LOG.info("Usage: client [--host H --port P --tls true|false --trust-cert ca.pem --startup-fixture up-to-date|optional-update|"
                + "required-update|install-fails|check-fails|connection-fails] | server [--config server.properties | --port 5555 --db data/deck-duel.db]"
                + " | ctl status|drain|resume|reload-tls|wait-safe [--socket S] | backup|restore|backup-keygen ... (docs/README.md#deployment)"
                + " | demo [--games 1] | bots [--host 127.0.0.1 --port 5555 --tls true --trust-cert ca.pem --count 2 --games 1 --password bot-pass]"
                + " | release keygen|package|sign ... (docs/README.md#updates)");
    }
}
