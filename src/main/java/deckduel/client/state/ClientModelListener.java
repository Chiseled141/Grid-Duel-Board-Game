package deckduel.client.state;

/** Client state observers; callbacks are delivered on the Swing EDT. */
public interface ClientModelListener {
    /** The visible page changed. */
    default void onScreenChanged(Screen screen) { }
    /** Open rooms, account data or the chosen deck changed. */
    default void onLobbyChanged() { }
    /** The account's match history arrived. */
    default void onHistoryChanged() { }
    /** A hosted room was created. */
    default void onMatchCreated(String code) { }
    /** A request failed. */
    default void onError(String text) { }
    /** Login or registration failed. */
    default void onLoginFailed(String reason) { }
    /** The connection closed unexpectedly. */
    default void onConnectionLost() { }
    /** A new authoritative match view arrived, or the reconnect status changed. */
    default void onMatchChanged() { }
    /** The server rejected a card play; nothing changed. */
    default void onActionRejected(String reason) { }
}
