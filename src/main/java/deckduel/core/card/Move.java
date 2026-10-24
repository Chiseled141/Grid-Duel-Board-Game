package deckduel.core.card;

import java.io.Serializable;

/**
 * Everything a player can do in a match: play a card, make a manual draw, keep one card a
 * Reconnaissance revealed, or skip a turn. The server validates every move against the state.
 */
public sealed interface Move extends Serializable permits PlayCard, DrawCard, KeepCard, SkipTurn, ChooseKing, KeepEdictCard { }
