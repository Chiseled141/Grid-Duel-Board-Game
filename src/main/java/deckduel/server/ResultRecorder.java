package deckduel.server;

import deckduel.core.card.RoundResult;
import java.util.List;

/** Stores a finished recorded match; implementations must not throw into the match thread. */
@FunctionalInterface
public interface ResultRecorder {
    /** Discards results, for servers started without match history. */
    ResultRecorder NONE = (result, rounds) -> { };

    /** Records the result and its banked round scores atomically. */
    void record(MatchResult result, List<RoundResult> rounds);
}
