package deckduel.core.card;

import java.util.ArrayList;
import java.util.List;

/**
 * Card-play legality and the public board part of every resolution, shared by the authoritative
 * match, the bots and client previews so all agree. Effects on hidden hands and decks (draws,
 * random discards and steals, Reconnaissance, Spy) are applied by {@link MatchState}, which owns
 * the decks and randomness; here they only validate.
 */
public final class Rules {
    /** Cards dealt to each player at the start of every round. */
    public static final int HAND_SIZE = 10;
    /** Play allowance per player per round, including Spells, replacements and voluntary skips. */
    public static final int PLAYS_PER_ROUND = 10;
    /** Manual draws per player per round. */
    public static final int MANUAL_DRAWS = 3;
    /** Rounds per match. */
    public static final int ROUNDS = 3;
    /** Both players' public Unit rows from one player's side. */
    public record Board(Row own, Row opponent) {
        /** This player's live score. */
        public ScoreBreakdown ownScore() { return Scoring.score(own); }

        /** The opponent's live score. */
        public ScoreBreakdown opponentScore() { return Scoring.score(opponent); }

        /** This player's live score minus the opponent's. */
        public long lead() { return ownScore().total() - opponentScore().total(); }
    }

    /** The outcome of one play on the public board, every card that left play, and a log line. */
    public record Resolution(Board board, List<CardInstance> discarded, String description) {
        /** Copies the discards. */
        public Resolution { discarded = List.copyOf(discarded); }
    }

    private Rules() { }

    /** Spaces a Unit may go to: the empty ones, or every space when the row is full. */
    public static List<Integer> placementSlots(Row row) {
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < Row.SIZE; slot++)
            if (row.isFull() || row.at(slot) == null) slots.add(slot);
        return slots;
    }

    /** Whether a Spell names a target when one is available. */
    public static boolean takesTarget(Effect effect) {
        return effect instanceof Effect.Disable || effect instanceof Effect.Destroy || effect instanceof Effect.ClearDisable
                || effect instanceof Effect.Exchange;
    }

    /**
     * Ids of what a Spell may target now: Units on a row, or for Exchange another card in the
     * caster's hand. Empty for Spells without a target.
     */
    public static List<Long> spellTargets(CardInstance spell, List<CardInstance> hand, Board board) {
        Effect effect = spell.def().effect();
        List<Long> ids = new ArrayList<>();
        if (effect instanceof Effect.Disable disable) {
            for (RowCard unit : row(board, disable.side()).cards()) if (!unit.has(disable.what())) ids.add(unit.card().id());
        } else if (effect instanceof Effect.Destroy destroy) {
            for (RowCard unit : row(board, destroy.side()).cards()) ids.add(unit.card().id());
        } else if (effect instanceof Effect.ClearDisable) {
            for (RowCard unit : board.own().cards()) if (!unit.suppressed().isEmpty()) ids.add(unit.card().id());
        } else if (effect instanceof Effect.Exchange) {
            for (CardInstance card : hand) if (card.id() != spell.id()) ids.add(card.id());
        }
        return ids;
    }

    /** Every card play the hand allows on this board. A Spell with no legal target may still be cast, to no effect. */
    public static List<PlayCard> legalPlays(List<CardInstance> hand, Board board) {
        List<PlayCard> plays = new ArrayList<>();
        for (CardInstance card : hand) {
            if (card.def().occupiesSpace()) {
                for (int slot : placementSlots(board.own())) plays.add(PlayCard.place(card.id(), slot));
                continue;
            }
            List<Long> targets = takesTarget(card.def().effect()) ? spellTargets(card, hand, board) : List.of();
            if (targets.isEmpty()) plays.add(PlayCard.cast(card.id(), null));
            for (long target : targets) plays.add(PlayCard.cast(card.id(), target));
        }
        return plays;
    }

    /** Validates one play of {@code card} from {@code hand} and returns the resulting board; nothing is modified. */
    public static Resolution resolve(CardInstance card, PlayCard play, List<CardInstance> hand, Board board) {
        if (card.id() != play.cardId()) throw new IllegalArgumentException("Play names another card");
        return card.def().occupiesSpace() ? place(card, play, board) : cast(card, play, hand, board);
    }

    private static Resolution place(CardInstance card, PlayCard play, Board board) {
        if (play.targetId() != null) throw new RuleViolation("Units do not take a target.");
        if (play.slot() < 0 || play.slot() >= Row.SIZE) throw new RuleViolation("Choose one of your seven row spaces.");
        Row own = board.own();
        RowCard replaced = own.at(play.slot());
        if (replaced != null && !own.isFull())
            throw new RuleViolation("That space is taken. Replacing is only allowed when your row is full.");
        Board after = new Board(own.place(play.slot(), card), board.opponent());
        String where = " to space " + (play.slot() + 1);
        if (replaced == null) return new Resolution(after, List.of(), "played " + card.def().name() + where);
        return new Resolution(after, List.of(replaced.card()),
                "replaced " + replaced.def().name() + " with " + card.def().name() + where.replace(" to", " in"));
    }

    private static Resolution cast(CardInstance card, PlayCard play, List<CardInstance> hand, Board board) {
        if (play.slot() != PlayCard.NO_SPACE) throw new RuleViolation("Spells do not use a row space.");
        Effect effect = card.def().effect();
        String name = card.def().name();
        if (!takesTarget(effect)) {
            if (play.targetId() != null) throw new RuleViolation(name + " does not take a target.");
            return new Resolution(board, List.of(card), "cast " + name);
        }
        List<Long> targets = spellTargets(card, hand, board);
        if (targets.isEmpty()) {
            // Without this a hand of untargetable Spells could leave a player with no legal play.
            if (play.targetId() != null) throw new RuleViolation(name + " has no legal target right now.");
            return new Resolution(board, List.of(card), "cast " + name + " with no legal target");
        }
        if (play.targetId() == null || !targets.contains(play.targetId())) throw new RuleViolation("Choose a legal target for " + name + ".");
        long target = play.targetId();
        if (effect instanceof Effect.Exchange)
            return new Resolution(board, List.of(card), "cast " + name + ", discarding a card from hand");
        return onUnit(card, target, board);
    }

    /** Disable, Destroy or Clear Disable on a Unit of either row. */
    private static Resolution onUnit(CardInstance card, long target, Board board) {
        Effect effect = card.def().effect();
        boolean onOwn = board.own().find(target) != null;
        Row row = onOwn ? board.own() : board.opponent();
        RowCard unit = row.find(target);
        List<CardInstance> discarded = new ArrayList<>(List.of(card));
        Row changed;
        if (effect instanceof Effect.Destroy) {
            changed = row.remove(target);
            discarded.add(unit.card());
        } else if (effect instanceof Effect.ClearDisable) {
            changed = row.update(unit.cleared());
        } else {
            changed = row.update(unit.with(((Effect.Disable) effect).what()));
        }
        Board after = onOwn ? new Board(changed, board.opponent()) : new Board(board.own(), changed);
        return new Resolution(after, discarded, "cast " + card.def().name() + " on " + unit.def().name());
    }

    private static Row row(Board board, TargetSide side) { return side == TargetSide.OWN ? board.own() : board.opponent(); }
}
