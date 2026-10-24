package deckduel.client.startup;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;

/**
 * Chooses the server the client connects to, in this order:
 * <ol>
 * <li>explicit development overrides ({@code --host}, {@code --port}, {@code --tls});</li>
 * <li>the production server packaged at build time ({@code -Ddeckduel.server.host},
 * {@code -Ddeckduel.server.port}), always over TLS;</li>
 * <li>the saved {@code host}/{@code port} preference, which defaults to the local development
 * server 127.0.0.1:5555.</li>
 * </ol>
 * A saved preference never overrides a packaged production server, and is never rewritten here.
 */
public final class Endpoints {
    /** The chosen route and where it came from, for the log and the login screen. */
    public record Choice(ServerRoute route, String source) { }

    private Endpoints() { }

    /** The packaged production server, or empty when this build has none. */
    public static Optional<ServerRoute> packaged() {
        Properties properties = new Properties();
        try (InputStream in = Endpoints.class.getResourceAsStream("/deckduel/connection.properties")) {
            if (in != null) properties.load(in);
        } catch (IOException unreadable) {
            throw new IllegalStateException("The packaged connection settings cannot be read", unreadable);
        }
        return from(properties);
    }

    /** Reads {@code host} and {@code port}; a production server always uses TLS. */
    static Optional<ServerRoute> from(Properties properties) {
        String host = unfiltered(properties.getProperty("host")), port = unfiltered(properties.getProperty("port"));
        if (host.isEmpty()) return Optional.empty();
        return Optional.of(new ServerRoute(host, port.isEmpty() ? 5555 : Integer.parseInt(port), true));
    }

    /**
     * Applies the precedence above.
     *
     * @param host a {@code --host} override, or null
     * @param port a {@code --port} override, or null
     * @param tls a {@code --tls} override, or null for "TLS unless this computer"
     */
    public static Choice resolve(String host, Integer port, Boolean tls, Optional<ServerRoute> packaged,
                                 String savedHost, int savedPort) {
        if (host != null || port != null || tls != null) {
            String chosenHost = host != null ? host : ServerRoute.LOCAL_DEVELOPMENT.host();
            int chosenPort = port != null ? port : ServerRoute.LOCAL_DEVELOPMENT.port();
            boolean secure = tls != null ? tls : !ServerRoute.isLoopback(chosenHost);
            return new Choice(new ServerRoute(chosenHost, chosenPort, secure), "development override");
        }
        if (packaged.isPresent()) return new Choice(packaged.get(), "packaged production server");
        ServerRoute saved = new ServerRoute(savedHost, savedPort);
        return new Choice(saved, saved.equals(ServerRoute.LOCAL_DEVELOPMENT) ? "local development default" : "saved setting");
    }

    private static String unfiltered(String value) {
        return value == null || value.contains("${") ? "" : value.trim();
    }
}
