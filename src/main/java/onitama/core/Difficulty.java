package onitama.core;

/**
 * The three difficulty levels of the practice-mode bot. ROOKIE plays a
 * forgiving heuristic (and sometimes a random card), SENIOR looks two
 * half-moves ahead, and LEGEND searches deeper with alpha-beta — see
 * {@link PracticeBot} for what each level actually does.
 */
public enum Difficulty {

    ROOKIE("Rookie Bot", 0),
    SENIOR("Senior Bot", 2),
    LEGEND("Legend Bot", 5);

    private final String botName;
    private final int searchDepth;

    Difficulty(String botName, int searchDepth) {
        this.botName = botName;
        this.searchDepth = searchDepth;
    }

    /** The opponent name shown in the UI for this level. */
    public String botName() {
        return botName;
    }

    /** How many half-moves ahead this level searches (0 = no search). */
    public int searchDepth() {
        return searchDepth;
    }
}
