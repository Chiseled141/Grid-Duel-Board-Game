package deckduel.core.duel;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameRandomTest {

    @Test
    @DisplayName("Fixed-seed SplitMix64 produces deterministic pinned outputs")
    void pinnedSequence() {
        // Reference vectors computed independently with unsigned, masked 64-bit arithmetic.
        GameRandom rng0 = new GameRandom(0L);
        assertEquals(0xe220a8397b1dcdafL, rng0.nextLong());
        assertEquals(0x6e789e6aa1b965f4L, rng0.nextLong());
        assertEquals(0x06c45d188009454fL, rng0.nextLong());
        GameRandom rng42 = new GameRandom(42L);
        assertEquals(0xbdd732262feb6e95L, rng42.nextLong());
        assertEquals(0x28efe333b266f103L, rng42.nextLong());
        assertEquals(0x47526757130f9f52L, rng42.nextLong());

        GameRandom bounded = new GameRandom(12345L);
        for (int expected : new int[] {6, 1, 2, 2, 1, 3, 1, 1}) {
            assertEquals(expected, bounded.nextInt(7));
        }
    }

    @Test
    @DisplayName("nextInt bound validation and range")
    void nextIntRange() {
        GameRandom rng = new GameRandom(12345L);
        assertThrows(IllegalArgumentException.class, () -> rng.nextInt(0));
        assertThrows(IllegalArgumentException.class, () -> rng.nextInt(-5));

        for (int i = 0; i < 500; i++) {
            int val = rng.nextInt(7);
            assertTrue(val >= 0 && val < 7, "value should be in [0, 6]: " + val);
        }
    }

    @Test
    @DisplayName("copy() creates independent copy with identical remaining sequence")
    void copyPreservesState() {
        GameRandom original = new GameRandom(999L);
        original.nextLong();
        original.nextInt(10);

        GameRandom replica = original.copy();

        // Mutating original generates values
        long o1 = original.nextLong();
        int o2 = original.nextInt(50);

        // Replica generates the exact same values
        long r1 = replica.nextLong();
        int r2 = replica.nextInt(50);

        assertEquals(o1, r1);
        assertEquals(o2, r2);

        // Mutating replica now does not affect original's future sequence
        replica.nextLong();
        assertNotEquals(original.state(), replica.state());
    }

    @Test
    @DisplayName("Descending Fisher-Yates shuffle permutes deterministically and conserves elements")
    void shuffleConservation() {
        GameRandom rng = new GameRandom(777L);
        List<String> items = new ArrayList<>(List.of("A", "B", "C", "D", "E", "F"));
        rng.shuffle(items);

        assertEquals(List.of("E", "F", "A", "C", "B", "D"), items);

        // Elements conserved
        assertEquals(6, items.size());
        assertTrue(items.containsAll(List.of("A", "B", "C", "D", "E", "F")));

        // Same seed yields exact same shuffle
        GameRandom rng2 = new GameRandom(777L);
        List<String> items2 = new ArrayList<>(List.of("A", "B", "C", "D", "E", "F"));
        rng2.shuffle(items2);
        assertEquals(items, items2);
    }
}
