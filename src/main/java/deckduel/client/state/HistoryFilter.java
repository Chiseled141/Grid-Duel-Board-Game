package deckduel.client.state;

import deckduel.net.HistoryEntry;
import java.util.List;
import java.util.Locale;

/** Local filtering of the history entries already authorized and returned by the server. */
public record HistoryFilter(String opponent, Result result, Mode mode) {
    /** Results apply to online and practice entries; practice stays outside account W/L. */
    public enum Result { ALL, WIN, LOSS, DRAW;
        @Override public String toString() { return this == ALL ? "All results" : title(name()); }
    }
    public enum Mode { ALL, ONLINE, PRACTICE;
        @Override public String toString() { return this == ALL ? "All matches" : title(name()); }
    }
    /** Visible totals keep practice separate even when the current result filter is Win. */
    public record Summary(int total, int wins, int losses, int draws, int practice) {
        public String description() {
            return total + (total == 1 ? " match · " : " matches · ") + wins + " won · " + losses + " lost · " + draws + " drawn"
                    + (practice == 0 ? "" : " · " + practice + " practice");
        }
    }

    public HistoryFilter {
        opponent = opponent == null ? "" : opponent.strip();
        java.util.Objects.requireNonNull(result);
        java.util.Objects.requireNonNull(mode);
    }
    public static HistoryFilter all() { return new HistoryFilter("", Result.ALL, Mode.ALL); }
    public boolean active() { return !opponent.isEmpty() || result != Result.ALL || mode != Mode.ALL; }
    public boolean matches(HistoryEntry entry) {
        return entry.opponent().toLowerCase(Locale.ROOT).contains(opponent.toLowerCase(Locale.ROOT))
                && (result == Result.ALL || entry.result().name().equals(result.name()))
                && (mode == Mode.ALL || entry.practice() == (mode == Mode.PRACTICE));
    }
    /** Preserves the server's order and never changes its entries. */
    public List<HistoryEntry> apply(List<HistoryEntry> entries) {
        return entries.stream().filter(this::matches).toList();
    }
    public static Summary summarize(List<HistoryEntry> entries) {
        int wins = 0, losses = 0, draws = 0, practice = 0;
        for (HistoryEntry entry : entries) {
            if (entry.practice()) { practice++; continue; }
            switch (entry.result()) {
                case WIN -> wins++;
                case LOSS -> losses++;
                case DRAW -> draws++;
            }
        }
        return new Summary(entries.size(), wins, losses, draws, practice);
    }
    private static String title(String value) {
        return value.charAt(0) + value.substring(1).toLowerCase(Locale.ROOT);
    }
}
