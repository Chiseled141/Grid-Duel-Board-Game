package deckduel.client.startup;

import java.io.IOException;

/** Opens the client's server connection, blocking until it is ready or has failed. */
@FunctionalInterface
public interface ServerConnector {

    /** Connects to {@code route}, or does nothing when already connected. */
    void connect(ServerRoute route) throws IOException;
}
