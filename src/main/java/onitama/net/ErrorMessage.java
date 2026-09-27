package onitama.net;

import java.io.Serial;

/**
 * S→C. Friendly failure text for anything not covered by a dedicated
 * response (e.g. joining a nonexistent room).
 */
public record ErrorMessage(String text) implements Message {

    @Serial
    private static final long serialVersionUID = 1L;
}
