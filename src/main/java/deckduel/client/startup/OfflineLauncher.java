package deckduel.client.startup;

import java.io.IOException;

/** Starts a private server on this computer for offline play when the real one cannot be reached. */
@FunctionalInterface
public interface OfflineLauncher {

    /** Starts the local server, or returns the running one, and says where to reach it; blocking. */
    ServerRoute start() throws IOException;
}
