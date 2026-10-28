package deckduel.client.state;

import deckduel.core.card.PlayerView;
import deckduel.core.card.ScoreBreakdown;
import deckduel.core.card.SideView;
import deckduel.core.card.King;
import java.util.ArrayList;
import java.util.List;

/** Explains score differences between adjacent stored frames, without rerunning game rules. */
public record ReplayScoreChange(String context, Side you, Side opponent) {
    /** A component's raw before/after values and signed difference. */
    public record Component(String title, long before, long after) {
        public long delta() { return after - before; }
        public String description() {
            return title + ": " + before + " -> " + after + " (" + signed(delta()) + ")";
        }
    }

    /** Raw components plus the separate multiplier effect, so contributions add up exactly. */
    public record Side(List<Component> components, long before, long after, long bankedBefore, long bankedAfter,
                       String formationBefore, String formationAfter, long multiplierBefore, long multiplierAfter,
                       List<Component> kingChanges) {
        public Side { components = List.copyOf(components); kingChanges = List.copyOf(kingChanges); }
        public long delta() { return after - before; }
        public long bankedDelta() { return bankedAfter - bankedBefore; }
    }

    /** Opening frames have no earlier evidence; boundaries are explicitly described as resets. */
    public static ReplayScoreChange between(PlayerView before, PlayerView after) {
        String context;
        if (before == null) context = "Opening snapshot · no previous recorded action";
        else if (before.round() != after.round())
            context = "Round " + before.round() + " -> " + after.round() + " · rows reset and scores banked";
        else if (after.rounds().size() > before.rounds().size())
            context = "Round completed · scores banked";
        else context = "Change since the previous recorded action";
        if (before != null && after.revision() > before.revision() + 1)
            context += " · missing revisions; this may include multiple actions";
        return new ReplayScoreChange(context, side(before == null ? null : before.you(), after.you()),
                side(before == null ? null : before.opponent(), after.opponent()));
    }

    private static Side side(SideView before, SideView after) {
        ScoreBreakdown old = before == null ? after.score() : before.score();
        ScoreBreakdown now = after.score();
        List<Component> components = List.of(
                new Component("Units", old.units(), now.units()),
                new Component("Pair combos", old.combos(), now.combos()),
                new Component("Formation bonus", old.formationBonus(), now.formationBonus()),
                new Component("Multiplier effect", multiplierEffect(old), multiplierEffect(now)),
                new Component("Seven nations", old.bonus(), now.bonus()),
                new Component("Kings", old.kingPoints(), now.kingPoints()));
        return new Side(components, old.total(), now.total(),
                before == null ? after.matchTotal() : before.matchTotal(), after.matchTotal(),
                formation(old), formation(now), old.multiplier(), now.multiplier(), kingChanges(old, now));
    }

    private static String formation(ScoreBreakdown score) {
        return score.formation() == null ? "None" : score.formation().title();
    }

    private static List<Component> kingChanges(ScoreBreakdown before, ScoreBreakdown after) {
        var changes = new ArrayList<Component>();
        for (King king : King.values()) {
            boolean present = before.kings().stream().anyMatch(bonus -> bonus.king() == king)
                    || after.kings().stream().anyMatch(bonus -> bonus.king() == king);
            if (present) changes.add(new Component(king.title(),
                    before.kings().stream().filter(bonus -> bonus.king() == king).mapToLong(bonus -> bonus.points()).sum(),
                    after.kings().stream().filter(bonus -> bonus.king() == king).mapToLong(bonus -> bonus.points()).sum()));
        }
        return List.copyOf(changes);
    }

    private static long multiplierEffect(ScoreBreakdown score) {
        return (score.units() + score.combos() + score.formationBonus()) * (score.multiplier() - 1);
    }

    /** A plus sign for gains and zero, a minus sign for losses. */
    public static String signed(long value) { return value >= 0 ? "+" + value : Long.toString(value); }
}
