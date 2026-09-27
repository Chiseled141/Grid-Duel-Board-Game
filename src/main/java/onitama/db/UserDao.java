package onitama.db;

import onitama.net.UserProfile;

import java.util.List;

/**
 * Account storage abstraction (Dependency Inversion): the server depends on
 * this interface, not on a concrete database. Implementations: an in-memory
 * store used until milestone M5 and for tests, and the SQLite implementation
 * added in M5 — swappable without touching server code.
 */
public interface UserDao {

    /**
     * Creates a new account and returns its profile.
     *
     * @throws AuthenticationException if the username is malformed or taken,
     *         or the password is too short
     */
    UserProfile register(String username, String password);

    /**
     * Verifies credentials and returns the profile.
     *
     * @throws AuthenticationException if the user does not exist or the
     *         password is wrong
     */
    UserProfile login(String username, String password);

    /**
     * Returns the profile of an existing user.
     *
     * @throws AuthenticationException if the user does not exist
     */
    UserProfile profileOf(String username);

    /** Persists new Elo rating and win/loss counters after a finished match. */
    void updateStats(String username, int elo, int wins, int losses);

    /** Returns up to {@code limit} users with the highest Elo, best first. */
    List<UserProfile> leaderboard(int limit);
}
