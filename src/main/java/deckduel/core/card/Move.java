package deckduel.core.card;

import java.io.Serializable;

/**
 * Everything a player can do in a match: choose Landmarks, play a card, make a manual draw, or
 * keep one card a Reconnaissance revealed, or skip a turn. The server validates every move against the state.
 */
public sealed interface Move extends Serializable permits PlayCard, DrawCard, PickLandmarks, KeepCard, SkipTurn { }
