package deckduel.client.state;

import deckduel.core.card.MatchPhase;
import deckduel.net.ReplayResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Seeking is local, pauses predictably and visits only positions actually recorded. */
class ReplayNavigationTest {
    private ReplayPlayback playback() { return new ReplayPlayback(ClientFixtures.replay(true)); }

    @Test void seekPausesPlayingEvenAtTheCurrentFrameAndRetainsSpeed() {
        var playback = playback();
        playback.setSpeed(4.0); playback.play();
        playback.seek(playback.index());
        assertFalse(playback.playing());
        playback.play(); playback.seek(12);
        assertEquals(12, playback.index()); assertFalse(playback.playing());
        assertEquals(4.0, playback.speed());
        assertSame(playback.replay().frames().get(12), playback.frame());
        assertSame(playback.replay().frames().get(11), playback.previousFrame());
    }

    @Test void invalidSeeksAndMissingRoundsLeavePlaybackUntouched() {
        var playback = playback(); playback.seek(5); playback.play();
        assertThrows(IllegalArgumentException.class, () -> playback.seek(-1));
        assertThrows(IllegalArgumentException.class, () -> playback.seek(playback.length()));
        assertThrows(IllegalArgumentException.class, () -> playback.seekRound(4));
        assertEquals(5, playback.index()); assertTrue(playback.playing());
    }

    @Test void roundNavigationReturnsToCurrentStartThenPreviousAndNeverWraps() {
        var playback = playback();
        assertEquals(List.of(1, 2, 3), playback.navigation().rounds().stream().map(ReplayIndex.RoundStart::round).toList());
        playback.nextRound(); assertEquals(2, playback.frame().round());
        int second = playback.index();
        playback.next(); playback.previousRound(); assertEquals(second, playback.index());
        playback.previousRound(); assertEquals(0, playback.index());
        playback.previousRound(); assertEquals(0, playback.index());
        playback.seekRound(3); int third = playback.index();
        playback.nextRound(); assertEquals(third, playback.index());
        playback.seekRound(2); assertEquals(second, playback.index());
        playback.seek(playback.length() - 1); assertEquals(MatchPhase.FINISHED, playback.frame().phase());
        playback.play(); assertEquals(0, playback.index());
    }

    @Test void singleFrameAndPartialRoundExcerptsHaveOnlyRealBoundaries() {
        var original = ClientFixtures.replay(false);
        var middle = original.frames().stream().filter(view -> view.round() == 2 && view.phase() == MatchPhase.IN_PROGRESS)
                .skip(3).findFirst().orElseThrow();
        var playback = new ReplayPlayback(new ReplayResponse(9, "bob", true, "", false, List.of(middle)));
        assertEquals("Start of recorded excerpt", playback.action());
        assertEquals(List.of(new ReplayIndex.RoundStart(2, 0)), playback.navigation().rounds());
        playback.seekRound(2); playback.nextRound(); playback.previousRound();
        assertEquals(0, playback.index()); assertTrue(playback.atEnd());
        assertThrows(IllegalArgumentException.class, () -> playback.seekRound(1));
        playback.play(); playback.next(); assertFalse(playback.playing());
    }

    @Test void observersAreNotifiedOncePerSeekAndCanBeDetachedOrRemoveThemselves() {
        var playback = playback(); var changes = new AtomicInteger();
        Runnable listener = changes::incrementAndGet;
        playback.addListener(listener);
        playback.seek(0); assertEquals(0, changes.get());
        playback.seek(5); assertEquals(1, changes.get());
        playback.removeListener(listener); playback.seek(6); assertEquals(1, changes.get());
        Runnable[] self = new Runnable[1];
        self[0] = () -> { changes.incrementAndGet(); playback.removeListener(self[0]); };
        playback.addListener(self[0]); playback.seek(7); playback.seek(8);
        assertEquals(2, changes.get());
    }

    @Test void journalClassifiesStructuredSpellKingAndRoundChanges() {
        var playback = playback(); var index = playback.navigation();
        assertTrue(index.at(0).kinds().contains(ReplayIndex.Kind.OPENING));
        assertTrue(index.at(playback.length() - 1).kinds().contains(ReplayIndex.Kind.RESULT));
        for (var entry : index.matching(ReplayIndex.Kind.SPELL, false, "")) {
            var now = playback.replay().frames().get(entry.index());
            assertEquals(deckduel.core.card.CardKind.SPELL, now.lastPlay().card().def().kind());
        }
        assertFalse(index.matching(ReplayIndex.Kind.SPELL, false, "").isEmpty());
        assertFalse(index.matching(ReplayIndex.Kind.KING, false, "").isEmpty());
        assertFalse(index.matching(ReplayIndex.Kind.ROUND, false, "").isEmpty());
        assertEquals(List.of(), index.matching(null, false, "this text cannot occur in the match"));
        assertTrue(index.matching(null, true, "").stream().allMatch(ReplayIndex.Entry::keyMoment));
        assertEquals(index.matching(null, false, "BOB"), index.matching(null, false, " bob "));
        assertEquals(playback.length(), index.entries().size());
        assertThrows(UnsupportedOperationException.class, () -> index.entries().clear());
        assertThrows(UnsupportedOperationException.class, () -> index.at(0).kinds().clear());
    }

    @Test void gapsKeepOriginalPositionsAndDoNotInventIntermediateActions() {
        var original = ClientFixtures.replay(false);
        var frames = List.of(original.frames().get(0), original.frames().get(5), original.frames().get(12));
        var playback = new ReplayPlayback(new ReplayResponse(8, "bob", false, "", false, frames));
        assertEquals(3, playback.length());
        assertEquals(List.of(1, 2), playback.navigation().matching(ReplayIndex.Kind.GAP, false, "").stream()
                .map(ReplayIndex.Entry::index).toList());
        playback.seek(1);
        assertTrue(playback.scoreChange().context().contains("missing revisions"));
        assertSame(frames.get(1), playback.frame());
        assertEquals(5, playback.navigation().at(1).revision());
    }

    @Test void backwardsSeekingCannotExposeLaterPrivateCards() {
        var playback = playback();
        var opening = playback.frame();
        var originalHand = List.copyOf(opening.hand());
        playback.seek(playback.length() - 1); playback.seek(0);
        assertSame(opening, playback.frame());
        assertEquals(originalHand, playback.frame().hand());
        assertTrue(playback.frame().revealed().isEmpty());
        assertTrue(playback.frame().looking().isEmpty());
        assertTrue(playback.frame().opponent().kings().kings().isEmpty());
        assertNull(playback.frame().selectedKing());
    }
}
