package deckduel.net;

import java.io.Serial;
import java.io.Serializable;

/**
 * Public profile of one user account, sent on login and after recorded matches.
 * Carries no secrets — password hashes never leave the server.
 */
public record UserProfile(String username, int wins, int losses)
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
