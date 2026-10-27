package deckduel.server;

import java.net.InetAddress;
import java.util.Objects;

/**
 * Immutable server settings. A disconnected player keeps their seat for the reconnect grace
 * period; the practice bot waits {@code botDelayMillis} before each play so humans can follow it.
 *
 * @param bind the address to listen on; anything but loopback requires {@code tls}
 * @param tls the certificate the server presents, or null for plain loopback development
 * @param minimumClientVersion the oldest client release accepted before login, or null for any
 * @param handshakeTimeoutMillis how long a new connection may take for its TLS handshake and hello
 */
public record ServerConfig(int port, int silenceSeconds, int clientPoolSize, int reconnectGraceSeconds,
                           int botDelayMillis, InetAddress bind, TlsMaterial tls, String minimumClientVersion,
                           int handshakeTimeoutMillis) {
    /** Validates settings before opening any sockets. */
    public ServerConfig {
        if (port < 0 || port > 65535 || silenceSeconds < 1 || clientPoolSize < 1
                || reconnectGraceSeconds < 1 || botDelayMillis < 0 || handshakeTimeoutMillis < 100)
            throw new IllegalArgumentException("Invalid server settings");
        Objects.requireNonNull(bind, "bind");
        if (minimumClientVersion != null) deckduel.launcher.ReleaseVersion.parse(minimumClientVersion);
    }

    /** Loopback-only, plain settings for local development and tests. */
    public ServerConfig(int port, int silenceSeconds, int clientPoolSize, int reconnectGraceSeconds, int botDelayMillis) {
        this(port, silenceSeconds, clientPoolSize, reconnectGraceSeconds, botDelayMillis,
                InetAddress.getLoopbackAddress(), null, null, 10_000);
    }

    /** Production defaults on loopback; port zero supports isolated tests. */
    public static ServerConfig defaults(int port) { return new ServerConfig(port, 90, 32, 60, 700); }

    /** The same settings listening on {@code address}. */
    public ServerConfig withBind(InetAddress address) {
        return new ServerConfig(port, silenceSeconds, clientPoolSize, reconnectGraceSeconds, botDelayMillis, address, tls,
                minimumClientVersion, handshakeTimeoutMillis);
    }

    /** The same settings presenting {@code material} over TLS. */
    public ServerConfig withTls(TlsMaterial material) {
        return new ServerConfig(port, silenceSeconds, clientPoolSize, reconnectGraceSeconds, botDelayMillis, bind, material,
                minimumClientVersion, handshakeTimeoutMillis);
    }

    /** The same settings refusing clients older than {@code version}. */
    public ServerConfig withMinimumClientVersion(String version) {
        return new ServerConfig(port, silenceSeconds, clientPoolSize, reconnectGraceSeconds, botDelayMillis, bind, tls,
                version, handshakeTimeoutMillis);
    }

    /** The same settings with a different handshake limit. */
    public ServerConfig withHandshakeTimeout(int millis) {
        return new ServerConfig(port, silenceSeconds, clientPoolSize, reconnectGraceSeconds, botDelayMillis, bind, tls,
                minimumClientVersion, millis);
    }

    /** Whether the server listens only on this computer. */
    public boolean loopbackOnly() { return bind.isLoopbackAddress(); }
}
