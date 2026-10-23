package deckduel.client.startup;

import java.io.IOException;

/**
 * Asks a release service whether the installed client is current. Calls block, so callers run
 * them on a worker thread, never on the Swing or JavaFX thread. No production service exists
 * yet; {@link #notConfigured()} is used until one does.
 */
@FunctionalInterface
public interface UpdateService {

    /** Checks for a release newer than {@code installedVersion}. */
    UpdateCheck check(String installedVersion) throws IOException;

    /** The placeholder service: reports that no check was made instead of claiming the client is current. */
    static UpdateService notConfigured() { return installedVersion -> UpdateCheck.notConfigured(); }
}
