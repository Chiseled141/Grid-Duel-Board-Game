package deckduel.client;

import static org.junit.jupiter.api.Assertions.*;

import deckduel.client.ScoringSounds.Cue;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScoringSoundsTest {
    private static int zeroCrossings(byte[] pcm) {
        int crossings = 0;
        short previous = 0;
        for (int i = 0; i < pcm.length; i += 2) {
            short sample = (short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8));
            if ((previous < 0) != (sample < 0)) crossings++;
            previous = sample;
        }
        return crossings;
    }

    @Test void cuesAreShortAudibleAndPairBellsRiseThroughACombo() {
        for (Cue.Kind kind : Cue.Kind.values()) {
            byte[] pcm = ScoringSounds.synthesize(new Cue(kind, 0));
            double seconds = pcm.length / 2.0 / ScoringSounds.RATE;
            assertTrue(seconds > 0.03 && seconds < 0.6, kind + " lasts " + seconds);
            assertTrue(zeroCrossings(pcm) > 5, kind + " is audible");
        }
        assertTrue(zeroCrossings(ScoringSounds.synthesize(new Cue(Cue.Kind.PAIR, 4)))
                > zeroCrossings(ScoringSounds.synthesize(new Cue(Cue.Kind.PAIR, 0))));
    }

    @Test void switchedOffSoundsProduceNothing() {
        List<Cue> played = new ArrayList<>();
        boolean[] on = {false};
        var sounds = new ScoringSounds(() -> on[0], played::add);
        sounds.play(new Cue(Cue.Kind.GAIN, 0));
        on[0] = true;
        sounds.play(new Cue(Cue.Kind.LOSS, 0));
        assertEquals(List.of(new Cue(Cue.Kind.LOSS, 0)), played);
    }

    /** The table plays cues on the JavaFX thread while the frame closes the sounds on the EDT. */
    @Test void cuesRacingCloseAreDroppedWithoutRejectedExecution() throws Exception {
        for (int run = 0; run < 25; run++) {
            var played = new java.util.concurrent.atomic.AtomicInteger();
            var sounds = ScoringSounds.onDevice(() -> true, pcm -> played.incrementAndGet());
            var failures = new java.util.concurrent.ConcurrentLinkedQueue<Throwable>();
            var start = new java.util.concurrent.CountDownLatch(1);
            List<Thread> players = new ArrayList<>();
            for (int t = 0; t < 4; t++) {
                Thread player = new Thread(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < 200; i++) sounds.play(new Cue(Cue.Kind.CARD, 0));
                    } catch (Throwable failure) { failures.add(failure); }
                });
                players.add(player);
                player.start();
            }
            start.countDown();
            Thread.sleep(run % 3);
            sounds.close();
            for (Thread player : players) player.join(5000);
            assertTrue(failures.isEmpty(), "cues racing close never throw: " + failures);
            // Cues queued before close drain; wait until playback settles.
            int settled;
            do { settled = played.get(); Thread.sleep(30); } while (played.get() != settled);
            sounds.play(new Cue(Cue.Kind.GAIN, 0));
            Thread.sleep(30);
            assertEquals(settled, played.get(), "nothing new plays after close");
            assertTrue(settled <= 800);
            sounds.close();
        }
    }

    @Test void cuesQueuedBeforeCloseStillPlay() throws Exception {
        var played = new java.util.concurrent.CopyOnWriteArrayList<Integer>();
        var sounds = ScoringSounds.onDevice(() -> true, pcm -> played.add(pcm.length));
        sounds.play(new Cue(Cue.Kind.CARD, 0));
        sounds.play(new Cue(Cue.Kind.ROUND, 0));
        sounds.close();
        long deadline = System.currentTimeMillis() + 2000;
        while (played.size() < 2 && System.currentTimeMillis() < deadline) Thread.sleep(5);
        assertEquals(2, played.size(), "close lets queued cues finish");
        var silent = new ArrayList<Cue>();
        var injected = new ScoringSounds(() -> true, silent::add);
        injected.close();
        injected.play(new Cue(Cue.Kind.CARD, 0));
        assertTrue(silent.isEmpty());
    }
}
