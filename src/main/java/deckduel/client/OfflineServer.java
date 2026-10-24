package deckduel.client;

import deckduel.client.startup.ServerRoute;
import deckduel.db.Database;
import deckduel.server.GameServer;
import deckduel.server.ServerConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A private server inside the client, for practice against the bots when the real server cannot
 * be reached. It is the ordinary {@link GameServer}, so offline matches follow the same rules and
 * protocol; it listens on loopback only and keeps its one account, history and replays in a
 * temporary database that is deleted on {@link #close()}.
 */
public final class OfflineServer implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(OfflineServer.class.getName());
    /** The local account offline play registers and signs in as. */
    public static final String USERNAME = "offline";

    // A fresh database each launch, so a random password per launch is all the account needs.
    private final String password = randomPassword();
    // Where the temporary database folder is made; null means the system's temporary folder.
    private final Path parent;
    private final ServerConfig config;
    private Path folder;
    private Database database;
    private GameServer server;
    private ServerRoute route;

    /** An offline server whose database lives in the system's temporary folder. */
    public OfflineServer() { this(null, ServerConfig.defaults(0)); }

    OfflineServer(Path parent, ServerConfig config) {
        this.parent = parent;
        this.config = config;
    }

    /** Starts the server on first use and returns where to reach it; blocking, never on a UI thread. */
    public synchronized ServerRoute start() throws IOException {
        if (route != null) return route;
        try {
            folder = parent == null ? Files.createTempDirectory("deck-duel-offline")
                    : Files.createTempDirectory(parent, "deck-duel-offline");
            database = new Database(folder.resolve("offline.db"));
            server = GameServer.recording(database, config);
            // Loopback, plain and never public: ServerConfig binds 127.0.0.1 unless told otherwise.
            if (!config.loopbackOnly()) throw new IOException("Offline play must listen on this computer only");
            route = new ServerRoute("127.0.0.1", server.start(), false);
            LOG.info(() -> "offline play server on " + route.host() + ":" + route.port());
            return route;
        } catch (IOException | SQLException | RuntimeException failure) {
            close();
            throw new IOException("Offline play could not start: " + failure.getMessage(), failure);
        }
    }

    /** The offline account's password for this launch. */
    public String password() { return password; }

    /** Stops the server and deletes its temporary database. Safe to call repeatedly. */
    @Override public synchronized void close() {
        if (server != null) server.stop();
        try {
            if (database != null) database.close();
            if (folder != null) try (var files = Files.walk(folder)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            }
        } catch (IOException | SQLException failure) {
            LOG.log(Level.FINE, "could not remove the offline database", failure);
        }
        server = null; database = null; folder = null; route = null;
    }

    private static String randomPassword() {
        byte[] bytes = new byte[18];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
