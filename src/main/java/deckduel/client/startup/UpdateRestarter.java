package deckduel.client.startup;

import java.io.IOException;

/**
 * Finishes an update that is downloaded and verified: hands over to a separate helper that
 * waits for the game to close, activates the new version and starts it. The caller closes the
 * game right after a successful hand-over.
 */
@FunctionalInterface
public interface UpdateRestarter {

    /** Starts the helper for {@code version}; nothing has changed yet when this throws. */
    void handOver(String version) throws IOException;
}
