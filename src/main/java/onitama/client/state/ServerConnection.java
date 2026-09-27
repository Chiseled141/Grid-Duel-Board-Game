package onitama.client.state;

import java.io.IOException;

/**
 * The client model's view of the network: sending messages, connecting and
 * the connected flag. Implemented by {@code OnitamaClient}; depending on
 * this interface instead of the socket class keeps the model testable and
 * the UI strictly separated from I/O (Dependency Inversion).
 */
public interface ServerConnection extends MessageSender {

    /** Returns true while the connection is open. */
    boolean isConnected();

    /** Connects to the server if not already connected. */
    void ensureConnected(String host, int port) throws IOException;
}
