package onitama.net;

import onitama.core.OnitamaException;

/**
 * Thrown when a peer misuses the protocol: unreadable or foreign objects on
 * the socket, messages sent at the wrong time, or malformed fields. The
 * connection is not trusted after this exception.
 */
public class ProtocolException extends OnitamaException {

    private static final long serialVersionUID = 1L;

    /** Creates an exception describing the protocol violation. */
    public ProtocolException(String message) {
        super(message);
    }
}
