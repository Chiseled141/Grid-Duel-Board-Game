package deckduel;

import java.io.Serial;
import java.io.Serializable;

/**
 * A serializable object that is deliberately NOT a {@code Message} and lives
 * outside the wire-filter allowlist packages ({@code deckduel.net},
 * {@code deckduel.core}); the wire-filter test sends it to prove that
 * {@code readObject} rejects classes outside the protocol.
 */
public final class SerializationProbe implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @SuppressWarnings("unused")
    private final String payload = "rogue";
}
