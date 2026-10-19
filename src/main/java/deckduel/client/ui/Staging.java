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
 * One planned table sequence: timed effects, sounds and camera shakes, written at base speed in
 * animation milliseconds. It is plain data, so the plan can be checked without a screen; the
 * JavaFX {@link TableEffects} performs it over the authoritative view, which is already final.
 */
final class Staging {
    /**
     * Real milliseconds per animation millisecond. Every timing in a plan is written at base
     * speed; this one factor slows the whole sequence evenly, so effects stay in step.
     */
    static final double TEMPO = 1.8;

    /** Effect colours, named for what they mean on the table. */
    enum Tone {
        EMBER(0xF0874A), ARCANE(0xA88BE8), CREAM(0xF4E6C6), GOLD(0xF4C766);
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
    /** A bolt from a Spell to its target. */
    record Bolt(Point2D from, Point2D to, Tone tone) implements Effect {
        Bolt { from = (Point2D) from.clone(); to = (Point2D) to.clone(); }
    }
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
    /** A war hammer that winds up over a card and strikes it {@code strike} milliseconds into the step. */
    record Hammer(Rectangle at, int strike) implements Effect {
        Hammer { at = new Rectangle(at); }
    }
    /** Cracks spreading across a struck card before it breaks. */
    record Cracks(CardDef card, List<Suppression> marks, Rectangle at, long seed) implements Effect {
        Cracks { marks = List.copyOf(marks); at = new Rectangle(at); }
    }
    /** A disable taking hold of a card. */
    record Chains(CardDef card, List<Suppression> marks, Rectangle at) implements Effect {
        Chains { marks = List.copyOf(marks); at = new Rectangle(at); }
    }
    /** Light sweeping across a card, as a Landmark wakes. */
    record Shine(Rectangle at) implements Effect {
        Shine { at = new Rectangle(at); }
    }
    /** A pair link drawing itself, then its bonus. */
    record Link(Point2D a, Point2D b, double sag, Tone tone, String bonus) implements Effect {
        Link { a = (Point2D) a.clone(); b = (Point2D) b.clone(); }
    }
    /** A large title for round changes and combo call-outs. */
    record Banner(String title, String subtitle, double x, double y, Tone tone, double size) implements Effect { }
    /** Gold flakes falling over an area, for a victory. */
    record Confetti(Rectangle area, long seed) implements Effect {
        Confetti { area = new Rectangle(area); }
    }

    record Step(int at, int duration, Effect effect) {
        int end() { return at + duration; }
    }
    record Beat(int at, Kind kind) { }
    record Shake(int at, int duration, double amplitude) { }

    private final List<Step> steps = new ArrayList<>();
    private final List<Beat> beats = new ArrayList<>();
    private final List<Shake> shakes = new ArrayList<>();
    private final Map<Long, Integer> hiddenOnRow = new HashMap<>(), hiddenInHand = new HashMap<>();
    private final Set<CardDef> cards = new LinkedHashSet<>();

    void add(int at, int duration, Effect effect) { steps.add(new Step(at, duration, effect)); }
    /** Notes a card the sequence will draw, so its image can be prepared before the clock starts. */
    void uses(CardDef card) { if (card != null) cards.add(card); }
    void sound(int at, Kind kind) { beats.add(new Beat(at, kind)); }
    void shake(int at, int duration, double amplitude) { shakes.add(new Shake(at, duration, amplitude)); }
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

    /** When the last effect, sound or shake has finished, in animation milliseconds. */
    int length() {
        int end = 0;
        for (Step step : steps) end = Math.max(end, step.end());
        for (Beat beat : beats) end = Math.max(end, beat.at());
        for (Shake shake : shakes) end = Math.max(end, shake.at() + shake.duration());
        return end;
    }

    /** The strongest shake still decaying at {@code elapsed}, in pixels. */
    double shakeAmplitude(double elapsed) {
        double amplitude = 0;
        for (Shake s : shakes)
            if (elapsed >= s.at() && elapsed < s.at() + s.duration())
                amplitude = Math.max(amplitude, s.amplitude() * (1 - (elapsed - s.at()) / s.duration()));
        return amplitude;
    }
}
