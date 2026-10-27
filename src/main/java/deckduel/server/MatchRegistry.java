package deckduel.server;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Which match each account currently belongs to; an account is in at most one match. */
final class MatchRegistry {
    private final ConcurrentHashMap<String, MatchSession> byUser = new ConcurrentHashMap<>();

    /** Binds every human account to the session, or binds none when one is already playing. */
    synchronized boolean bind(MatchSession session, List<String> usernames) {
        if (usernames.stream().anyMatch(byUser::containsKey)) return false;
        usernames.forEach(name -> byUser.put(name, session));
        return true;
    }

    /** Releases an account only from this exact session. */
    void release(String username, MatchSession session) { byUser.remove(username, session); }

    /** The account's session, or null. */
    MatchSession sessionFor(String username) { return username == null ? null : byUser.get(username); }

    /** All sessions, for shutdown. */
    List<MatchSession> sessions() { return byUser.values().stream().distinct().toList(); }
}
