package deckduel.client.ui;

/**
 * Areas of the match table a pointer, play or effect can refer to: the hand, both Unit rows and
 * Landmark areas, the cast button, the Landmark offers and their confirm button, the cards a
 * Reconnaissance revealed, and the manual-draw and skip-turn buttons.
 */
enum TableZone { HAND, OWN, OPPONENT, OWN_LANDMARK, OPPONENT_LANDMARK, CAST, OFFER, CONFIRM, LOOK, DRAW, SKIP, REVEALED }
