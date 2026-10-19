package deckduel.net;

import deckduel.core.card.Move;
import java.io.Serial;

/**
 * C→S. One match move (Landmark choice, card play, manual draw or Reconnaissance keep) quoted
 * against the view revision it was chosen from. The request id makes a retry of an already
 * accepted move harmless: the server answers with the current view.
 */
public record MoveRequest(long requestId, int revision, Move move) implements Message {
    @Serial private static final long serialVersionUID = 1L;
}
