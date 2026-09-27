package onitama.db;

import onitama.net.UserProfile;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * In-memory UserDao used until milestone M5 and by integration tests that do
 * not need persistence. Behavior (validation, hashing, errors) is identical
 * to the SQLite implementation, so the server cannot tell them apart.
 */
public final class InMemoryUserDao implements UserDao {

    private static final Pattern VALID_USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final int MIN_PASSWORD_LENGTH = 4;

    private record Account(String passwordHash, UserProfile profile) {
    }

    private final Map<String, Account> users = new ConcurrentHashMap<>();
    private final Object statsLock = new Object();

    @Override
    public UserProfile register(String username, String password) {
        validate(username, password);
        UserProfile profile = new UserProfile(username, 1000, 0, 0);
        Account fresh = new Account(PasswordHasher.hash(password), profile);
        if (users.putIfAbsent(username, fresh) != null) {
            throw new AuthenticationException("username is already taken");
        }
        return profile;
    }

    @Override
    public UserProfile login(String username, String password) {
        Account account = users.get(username);
        if (account == null || !PasswordHasher.verify(password, account.passwordHash())) {
            // Same message for unknown user and wrong password: no account probing.
            throw new AuthenticationException("wrong username or password");
        }
        return account.profile();
    }

    @Override
    public UserProfile profileOf(String username) {
        Account account = users.get(username);
        if (account == null) {
            throw new AuthenticationException("unknown user: " + username);
        }
        return account.profile();
    }

    @Override
    public void updateStats(String username, int elo, int wins, int losses) {
        synchronized (statsLock) {
            Account account = users.get(username);
            if (account != null) {
                users.put(username, new Account(account.passwordHash(),
                        new UserProfile(username, elo, wins, losses)));
            }
        }
    }

    @Override
    public List<UserProfile> leaderboard(int limit) {
        return users.values().stream()
                .map(Account::profile)
                .sorted((a, b) -> Integer.compare(b.elo(), a.elo()))
                .limit(limit)
                .toList();
    }

    private void validate(String username, String password) {
        if (!VALID_USERNAME.matcher(username).matches()) {
            throw new AuthenticationException(
                    "username must be 3-16 letters, digits or underscores");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new AuthenticationException("password must be at least 4 characters");
        }
    }
}
