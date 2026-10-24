package deckduel.client.state;

import deckduel.client.startup.ServerRoute;
import java.io.IOException;

/**
 * The client model's view of the network: sending messages, connecting and
 * the connected flag. Implemented by {@code DeckDuelClient}; depending on
 * this interface instead of the socket class keeps the model testable and
 * the UI strictly separated from I/O (Dependency Inversion).
 */
public interface ServerConnection extends MessageSender {

    /** Returns true while the connection is open. */
    boolean isConnected();

    /** Connects to {@code route} (with TLS when the route says so) if not already connected. */
    void ensureConnected(ServerRoute route) throws IOException;

    /** Drops the current connection, if any, before a reconnect attempt. */
    default void close() { }
}
