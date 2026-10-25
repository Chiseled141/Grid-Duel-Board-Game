package deckduel.server;

/** Match-thread-owned deadline shared by both King choices; confirmations never restart it. */
final class KingChoiceDeadline {
    static final long DURATION_MILLIS = 30_000;
    private int game = -1, round;
    private long millis;

    /** Opens a new selection window only when the game or round changes. */
    boolean begin(int game, int round, long now) {
        if (millis != 0 && this.game == game && this.round == round) return false;
        this.game = game; this.round = round; millis = now + DURATION_MILLIS;
        return true;
    }

    long millis() { return millis; }
    boolean expired(long now) { return millis != 0 && now >= millis; }
    boolean matches(int game, int round, long deadline) {
        return millis != 0 && this.game == game && this.round == round && millis == deadline;
    }
    void clear() { millis = 0; game = -1; round = 0; }
}
