package deckduel.net;

import java.io.Serial;

/** S→C. The recipient's refreshed win/loss record after a recorded match. */
public record ProfileUpdate(UserProfile profile) implements Message {
    @Serial private static final long serialVersionUID = 1L;
}
