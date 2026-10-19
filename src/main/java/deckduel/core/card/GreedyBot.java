package deckduel.core.card;

import java.util.Comparator;
import java.util.List;

/**
 * Practice and load-test opponent. It sees only its own {@link PlayerView}: it keeps the
 * Landmarks it rates highest, draws while its hand is shorter than its remaining plays, and
 * otherwise picks the legal play that most improves its live score lead, preferring earlier plays
 * on ties so it is deterministic for a given view.
 */
public final class GreedyBot {
    /** Landmark preference: pair bonuses first, then flat points, then draw and information effects. */
    private static final List<Class<? extends Effect>> PREFERENCE = List.of(Effect.SharedTagBonus.class,
            Effect.WeakUnitBonus.class, Effect.EmptySpaceBonus.class, Effect.FirstSpellDraw.class, Effect.DeckTopView.class);

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
     * The moves both bots make the same way: Landmark choice, keeping a revealed card, and manual
     * draws. Null when the bot should choose a card play.
     */
    static Move routine(PlayerView view) {
        if (view.choosingLandmarks()) return pickLandmarks(view.offers());
        if (view.keeping()) return new KeepCard(view.looking().stream().max(Comparator.comparingInt(c -> c.def().points())).orElseThrow().id());
        boolean shortHanded = view.hand().size() < view.you().playsRemaining();
        if (view.canDraw() && (shortHanded || view.legalPlays().isEmpty())) return new DrawCard();
        if (view.legalPlays().isEmpty()) throw new IllegalStateException("No legal move available");
        return null;
    }

    /** The two offers earliest in {@link #PREFERENCE}, in offer order on ties. */
    static PickLandmarks pickLandmarks(List<CardInstance> offers) {
        List<Long> ids = offers.stream()
                .sorted(Comparator.comparingInt(offer -> rank(offer.def().effect())))
                .limit(Rules.LANDMARK_PICKS).map(CardInstance::id).toList();
        return new PickLandmarks(ids);
    }

    private static int rank(Effect effect) {
        for (int i = 0; i < PREFERENCE.size(); i++) if (PREFERENCE.get(i).isInstance(effect)) return i;
        return PREFERENCE.size();
    }

    /** The bot's score minus the opponent's after a play, computed from public tables only. */
    static long leadAfter(PlayerView view, PlayCard play) {
        CardInstance card = view.hand().stream().filter(c -> c.id() == play.cardId()).findFirst().orElseThrow();
        return Rules.resolve(card, play, view.hand(), view.board()).board().lead();
    }
}
