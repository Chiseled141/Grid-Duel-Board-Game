package deckduel.client.ui;

import deckduel.client.ScoringSounds.Cue.Kind;
import deckduel.client.ui.Staging.Tone;
import deckduel.core.card.CardDef;
import deckduel.core.card.CardInstance;
import deckduel.core.card.Effect;
import deckduel.core.card.Formation;
import deckduel.core.card.MatchPhase;
import deckduel.core.card.PairBonus;
import deckduel.core.card.PlayRecord;
import deckduel.core.card.PlayerView;
import deckduel.core.card.RoundResult;
import deckduel.core.card.Row;
import deckduel.core.card.RowCard;
import deckduel.core.card.Suppression;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.ArrayList;
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
    /** A Disable Tags seal and the moment it stamps; a Random Steal thread and when it hooks its card. */
    static final int SEAL = 760, STAMP = 340, TETHER = 620, HOOK = 280;
    /** How long a discarded card burns, an Exchange vortex turns, Reconnaissance cards hover and a Spy's eye stays open. */
    static final int BURN = 760, VORTEX = 1000, PEEK = 1000, EYE = 1150;
    /** How long a card's landing ring and dust last. */
    static final int IMPACT = 560;
    /** A replaced Unit strains, rips in two and its halves fall away; it starts as the new card is thrown. */
    static final int TEAR = 700, TEAR_AT = 100;

    /** Where things are on the table now. */
    interface Layout {
        Rectangle slot(TableZone zone, int slot);
        /** Where a card of {@code hand} rests in the hand as the player has arranged it, fan dip included. */
        Rectangle hand(List<CardInstance> hand, long cardId);
        /** The fan tilt of that hand card, in radians. */
        default double handAngle(List<CardInstance> hand, long cardId) { return 0; }
        Rectangle pile(TableZone zone);
        Rectangle stage();
        Rectangle opponentHand();
        Rectangle table();
        /** Where a scoring chip for {@code pocket} lands on the score rail, in table pixels. */
        Point2D pocket(ScoreRail.Pocket pocket);
    }

    /** The staged effects, and how long the table needs before the scoring trace should begin. */
    record Plan(Staging staging, int scoringDelay) { }

    private PlayAnimator() { }

    /** A round's start: the banner, then the ten-card hand dealt card by card. */
    static Plan opening(PlayerView view, Layout layout) {
        Staging s = new Staging();
        view.hand().forEach(card -> s.uses(card.def()));
        s.sound(0, Kind.ROUND);
        Point2D gap = gap(layout);
        s.add(0, 1400, new Staging.Banner("Round " + view.round(), "Ten cards each · ten plays · three draws",
                gap.getX(), gap.getY(), Tone.GOLD, 46));
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

    /** Carried to its space and set down; a replaced Unit is torn apart, since it leaves the game. */
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
            s.add(0, TEAR_AT, new Staging.Hold(play.replaced().def(), marks, to));
            s.add(TEAR_AT, TEAR, new Staging.Tear(play.replaced().def(), marks, to, play.replaced().id()));
            s.sound(TEAR_AT + TEAR / 4, Kind.RIP);
        }
        return FLIGHT;
    }

    /**
     * A Spell rises to the middle of the table and charges in its own colour, then shows its own
     * effect, so no two Spells look alike: a bolt shatters a destroyed Unit, a flung chain binds
     * one (Disable Points), a rune seal stamps one grey (Disable Tags), a beam of light clears one,
     * a discarded card burns, a golden thread hooks a stolen
     * card, Draw dives into its deck and fountains, Exchange opens a vortex, Reconnaissance fans the
     * deck's top cards up and Spy opens an eye over the enemy hand. Without a target it dissolves.
     */
    private static int cast(Staging s, PlayerView before, PlayRecord play, Rectangle from, Layout layout, boolean flip) {
        CardDef spell = play.card().def();
        Effect effect = spell.effect();
        Target target = target(before, play, layout);
        Rectangle stage = stageAwayFrom(layout, target == null ? null : target.at());
        Tone tone = tone(effect);
        s.sound(0, Kind.WHOOSH);
        s.add(0, RISE, new Staging.Flight(spell, from, stage, flip));
        s.sound(RISE, Kind.SPELL);
        s.add(RISE, CHARGE, new Staging.Charge(spell, stage, tone));
        Cast cast = new Cast(s, before, play, layout, stage, tone, RISE + CHARGE);
        if (target != null) {
            if (effect instanceof Effect.Destroy) return destroy(cast, target);
            if (effect instanceof Effect.ClearDisable) return clear(cast, target);
            if (effect instanceof Effect.Disable disable)
                return disable.what() == Suppression.POINTS ? shackle(cast, target) : seal(cast, target);
        }
        boolean took = play.handCard() != null;
        if (took && effect instanceof Effect.RandomDiscard) return burn(cast);
        if (took && effect instanceof Effect.RandomSteal) return tether(cast);
        if (took && effect instanceof Effect.Exchange) return vortex(cast);
        if (effect instanceof Effect.Draw) return wellspring(cast);
        if (effect instanceof Effect.Reconnaissance recon && looked(cast, recon) > 0) return peek(cast, looked(cast, recon));
        if (effect instanceof Effect.Spy) return eye(cast);
        cast.spent(cast.launch());
        return cast.launch();
    }

    /** Each Spell family's colour, so the charge already tells them apart. */
    private static Tone tone(Effect effect) {
        if (effect instanceof Effect.Destroy) return Tone.CRIMSON;
        if (effect instanceof Effect.Disable disable) return disable.what() == Suppression.POINTS ? Tone.STEEL : Tone.ARCANE;
        if (effect instanceof Effect.ClearDisable) return Tone.CREAM;
        if (effect instanceof Effect.RandomDiscard) return Tone.EMBER;
        if (effect instanceof Effect.RandomSteal) return Tone.GOLD;
        if (effect instanceof Effect.Draw) return Tone.VERDANT;
        if (effect instanceof Effect.Exchange) return Tone.AZURE;
        // Reconnaissance and Spy, the information Spells.
        return Tone.TEAL;
    }

    /** One Spell being staged: where it charged, in which colour, and when its power is released. */
    private record Cast(Staging s, PlayerView before, PlayRecord play, Layout layout, Rectangle stage, Tone tone, int launch) {
        boolean mine() { return play.actor() == before.seat(); }
        /** The caster's side of the table. */
        TableZone zone() { return mine() ? TableZone.OWN : TableZone.OPPONENT; }
        /** The spent Spell fades into embers from {@code at}. */
        void spent(int at) { s.add(at, 460, new Staging.Dissolve(play.card().def(), stage, tone, play.card().id())); }
        /** The targeted card as it was, held until {@code until}; the effect draws it until {@code hidden}. */
        void hold(Target target, int until, int hidden) {
            RowCard unit = target.unit();
            s.add(0, until, new Staging.Hold(unit.def(), unit.suppressed(), target.at()));
            s.hideOnRow(play.targetId(), hidden);
        }
        /** The targeted Unit's disable marks once the Spell has resolved. */
        List<Suppression> marksAfter(Target target) {
            RowCard changed = row(play, target.zone(), before).find(play.targetId());
            return changed == null ? List.of() : changed.suppressed();
        }
    }

    /** A crimson bolt arcs into the Unit, which shatters with a soft flash and a brief hit-stop. */
    private static int destroy(Cast c, Target target) {
        Staging s = c.s();
        RowCard unit = target.unit();
        Rectangle at = target.at();
        int hit = c.launch() + BOLT;
        s.add(c.launch(), BOLT, new Staging.Charge(c.play().card().def(), c.stage(), c.tone()));
        s.add(c.launch(), BOLT, new Staging.Bolt(centre(c.stage()), centre(at), c.tone()));
        c.spent(hit);
        // The destroyed Unit is already gone from the view; show it until the bolt arrives.
        s.add(0, hit, new Staging.Hold(unit.def(), unit.suppressed(), at));
        s.add(hit, 460, new Staging.Ring(at.getCenterX(), at.getCenterY(), at.width * 1.1, c.tone()));
        s.add(hit, 520, new Staging.Burst(at.getCenterX(), at.getCenterY(), c.tone(), 22, at.width, c.play().targetId()));
        s.sound(hit, Kind.IMPACT);
        s.add(hit, 760, new Staging.Shatter(unit.def(), unit.suppressed(), at, c.play().targetId()));
        return hit + 90;
    }

    /** A chain is flung from the Spell and binds the Unit, which shudders under a grey veil. */
    private static int shackle(Cast c, Target target) {
        Staging s = c.s();
        int hit = c.launch() + BOLT;
        s.add(c.launch(), BOLT, new Staging.Lash(centre(c.stage()), centre(target.at())));
        c.spent(hit);
        c.hold(target, hit, hit + 520);
        s.sound(hit, Kind.CHAIN);
        s.add(hit, 520, new Staging.Chains(target.unit().def(), c.marksAfter(target), target.at()));
        return hit + 40;
    }

    /** A rune seal descends on the Unit and stamps it; its colour drains as its tags fall silent. */
    private static int seal(Cast c, Target target) {
        Staging s = c.s();
        RowCard unit = target.unit();
        int stamp = c.launch() + STAMP;
        c.spent(c.launch());
        c.hold(target, c.launch(), c.launch() + SEAL);
        s.add(c.launch(), SEAL, new Staging.Sigil(unit.def(), unit.suppressed(), c.marksAfter(target), target.at(), STAMP));
        s.sound(stamp, Kind.SEAL);
        return stamp + 60;
    }

    /** A column of light falls on the Unit and lifts its disables; a light sweep follows. */
    private static int clear(Cast c, Target target) {
        Staging s = c.s();
        Rectangle at = target.at();
        int hit = c.launch() + 260;
        c.spent(c.launch());
        c.hold(target, hit, hit + 520);
        s.add(hit, 520, new Staging.Landing(target.unit().def(), c.marksAfter(target), at));
        s.add(c.launch(), 780, new Staging.Beam(at, c.play().targetId()));
        s.add(hit + 60, 560, new Staging.Shine(at));
        s.sound(hit, Kind.CHIME);
        return hit + 40;
    }

    /** The discarded card is torn from the enemy hand, shown to both players, and burns away beside the Spell. */
    private static int burn(Cast c) {
        Staging s = c.s();
        CardInstance card = c.play().handCard();
        Rectangle from = c.mine() ? c.layout().opponentHand() : c.layout().hand(c.before().hand(), card.id());
        Rectangle stage = c.stage();
        Rectangle pyre = new Rectangle(stage.x + stage.width * 13 / 10, stage.y + stage.height / 10, stage.width * 4 / 5, stage.height * 4 / 5);
        c.spent(c.launch());
        s.add(c.launch(), FLIGHT, new Staging.Flight(card.def(), from, pyre, c.mine()));
        int lit = c.launch() + FLIGHT;
        s.sound(lit, Kind.FLAME);
        s.add(lit, BURN, new Staging.Burn(card.def(), pyre, card.id()));
        return lit + 200;
    }

    /** A golden thread is cast into the enemy hand and hooks a card, which then crosses to the thief. */
    private static int tether(Cast c) {
        Staging s = c.s();
        Rectangle hand = c.mine() ? c.layout().opponentHand() : c.layout().hand(c.before().hand(), c.play().handCard().id());
        int hook = c.launch() + HOOK;
        s.add(c.launch(), TETHER, new Staging.Tether(centre(c.stage()), centre(hand)));
        c.spent(hook);
        s.sound(hook, Kind.GLINT);
        return hook;
    }

    /** The Spell dives into its caster's deck, which fountains as it gives up the drawn cards. */
    private static int wellspring(Cast c) {
        Staging s = c.s();
        Rectangle pile = c.layout().pile(c.zone());
        s.add(c.launch(), 420, new Staging.Eject(c.play().card().def(), List.of(), c.stage(), pile));
        s.sound(c.launch() + 380, Kind.CHIME);
        s.add(c.launch() + 360, 760, new Staging.Wellspring(pile, c.tone(), c.play().card().id()));
        return c.launch() + 420;
    }

    /** A vortex swallows the Spell and the card given up; the new cards come out of it (staged with the hands). */
    private static int vortex(Cast c) {
        Staging s = c.s();
        CardInstance card = c.play().handCard();
        Rectangle from = c.mine() ? c.layout().hand(c.before().hand(), card.id()) : c.layout().opponentHand();
        s.sound(c.launch(), Kind.WARP);
        s.add(c.launch(), VORTEX, new Staging.Vortex(c.stage(), c.tone()));
        s.add(c.launch(), 520, new Staging.Spiral(c.play().card().def(), c.stage(), c.stage()));
        s.add(c.launch() + 120, 640, new Staging.Spiral(card.def(), from, c.stage()));
        return c.launch() + 520;
    }

    /** How many cards a Reconnaissance could look at: the caster's deck may hold fewer. */
    private static int looked(Cast c, Effect.Reconnaissance recon) {
        return Math.min(recon.look(), c.mine() ? c.before().you().deckCount() : c.before().opponent().deckCount());
    }

    /** The top cards of the caster's deck fan up face down, are looked over and sink back. */
    private static int peek(Cast c, int count) {
        c.spent(c.launch());
        c.s().sound(c.launch() + 120, Kind.GLINT);
        c.s().add(c.launch(), PEEK, new Staging.Peek(c.layout().pile(c.zone()), count));
        return c.launch() + 300;
    }

    /** An eye opens over the enemy hand and looks it over; only the caster's view lists what it saw. */
    private static int eye(Cast c) {
        Rectangle hand = c.mine() ? c.layout().opponentHand() : handArea(c.before(), c.layout());
        double width = c.layout().stage().width * 1.1;
        double y = c.mine() ? hand.getMaxY() + width * .32 : hand.y - width * .3;
        c.spent(c.launch());
        c.s().sound(c.launch() + 200, Kind.GLINT);
        c.s().add(c.launch(), EYE, new Staging.Eye(hand.getCenterX(), y, width));
        return c.launch() + 300;
    }

    /** The space the viewer's whole hand covers, or the stage when it is empty. */
    private static Rectangle handArea(PlayerView view, Layout layout) {
        Rectangle area = null;
        for (CardInstance card : view.hand()) {
            Rectangle at = layout.hand(view.hand(), card.id());
            area = area == null ? at : area.union(at);
        }
        return area == null ? layout.stage() : area;
    }

    /** What a Spell targeted on the table: a Unit on a row. */
    private record Target(TableZone zone, Rectangle at, RowCard unit) { }

    private static Target target(PlayerView before, PlayRecord play, Layout layout) {
        if (play.targetId() == null) return null;
        for (TableZone zone : List.of(TableZone.OWN, TableZone.OPPONENT)) {
            RowCard unit = row(before, zone).find(play.targetId());
            if (unit != null) return new Target(zone, layout.slot(zone, unit.slot()), unit);
        }
        // An Exchange names a card in its caster's hand; that card's departure is staged with the hands.
        return null;
    }

    /**
     * Cards that changed hands outside the played card: cards arriving in the viewer's hand glide
     * from their deck (out of the vortex for an Exchange, or from the opponent's hand when stolen),
     * cards leaving it go to its discard pile (or to the opponent when stolen), and the opponent's
     * concealed gains arrive face down. A burnt or exchanged card is staged by its Spell instead.
     */
    private static void handMoves(Staging s, PlayerView before, PlayerView after, PlayRecord play, Layout layout, int at) {
        boolean mine = play != null && play.actor() == after.seat();
        Effect effect = play == null ? null : play.card().def().effect();
        CardInstance taken = play == null ? null : play.handCard();
        boolean stealing = taken != null && effect instanceof Effect.RandomSteal;
        boolean spellStaged = taken != null && (effect instanceof Effect.Exchange || effect instanceof Effect.RandomDiscard);
        Rectangle deck = taken != null && effect instanceof Effect.Exchange ? layout.stage() : null;
        Set<Long> held = before.hand().stream().map(CardInstance::id).collect(Collectors.toSet());
        Set<Long> kept = after.hand().stream().map(CardInstance::id).collect(Collectors.toSet());
        for (CardInstance card : after.hand()) {
            if (held.contains(card.id())) continue;
            boolean stolen = stealing && mine && card.id() == taken.id();
            Rectangle from = stolen ? layout.opponentHand() : deck != null ? deck : layout.pile(TableZone.OWN);
            s.sound(at, Kind.DEAL);
            s.add(at, DRAW, new Staging.Flight(card.def(), from, layout.hand(after.hand(), card.id()), true, true,
                    layout.handAngle(after.hand(), card.id())));
            s.hideInHand(card.id(), at + DRAW);
        }
        for (CardInstance card : before.hand()) {
            if (kept.contains(card.id()) || play != null && card.id() == play.card().id()) continue;
            if (spellStaged && card.id() == taken.id()) continue;
            boolean lost = stealing && !mine && card.id() == taken.id();
            Rectangle from = layout.hand(before.hand(), card.id());
            s.add(at, EJECT, new Staging.Eject(card.def(), List.of(), from, lost ? layout.opponentHand() : layout.pile(TableZone.OWN)));
        }
        // The opponent's own play reports what it drew; otherwise only their hand count says.
        int gained = play != null && !mine ? play.drawn() : play != null ? 0 : after.opponent().handCount() - before.opponent().handCount();
        for (int i = 0; i < gained; i++) {
            s.sound(at + i * DEAL_GAP, Kind.DEAL);
            s.add(at + i * DEAL_GAP, DRAW, new Staging.Flight(null, deck != null ? deck : layout.pile(TableZone.OPPONENT),
                    layout.opponentHand(), false, true, 0));
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
        int bannerAt = clearAt + 380;
        s.sound(bannerAt, Kind.ROUND);
        String score = "Round " + last.round() + ": you " + last.scoreOf(after.seat()) + " – " + last.scoreOf(after.seat().other()) + " " + opponent;
        Point2D gap = gap(layout);
        s.add(bannerAt, 1700, new Staging.Banner("Round " + after.round(), score, gap.getX(), gap.getY(), Tone.GOLD, 46));
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
     * Combos, after the card has landed: the Units' change pops up as a chip over the card, each
     * new pair link draws itself and its bonus pops as a chip (gold for a +9 pair), and every chip
     * is flung onto its place on the score rail in turn, chinking a little higher each time. A new
     * formation is named in a slim callout above its row, never over the cards, and its bonus and
     * multiplier are flung onto the rail. Returns when each chip lands, for the rail.
     */
    static List<ScoreRail.Tally> combos(Staging s, ScoringTrace trace, int delay, Layout layout) {
        List<ScoreRail.Tally> tallies = new ArrayList<>();
        for (TableZone zone : List.of(TableZone.OWN, TableZone.OPPONENT)) {
            boolean own = zone == TableZone.OWN;
            int[] step = {0};
            long units = trace.unitGain.get(zone);
            if (units != 0) {
                int slot = trace.unitSlot.get(zone);
                Rectangle from = slot >= 0 ? layout.slot(zone, slot) : rowSpan(zone, layout);
                chip(s, tallies, layout, delay, own ? ScoreRail.Pocket.UNITS : ScoreRail.Pocket.OPPONENT, units,
                        new Point2D.Double(from.getCenterX(), from.y + 14), units > 0 ? Tone.AZURE : Tone.ASH, false, step);
            }
            List<ScoringTrace.Link> links = trace.links.stream().filter(link -> link.zone() == zone).toList();
            for (ScoringTrace.Link link : links) {
                PairBonus pair = link.pair();
                Rectangle a = layout.slot(zone, pair.slotA()), b = layout.slot(zone, pair.slotB());
                double dip = Math.min(9, 3 + Math.abs(b.getCenterX() - a.getCenterX()) * .02);
                boolean big = pair.points() >= 9;
                Point2D left = new Point2D.Double(a.getCenterX(), a.getMaxY() - 3), right = new Point2D.Double(b.getCenterX(), b.getMaxY() - 3);
                s.add(delay + link.at(), ScoringTrace.LINK_MS, new Staging.Link(left, right, dip * 2, big ? Tone.GOLD : Tone.CREAM, null));
                chip(s, tallies, layout, delay + link.at() + ScoringTrace.CHIP_POP_MS, own ? ScoreRail.Pocket.COMBOS : ScoreRail.Pocket.OPPONENT,
                        pair.points(), new Point2D.Double((left.getX() + right.getX()) / 2, left.getY() + dip * 1.2 + 4),
                        big ? Tone.GOLD : Tone.CRIMSON, big, step);
            }
            Formation formation = trace.formations.get(zone);
            if (formation != null) formation(s, tallies, layout, zone, formation, delay + trace.formationAt, step);
        }
        return tallies;
    }

    /** A formation's name, bonus and multiplier called out just above its row, then flung onto the rail. */
    private static void formation(Staging s, List<ScoreRail.Tally> tallies, Layout layout, TableZone zone, Formation formation,
                                  int at, int[] step) {
        boolean own = zone == TableZone.OWN;
        Rectangle row = rowSpan(zone, layout);
        Point2D above = new Point2D.Double(row.getCenterX(), row.y - 4);
        String value = "+" + formation.bonus() + "  ×" + formation.multiplier();
        Tone tone = formation.multiplier() > 1 ? Tone.GOLD : Tone.CREAM;
        s.sound(at, Kind.CHIME);
        s.add(at, ScoringTrace.FORMATION_MS + 300, new Staging.Callout(formation.title(), value, above, tone));
        int fling = at + ScoringTrace.FORMATION_MS - ScoringTrace.CHIP_MS;
        s.add(fling, ScoringTrace.CHIP_MS, new Staging.Chip(value, new Point2D.Double(above.getX(), above.getY() - 14),
                layout.pocket(own ? ScoreRail.Pocket.FORMATION : ScoreRail.Pocket.OPPONENT), tone, true));
        s.sound(fling + ScoringTrace.CHIP_MS, Kind.TALLY, step[0]++);
        tallies.add(new ScoreRail.Tally(fling + ScoringTrace.CHIP_MS, own ? ScoreRail.Pocket.FORMATION : ScoreRail.Pocket.OPPONENT, 0));
    }

    /** One chip worth {@code points}, flung from {@code from} to {@code pocket}; it lands with a chink pitched by its {@code step}. */
    private static void chip(Staging s, List<ScoreRail.Tally> tallies, Layout layout, int at, ScoreRail.Pocket pocket, long points,
                             Point2D from, Tone tone, boolean big, int[] step) {
        String text = (points > 0 ? "+" : "−") + Math.abs(points);
        s.add(at, ScoringTrace.CHIP_MS, new Staging.Chip(text, from, layout.pocket(pocket), tone, big));
        int landed = at + ScoringTrace.CHIP_MS;
        s.sound(landed, Kind.TALLY, step[0]++);
        tallies.add(new ScoreRail.Tally(landed, pocket, points));
    }

    /** The middle of the gap between the two rows, where notifications sit without covering cards. */
    private static Point2D gap(Layout layout) {
        Rectangle upper = layout.slot(TableZone.OPPONENT, 0), lower = layout.slot(TableZone.OWN, 0);
        return new Point2D.Double(rowSpan(TableZone.OWN, layout).getCenterX(), (upper.getMaxY() + lower.y) / 2.0);
    }

    /** The whole of a Unit row, from its first space to its last. */
    private static Rectangle rowSpan(TableZone zone, Layout layout) {
        return layout.slot(zone, 0).union(layout.slot(zone, Row.SIZE - 1));
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

    private static Point2D centre(Rectangle r) { return new Point2D.Double(r.getCenterX(), r.getCenterY()); }

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
