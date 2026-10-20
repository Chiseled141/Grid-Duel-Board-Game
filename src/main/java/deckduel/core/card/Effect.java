package deckduel.core.card;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * The closed effect vocabulary the engine can resolve. Landmark effects are ongoing and apply to
 * their owner while active; Spell effects resolve once. Which card carries which effect, and the
 * numbers, are catalog content, not part of these contracts.
 */
public sealed interface Effect extends Serializable {

    /** Whether this effect belongs on a Landmark (ongoing) rather than a Spell (one-shot). */
    boolean ongoing();

    /** Player-facing rules text. */
    String text();

    // ------------------------------------------------------------------ Landmarks

    /** Landmark: every scoring pair of the owner's Units that shares a tag of {@code type} earns {@code points} more. */
    record SharedTagBonus(TagType type, int points) implements Effect {
        @Serial private static final long serialVersionUID = 1L;
        /** Validates the bonus. */
        public SharedTagBonus {
            Objects.requireNonNull(type, "type");
            positive(points);
        }
        @Override public boolean ongoing() { return true; }
        @Override public String text() {
            return "+" + points + " to each combo pair sharing a " + (type == TagType.REGION ? "nationality" : "class") + ".";
        }
    }

    /** Landmark: the owner's Units printing at most {@code maxPrinted} points get {@code points} extra printed points. */
    record WeakUnitBonus(int maxPrinted, int points) implements Effect {
        @Serial private static final long serialVersionUID = 1L;
        /** Validates the threshold and bonus. */
        public WeakUnitBonus {
            if (maxPrinted < 1 || maxPrinted > 6) throw new IllegalArgumentException("Threshold must be 1–6");
            positive(points);
        }
        @Override public boolean ongoing() { return true; }
        @Override public String text() {
            return "Your Units printing " + maxPrinted + " or fewer points get +" + points + " printed point" + (points == 1 ? "" : "s") + ".";
        }
    }

    /** Landmark: {@code points} live points for each empty space on the owner's Unit row. */
    record EmptySpaceBonus(int points) implements Effect {
        @Serial private static final long serialVersionUID = 1L;
        /** Validates the bonus. */
        public EmptySpaceBonus { positive(points); }
        @Override public boolean ongoing() { return true; }
        @Override public String text() { return "+" + points + " for each empty space on your Unit row."; }
    }

    /** Landmark: the owner's first Spell each round also draws {@code cards}. */
    record FirstSpellDraw(int cards) implements Effect {
        @Serial private static final long serialVersionUID = 1L;
        /** Validates the count. */
        public FirstSpellDraw { positive(cards); }
        @Override public boolean ongoing() { return true; }
        @Override public String text() { return "Your first Spell each round also draws " + cards + " card" + (cards == 1 ? "" : "s") + "."; }
    }

    /** Landmark: the owner privately sees the top card of their own draw deck. */
    record DeckTopView() implements Effect {
        @Serial private static final long serialVersionUID = 1L;
        @Override public boolean ongoing() { return true; }
        @Override public String text() { return "You see the top card of your own deck."; }
    }

    // ------------------------------------------------------------------ Spells

    /** Spell: suppresses a Unit's printed points or tags until the round ends. */
    record Disable(TargetSide side, Suppression what) implements Effect {
        @Serial private static final long serialVersionUID = 1L;
        /** Validates the target description. */
        public Disable {
            Objects.requireNonNull(side, "side");
            Objects.requireNonNull(what, "what");
        }
        @Override public boolean ongoing() { return false; }
        @Override public String text() {
            return what == Suppression.POINTS
                    ? "Disable the printed points of " + whose(side) + " this round. Its combos still count."
                    : "Disable the nation and class tags of " + whose(side) + " until the round ends.";
        }
    }

    /** Spell: removes a Unit from its row; its points and combos no longer count. */
    record Destroy(TargetSide side) implements Effect {
        @Serial private static final long serialVersionUID = 1L;
        /** Validates the side. */
        public Destroy { Objects.requireNonNull(side, "side"); }
        @Override public boolean ongoing() { return false; }
        @Override public String text() { return "Destroy " + whose(side) + ". Remove its points and combos."; }
    }

    /** Spell: removes every current disable from one of the caster's Units. */
    record ClearDisable() implements Effect {
        @Serial private static final long serialVersionUID = 1L;
        @Override public boolean ongoing() { return false; }
        @Override public String text() { return "Clear the disabled printed points and tags of one of your Units."; }
    }

    /** Spell: the opponent discards {@code cards} chosen at random from their hand. */
    record RandomDiscard(int cards) implements Effect {
        @Serial private static final long serialVersionUID = 1L;
        /** Validates the count. */
        public RandomDiscard { positive(cards); }
        @Override public boolean ongoing() { return false; }
        @Override public String text() { return "Discard " + cards + " random card" + (cards == 1 ? "" : "s") + " from the enemy's hand."; }
    }

    /** Spell: takes {@code cards} chosen at random from the opponent's hand into the caster's. */
    record RandomSteal(int cards) implements Effect {
        @Serial private static final long serialVersionUID = 1L;
        /** Validates the count. */
        public RandomSteal { positive(cards); }
        @Override public boolean ongoing() { return false; }
        @Override public String text() { return "Take " + cards + " random card" + (cards == 1 ? "" : "s") + " from the enemy's hand into yours."; }
    }

    /** Spell: draws {@code cards} from the caster's deck; not a manual draw. */
    record Draw(int cards) implements Effect {
        @Serial private static final long serialVersionUID = 1L;
        /** Validates the count. */
        public Draw { positive(cards); }
        @Override public boolean ongoing() { return false; }
        @Override public String text() { return "Draw " + cards + " cards from your deck."; }
    }

    /** Spell: discards another chosen card from the caster's hand, then draws. */
    record Exchange(int discard, int draw) implements Effect {
        @Serial private static final long serialVersionUID = 1L;
        /** Validates the counts. */
        public Exchange {
            if (discard != 1) throw new IllegalArgumentException("Exchange discards exactly one chosen card");
            positive(draw);
        }
        @Override public boolean ongoing() { return false; }
        @Override public String text() { return "Discard 1 card from your hand. Then draw " + draw + " cards."; }
    }

    /** Spell: looks at the top of the caster's deck, keeps one, and puts the rest on the bottom. */
    record Reconnaissance(int look) implements Effect {
        @Serial private static final long serialVersionUID = 1L;
        /** Validates the count. */
        public Reconnaissance { positive(look); }
        @Override public boolean ongoing() { return false; }
        @Override public String text() {
            return "Look at your deck's top " + look + " cards. Take 1; put the others on the bottom.";
        }
    }

    /** Spell: suppresses one enemy Landmark's effect until the round ends. */
    record Sabotage() implements Effect {
        @Serial private static final long serialVersionUID = 1L;
        @Override public boolean ongoing() { return false; }
        @Override public String text() { return "Disable an enemy Landmark's effect until the round ends."; }
    }

    /** Spell: privately reveals {@code cards} random cards of the enemy's hand to the caster. */
    record Spy(int cards) implements Effect {
        @Serial private static final long serialVersionUID = 1L;
        /** Validates the count. */
        public Spy { positive(cards); }
        @Override public boolean ongoing() { return false; }
        @Override public String text() { return "Privately reveal " + cards + " random cards in the enemy's hand."; }
    }

    private static void positive(int value) {
        if (value < 1) throw new IllegalArgumentException("Effect values must be positive");
    }

    private static String whose(TargetSide side) {
        return side == TargetSide.OWN ? "one of your Units" : "an enemy Unit";
    }
}
