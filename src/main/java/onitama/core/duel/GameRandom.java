package onitama.core.duel;

import java.util.Collections;
import java.util.List;

/**
 * Deterministic SplitMix64 PRNG wrapper. Copyable to preserve exact state on deep copy.
 * Package-private to engine; never exposed in public views, serialization or logs.
 */
final class GameRandom {

    private long state;

    GameRandom(long seed) {
        this.state = seed;
    }

    private GameRandom(GameRandom other) {
        this.state = other.state;
    }

    GameRandom copy() {
        return new GameRandom(this);
    }

    long state() {
        return state;
    }

    long nextLong() {
        state += 0x9E3779B97F4A7C15L;
        long z = state;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive: " + bound);
        }
        long u = nextLong() >>> 1;
        long r = u % bound;
        while (u - r + (bound - 1) < 0) {
            u = nextLong() >>> 1;
            r = u % bound;
        }
        return (int) r;
    }

    <T> void shuffle(List<T> list) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = nextInt(i + 1);
            Collections.swap(list, i, j);
        }
    }
}
