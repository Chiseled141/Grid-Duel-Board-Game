package deckduel.core.card;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The stronger practice opponent. Like {@link GreedyBot} it sees only its own {@link PlayerView}
 * and makes the same keep and draw decisions, but for card plays it plays out the rest
 * of its known hand for each candidate, scores round-ending plays exactly, and holds disruption
 * Spells while the opponent still has plays left to rebuild with. Deterministic for a given view.
 */
public final class PlannerBot {
    /** Value given up per opponent play left when a disruption Spell is cast now rather than later. */
    static final double SPELL_PATIENCE = 1.5;
    /**
     * Weight of the played-out hand against the immediate lead. Formations multiply a whole row, so
     * the row the hand can still build matters as much as the lead now: against the greedy bot over
     * 400 seeded matches, 0.2 won 203–195, 0.5 won 205–192, 1.0 won 254–146 and 1.5 won 250–150.
     */
    static final double FUTURE_WEIGHT = 1.0;

    private PlannerBot() { }

    /** Chooses a move for a view in which the bot must act. */
    public static Move choose(PlayerView view) {
        Move routine = GreedyBot.routine(view);
        if (routine != null) return routine;
        List<PlayCard> plays = candidates(view.hand(), view.board());
        PlayCard best = plays.get(0);
        double bestValue = Double.NEGATIVE_INFINITY;
        for (PlayCard play : plays) {
            double value = value(view, play);
            if (value > bestValue) { bestValue = value; best = play; }
        }
        return best;
    }

    /** Estimated round lead after this play and the bot's own remaining known plays. */
    static double value(PlayerView view, PlayCard play) {
        CardInstance card = find(view.hand(), play.cardId());
        Rules.Board after = Rules.resolve(card, play, view.hand(), view.board()).board();
        int ownLeft = view.you().playsRemaining() - 1, opponentLeft = view.opponent().playsRemaining();
        if (ownLeft == 0 && opponentLeft == 0) return GreedyBot.leadAfter(view, play);
        List<CardInstance> hand = new ArrayList<>(view.hand());
        hand.remove(card);
        long now = GreedyBot.leadAfter(view, play);
        double value = now + FUTURE_WEIGHT * (rollout(hand, after, ownLeft, view, card.def().kind() == CardKind.SPELL ? 1 : 0) - now);
        if (disruptsOpponent(card.def())) value -= SPELL_PATIENCE * opponentLeft;
        return value;
    }

    /** Greedily plays the remaining known hand against a static opponent table; returns the final lead. */
    private static long rollout(List<CardInstance> hand, Rules.Board board, int playsLeft, PlayerView view, int extraSpells) {
        List<CardInstance> remaining = new ArrayList<>(hand);
        for (int step = 0; step < playsLeft && !remaining.isEmpty(); step++) {
            Rules.Board best = null;
            CardInstance used = null;
            long bestLead = Long.MIN_VALUE;
            for (PlayCard play : candidates(remaining, board)) {
                CardInstance card = find(remaining, play.cardId());
                Rules.Board result = Rules.resolve(card, play, remaining, board).board();
                long lead = GreedyBot.lead(view, result, extraSpells + (card.def().kind() == CardKind.SPELL ? 1 : 0));
                if (lead > bestLead) { bestLead = lead; best = result; used = card; }
            }
            board = best;
            remaining.remove(used);
            if (used.def().kind() == CardKind.SPELL) extraSpells++;
        }
        return GreedyBot.lead(view, board, extraSpells);
    }

    /**
     * Legal plays without equivalent duplicates: empty spaces score identically, and identical
     * cards in hand have identical outcomes, so only one of each is kept.
     */
    static List<PlayCard> candidates(List<CardInstance> hand, Rules.Board board) {
        List<PlayCard> plays = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (PlayCard play : Rules.legalPlays(hand, board)) {
            CardDef def = find(hand, play.cardId()).def();
            boolean emptySpace = play.slot() != PlayCard.NO_SPACE && board.own().at(play.slot()) == null;
            String key = def.id() + "|" + (emptySpace ? "empty" : play.slot() + "|" + play.targetId());
            if (seen.add(key)) plays.add(play);
        }
        return plays;
    }

    private static boolean disruptsOpponent(CardDef card) {
        Effect effect = card.effect();
        return effect instanceof Effect.Destroy destroy && destroy.side() == TargetSide.OPPONENT
                || effect instanceof Effect.Disable disable && disable.side() == TargetSide.OPPONENT;
    }

    private static CardInstance find(List<CardInstance> hand, long id) {
        return hand.stream().filter(card -> card.id() == id).findFirst().orElseThrow();
    }
}
