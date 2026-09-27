package onitama.server;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bookkeeping of authenticated connections. Two maps, both
 * {@link ConcurrentHashMap}s so any thread can look up safely: username →
 * current handler (one live connection per account — a second login kicks
 * the first) and reconnect token → handler (tokens survive a disconnect so a
 * dropped client can re-attach to its match).
 */
public final class SessionRegistry {

    private final Map<String, ClientHandler> byUsername = new ConcurrentHashMap<>();
    private final Map<String, ClientHandler> byToken = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    /**
     * Registers an authenticated handler, kicking any previous connection of
     * the same account, and returns a fresh reconnect token.
     */
    public String attach(ClientHandler handler) {
        ClientHandler previous = byUsername.put(handler.username(), handler);
        if (previous != null && previous != handler) {
            previous.kick("you were logged in from another connection");
        }
        String token = newToken();
        byToken.put(token, handler);
        return token;
    }

    /** Looks up the handler owning a reconnect token, or null if unknown. */
    public ClientHandler byToken(String token) {
        return token == null ? null : byToken.get(token);
    }

    /** Moves a reconnect token to a new handler after a successful reconnect. */
    public void rebind(String token, ClientHandler newHandler) {
        byToken.put(token, newHandler);
    }

    /**
     * Removes a disconnecting handler. The username binding is dropped, but a
     * reconnect token is kept while the handler is in a live match — the
     * whole point is being reachable after the socket is gone.
     */
    public void detach(ClientHandler handler) {
        byUsername.remove(handler.username(), handler);
        if (handler.currentMatch() == null && handler.reconnectToken() != null) {
            byToken.remove(handler.reconnectToken(), handler);
        }
    }

    /** Drops a token whose match is over. */
    public void clearToken(String token) {
        byToken.remove(token);
    }

    /** Kicks every connected handler; used during server shutdown. */
    public void kickAll(String reason) {
        byUsername.values().forEach(handler -> handler.kick(reason));
    }

    private String newToken() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
