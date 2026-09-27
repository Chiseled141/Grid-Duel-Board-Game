package onitama.client.state;

import onitama.net.GameOver;

/**
 * Observer interface for the client model. Panels subscribe with method
 * references and override only what they show; all callbacks run on the
 * Swing EDT, so implementations may touch Swing components directly.
 */
public interface ClientModelListener {

    /** The active screen changed. */
    default void onScreenChanged(Screen screen) {
    }

    /** Any in-match data changed (new state, selection, opponent info). */
    default void onMatchChanged() {
    }

    /** Lobby data changed (open matches, profile). */
    default void onLobbyChanged() {
    }

    /** A leaderboard response arrived. */
    default void onLeaderboardChanged() {
    }

    /** The server acknowledged a created match with its room code. */
    default void onMatchCreated(String roomCode) {
    }

    /** The match ended; carries winner, way and the updated Elo ratings. */
    default void onGameOver(GameOver over) {
    }

    /** The opponent disconnected; the match is parked for the grace period. */
    default void onOpponentLeft(int graceSeconds) {
    }

    /** The opponent offered a rematch. */
    default void onRematchOffered() {
    }

    /** A server error message arrived; show it to the user. */
    default void onError(String text) {
    }

    /** Login or registration failed; the login screen shows the reason inline. */
    default void onLoginFailed(String reason) {
    }

    /** The connection to the server dropped unexpectedly. */
    default void onConnectionLost() {
    }
}
