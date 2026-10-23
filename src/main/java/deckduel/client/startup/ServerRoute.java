package deckduel.client.startup;

import java.util.Locale;
import java.util.Objects;

/**
 * Where the client connects and whether the connection uses TLS. Plain connections are allowed
 * only to this computer (local development and offline practice); every other host must use
 * TLS, so a remote route can never fall back to plaintext.
 */
public record ServerRoute(String host, int port, boolean tls) {

    /** The local development server started with {@code server --port 5555}. */
    public static final ServerRoute LOCAL_DEVELOPMENT = new ServerRoute("127.0.0.1", 5555, false);

    /** Validates the host and port, and refuses plaintext to another computer. */
    public ServerRoute {
        Objects.requireNonNull(host, "host");
        if (host.isBlank()) throw new IllegalArgumentException("A server host is required");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Bad server port " + port);
        if (!tls && !isLoopback(host)) throw new IllegalArgumentException("Connections to " + host + " must use TLS");
    }

    /** A route that uses TLS unless it points at this computer. */
    public ServerRoute(String host, int port) { this(host, port, !isLoopback(host)); }

    /** Whether {@code host} names this computer. */
    public static boolean isLoopback(String host) {
        String name = host.toLowerCase(Locale.ROOT);
        return name.equals("localhost") || name.equals("::1") || name.equals("[::1]") || name.matches("127(\\.\\d{1,3}){3}");
    }

    /** Whether the route points at this machine. */
    public boolean local() { return isLoopback(host); }

    /** A player-facing name, for example "local development server 127.0.0.1:5555". */
    public String describe() {
        if (!tls) return "local development server " + host + ":" + port;
        return (local() ? "local secure test server " : "secure server ") + host + ":" + port;
    }
}
