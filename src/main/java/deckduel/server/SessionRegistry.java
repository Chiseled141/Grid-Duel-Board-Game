package deckduel.server;

import java.util.concurrent.ConcurrentHashMap;

/** One authenticated connection per account; stale disconnects cannot remove newer logins. */
public final class SessionRegistry {
    private final ConcurrentHashMap<String, ClientHandler> accounts = new ConcurrentHashMap<>();

    /** Replaces an account connection and closes the previous socket. */
    public void attach(ClientHandler handler) {
        ClientHandler previous = accounts.put(handler.username(), handler);
        if (previous != null && previous != handler) previous.kick("You logged in from another connection.");
    }

    /** Removes only the exact disconnecting connection. */
    public void detach(ClientHandler handler) {
        if (handler.username() != null) accounts.remove(handler.username(), handler);
    }
}
