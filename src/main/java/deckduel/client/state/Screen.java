package deckduel.client.state;

/**
 * Client pages; STARTUP checks for updates and connects before LOGIN, TABLE is the live card
 * match, REPLAY plays back an account recording, and HOW_TO_PLAY is the local guide with training recordings.
 */
public enum Screen { STARTUP, LOGIN, LOBBY, TABLE, HISTORY, REPLAY, HOW_TO_PLAY }
