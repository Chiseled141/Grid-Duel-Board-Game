package deckduel.client.ui;

import deckduel.client.ScoringSounds.Cue.Kind;
import deckduel.core.card.CardDef;
import deckduel.core.card.Suppression;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One planned table sequence: timed effects and sounds, written at base speed in
 * animation milliseconds. It is plain data, so the plan can be checked without a screen; the
 * JavaFX {@link TableEffects} performs it over the authoritative view, which is already final.
 */
final class Staging {
    /**
     * Real milliseconds per animation millisecond. Every timing in a plan is written at base
     * speed; this one factor slows the whole sequence evenly, so effects stay in step.
     */
    static final double TEMPO = 1.8;

    /**
     * Effect colours, named for what they mean on the table; each Spell family charges in its own.
     * They stay close to the painted props' own colours, so the light matches the art it surrounds.
     */
    enum Tone {
        EMBER(0xE0783A), ARCANE(0x8A6BA6), CREAM(0xEDE3CF), GOLD(0xD9B56A),
        CRIMSON(0xC2443A), STEEL(0x9AA6B4), VERDANT(0x7D9966), AZURE(0x5B7FA3), TEAL(0x5A8F86), ASH(0x9A9088);
        final int rgb;
        Tone(int rgb) { this.rgb = rgb; }
    }

    /** What one step shows; geometry is in table pixels. */
    sealed interface Effect { }
    /** A card held still where the view has already moved on from it. */
    record Hold(CardDef card, List<Suppression> marks, Rectangle at) implements Effect {
        Hold { marks = List.copyOf(marks); at = new Rectangle(at); }
    }
    /**
     * A card carried between two places; {@code face} null is a concealed back. A {@code glide} is
     * the gentle draw into the hand, ending at the hand's tilt {@code angle} (radians); otherwise
     * the card is thrown with a spring to a level landing.
     */
    record Flight(CardDef face, Rectangle from, Rectangle to, boolean flip, boolean glide, double angle) implements Effect {
        Flight { from = new Rectangle(from); to = new Rectangle(to); }

        /** A thrown card, landing level. */
        Flight(CardDef face, Rectangle from, Rectangle to, boolean flip) { this(face, from, to, flip, false, 0); }
    }
    /** A card settling into its space. */
    record Landing(CardDef card, List<Suppression> marks, Rectangle at) implements Effect {
        Landing { marks = List.copyOf(marks); at = new Rectangle(at); }
    }
    /** A card meeting the table: a soft ring across the tabletop and a little dust. */
    record Impact(Rectangle at, Tone tone, long seed) implements Effect {
        Impact { at = new Rectangle(at); }
    }
    /** A card leaving play for its owner's discard pile. */
    record Eject(CardDef card, List<Suppression> marks, Rectangle from, Rectangle pile) implements Effect {
        Eject { marks = List.copyOf(marks); from = new Rectangle(from); pile = new Rectangle(pile); }
    }
    /** A Spell held aloft, gathering power. */
    record Charge(CardDef spell, Rectangle at, Tone tone) implements Effect {
        Charge { at = new Rectangle(at); }
    }
    /** A war axe swung down onto a Unit (Destroy Unit): it winds up, chops and bites at {@link PlayAnimator#CHOP_HIT}. */
    record Chop(Rectangle at) implements Effect {
        Chop { at = new Rectangle(at); }
    }
    /** The table's cards and effects shaken by a heavy blow, {@code strength} pixels at first. */
    record Shake(double strength) implements Effect { }
    /** A spent Spell fading into embers. */
    record Dissolve(CardDef spell, Rectangle at, Tone tone, long seed) implements Effect {
        Dissolve { at = new Rectangle(at); }
    }
    /** An expanding ring at an impact. */
    record Ring(double x, double y, double radius, Tone tone) implements Effect { }
    /** Sparks thrown from a point. */
    record Burst(double x, double y, Tone tone, int count, double reach, long seed) implements Effect { }
    /** A removed card blown apart. */
    record Shatter(CardDef card, List<Suppression> marks, Rectangle at, long seed) implements Effect {
        Shatter { marks = List.copyOf(marks); at = new Rectangle(at); }
    }
    /** A replaced Unit torn in two and gone for good: it never reaches the discard pile. */
    record Tear(CardDef card, List<Suppression> marks, Rectangle at, long seed) implements Effect {
        Tear { marks = List.copyOf(marks); at = new Rectangle(at); }
    }
    /** A disable taking hold of a card. */
    record Chains(CardDef card, List<Suppression> marks, Rectangle at) implements Effect {
        Chains { marks = List.copyOf(marks); at = new Rectangle(at); }
    }
    /** A chain flung from a Spell to the Unit it will bind (Disable Points). */
    record Lash(Point2D from, Point2D to) implements Effect {
        Lash { from = (Point2D) from.clone(); to = (Point2D) to.clone(); }
    }
    /**
     * A rune seal descending on a Unit and stamping it {@code stamp} milliseconds into the step,
     * draining its colour; the card shows {@code marks} from then on (Disable Tags).
     */
    record Sigil(CardDef card, List<Suppression> was, List<Suppression> marks, Rectangle at, int stamp) implements Effect {
        Sigil { was = List.copyOf(was); marks = List.copyOf(marks); at = new Rectangle(at); }
    }
    /** A column of light falling on a card, with motes rising through it (Clear Disable). */
    record Beam(Rectangle at, long seed) implements Effect {
        Beam { at = new Rectangle(at); }
    }
    /** A card burning away from its bottom edge, throwing embers (Random Discard). */
    record Burn(CardDef card, Rectangle at, long seed) implements Effect {
        Burn { at = new Rectangle(at); }
    }
    /** A brass wire cast from a Spell that hooks a card in a hand, then snaps taut (Random Steal). */
    record Tether(Point2D from, Point2D hook) implements Effect {
        Tether { from = (Point2D) from.clone(); hook = (Point2D) hook.clone(); }
    }
    /** Motes fountaining up from a deck as it gives up cards (Draw). */
    record Wellspring(Rectangle pile, Tone tone, long seed) implements Effect {
        Wellspring { pile = new Rectangle(pile); }
    }
    /** A turning vortex that swallows cards and gives others back (Exchange). */
    record Vortex(Rectangle at, Tone tone) implements Effect {
        Vortex { at = new Rectangle(at); }
    }
    /** A card spiralling from {@code from} into the middle of {@code into}, shrinking as it turns. */
    record Spiral(CardDef card, Rectangle from, Rectangle into) implements Effect {
        Spiral { from = new Rectangle(from); into = new Rectangle(into); }
    }
    /** Card backs fanning up from a deck to be looked over, then sinking back (Reconnaissance). */
    record Peek(Rectangle pile, int count) implements Effect {
        Peek { pile = new Rectangle(pile); }
    }
    /** An eye that opens over a hand, looks it over and closes (Spy). */
    record Eye(double x, double y, double width) implements Effect { }
    /** Light sweeping across a card as its disables are cleared. */
    record Shine(Rectangle at) implements Effect {
        Shine { at = new Rectangle(at); }
    }
    /** A scoring chip that pops up at {@code from} and flies to the score rail at {@code to}; {@code big} for a +9 pair. */
    record Chip(String text, Point2D from, Point2D to, Tone tone, boolean big) implements Effect {
        Chip { from = (Point2D) from.clone(); to = (Point2D) to.clone(); }
    }
    /** A pair link drawing itself, then its bonus ({@code bonus} null when a chip carries it instead). */
    record Link(Point2D a, Point2D b, double sag, Tone tone, String bonus) implements Effect {
        Link { a = (Point2D) a.clone(); b = (Point2D) b.clone(); }
    }
    /** A large title for round changes and combo call-outs. */
    record Banner(String title, double x, double y, Tone tone, double size) implements Effect { }
    /**
     * A small plate naming a new formation, with its bonus and multiplier on a ribbon tag,
     * rising into the gap just above its row ({@code at} is the plate's bottom centre) so it never
     * covers the cards that scored.
     */
    record Callout(String title, String value, Point2D at, Tone tone) implements Effect {
        Callout { at = (Point2D) at.clone(); }
    }
    /** Gold flakes falling over an area, for a victory. */
    record Confetti(Rectangle area, long seed) implements Effect {
        Confetti { area = new Rectangle(area); }
    }

    record Step(int at, int duration, Effect effect) {
        int end() { return at + duration; }
    }
    /** A sound due {@code at}; {@code step} raises the pitch of rising cues such as a chip tally. */
    record Beat(int at, Kind kind, int step) { }

    private final List<Step> steps = new ArrayList<>();
    private final List<Beat> beats = new ArrayList<>();
    private final Map<Long, Integer> hiddenOnRow = new HashMap<>(), hiddenInHand = new HashMap<>();
    private final Set<CardDef> cards = new LinkedHashSet<>();

    void add(int at, int duration, Effect effect) { steps.add(new Step(at, duration, effect)); }
    /** Notes a card the sequence will draw, so its image can be prepared before the clock starts. */
    void uses(CardDef card) { if (card != null) cards.add(card); }
    void sound(int at, Kind kind) { sound(at, kind, 0); }
    void sound(int at, Kind kind, int step) { beats.add(new Beat(at, kind, step)); }
    /** Keeps a row card out of the normal drawing until its animation has put it there. */
    void hideOnRow(long cardId, int until) { hiddenOnRow.merge(cardId, until, Math::max); }
    /** Keeps a hand card out of the normal drawing until it has been dealt in. */
    void hideInHand(long cardId, int until) { hiddenInHand.merge(cardId, until, Math::max); }

    List<Step> steps() { return Collections.unmodifiableList(steps); }
    /** Sounds in the order they are due. */
    List<Beat> beats() {
        List<Beat> sorted = new ArrayList<>(beats);
        sorted.sort(java.util.Comparator.comparingInt(Beat::at));
        return sorted;
    }
    Set<CardDef> cards() { return Collections.unmodifiableSet(cards); }

    boolean hiddenOnRow(long cardId, double elapsed) { return elapsed < hiddenOnRow.getOrDefault(cardId, -1); }
    boolean hiddenInHand(long cardId, double elapsed) { return elapsed < hiddenInHand.getOrDefault(cardId, -1); }

    /** When the last effect or sound has finished, in animation milliseconds. */
    int length() {
        int end = 0;
        for (Step step : steps) end = Math.max(end, step.end());
        for (Beat beat : beats) end = Math.max(end, beat.at());
        return end;
    }
}
