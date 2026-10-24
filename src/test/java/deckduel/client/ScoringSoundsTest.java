package deckduel.client;

import static org.junit.jupiter.api.Assertions.*;

import deckduel.client.ScoringSounds.Cue;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScoringSoundsTest {
    @Test void outputStopsAfterTheCueInsteadOfRunningWithAnEmptyBuffer() throws Exception {
        var line = new AudioLineStub();
        try (var sounds = new ScoringSounds(() -> true, () -> line)) {
            sounds.play(new Cue(Cue.Kind.DEAL, 0));
            assertTrue(line.stopped.await(3, java.util.concurrent.TimeUnit.SECONDS));
            assertFalse(line.isRunning(), "an idle output must not keep running with no PCM data");
            assertTrue(line.calls.indexOf("drain") < line.calls.indexOf("stop"), "finish the audible tail first");
        }
        assertTrue(line.closed.await(3, java.util.concurrent.TimeUnit.SECONDS));
    }

    @Test void mutingDropsQueuedCuesClosesTheDeviceAndAllowsLaterPlayback() throws Exception {
        var on = new java.util.concurrent.atomic.AtomicBoolean(true);
        var first = new AudioLineStub();
        var second = new AudioLineStub();
        first.holdWrite = new java.util.concurrent.CountDownLatch(1);
        var opened = new java.util.concurrent.atomic.AtomicInteger();
        try (var sounds = new ScoringSounds(on::get, () -> opened.getAndIncrement() == 0 ? first : second)) {
            sounds.play(new Cue(Cue.Kind.DEAL, 0));
            assertTrue(first.written.await(3, java.util.concurrent.TimeUnit.SECONDS));
            sounds.play(new Cue(Cue.Kind.DEAL, 0));
            sounds.play(new Cue(Cue.Kind.ROUND, 0));
            on.set(false);
            sounds.stop();
            first.holdWrite.countDown();
            assertTrue(first.closed.await(3, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, first.writes, "sounds queued before mute must not play afterwards");
            assertFalse(first.isOpen());
            sounds.play(new Cue(Cue.Kind.LOSS, 0));
            on.set(true);
            sounds.play(new Cue(Cue.Kind.DEAL, 0));
            assertTrue(second.written.await(3, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, second.writes, "enabling sound starts a fresh device for new cues only");
        } finally { first.holdWrite.countDown(); }
        assertTrue(second.closed.await(3, java.util.concurrent.TimeUnit.SECONDS));
    }

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

    @Test void recordingsExistForTheMatchingCuesAndOtherCuesKeepTheirFallback() {
        for (Cue.Kind kind : List.of(Cue.Kind.CARD, Cue.Kind.PAIR, Cue.Kind.GAIN,
                Cue.Kind.IMPACT, Cue.Kind.CHAIN, Cue.Kind.SPELL, Cue.Kind.TALLY, Cue.Kind.FLAME)) {
            String resource = ScoringSounds.sampleResource(kind);
            assertNotNull(resource, kind + " has a bundled recording");
            assertNotNull(ScoringSounds.class.getResource(resource), resource + " is packaged");
        }
        for (Cue.Kind kind : List.of(Cue.Kind.LOSS, Cue.Kind.WHOOSH, Cue.Kind.DEAL,
                Cue.Kind.ROUND, Cue.Kind.SEAL, Cue.Kind.CHIME,
                Cue.Kind.WARP, Cue.Kind.GLINT, Cue.Kind.RIP)) {
            assertNull(ScoringSounds.sampleResource(kind), kind + " keeps its existing synthesized sound");
        }
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
