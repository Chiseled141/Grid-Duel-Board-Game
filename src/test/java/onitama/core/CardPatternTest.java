package onitama.core;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UT01 — the guardian against card-data typos. For every one of the 16 cards,
 * the unrotated destinations from the center of an empty board must equal the
 * authoritative offset table from Appendix A of the brief, and the Red
 * perspective must be its exact 180-degree rotation. The expected data below
 * is written independently of {@link CardDeck} on purpose.
 */
class CardPatternTest {

    /** Appendix A of the brief: card id -> offsets (dx, dy), Blue's orientation. */
    private static final Map<String, int[][]> APPENDIX_A = Map.ofEntries(
            Map.entry("tiger", new int[][]{{0, 2}, {0, -1}}),
            Map.entry("dragon", new int[][]{{-2, 1}, {2, 1}, {-1, -1}, {1, -1}}),
            Map.entry("frog", new int[][]{{-2, 0}, {-1, 1}, {1, -1}}),
            Map.entry("rabbit", new int[][]{{2, 0}, {1, 1}, {-1, -1}}),
            Map.entry("crab", new int[][]{{0, 1}, {-2, 0}, {2, 0}}),
            Map.entry("elephant", new int[][]{{-1, 0}, {1, 0}, {-1, 1}, {1, 1}}),
            Map.entry("goose", new int[][]{{-1, 0}, {1, 0}, {-1, 1}, {1, -1}}),
            Map.entry("rooster", new int[][]{{-1, 0}, {1, 0}, {-1, -1}, {1, 1}}),
            Map.entry("monkey", new int[][]{{-1, 1}, {1, 1}, {-1, -1}, {1, -1}}),
            Map.entry("mantis", new int[][]{{0, -1}, {-1, 1}, {1, 1}}),
            Map.entry("horse", new int[][]{{0, 1}, {0, -1}, {-1, 0}}),
            Map.entry("ox", new int[][]{{0, 1}, {0, -1}, {1, 0}}),
            Map.entry("crane", new int[][]{{0, 1}, {-1, -1}, {1, -1}}),
            Map.entry("boar", new int[][]{{0, 1}, {-1, 0}, {1, 0}}),
            Map.entry("eel", new int[][]{{-1, 1}, {-1, -1}, {1, 0}}),
            Map.entry("cobra", new int[][]{{1, 1}, {1, -1}, {-1, 0}}));

    static Stream<Card> allCards() {
        return CardDeck.catalog().stream();
    }

    @ParameterizedTest(name = "blue perspective of {0}")
    @MethodSource("allCards")
    void unrotatedDestinationsMatchAppendixA(Card card) {
        Square center = new Square(2, 2);
        Set<String> actual = relativeOffsets(card.destinationsFrom(center, PlayerColor.BLUE), center);
        Set<String> expected = new HashSet<>();
        for (int[] offset : APPENDIX_A.get(card.id())) {
            expected.add(offset[0] + "," + offset[1]);
        }
        assertEquals(expected, actual, "card " + card.id() + " disagrees with Appendix A");
    }

    @ParameterizedTest(name = "red perspective of {0}")
    @MethodSource("allCards")
    void rotatedDestinationsAre180DegreeMirror(Card card) {
        Square center = new Square(2, 2);
        Set<String> actual = relativeOffsets(card.destinationsFrom(center, PlayerColor.RED), center);
        Set<String> expected = new HashSet<>();
        for (int[] offset : APPENDIX_A.get(card.id())) {
            expected.add(-offset[0] + "," + -offset[1]);
        }
        assertEquals(expected, actual, "red view of " + card.id() + " must be the 180° rotation");
    }

    /** The catalog must contain exactly the 16 cards of Appendix A, nothing missing. */
    @org.junit.jupiter.api.Test
    void catalogMatchesAppendixCardSet() {
        List<String> catalogIds = CardDeck.catalog().stream().map(Card::id).toList();
        assertEquals(APPENDIX_A.keySet(), Set.copyOf(catalogIds),
                "catalog must be exactly the 16 Appendix A cards");
        assertEquals(16, catalogIds.size(), "ids must be unique");
    }

    /** Every offset of every card stays within the 5x5 board when started from the center. */
    @org.junit.jupiter.api.Test
    void allCatalogOffsetsAreWithinBoardRangeFromCenter() {
        for (Card card : CardDeck.catalog()) {
            for (Offset offset : card.offsets()) {
                assertTrue(Math.abs(offset.dx()) <= 2 && Math.abs(offset.dy()) <= 2,
                        card.id() + " offset out of 5x5 range: " + offset);
            }
        }
    }

    private static Set<String> relativeOffsets(List<Square> destinations, Square origin) {
        return destinations.stream()
                .map(square -> (square.x() - origin.x()) + "," + (square.y() - origin.y()))
                .collect(Collectors.toSet());
    }
}
