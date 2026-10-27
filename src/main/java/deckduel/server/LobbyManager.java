package deckduel.server;

import deckduel.net.MatchSummary;
import java.security.SecureRandom;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Hosted room directory, keyed by room code and owned by connection identity. */
public final class LobbyManager {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final ConcurrentHashMap<String, Entrant> rooms = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    // Closed during maintenance: no room may be created or put back.
    private boolean closed;

    /** Replaces a host's old room with one uniquely claimed code, or returns null during maintenance. */
    synchronized String createLobby(Entrant host) {
        removeHostedLobby(host.handler());
        if (closed) return null;
        String code;
        do { code = randomCode(); } while (rooms.putIfAbsent(code, host) != null);
        return code;
    }

    /** Atomically takes a room for a joiner, or returns null when it no longer exists. */
    synchronized Entrant claim(String code) { return code == null ? null : rooms.remove(code); }

    /** Puts a room back when the match could not start. */
    synchronized void restore(String code, Entrant host) { if (!closed) rooms.putIfAbsent(code, host); }

    /** Closes every waiting room and refuses new ones until {@link #reopen()}. */
    synchronized void close() { closed = true; rooms.clear(); }

    /** Admits rooms again after maintenance. */
    synchronized void reopen() { closed = false; }

    /** Lists the hosts in stable room-code order. */
    public List<MatchSummary> openMatches() {
        return rooms.entrySet().stream()
                .map(entry -> new MatchSummary(entry.getKey(), entry.getValue().username()))
                .sorted(Comparator.comparing(MatchSummary::roomCode)).toList();
    }

    /** Removes rooms by connection identity, including after account replacement. */
    public synchronized void removeHostedLobby(ClientHandler host) {
        rooms.values().removeIf(entrant -> entrant.handler() == host);
    }

    /** Clears rooms during shutdown. */
    public synchronized void clear() { rooms.clear(); }

    private String randomCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 5; i++) code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return code.toString();
    }
}
