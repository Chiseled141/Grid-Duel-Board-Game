package onitama.net;

import java.io.Serial;

/** S→C. Accepted match text with the authenticated sender's username. */
public record ChatMessage(String matchId, String sender, String text) implements Message {
    @Serial
    private static final long serialVersionUID = 1L;
}
