package deckduel.client.startup;

import java.io.IOException;
import java.util.function.DoubleConsumer;

/**
 * Downloads and installs a release an {@link UpdateService} offered. Calls block, so callers run
 * them on a worker thread. A finished install takes effect when the client restarts.
 */
@FunctionalInterface
public interface UpdateInstaller {

    /** Installs {@code update}, reporting progress from 0 to 1 on the calling thread. */
    void install(UpdateCheck update, DoubleConsumer progress) throws IOException;

    /** The placeholder installer: always fails, because nothing can be downloaded yet. */
    static UpdateInstaller notConfigured() {
        return (update, progress) -> { throw new IOException("No update installer is configured yet."); };
    }
}
