package deckduel.client;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/**
 * Short synthesized match cues (no audio assets): a card tap, a bell per scoring pair that
 * rises through a combo, a two-note coin for a gain, a falling tone for a loss, and the motion
 * cues of the table: a whoosh, a spell shimmer, a shattering impact, a chain clink, a dealing
 * tick and a round bell. Playback
 * runs on one daemon thread; a machine without audio simply stays silent.
 */
public final class ScoringSounds implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(ScoringSounds.class.getName());
    static final float RATE = 44_100f;
    private static final AudioFormat FORMAT = new AudioFormat(RATE, 16, 1, true, false);

    /** One sound cue; {@code step} raises a pair bell by two semitones per pair in a sequence. */
    public record Cue(Kind kind, int step) {
        /** The cue families. */
        public enum Kind { CARD, PAIR, GAIN, LOSS, WHOOSH, SPELL, IMPACT, CHAIN, DEAL, ROUND }
    }

    private final BooleanSupplier enabled;
    private final Consumer<Cue> output;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "scoring-sounds");
        thread.setDaemon(true);
        return thread;
    });
    // Owned by the worker thread only.
    private SourceDataLine line;
    private boolean unavailable;

    /** Plays through the default audio device while {@code enabled} is true. */
    public ScoringSounds(BooleanSupplier enabled) {
        this.enabled = enabled;
        this.output = cue -> worker.execute(() -> write(synthesize(cue)));
    }

    /** Sends cues to {@code output} instead of the audio device, for tests and silent tables. */
    public ScoringSounds(BooleanSupplier enabled, Consumer<Cue> output) {
        this.enabled = enabled;
        this.output = output;
    }

    /** Plays a cue unless sounds are switched off; never blocks the caller. */
    public void play(Cue cue) {
        if (enabled.getAsBoolean()) output.accept(cue);
    }

    private void write(byte[] pcm) {
        if (unavailable) return;
        try {
            if (line == null) {
                line = AudioSystem.getSourceDataLine(FORMAT);
                line.open(FORMAT, (int) RATE / 5);
                line.start();
            }
            line.write(pcm, 0, pcm.length);
        } catch (Exception | LinkageError unavailableAudio) {
            unavailable = true;
            LOG.log(Level.FINE, "Scoring sounds unavailable", unavailableAudio);
        }
    }

    /** 16-bit little-endian mono PCM for one cue. */
    static byte[] synthesize(Cue cue) {
        return switch (cue.kind()) {
            case CARD -> tone(new double[]{180}, 0.06, 0.35, true);
            case PAIR -> tone(new double[]{660 * Math.pow(2, Math.min(cue.step(), 12) * 2 / 12.0)}, 0.16, 0.22, false);
            case GAIN -> concat(tone(new double[]{988}, 0.07, 0.25, false), tone(new double[]{1319}, 0.16, 0.25, false));
            case LOSS -> concat(tone(new double[]{330}, 0.12, 0.25, false), tone(new double[]{247}, 0.2, 0.25, false));
            case WHOOSH -> sweep(0.22, 0.30);
            case SPELL -> shimmer();
            case IMPACT -> mix(tone(new double[]{85}, 0.22, 0.55, false), sweep(0.14, 0.35));
            case CHAIN -> concat(tone(new double[]{1760, 2490}, 0.07, 0.16, false), tone(new double[]{2210, 3120}, 0.12, 0.14, false));
            case DEAL -> tone(new double[]{420}, 0.04, 0.22, true);
            case ROUND -> tone(new double[]{523.25, 659.25, 783.99}, 0.45, 0.14, false);
        };
    }

    /** Filtered noise that swells and fades, like a card cutting the air. */
    private static byte[] sweep(double seconds, double volume) {
        int samples = (int) (RATE * seconds);
        byte[] pcm = new byte[samples * 2];
        java.util.Random noise = new java.util.Random(11);
        double low = 0;
        for (int i = 0; i < samples; i++) {
            double t = (double) i / samples, envelope = Math.sin(Math.PI * t);
            // A one-pole low-pass whose opening follows the swell gives the rushing colour.
            double cutoff = 0.04 + 0.25 * envelope;
            low += cutoff * (noise.nextGaussian() - low);
            write(pcm, i, low * envelope * volume * 30_000);
        }
        return pcm;
    }

    /** A rising major triad with vibrato that fades in, for a spell gathering power. */
    private static byte[] shimmer() {
        int samples = (int) (RATE * 0.34);
        byte[] pcm = new byte[samples * 2];
        double[] notes = {523.25, 659.25, 783.99};
        for (int i = 0; i < samples; i++) {
            double t = i / RATE, progress = (double) i / samples;
            double envelope = Math.min(1, progress * 4) * (1 - progress), value = 0;
            for (double note : notes) value += Math.sin(2 * Math.PI * note * (1 + progress * 0.12) * t + Math.sin(t * 38) * 0.6);
            write(pcm, i, value * envelope * 0.12 * 12_000);
        }
        return pcm;
    }

    /** Sums two PCM buffers, padding the shorter with silence. */
    private static byte[] mix(byte[] a, byte[] b) {
        byte[] out = new byte[Math.max(a.length, b.length)];
        for (int i = 0; i + 1 < out.length; i += 2) {
            int sum = sample(a, i) + sample(b, i);
            write(out, i / 2, sum);
        }
        return out;
    }

    private static int sample(byte[] pcm, int i) {
        return i + 1 < pcm.length ? (short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8)) : 0;
    }

    private static void write(byte[] pcm, int index, double value) {
        short sample = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
        pcm[2 * index] = (byte) sample;
        pcm[2 * index + 1] = (byte) (sample >> 8);
    }

    /** A decaying sine (bell-like) or noise-tinged tap at the given frequencies. */
    private static byte[] tone(double[] frequencies, double seconds, double volume, boolean tap) {
        int samples = (int) (RATE * seconds);
        byte[] pcm = new byte[samples * 2];
        java.util.Random noise = new java.util.Random(7);
        for (int i = 0; i < samples; i++) {
            double t = i / RATE, envelope = Math.exp(-t * (tap ? 60 : 14)) * Math.min(1, i / (RATE * 0.004));
            double value = 0;
            for (double frequency : frequencies) value += Math.sin(2 * Math.PI * frequency * t) + 0.3 * Math.sin(4 * Math.PI * frequency * t);
            if (tap) value += noise.nextGaussian() * 0.6;
            short sample = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value * envelope * volume * 12_000));
            pcm[2 * i] = (byte) sample;
            pcm[2 * i + 1] = (byte) (sample >> 8);
        }
        return pcm;
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] both = java.util.Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, both, first.length, second.length);
        return both;
    }

    /** Stops playback and releases the audio device. */
    @Override public void close() {
        worker.execute(() -> { if (line != null) { line.drain(); line.close(); } });
        worker.shutdown();
    }
}
