package deckduel.client.state;

/**
 * Client pages; STARTUP checks for updates and connects before LOGIN, TABLE is the live card
 * match, REPLAY plays back one of the account's recorded matches from HISTORY.
 */
public enum Screen { STARTUP, LOGIN, LOBBY, TABLE, HISTORY, REPLAY }
