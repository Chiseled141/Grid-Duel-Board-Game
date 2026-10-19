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
}
