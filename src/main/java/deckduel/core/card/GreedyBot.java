package deckduel.core.card;

import java.util.Comparator;
import java.util.List;

/**
 * Practice and load-test opponent. It sees only its own {@link PlayerView}: it swaps away cards
 * that cannot help it now, and otherwise picks the legal play that most improves its live score lead, preferring earlier plays
 * on ties so it is deterministic for a given view.
 */
public final class GreedyBot {
    private GreedyBot() { }

    /** Chooses a move for a view in which the bot must act. */
    public static Move choose(PlayerView view) {
        Move routine = routine(view);
        if (routine != null) return routine;
        List<PlayCard> plays = view.legalPlays();
        PlayCard best = plays.get(0);
        long bestLead = Long.MIN_VALUE;
        for (PlayCard play : plays) {
            long lead = leadAfter(view, play);
            if (lead > bestLead) { bestLead = lead; best = play; }
        }
        return best;
    }

    /**
     * The moves both bots make the same way: choosing a King, keeping a revealed card, and swaps.
     * Null when the bot should choose a card play.
     */
    static Move routine(PlayerView view) {
        if (view.choosingKing()) return new ChooseKing(view.kingOffer().stream().max(Comparator.comparingLong(king ->
                Scoring.score(view.you().row(), new KingStatus(List.of(king), view.you().kings().spellsPlayed(), false)).kingPoints()
                + view.hand().stream().filter(c -> c.def().kind() == CardKind.UNIT).mapToLong(c ->
                Scoring.score(Row.EMPTY.place(0, c), new KingStatus(List.of(king), 0, false)).kingPoints()).sum())).orElseThrow());
        if (view.keeping() && view.choiceKind() == ChoiceKind.SECRET_EDICT)
            return new KeepEdictCard(view.looking().stream().max(Comparator.comparingInt(c -> c.def().points())).orElseThrow().id());
        if (view.keeping()) return new KeepCard(view.looking().stream().max(Comparator.comparingInt(c -> c.def().points())).orElseThrow().id());
        List<Long> dead = deadCards(view);
        if (!dead.isEmpty()) return new SwapCards(dead);
        if (view.legalPlays().isEmpty()) throw new IllegalStateException("No legal move available");
        return null;
    }

    /**
     * Hand cards worth swapping, weakest first and within the swap limit: Units no play of which
     * raises the lead (a full row of better Units), and targeted Spells with no legal target.
     */
    static List<Long> deadCards(PlayerView view) {
        if (!view.canSwap() || view.you().playsRemaining() == 0) return List.of();
        long lead = lead(view, view.board(), 0);
        return view.hand().stream()
                .filter(card -> card.def().kind() == CardKind.UNIT
                        ? view.legalPlays().stream().filter(play -> play.cardId() == card.id())
                                .noneMatch(play -> leadAfter(view, play) > lead)
                        : Rules.takesTarget(card.def().effect())
                                && Rules.spellTargets(card, view.hand(), view.board()).isEmpty())
                .sorted(Comparator.comparingInt(card -> card.def().points()))
                .limit(view.swapLimit()).map(CardInstance::id).toList();
    }

    /** The bot's score minus the opponent's after a play, computed from public tables only. */
    static long leadAfter(PlayerView view, PlayCard play) {
        CardInstance card = view.hand().stream().filter(c -> c.id() == play.cardId()).findFirst().orElseThrow();
        Rules.Board result = Rules.resolve(card, play, view.hand(), view.board()).board();
        return lead(view, result, card.def().kind() == CardKind.SPELL ? 1 : 0);
    }
    static long lead(PlayerView view, Rules.Board board, int extraSpells) {
        KingStatus own = view.you().kings();
        return Scoring.score(board.own(), new KingStatus(own.kings(), own.spellsPlayed() + extraSpells, own.edictUsed())).total()
                - Scoring.score(board.opponent(), view.opponent().kings()).total();
    }
}
