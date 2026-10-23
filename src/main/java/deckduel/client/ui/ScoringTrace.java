package deckduel.client.ui;

import deckduel.client.ScoringSounds;
import deckduel.core.card.Formation;
import deckduel.core.card.PairBonus;
import deckduel.core.card.PlayerView;
import deckduel.core.card.Row;
import deckduel.core.card.SideView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The card-by-card explanation of one accepted change between two public views: which cards
 * changed, which pairs are new, which rows' formations changed, and when each lights up.
 * It is presentation only; the totals always come from the server's view.
 */
final class ScoringTrace {
    static final int GLOW_MS = 650, FIRST_PAIR_MS = 260, PAIR_GAP_MS = 150, LINK_MS = 1100, FORMATION_MS = 900;
    /**
     * A pair's chip pops this long after its link starts drawing, as the link reaches the second
     * card and both cards jump; then it takes {@code CHIP_MS} to reach the score rail.
     */
    static final int CHIP_POP_MS = 340, CHIP_MS = 600;
    /** Pairs beyond this light together, so a huge combo never makes the trace drag. */
    private static final int PAIR_STEPS = 10;

    record Link(TableZone zone, PairBonus pair, int at) { }
    record Beat(int at, ScoringSounds.Cue cue) { }

    final Map<TableZone, Set<Integer>> changed = new EnumMap<>(TableZone.class);
    /** Rows (OWN, OPPONENT) whose best formation changed, so it is called out after the pairs. */
    final Set<TableZone> formationChanged = java.util.EnumSet.noneOf(TableZone.class);
    /** The formation each changed row now makes; null when it lost its formation. */
    final Map<TableZone, Formation> formations = new EnumMap<>(TableZone.class);
    final List<Link> links = new ArrayList<>();
    final List<Beat> beats = new ArrayList<>();
    /** Each row's change in Unit points, and the changed space its chip rises from (-1: the row as a whole). */
    final Map<TableZone, Long> unitGain = new EnumMap<>(TableZone.class);
    final Map<TableZone, Integer> unitSlot = new EnumMap<>(TableZone.class);
    final int formationAt, finishAt;
    final long ownGain, opponentGain;

    private ScoringTrace(PlayerView before, PlayerView after) {
        ownGain = after.you().score().total() - before.you().score().total();
        opponentGain = after.opponent().score().total() - before.opponent().score().total();
        int at = FIRST_PAIR_MS, step = 0;
        for (TableZone zone : List.of(TableZone.OWN, TableZone.OPPONENT)) {
            SideView was = zone == TableZone.OWN ? before.you() : before.opponent();
            SideView now = zone == TableZone.OWN ? after.you() : after.opponent();
            changed.put(zone, changedSlots(was.row(), now.row()));
            unitGain.put(zone, now.score().units() - was.score().units());
            unitSlot.put(zone, changed.get(zone).stream().min(Integer::compare).orElse(-1));
            Set<String> old = new HashSet<>();
            for (PairBonus pair : was.score().pairs()) old.add(key(was.row(), pair));
            for (PairBonus pair : now.score().pairs()) {
                if (old.contains(key(now.row(), pair))) continue;
                links.add(new Link(zone, pair, at));
                if (step < PAIR_STEPS) { beats.add(new Beat(at, new ScoringSounds.Cue(ScoringSounds.Cue.Kind.PAIR, step))); at += PAIR_GAP_MS; }
                step++;
            }
            if (was.score().formation() != now.score().formation()) {
                formationChanged.add(zone);
                formations.put(zone, now.score().formation());
            }
        }
        formationAt = links.isEmpty() ? FIRST_PAIR_MS : at;
        // The score counts once the last chip has reached the rail.
        int landed = unitGain.values().stream().anyMatch(gain -> gain != 0) ? CHIP_MS : 0;
        for (Link link : links) landed = Math.max(landed, link.at() + CHIP_POP_MS + CHIP_MS);
        finishAt = Math.max(formationAt + (formationChanged.isEmpty() ? 0 : FORMATION_MS) + 80, landed + 60);
        beats.add(0, new Beat(0, new ScoringSounds.Cue(ScoringSounds.Cue.Kind.CARD, 0)));
        if (ownGain != 0) beats.add(new Beat(finishAt, new ScoringSounds.Cue(
                ownGain > 0 ? ScoringSounds.Cue.Kind.GAIN : ScoringSounds.Cue.Kind.LOSS, 0)));
        beats.sort(Comparator.comparingInt(Beat::at));
    }

    /** The trace of a change within one round, or null when neither table changed. */
    static ScoringTrace between(PlayerView before, PlayerView after) {
        if (before.round() != after.round()) return null;
        boolean same = before.you().row().equals(after.you().row()) && before.opponent().row().equals(after.opponent().row());
        return same ? null : new ScoringTrace(before, after);
    }

    private static Set<Integer> changedSlots(Row was, Row now) {
        Set<Integer> slots = new HashSet<>();
        for (int slot = 0; slot < Row.SIZE; slot++)
            if (!Objects.equals(was.at(slot), now.at(slot)) && now.at(slot) != null) slots.add(slot);
        return slots;
    }

    /** Identifies a pair by its two cards, so replacing a card in a space makes its pairs new. */
    private static String key(Row row, PairBonus pair) {
        return row.at(pair.slotA()).card().id() + "-" + row.at(pair.slotB()).card().id();
    }

    /** The link for a pair on a row, or null when that pair existed before this change. */
    Link link(TableZone zone, PairBonus pair) {
        for (Link link : links) if (link.zone() == zone && link.pair().equals(pair)) return link;
        return null;
    }
}
