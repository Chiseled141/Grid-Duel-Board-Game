package deckduel.client.state;

/** Account/lobby observers; callbacks are delivered on the Swing EDT. */
public interface ClientModelListener {
    /** The visible shell page changed. */
    default void onScreenChanged(Screen screen) { }
    /** Open rooms or account data changed. */
    default void onLobbyChanged() { }
    /** Ranking data changed. */
    default void onLeaderboardChanged() { }
    /** A hosted room was created. */
    default void onMatchCreated(String code) { }
    /** A request failed. */
    default void onError(String text) { }
    /** Login or registration failed. */
    default void onLoginFailed(String reason) { }
    /** The connection closed unexpectedly. */
    default void onConnectionLost() { }
}
