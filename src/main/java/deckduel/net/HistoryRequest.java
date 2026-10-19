package deckduel.net;

import java.io.Serial;

/** C→S. Requests the signed-in account's recent recorded matches. */
public record HistoryRequest() implements Message {
    @Serial private static final long serialVersionUID = 1L;
}
