package deckduel.client.state;

import deckduel.net.HistoryEntry;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Filter combinations preserve order, legacy records and practice's separation from W/L. */
class HistoryFilterTest {
    private static HistoryEntry entry(String name, HistoryEntry.Result result, boolean practice, Long id) {
        return new HistoryEntry("2026-10-28T10:00:00", name, result, "SCORE", "cards-neutral-5",
                List.of(10L), List.of(5L), practice, id,
                id == null ? HistoryEntry.Replay.UNAVAILABLE : HistoryEntry.Replay.AVAILABLE);
    }
    private final List<HistoryEntry> entries = List.of(
            entry("Bob", HistoryEntry.Result.WIN, false, 1L),
            entry("bobby", HistoryEntry.Result.LOSS, false, 2L),
            entry("Bot · Hard", HistoryEntry.Result.WIN, true, 3L),
            entry("Carol", HistoryEntry.Result.DRAW, false, null),
            entry("Bot · Easy", HistoryEntry.Result.DRAW, true, 4L));

    @Test void allIsAnImmutableOrderedViewAndEmptySearchIsInactive() {
        var filter = HistoryFilter.all(); assertFalse(filter.active());
        assertEquals(entries, filter.apply(entries));
        assertThrows(UnsupportedOperationException.class, () -> filter.apply(entries).clear());
        assertFalse(new HistoryFilter("  ", HistoryFilter.Result.ALL, HistoryFilter.Mode.ALL).active());
    }
    @Test void opponentSearchIsCaseInsensitiveLiteralAndTrimmed() {
        var filter = new HistoryFilter("  BOB ", HistoryFilter.Result.ALL, HistoryFilter.Mode.ALL);
        assertEquals(entries.subList(0, 2), filter.apply(entries));
        assertTrue(filter.active());
        assertEquals(List.of(), new HistoryFilter(".*", HistoryFilter.Result.ALL, HistoryFilter.Mode.ALL).apply(entries));
        assertEquals(List.of(), new HistoryFilter("unknown", HistoryFilter.Result.ALL, HistoryFilter.Mode.ALL).apply(entries));
    }
    @Test void resultAndModeCombineWithoutReordering() {
        for (HistoryFilter.Result result : HistoryFilter.Result.values()) for (HistoryFilter.Mode mode : HistoryFilter.Mode.values()) {
            var actual = new HistoryFilter("", result, mode).apply(entries);
            var expected = entries.stream()
                    .filter(e -> result == HistoryFilter.Result.ALL || e.result().name().equals(result.name()))
                    .filter(e -> mode == HistoryFilter.Mode.ALL || e.practice() == (mode == HistoryFilter.Mode.PRACTICE)).toList();
            assertEquals(expected, actual, result + " / " + mode);
        }
    }
    @Test void summaryCountsPracticeApartEvenWhenItIsAWin() {
        assertEquals(new HistoryFilter.Summary(5, 1, 1, 1, 2), HistoryFilter.summarize(entries));
        var practice = new HistoryFilter("", HistoryFilter.Result.WIN, HistoryFilter.Mode.PRACTICE).apply(entries);
        assertEquals(new HistoryFilter.Summary(1, 0, 0, 0, 1), HistoryFilter.summarize(practice));
        assertTrue(HistoryFilter.summarize(practice).description().contains("0 won"));
        assertEquals(new HistoryFilter.Summary(0, 0, 0, 0, 0), HistoryFilter.summarize(List.of()));
    }
    @Test void searchDoesNotDependOnTheOperatingSystemLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            var entry = entry("IRIS", HistoryEntry.Result.WIN, false, 9L);
            assertTrue(new HistoryFilter("iris", HistoryFilter.Result.ALL, HistoryFilter.Mode.ALL).matches(entry));
        } finally { Locale.setDefault(original); }
    }
    @Test void legacyEntriesRemainFilterableWithoutReplayOrScores() {
        var old = new HistoryEntry("2026-09-01", "old opponent", HistoryEntry.Result.LOSS, "FORFEIT", "legacy",
                List.of(), List.of(), false, null, HistoryEntry.Replay.UNAVAILABLE);
        var filtered = new HistoryFilter("OLD", HistoryFilter.Result.LOSS, HistoryFilter.Mode.ONLINE).apply(List.of(old));
        assertEquals(List.of(old), filtered);
        assertNull(filtered.get(0).replayId()); assertTrue(filtered.get(0).yourRounds().isEmpty());
    }
}
