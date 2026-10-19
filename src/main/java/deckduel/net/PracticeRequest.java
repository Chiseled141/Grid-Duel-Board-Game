package deckduel.net;

import java.io.Serial;

/** C→S. Starts a practice match against the server's practice bot; {@code hard} picks the planning bot. */
public record PracticeRequest(boolean hard) implements Message {
    @Serial private static final long serialVersionUID = 1L;
}
