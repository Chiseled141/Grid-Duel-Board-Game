package deckduel.server;

import deckduel.net.HistoryEntry;
import java.util.List;

/** Reads an account's recorded matches for the history screen. */
@FunctionalInterface
public interface MatchHistory {
    /** For servers started without match history. */
    MatchHistory NONE = (username, limit) -> List.of();

    /** Up to {@code limit} of the user's matches, newest first, from the user's point of view. */
    List<HistoryEntry> historyFor(String username, int limit);
}
