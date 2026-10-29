package deckduel.client.ui;

/**
 * Areas of the match table a pointer, play or effect can refer to: the hand, both Unit rows, the
 * cast button, the cards a Reconnaissance revealed, the swap and skip-turn buttons, and the
 * opponent's hand cards a Spy revealed.
 */
enum TableZone { HAND, OWN, OPPONENT, CAST, LOOK, SWAP, SKIP, REVEALED }
