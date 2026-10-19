package deckduel.net;

import java.io.Serial;
import java.util.List;

/** S→C. The recipient's recent recorded matches, newest first. */
public record HistoryResponse(List<HistoryEntry> matches) implements Message {
    @Serial private static final long serialVersionUID = 1L;
}
