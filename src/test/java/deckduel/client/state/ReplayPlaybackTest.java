package deckduel.client.state;

import static org.junit.jupiter.api.Assertions.*;

import deckduel.core.card.MatchEvent;
import deckduel.core.card.MatchPhase;
import deckduel.core.card.Seat;
import deckduel.net.ReplayResponse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Playback navigation over a real recorded game, without any connection. */
class ReplayPlaybackTest {
    private static ReplayResponse replay(boolean complete) { return ClientFixtures.replay(complete); }

    @Test void stepsPlaysPausesAndStopsAtBothEnds() {
        var playback = new ReplayPlayback(replay(true));
        List<Integer> seen = new ArrayList<>();
        playback.addListener(() -> seen.add(playback.index()));
        assertEquals(0, playback.index());
        assertEquals("Opening deal", playback.action());
        assertEquals("Round 1 · Action 0 of " + (playback.length() - 1), playback.position());
        playback.previous();
        assertTrue(seen.isEmpty(), "nothing before the opening deal");
        playback.next();
        playback.next();
        assertEquals(2, playback.index());
        playback.play();
        assertTrue(playback.playing());
        playback.previous();
        assertFalse(playback.playing(), "stepping back pauses");
        assertEquals(1, playback.index());
        while (!playback.atEnd()) playback.next();
        assertEquals(MatchPhase.FINISHED, playback.frame().phase());
        assertTrue(playback.position().startsWith("Round 3"));
        playback.play();
        assertEquals(0, playback.index(), "Play from the end starts over");
        playback.pause();
        assertFalse(playback.playing());
    }

    @Test void playbackPausesWhenItReachesTheEnd() {
        var playback = new ReplayPlayback(replay(true));
        playback.play();
        while (playback.playing()) playback.next();
        assertTrue(playback.atEnd());
    }

    @Test void speedsChangeTheStepTime() {
        var playback = new ReplayPlayback(replay(true));
        assertEquals(ReplayPlayback.BASE_STEP_MILLIS, playback.stepMillis());
        playback.setSpeed(4.0);
        assertEquals(ReplayPlayback.BASE_STEP_MILLIS / 4, playback.stepMillis());
        playback.setSpeed(0.5);
        assertEquals(ReplayPlayback.BASE_STEP_MILLIS * 2, playback.stepMillis());
        assertThrows(IllegalArgumentException.class, () -> playback.setSpeed(3.0));
    }

    @Test void eachActionNamesWhoDidWhat() {
        var playback = new ReplayPlayback(replay(true));
        boolean yours = false, theirs = false;
        while (!playback.atEnd()) {
            playback.next();
            String text = playback.action();
            assertFalse(text.isBlank());
            yours |= text.startsWith("You: ");
            theirs |= text.startsWith("bob: ");
        }
        assertTrue(yours && theirs, "both players' actions are labelled");
        assertTrue(playback.action().contains("The match"), "the last step announces the result");
    }

    @Test void addedLinesSurviveTheLogsTrimming() {
        var a = new MatchEvent(Seat.FIRST, "drew a card");
        var b = new MatchEvent(Seat.SECOND, "played Knight");
        var c = new MatchEvent(null, "Round 1 ends.");
        assertEquals(List.of(a), ReplayPlayback.added(List.of(b, a), List.of(b, a, a)));
        assertEquals(List.of(c), ReplayPlayback.added(List.of(a, b), List.of(b, c)), "the oldest line was trimmed");
        assertEquals(List.of(), ReplayPlayback.added(List.of(a, b), List.of(a, b)));
    }
}
