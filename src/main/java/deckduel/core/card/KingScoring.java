package deckduel.core.card;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Stateless King bonus calculation from the current row and accepted round counters. */
public final class KingScoring {
    private KingScoring() { }

    /** Includes zero contributions so the inspector can explain every owned King. */
    public static List<KingBonus> bonuses(Row row, KingStatus status) {
        Map<String, Integer> classes = new HashMap<>(), nations = new HashMap<>();
        for (RowCard card : row.cards()) if (!card.has(Suppression.TAGS)) {
            classes.merge(card.def().unitClass(), 1, Integer::sum);
            nations.merge(card.def().nation(), 1, Integer::sum);
        }
        return status.kings().stream().map(king -> {
            int count = switch (king) {
                case VARKHAN -> classes.getOrDefault("Assassin", 0) + classes.getOrDefault("Bowman", 0);
                case ALTHARION -> classes.size();
                case MORDRAVEN -> Math.max(0, classes.values().stream().mapToInt(Integer::intValue).max().orElse(0) - king.threshold());
                case NETHRION -> 0;
                case AURENDOR -> classes.isEmpty() ? 0 : status.spellsPlayed();
                case THALVAREN -> nations.size();
                case VAELTHOR -> (int) nations.values().stream().filter(n -> n >= king.threshold()).count();
            };
            return new KingBonus(king, Math.min(king.cap(), king.points() * count));
        }).toList();
    }
}
