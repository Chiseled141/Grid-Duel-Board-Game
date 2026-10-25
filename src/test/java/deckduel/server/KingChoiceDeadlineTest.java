package deckduel.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KingChoiceDeadlineTest {
    @Test void thirtySecondsExpiresAtTheBoundaryWithoutResettingForAnotherConfirmationOrReconnect() {
        var clock = new KingChoiceDeadline();
        assertTrue(clock.begin(1, 1, 1000));
        assertEquals(31_000, clock.millis());
        assertFalse(clock.expired(30_999));
        assertTrue(clock.expired(31_000));
        assertFalse(clock.begin(1, 1, 15_000));
        assertEquals(31_000, clock.millis());
        assertFalse(clock.begin(1, 1, 25_000));
        assertEquals(31_000, clock.millis());
    }

    @Test void newRoundAndRematchRejectOldCallbacksAndClearingCancelsTheWindow() {
        var clock = new KingChoiceDeadline();
        clock.begin(1, 1, 1000); long first = clock.millis();
        assertTrue(clock.matches(1, 1, first));
        assertTrue(clock.begin(1, 2, 50_000));
        assertFalse(clock.matches(1, 1, first));
        assertEquals(80_000, clock.millis());
        clock.clear(); assertFalse(clock.expired(Long.MAX_VALUE)); assertEquals(0, clock.millis());
        assertTrue(clock.begin(2, 1, 100_000));
        assertEquals(130_000, clock.millis());
        assertFalse(clock.matches(1, 2, 80_000));
    }
}
