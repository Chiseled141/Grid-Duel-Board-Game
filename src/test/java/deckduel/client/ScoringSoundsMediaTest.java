package deckduel.client;

import static org.junit.jupiter.api.Assertions.*;

import deckduel.client.ScoringSounds.Cue;
import java.util.List;
import javafx.scene.media.AudioClip;
import org.junit.jupiter.api.Test;

/** Native media checks require the desktop audio backend, with speaker output muted. */
class ScoringSoundsMediaTest {
    @Test void bundledRecordingsPlayAndFinishInsteadOfPausingAtZeroRate() throws Exception {
        for (Cue.Kind kind : List.of(Cue.Kind.CARD, Cue.Kind.PAIR, Cue.Kind.GAIN,
                Cue.Kind.IMPACT, Cue.Kind.CHAIN, Cue.Kind.SPELL, Cue.Kind.TALLY, Cue.Kind.FLAME)) {
            verifyPlayback(new Cue(kind, 0), 1.0);
        }
    }

    @Test void risingBellsAndCoinsUseAFasterRateWithoutChangingVolumeOrBalance() throws Exception {
        for (Cue.Kind kind : List.of(Cue.Kind.PAIR, Cue.Kind.TALLY)) {
            verifyPlayback(new Cue(kind, 6), 2.0);
        }
    }

    private static void verifyPlayback(Cue cue, double expectedRate) throws Exception {
        var url = ScoringSounds.class.getResource(ScoringSounds.sampleResource(cue.kind()));
        assertNotNull(url, cue.kind() + " has a packaged recording");
        var clip = new AudioClip(url.toExternalForm());
        clip.setVolume(0.0);
        try {
            ScoringSounds.playRecording(clip, cue);
            assertEquals(expectedRate, clip.getRate(), 0.0001, cue.kind() + " playback rate");
            assertEquals(0.0, clip.getVolume(), "playback preserves the silent test volume");
            assertEquals(0.0, clip.getBalance(), "playback remains centred");
            assertTrue(clip.isPlaying(), cue.kind() + " enters native playback");
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
            while (clip.isPlaying() && System.nanoTime() < deadline) Thread.sleep(10);
            assertFalse(clip.isPlaying(), cue.kind() + " finishes naturally; zero-rate playback hangs");
        } finally {
            clip.stop();
        }
    }
}
