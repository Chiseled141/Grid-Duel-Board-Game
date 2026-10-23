package deckduel.server;

import java.io.IOException;
import java.io.Reader;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;

/**
 * The production server's configuration file ({@code /etc/deckduel/server.properties}; template in
 * {@code deploy/config/server.properties.template}). Unknown keys are refused so a typo cannot
 * silently drop a setting. Passwords never appear here: TLS uses PEM files without passphrases,
 * protected by file permissions.
 */
record ServerSettings(ServerConfig config, Path database, Path chainFile, Path keyFile, Path controlSocket) {
    static final Set<String> KEYS = Set.of("bind", "port", "database", "tls.certificateChain", "tls.privateKey",
            "minimumClientVersion", "control.socket", "handshakeTimeoutSeconds", "reconnectGraceSeconds",
            "silenceSeconds", "clientPoolSize", "botDelayMillis");

    /** Reads and validates a configuration file; TLS material is loaded here so errors appear at startup. */
    static ServerSettings load(Path file) throws IOException {
        Properties properties = new Properties();
        try (Reader in = Files.newBufferedReader(file)) {
            properties.load(in);
        }
        return from(properties, file.toAbsolutePath().getParent());
    }

    static ServerSettings from(Properties properties, Path base) throws IOException {
        for (String key : properties.stringPropertyNames())
            if (!KEYS.contains(key)) throw new IOException("Unknown server setting '" + key + "'");
        String database = text(properties, "database");
        if (database.isEmpty()) throw new IOException("Set database to the SQLite file, for example /var/lib/deckduel/deck-duel.db");
        InetAddress bind = InetAddress.getByName(properties.getProperty("bind", "127.0.0.1").trim());
        String chain = text(properties, "tls.certificateChain"), key = text(properties, "tls.privateKey");
        if (chain.isEmpty() != key.isEmpty()) throw new IOException("Set both tls.certificateChain and tls.privateKey, or neither");
        if (!bind.isLoopbackAddress() && chain.isEmpty())
            throw new IOException("bind=" + bind.getHostAddress() + " is reachable from other computers, so TLS is required");
        ServerConfig config = new ServerConfig(number(properties, "port", 5555), number(properties, "silenceSeconds", 90),
                number(properties, "clientPoolSize", 32), number(properties, "reconnectGraceSeconds", 60),
                number(properties, "botDelayMillis", 700), bind, null,
                text(properties, "minimumClientVersion").isEmpty() ? null : text(properties, "minimumClientVersion"),
                number(properties, "handshakeTimeoutSeconds", 10) * 1000);
        Path chainFile = chain.isEmpty() ? null : base.resolve(chain), keyFile = key.isEmpty() ? null : base.resolve(key);
        if (chainFile != null) config = config.withTls(TlsMaterial.load(chainFile, keyFile));
        String socket = text(properties, "control.socket");
        return new ServerSettings(config, base.resolve(database), chainFile, keyFile, socket.isEmpty() ? null : base.resolve(socket));
    }

    private static String text(Properties properties, String key) { return properties.getProperty(key, "").trim(); }

    private static int number(Properties properties, String key, int fallback) throws IOException {
        String value = text(properties, key);
        try {
            return value.isEmpty() ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException bad) {
            throw new IOException("Setting " + key + " must be a whole number, not '" + value + "'");
        }
    }
}
