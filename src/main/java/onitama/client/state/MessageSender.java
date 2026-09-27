package onitama.client.state;

import onitama.net.Message;

/**
 * Sends one message to the server. Implemented by the client's network
 * endpoint; the model and panels depend on this interface, never on the
 * socket directly (Dependency Inversion).
 */
@FunctionalInterface
public interface MessageSender {

    /** Enqueues the message for delivery; returns immediately. */
    void send(Message message);
}
