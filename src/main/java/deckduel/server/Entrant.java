package deckduel.server;

/** A connection waiting for a match; the server builds every deck itself when the match starts. */
record Entrant(ClientHandler handler) {
    String username() { return handler.username(); }
}
