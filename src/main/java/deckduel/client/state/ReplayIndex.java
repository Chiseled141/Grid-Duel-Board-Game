package deckduel.client.state;

import deckduel.core.card.CardKind;
import deckduel.core.card.MatchPhase;
import deckduel.core.card.PlayerView;
import deckduel.net.ReplayResponse;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Navigation metadata derived exclusively from the recorded recipient-visible frames. */
public final class ReplayIndex {
    /** Several markers may belong to one accepted revision. */
    public enum Kind {
        OPENING("Opening"), KING("King choice"), SPELL("Spell"), ROUND("Round change"),
        RESULT("Result"), GAP("Recording gap"), ACTION("Action");
        private final String title;
        Kind(String title) { this.title = title; }
        @Override public String toString() { return title; }
    }

    /** One recorded position, including all log lines added at that position. */
    public record Entry(int index, int round, int revision, Set<Kind> kinds, String text) {
        public Entry { kinds = Set.copyOf(kinds); }
        /** Whether this position is a useful chapter marker. */
        public boolean keyMoment() { return kinds.stream().anyMatch(kind -> kind != Kind.ACTION); }
        /** Plain text for journal rows and accessible names. */
        public String caption() {
            return "#" + index + " · R" + round + " · " + text;
        }
    }

    /** The first recorded position in a round; an excerpt may start partway through that round. */
    public record RoundStart(int round, int index) {
        @Override public String toString() { return "Round " + round; }
    }

    private final List<Entry> entries;
    private final List<RoundStart> rounds;

    /** Builds one immutable index per opened recording; seeking never rescans the match. */
    public ReplayIndex(ReplayResponse replay) {
        var indexed = new ArrayList<Entry>();
        var chapters = new ArrayList<RoundStart>();
        PlayerView previous = null;
        for (int i = 0; i < replay.frames().size(); i++) {
            PlayerView current = replay.frames().get(i);
            EnumSet<Kind> kinds = kinds(previous, current);
            if (previous == null || previous.round() != current.round())
                chapters.add(new RoundStart(current.round(), i));
            indexed.add(new Entry(i, current.round(), current.revision(), kinds,
                    describe(previous, current, replay.opponent())));
            previous = current;
        }
        entries = List.copyOf(indexed);
        rounds = List.copyOf(chapters);
    }

    /** Every recorded position, without synthesizing missing actions. */
    public List<Entry> entries() { return entries; }
    /** Round starts that actually exist in this recording. */
    public List<RoundStart> rounds() { return rounds; }
    /** Metadata at an existing playback index. */
    public Entry at(int index) { return entries.get(index); }
    /** A filtered journal, retaining original frame indices. */
    public List<Entry> matching(Kind kind, boolean keyOnly, String query) {
        String needle = query == null ? "" : query.strip().toLowerCase(java.util.Locale.ROOT);
        return entries.stream().filter(entry -> kind == null || entry.kinds().contains(kind))
                .filter(entry -> !keyOnly || entry.keyMoment())
                .filter(entry -> entry.caption().toLowerCase(java.util.Locale.ROOT).contains(needle)).toList();
    }

    /** Latest recorded round boundary at or before the position. */
    public RoundStart roundAt(int position) {
        RoundStart found = rounds.get(0);
        for (RoundStart round : rounds) {
            if (round.index() > position) break;
            found = round;
        }
        return found;
    }

    private static EnumSet<Kind> kinds(PlayerView before, PlayerView now) {
        EnumSet<Kind> kinds = EnumSet.noneOf(Kind.class);
        if (before == null) kinds.add(Kind.OPENING);
        if (before != null && (before.round() != now.round() || before.rounds().size() != now.rounds().size()))
            kinds.add(Kind.ROUND);
        if (now.phase() == MatchPhase.FINISHED && (before == null || before.phase() != MatchPhase.FINISHED))
            kinds.add(Kind.RESULT);
        if (before != null && now.revision() > before.revision() + 1) kinds.add(Kind.GAP);
        if (before != null && (before.selectedKing() != now.selectedKing()
                || !before.you().kings().kings().equals(now.you().kings().kings())
                || !before.opponent().kings().kings().equals(now.opponent().kings().kings()))) kinds.add(Kind.KING);
        if (before != null && now.lastPlay() != null && !now.lastPlay().equals(before.lastPlay())
                && now.lastPlay().card().def().kind() == CardKind.SPELL) kinds.add(Kind.SPELL);
        if (kinds.isEmpty()) kinds.add(Kind.ACTION);
        return kinds;
    }

    static String describe(PlayerView before, PlayerView now, String opponent) {
        if (before == null) return now.revision() == 0 ? "Opening deal" : "Start of recorded excerpt";
        var lines = new ArrayList<String>();
        for (var event : ReplayPlayback.added(before.events(), now.events())) {
            String actor = event.actor() == null ? "" : event.actor() == now.seat() ? "You: " : opponent + ": ";
            lines.add(actor + event.text());
        }
        return lines.isEmpty() ? "No logged change" : String.join("  ·  ", lines);
    }
}
