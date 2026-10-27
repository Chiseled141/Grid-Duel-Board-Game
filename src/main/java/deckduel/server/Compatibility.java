package deckduel.server;

import deckduel.launcher.ReleaseVersion;
import deckduel.net.UpdateRequired;

/**
 * The server's own client-version policy, enforced at the handshake whatever the client's update
 * screen did: a client older than the configured minimum is refused before login. A client
 * without a release version (a development build) is refused too once a minimum is set.
 */
final class Compatibility {
    private Compatibility() { }

    /** An UpdateRequired answer for an outdated client, or null when it may continue. */
    static UpdateRequired check(String clientVersion, String minimumVersion) {
        if (minimumVersion == null) return null;
        ReleaseVersion minimum = ReleaseVersion.parse(minimumVersion);
        boolean outdated = clientVersion == null || !ReleaseVersion.isValid(clientVersion)
                || ReleaseVersion.parse(clientVersion).compareTo(minimum) < 0;
        return outdated ? new UpdateRequired(minimumVersion, "This server needs Deck Duel " + minimumVersion
                + " or newer. Update the game to keep playing online.") : null;
    }
}
