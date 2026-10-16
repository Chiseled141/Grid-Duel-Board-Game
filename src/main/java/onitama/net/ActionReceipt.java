package onitama.net;

import java.io.Serial;
import java.io.Serializable;
import onitama.core.duel.ActionError;

/**
 * Receipt describing the immediate outcome of an ActionRequest.
 */
public record ActionReceipt(boolean accepted, long revision, ActionError error) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
