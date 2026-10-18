package deckduel.net;

import java.io.Serial;

/** C→S. Text for the current match; sender identity is supplied by the server. */
public record ChatRequest(String matchId, String text) implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    /** Maximum UTF-16 length of one single-line chat message. */
    public static final int MAX_LENGTH = 280;

    /** Validates untrusted text before any relay or history mutation. */
    public static boolean validText(String text) {
        return text != null && !text.isBlank() && text.length() <= MAX_LENGTH
                && text.codePoints().noneMatch(c -> Character.isISOControl(c)
                    || Character.getType(c) == Character.FORMAT
                    || Character.getType(c) == Character.LINE_SEPARATOR
                    || Character.getType(c) == Character.PARAGRAPH_SEPARATOR);
    }
}
