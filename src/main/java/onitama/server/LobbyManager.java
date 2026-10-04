package onitama.server;

import onitama.net.MatchSummary;

import java.security.SecureRandom;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The lobby: open matches waiting for a second player, keyed by 5-character
 * room code. All operations are single atomic operations on a
 * {@link ConcurrentHashMap}, so two clients racing to join the same room
 * cannot both succeed — exactly one wins the atomic remove.
 */
public final class LobbyManager {

    /** Unambiguous alphabet: no I, O, 0, 1 — room codes are read aloud by humans. */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 5;

    private final ConcurrentMap<String, ClientHandler> lobbies = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    /**
     * Opens a lobby hosted by the given (authenticated) handler and returns
     * its room code. A host can only host one lobby; a previous one is closed.
     */
    public String createLobby(ClientHandler host) {
        removeHostedLobby(host);
        // putIfAbsent claims the code atomically: two hosts racing to draw the
        // same random code cannot both succeed — the loser just draws again.
        String code = randomCode();
        while (lobbies.putIfAbsent(code, host) != null) {
            code = randomCode();
        }
        return code;
    }

    /**
     * Atomically claims the lobby with the given code for the joiner and
     * returns its host, or returns null if the code is unknown, the lobby
     * already started, either player is already in a match, or the joiner is
     * trying to join their own lobby.
     */
    public ClientHandler takeLobby(String code, ClientHandler joiner) {
        ClientHandler host = lobbies.get(code);
        if (host == null || host == joiner || host.username() == null
                || inActiveMatch(host) || inActiveMatch(joiner)) {
            return null;
        }
        return lobbies.remove(code, host) ? host : null;
    }

    private static boolean inActiveMatch(ClientHandler handler) {
        MatchSession session = handler.currentMatch();
        return session != null && session.isActive();
    }

    /** Returns the currently open matches with their hosts, sorted by code. */
    public List<MatchSummary> openMatches() {
        return lobbies.entrySet().stream()
                .filter(entry -> entry.getValue().username() != null)
                .map(entry -> new MatchSummary(entry.getKey(), entry.getValue().username()))
                .sorted(Comparator.comparing(MatchSummary::roomCode))
                .toList();
    }

    /** Closes any lobby hosted by the given handler (disconnect or new lobby). */
    public void removeHostedLobby(ClientHandler host) {
        lobbies.values().removeIf(handler -> handler == host);
    }

    /** Closes every lobby; used during server shutdown. */
    public void clear() {
        lobbies.clear();
    }

    /** Returns a fresh random 5-character room code (not guaranteed free). */
    private String randomCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
