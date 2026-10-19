package deckduel.server;

import deckduel.net.MatchSummary;
import java.security.SecureRandom;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Hosted room directory; gameplay admission is unavailable during the rebuild. */
public final class LobbyManager {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final ConcurrentHashMap<String, ClientHandler> rooms = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    /** Replaces a host's old room with one uniquely claimed code. */
    public synchronized String createLobby(ClientHandler host) {
        removeHostedLobby(host);
        String code;
        do { code = randomCode(); } while (rooms.putIfAbsent(code, host) != null);
        return code;
    }

    /** Lists the hosts in stable room-code order. */
    public List<MatchSummary> openMatches() {
        return rooms.entrySet().stream()
                .map(entry -> new MatchSummary(entry.getKey(), entry.getValue().username()))
                .sorted(Comparator.comparing(MatchSummary::roomCode)).toList();
    }

    /** Removes rooms by connection identity, including after account replacement. */
    public synchronized void removeHostedLobby(ClientHandler host) {
        rooms.values().removeIf(handler -> handler == host);
    }

    /** Clears the directory during shutdown. */
    public void clear() { rooms.clear(); }

    private String randomCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 5; i++) code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return code.toString();
    }
}
