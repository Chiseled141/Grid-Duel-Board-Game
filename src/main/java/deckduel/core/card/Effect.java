package deckduel.core.card;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * The closed vocabulary of Spell effects the engine can resolve; each resolves once. Which card
 * carries which effect, and the numbers, are catalog content, not part of these contracts.
 */
public sealed interface Effect extends Serializable {

    /** Player-facing rules text. */
    String text();

    /** Spell: suppresses a Unit's printed points or tags until the round ends. */
    record Disable(TargetSide side, Suppression what) implements Effect {
        @Serial private static final long serialVersionUID = 1L;
        /** Validates the target description. */
        public Disable {
            Objects.requireNonNull(side, "side");
            Objects.requireNonNull(what, "what");
        }
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
        @Override public String text() { return "Destroy " + whose(side) + ". Remove its points and combos."; }
    }

    /** Spell: removes every current disable from one of the caster's Units. */
    record ClearDisable() implements Effect {
        @Serial private static final long serialVersionUID = 1L;
        @Override public String text() { return "Clear the disabled printed points and tags of one of your Units."; }
    }

    /** Spell: the opponent discards {@code cards} chosen at random from their hand. */
    record RandomDiscard(int cards) implements Effect {
        @Serial private static final long serialVersionUID = 1L;
        /** Validates the count. */
        public RandomDiscard { positive(cards); }
        @Override public String text() { return "Discard " + cards + " random card" + (cards == 1 ? "" : "s") + " from the enemy's hand."; }
    }

    /** Spell: takes {@code cards} chosen at random from the opponent's hand into the caster's. */
    record RandomSteal(int cards) implements Effect {
        @Serial private static final long serialVersionUID = 1L;
        /** Validates the count. */
        public RandomSteal { positive(cards); }
        @Override public String text() { return "Take " + cards + " random card" + (cards == 1 ? "" : "s") + " from the enemy's hand into yours."; }
    }

    /** Spell: draws {@code cards} from the caster's deck; not a swap. */
    record Draw(int cards) implements Effect {
        @Serial private static final long serialVersionUID = 1L;
        /** Validates the count. */
        public Draw { positive(cards); }
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
        @Override public String text() { return "Discard 1 card from your hand. Then draw " + draw + " cards."; }
    }

    /** Spell: looks at the top of the caster's deck, keeps one, and puts the rest on the bottom. */
    record Reconnaissance(int look) implements Effect {
        @Serial private static final long serialVersionUID = 1L;
        /** Validates the count. */
        public Reconnaissance { positive(look); }
        @Override public String text() {
            return "Look at your deck's top " + look + " cards. Take 1; put the others on the bottom.";
        }
    }

    /** Spell: privately reveals {@code cards} random cards of the enemy's hand to the caster. */
    record Spy(int cards) implements Effect {
        @Serial private static final long serialVersionUID = 1L;
        /** Validates the count. */
        public Spy { positive(cards); }
        @Override public String text() { return "Privately reveal " + cards + " random cards in the enemy's hand."; }
    }

    private static void positive(int value) {
        if (value < 1) throw new IllegalArgumentException("Effect values must be positive");
    }

    private static String whose(TargetSide side) {
        return side == TargetSide.OWN ? "one of your Units" : "an enemy Unit";
    }
}
