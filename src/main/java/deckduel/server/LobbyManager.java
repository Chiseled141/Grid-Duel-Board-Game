package deckduel.server;

import deckduel.net.MatchSummary;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Hosted room directory and the automatic match queue; both are keyed by connection identity. */
public final class LobbyManager {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final ConcurrentHashMap<String, Entrant> rooms = new ConcurrentHashMap<>();
    private final Deque<Entrant> queue = new ArrayDeque<>();
    private final SecureRandom random = new SecureRandom();

    /** Replaces a host's old room with one uniquely claimed code. */
    synchronized String createLobby(Entrant host) {
        removeHostedLobby(host.handler());
        String code;
        do { code = randomCode(); } while (rooms.putIfAbsent(code, host) != null);
        return code;
    }

    /** Atomically takes a room for a joiner, or returns null when it no longer exists. */
    synchronized Entrant claim(String code) { return code == null ? null : rooms.remove(code); }

    /** Puts a room back when the match could not start. */
    synchronized void restore(String code, Entrant host) { rooms.putIfAbsent(code, host); }

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

    /** Pairs the entrant with the longest-waiting other account, or queues it and returns null. */
    synchronized Entrant enqueue(Entrant entrant) {
        dequeue(entrant.handler());
        for (Entrant waiting : queue)
            if (!waiting.username().equals(entrant.username())) {
                queue.remove(waiting);
                return waiting;
            }
        queue.addLast(entrant);
        return null;
    }

    /** Leaves the queue; returns whether the connection was waiting. */
    synchronized boolean dequeue(ClientHandler handler) {
        return queue.removeIf(waiting -> waiting.handler() == handler);
    }

    /** Clears rooms and queue during shutdown. */
    public synchronized void clear() { rooms.clear(); queue.clear(); }

    private String randomCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 5; i++) code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return code.toString();
    }
}
