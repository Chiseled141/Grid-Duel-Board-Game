package deckduel.client.ui;

import deckduel.client.ScoringSounds.Cue.Kind;
import deckduel.client.ui.Staging.Tone;
import deckduel.core.card.CardDef;
import deckduel.core.card.CardInstance;
import deckduel.core.card.Effect;
import deckduel.core.card.LandmarkCard;
import deckduel.core.card.MatchPhase;
import deckduel.core.card.PairBonus;
import deckduel.core.card.PlayRecord;
import deckduel.core.card.PlayerView;
import deckduel.core.card.RoundResult;
import deckduel.core.card.Row;
import deckduel.core.card.RowCard;
import deckduel.core.card.Rules;
import deckduel.core.card.Suppression;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Turns one accepted change into a short staged sequence, in the order a player reads it:
 * the card travels, lands or casts, its target reacts, cards move between hands and decks, and
 * only then do the combos and score count up (the returned delay). Layered motion, sound and
 * particles follow the "juice" practice of games like Balatro and Hearthstone, while the rules
 * have already resolved on the server. Only public facts and the viewer's own hand are used.
 */
final class PlayAnimator {
    static final int FLIGHT = 480, LAND = 260, RISE = 420, CHARGE = 300, BOLT = 240, EJECT = 480;
    /** A draw is an unhurried glide (about 1.3 s at {@link Staging#TEMPO}); dealt cards follow each other closely. */
    static final int DRAW = 720, DEAL_GAP = 150;
    /** A sabotaged Landmark: the hammer's wind-up and swing, then the cracks spreading. */
    static final int SWING = 520, CRACK = 260;
    /** How long a card's landing ring and dust last. */
    static final int IMPACT = 560;

    /** Where things are on the table now. */
    interface Layout {
        Rectangle slot(TableZone zone, int slot);
        /** Where a player's Landmark {@code index} sits in their separate area. */
        Rectangle landmark(TableZone zone, int index);
        /** Where a card of {@code hand} rests in the hand as the player has arranged it, fan dip included. */
        Rectangle hand(List<CardInstance> hand, long cardId);
        /** The fan tilt of that hand card, in radians. */
        default double handAngle(List<CardInstance> hand, long cardId) { return 0; }
        Rectangle pile(TableZone zone);
        Rectangle stage();
        Rectangle opponentHand();
        Rectangle table();
    }

    /** The staged effects, and how long the table needs before the scoring trace should begin. */
    record Plan(Staging staging, int scoringDelay) { }

    private PlayAnimator() { }

    /** A round's start: the banner, then the ten-card hand dealt card by card. */
    static Plan opening(PlayerView view, Layout layout) {
        Staging s = new Staging();
        view.hand().forEach(card -> s.uses(card.def()));
        Rectangle table = layout.table();
        s.sound(0, Kind.ROUND);
        s.add(0, 1400, new Staging.Banner("Round " + view.round(), "Ten cards each · ten plays · three draws",
                table.getCenterX(), table.getCenterY(), Tone.GOLD, 46));
        deal(s, view.hand(), layout, 450);
        return new Plan(s, 0);
    }

    /** The card play that produced {@code after}, staged from the table as it was in {@code before}. */
    static Plan play(PlayerView before, PlayerView after, String opponent, Layout layout) {
        PlayRecord play = after.lastPlay();
        Staging s = new Staging();
        s.uses(play.card().def());
        if (play.replaced() != null) s.uses(play.replaced().def());
        if (play.removed() != null) s.uses(play.removed().def());
        after.hand().forEach(card -> s.uses(card.def()));
        boolean mine = play.actor() == after.seat();
        TableZone actorZone = mine ? TableZone.OWN : TableZone.OPPONENT;
        Rectangle from = mine ? layout.hand(before.hand(), play.card().id()) : layout.opponentHand();
        int settle = play.card().def().occupiesSpace()
                ? place(s, before, play, actorZone, from, layout)
                : cast(s, before, play, from, layout, !mine);
        boolean roundOver = after.round() != before.round();
        // Cards a Spell moved between hands and decks travel once the Spell has done its work.
        if (!roundOver) handMoves(s, before, after, play, layout, play.card().def().occupiesSpace() ? FLIGHT / 2 : settle);
        if (roundOver) clearAndDeal(s, play, after, opponent, actorZone, layout, settle + 650);
        if (after.phase() == MatchPhase.FINISHED && after.outcome().winner() == after.seat())
            s.add(settle + 500, 2600, new Staging.Confetti(layout.table(), after.revision()));
        return new Plan(s, settle);
    }

    /** A manual draw or a kept Reconnaissance card: only the hands changed. Null when nothing moved. */
    static Plan handChanges(PlayerView before, PlayerView after, Layout layout) {
        if (before.round() != after.round() || after.phase() != MatchPhase.IN_PROGRESS) return null;
        Staging s = new Staging();
        after.hand().forEach(card -> s.uses(card.def()));
        handMoves(s, before, after, null, layout, 0);
        return s.steps().isEmpty() ? null : new Plan(s, 0);
    }

    /** Carried to its space and set down; a replaced Unit is lifted out toward the discard pile. */
    private static int place(Staging s, PlayerView before, PlayRecord play, TableZone zone, Rectangle from, Layout layout) {
        CardDef card = play.card().def();
        Rectangle to = layout.slot(zone, play.slot());
        s.sound(0, Kind.WHOOSH);
        s.add(0, FLIGHT, new Staging.Flight(card, from, to, zone == TableZone.OPPONENT));
        RowCard placed = play.actorRow().at(play.slot());
        s.add(FLIGHT, LAND, new Staging.Landing(card, placed == null ? List.of() : placed.suppressed(), to));
        s.add(FLIGHT, IMPACT, new Staging.Impact(to, Tone.CREAM, play.card().id()));
        s.hideOnRow(play.card().id(), FLIGHT + LAND);
        if (play.replaced() != null) {
            RowCard old = row(before, zone).find(play.replaced().id());
            List<Suppression> marks = old == null ? List.of() : old.suppressed();
            s.add(0, FLIGHT - 120, new Staging.Hold(play.replaced().def(), marks, to));
            s.add(FLIGHT - 120, EJECT, new Staging.Eject(play.replaced().def(), marks, to, layout.pile(zone)));
        }
        return FLIGHT;
    }

    /**
     * A Spell rises to the middle of the table and charges. A Unit target is struck by a bolt and
     * shatters (Destroy), is chained (Disable) or is cleared with light (Clear Disable); an enemy
     * Landmark is struck by a hammer and cracks (Sabotage). Without a table target it dissolves.
     */
    private static int cast(Staging s, PlayerView before, PlayRecord play, Rectangle from, Layout layout, boolean flip) {
        CardDef spell = play.card().def();
        Target target = target(before, play, layout);
        Rectangle stage = stageAwayFrom(layout, target == null ? null : target.at());
        Effect effect = spell.effect();
        Tone tone = effect instanceof Effect.Destroy ? Tone.EMBER : effect instanceof Effect.ClearDisable ? Tone.CREAM : Tone.ARCANE;
        s.sound(0, Kind.WHOOSH);
        s.add(0, RISE, new Staging.Flight(spell, from, stage, flip));
        s.sound(RISE, Kind.SPELL);
        s.add(RISE, CHARGE, new Staging.Charge(spell, stage, tone));
        int launch = RISE + CHARGE;
        if (target == null) {
            s.add(launch, 460, new Staging.Dissolve(spell, stage, tone, play.card().id()));
            return launch;
        }
        if (effect instanceof Effect.Sabotage) return sabotage(s, play, target, stage, tone, launch);
        RowCard unit = target.unit();
        Rectangle at = target.at();
        int hit = launch + BOLT;
        s.add(launch, BOLT, new Staging.Charge(spell, stage, tone));
        s.add(launch, BOLT, new Staging.Bolt(new Point2D.Double(stage.getCenterX(), stage.getCenterY()),
                new Point2D.Double(at.getCenterX(), at.getCenterY()), tone));
        s.add(hit, 420, new Staging.Dissolve(spell, stage, tone, play.card().id()));
        s.add(hit, 460, new Staging.Ring(at.getCenterX(), at.getCenterY(), at.width * 1.1, tone));
        s.add(hit, 520, new Staging.Burst(at.getCenterX(), at.getCenterY(), tone, 22, at.width, play.targetId()));
        if (effect instanceof Effect.Destroy) {
            // The destroyed Unit is already gone from the view; show it until the bolt arrives.
            s.add(0, hit, new Staging.Hold(unit.def(), unit.suppressed(), at));
            s.sound(hit, Kind.IMPACT);
            s.add(hit, 760, new Staging.Shatter(unit.def(), unit.suppressed(), at, play.targetId()));
            s.shake(hit, 300, 7);
            // A brief hit-stop: the scoring waits a beat after the blow lands.
            return hit + 90;
        }
        RowCard changed = row(play, target.zone(), before).find(play.targetId());
        List<Suppression> marks = changed == null ? List.of() : changed.suppressed();
        s.add(0, hit, new Staging.Hold(unit.def(), unit.suppressed(), at));
        s.hideOnRow(play.targetId(), hit + 520);
        if (effect instanceof Effect.ClearDisable) {
            s.add(hit, 520, new Staging.Landing(unit.def(), marks, at));
            s.add(hit + 60, 560, new Staging.Shine(at));
            return hit + 40;
        }
        s.sound(hit, Kind.CHAIN);
        s.add(hit, 520, new Staging.Chains(unit.def(), marks, at));
        s.shake(hit, 200, 3);
        return hit + 40;
    }

    /** The Spell's power becomes a war hammer that strikes the enemy Landmark, which cracks; it stays shaded until the round ends. */
    private static int sabotage(Staging s, PlayRecord play, Target target, Rectangle stage, Tone tone, int launch) {
        int strike = launch + SWING;
        Rectangle at = target.at();
        CardDef landmark = target.landmark().def();
        s.add(launch, 420, new Staging.Dissolve(play.card().def(), stage, tone, play.card().id()));
        s.add(launch, SWING + 300, new Staging.Hammer(at, SWING));
        s.add(0, strike, new Staging.Hold(landmark, List.of(), at));
        s.hideOnRow(play.targetId(), strike + CRACK);
        s.sound(strike, Kind.IMPACT);
        s.shake(strike, 320, 9);
        s.add(strike, CRACK, new Staging.Cracks(landmark, List.of(), at, play.targetId()));
        s.add(strike, 480, new Staging.Ring(at.getCenterX(), at.getCenterY(), at.width * 1.2, Tone.CREAM));
        s.add(strike, 560, new Staging.Burst(at.getCenterX(), at.getCenterY(), Tone.CREAM, 26, at.width * .9, play.targetId()));
        return strike + CRACK + 60;
    }

    /** What a Spell targeted on the table: a Unit on a row or a Landmark in its area. */
    private record Target(TableZone zone, Rectangle at, RowCard unit, LandmarkCard landmark) { }

    private static Target target(PlayerView before, PlayRecord play, Layout layout) {
        if (play.targetId() == null) return null;
        for (TableZone zone : List.of(TableZone.OWN, TableZone.OPPONENT)) {
            RowCard unit = row(before, zone).find(play.targetId());
            if (unit != null) return new Target(zone, layout.slot(zone, unit.slot()), unit, null);
        }
        List<LandmarkCard> enemy = play.actor() == before.seat() ? before.opponent().landmarks() : before.you().landmarks();
        TableZone area = play.actor() == before.seat() ? TableZone.OPPONENT_LANDMARK : TableZone.OWN_LANDMARK;
        for (int i = 0; i < enemy.size(); i++)
            if (enemy.get(i).card().id() == play.targetId()) return new Target(area, layout.landmark(area, i), null, enemy.get(i));
        // An Exchange names a card in its caster's hand; that card's departure is staged with the hands.
        return null;
    }

    /**
     * Cards that changed hands outside the played card: cards arriving in the viewer's hand glide
     * from their deck (or from the opponent's hand when stolen), cards leaving it go to its discard
     * pile (or to the opponent when stolen), and the opponent's concealed gains arrive face down.
     */
    private static void handMoves(Staging s, PlayerView before, PlayerView after, PlayRecord play, Layout layout, int at) {
        boolean mine = play != null && play.actor() == after.seat();
        boolean stealing = play != null && play.card().def().effect() instanceof Effect.RandomSteal && play.handCard() != null;
        Set<Long> held = before.hand().stream().map(CardInstance::id).collect(Collectors.toSet());
        Set<Long> kept = after.hand().stream().map(CardInstance::id).collect(Collectors.toSet());
        for (CardInstance card : after.hand()) {
            if (held.contains(card.id())) continue;
            boolean stolen = stealing && mine && card.id() == play.handCard().id();
            Rectangle from = stolen ? layout.opponentHand() : layout.pile(TableZone.OWN);
            s.sound(at, Kind.DEAL);
            s.add(at, DRAW, new Staging.Flight(card.def(), from, layout.hand(after.hand(), card.id()), true, true,
                    layout.handAngle(after.hand(), card.id())));
            s.hideInHand(card.id(), at + DRAW);
        }
        for (CardInstance card : before.hand()) {
            if (kept.contains(card.id()) || play != null && card.id() == play.card().id()) continue;
            boolean taken = stealing && !mine && card.id() == play.handCard().id();
            Rectangle from = layout.hand(before.hand(), card.id());
            s.add(at, EJECT, new Staging.Eject(card.def(), List.of(), from, taken ? layout.opponentHand() : layout.pile(TableZone.OWN)));
        }
        int played = play != null && !mine ? 1 : 0, takenFromMe = stealing && !mine ? 1 : 0;
        int gained = after.opponent().handCount() - before.opponent().handCount() + played - takenFromMe;
        for (int i = 0; i < gained; i++) {
            s.sound(at + i * DEAL_GAP, Kind.DEAL);
            s.add(at + i * DEAL_GAP, DRAW, new Staging.Flight(null, layout.pile(TableZone.OPPONENT), layout.opponentHand(), false, true, 0));
        }
    }

    /** The finished round's rows stay until swept away; then the banner and a fresh deal. */
    private static void clearAndDeal(Staging s, PlayRecord play, PlayerView after, String opponent,
                                     TableZone actorZone, Layout layout, int clearAt) {
        TableZone otherZone = actorZone == TableZone.OWN ? TableZone.OPPONENT : TableZone.OWN;
        int index = 0;
        for (Map.Entry<TableZone, Row> side : Map.of(actorZone, play.actorRow(), otherZone, play.opponentRow()).entrySet()) {
            Rectangle pile = layout.pile(side.getKey());
            for (RowCard card : side.getValue().cards()) {
                Rectangle at = layout.slot(side.getKey(), card.slot());
                boolean justPlaced = card.card().id() == play.card().id();
                int from = justPlaced ? FLIGHT + LAND : 0;
                s.add(from, clearAt - from, new Staging.Hold(card.def(), card.suppressed(), at));
                s.add(clearAt + index * 45, EJECT, new Staging.Eject(card.def(), card.suppressed(), at, pile));
                index++;
            }
        }
        RoundResult last = after.rounds().get(after.rounds().size() - 1);
        Rectangle table = layout.table();
        int bannerAt = clearAt + 380;
        s.sound(bannerAt, Kind.ROUND);
        String score = "Round " + last.round() + ": you " + last.scoreOf(after.seat()) + " – " + last.scoreOf(after.seat().other()) + " " + opponent;
        s.add(bannerAt, 1700, new Staging.Banner("Round " + after.round(), score, table.getCenterX(), table.getCenterY(), Tone.GOLD, 46));
        deal(s, after.hand(), layout, bannerAt + 700);
    }

    private static void deal(Staging s, List<CardInstance> hand, Layout layout, int at) {
        for (int i = 0; i < hand.size(); i++) {
            int start = at + i * DEAL_GAP;
            long id = hand.get(i).id();
            s.sound(start, Kind.DEAL);
            s.add(start, DRAW, new Staging.Flight(hand.get(i).def(), layout.pile(TableZone.OWN), layout.hand(hand, id), true, true,
                    layout.handAngle(hand, id)));
            s.hideInHand(id, start + DRAW);
        }
    }

    /**
     * Combos, after the card has landed: each new pair link draws itself with its bonus popping,
     * a call-out for several pairs at once (bigger combos shake harder), and a light sweep across
     * Landmarks whose effect on the score changed.
     */
    static void combos(Staging s, ScoringTrace trace, int delay, Layout layout) {
        for (TableZone zone : List.of(TableZone.OWN, TableZone.OPPONENT)) {
            List<ScoringTrace.Link> links = trace.links.stream().filter(link -> link.zone() == zone).toList();
            for (ScoringTrace.Link link : links) {
                PairBonus pair = link.pair();
                Rectangle a = layout.slot(zone, pair.slotA()), b = layout.slot(zone, pair.slotB());
                double dip = Math.min(9, 3 + Math.abs(b.getCenterX() - a.getCenterX()) * .02);
                s.add(delay + link.at(), ScoringTrace.LINK_MS, new Staging.Link(new Point2D.Double(a.getCenterX(), a.getMaxY() - 3),
                        new Point2D.Double(b.getCenterX(), b.getMaxY() - 3), dip * 2, pair.points() >= 9 ? Tone.GOLD : Tone.CREAM,
                        "+" + pair.points()));
            }
            int bonus = links.stream().mapToInt(link -> link.pair().points()).sum();
            if (links.size() >= 2) {
                int at = delay + links.get(links.size() - 1).at() + 160;
                Rectangle first = layout.slot(zone, 0), last = layout.slot(zone, Row.SIZE - 1);
                double x = (first.getX() + last.getMaxX()) / 2, y = first.getCenterY();
                Tone tone = zone == TableZone.OWN ? Tone.GOLD : Tone.CREAM;
                s.add(at, 1150, new Staging.Banner("Combo ×" + links.size(), "+" + bonus + " combo points", x, y, tone,
                        links.size() >= 4 ? 40 : 32));
                s.add(at, 700, new Staging.Burst(x, y - 10, tone, 18 + links.size() * 4, 120 + bonus * 2, bonus));
                if (bonus >= 18) s.shake(at, 280, bonus >= 36 ? 7 : 4);
            }
            if (trace.landmarksChanged.contains(zone)) {
                TableZone area = zone == TableZone.OWN ? TableZone.OWN_LANDMARK : TableZone.OPPONENT_LANDMARK;
                for (int i = 0; i < Rules.LANDMARK_PICKS; i++) s.add(delay + trace.landmarkAt, 560, new Staging.Shine(layout.landmark(area, i)));
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Where a Spell charges: across the table from its target, so the strike visibly travels. */
    private static Rectangle stageAwayFrom(Layout layout, Rectangle target) {
        Rectangle stage = new Rectangle(layout.stage()), table = layout.table();
        if (target == null) return stage;
        int shift = (int) (table.width * .27) * (target.getCenterX() < table.getCenterX() ? 1 : -1);
        stage.translate(shift, 0);
        return stage;
    }

    private static Row row(PlayerView view, TableZone zone) {
        return zone == TableZone.OWN ? view.you().row() : view.opponent().row();
    }

    /** A row as the play left it, seen from the recipient's side of the table. */
    private static Row row(PlayRecord play, TableZone zone, PlayerView before) {
        boolean actorIsRecipient = play.actor() == before.seat();
        boolean ownRow = zone == TableZone.OWN;
        return ownRow == actorIsRecipient ? play.actorRow() : play.opponentRow();
    }
}
