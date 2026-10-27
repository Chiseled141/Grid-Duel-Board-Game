package deckduel.client;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javafx.scene.media.AudioClip;

/**
 * Match cues use bundled recordings where available and synthesized fallbacks for other cues: a card tap, a bell per scoring pair that
 * rises through a combo, a two-note coin for a gain, a falling tone for a loss, and the motion
 * cues of the table: a whoosh, a spell shimmer, a shattering impact, a chain clink, a dealing
 * tick, a round bell, and the Spells' own cues: a seal's thud, a chime, a crackle of flame, a
 * vortex's falling warble and a high glint, and a coin chink that rises as score chips land. Playback
 * runs on one daemon thread; a machine without audio simply stays silent. Cues that arrive after
 * {@link #close()}, as the window closes during a match, are not played.
 */
public final class ScoringSounds implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(ScoringSounds.class.getName());
    static final float RATE = 44_100f;
    private static final AudioFormat FORMAT = new AudioFormat(RATE, 16, 1, true, false);

    /** One sound cue; {@code step} raises a pair bell by two semitones per pair in a sequence. */
    public record Cue(Kind kind, int step) {
        /** The cue families. */
        public enum Kind { CARD, PAIR, GAIN, LOSS, WHOOSH, SPELL, IMPACT, CHAIN, DEAL, ROUND, SEAL, CHIME, FLAME, WARP, GLINT, TALLY, RIP }
    }

    private final BooleanSupplier enabled;
    private final Consumer<Cue> output;
    private final LineFactory lineFactory;
    @FunctionalInterface
    interface LineFactory { SourceDataLine create() throws LineUnavailableException; }
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "scoring-sounds");
        thread.setDaemon(true);
        return thread;
    });
    // Queuing and closing hold this lock, so no cue is handed to the worker after it shut down.
    private final Object lifecycle = new Object();
    private volatile boolean closed;
    private volatile long generation;
    // Owned by the worker thread only.
    private SourceDataLine line;
    private boolean unavailable;
    // AudioClip playback is asynchronous; keep handles so mute/close can stop active recordings.
    private final Map<Cue.Kind, AudioClip> sampleClips = new EnumMap<>(Cue.Kind.class);
    private final Map<Cue.Kind, Boolean> missingSamples = new EnumMap<>(Cue.Kind.class);

    /** Plays through the default audio device while {@code enabled} is true. */
    public ScoringSounds(BooleanSupplier enabled) { this(enabled, null, null); }

    /** Sends cues to {@code output} instead of the audio device, for tests and silent tables. */
    public ScoringSounds(BooleanSupplier enabled, Consumer<Cue> output) { this(enabled, output, null); }

    /** Plays synthesized cues on the playback thread into {@code device} instead of the audio device. */
    static ScoringSounds onDevice(BooleanSupplier enabled, Consumer<byte[]> device) {
        return new ScoringSounds(enabled, null, device);
    }

    private ScoringSounds(BooleanSupplier enabled, Consumer<Cue> output, Consumer<byte[]> device) {
        this(enabled, output, device, () -> AudioSystem.getSourceDataLine(FORMAT));
    }

    ScoringSounds(BooleanSupplier enabled, LineFactory lineFactory) {
        this(enabled, null, null, lineFactory);
    }

    private ScoringSounds(BooleanSupplier enabled, Consumer<Cue> output, Consumer<byte[]> device,
                          LineFactory lineFactory) {
        this.enabled = enabled;
        this.lineFactory = lineFactory;
        Consumer<byte[]> sink = device == null ? this::write : device;
        this.output = output != null ? output : cue -> queue(() -> {
            if (device != null) sink.accept(synthesize(cue));
            else playCue(cue, sink);
        });
    }

    /** Plays a cue unless sounds are switched off or closed; never blocks the caller. */
    public void play(Cue cue) {
        if (!closed && enabled.getAsBoolean()) output.accept(cue);
    }

    private void queue(Runnable task) {
        synchronized (lifecycle) {
            long queuedGeneration = generation;
            if (!closed) worker.execute(() -> {
                if (enabled.getAsBoolean() && queuedGeneration == generation) task.run();
            });
        }
    }

    /** Cancels pending effects and releases the device off the UI thread; later cues may play again. */
    public void stop() {
        synchronized (lifecycle) {
            if (closed) return;
            generation++;
            worker.execute(this::releaseLine);
        }
    }

    private void write(byte[] pcm) {
        if (unavailable) return;
        try {
            if (line == null) {
                line = lineFactory.create();
                line.open(FORMAT, (int) RATE / 5);
            }
            line.start();
            line.write(pcm, 0, pcm.length);
            // Stop after the tail finishes, rather than leave a running device with no data.
            line.drain();
            line.stop();
            line.flush();
        } catch (Exception | LinkageError unavailableAudio) {
            unavailable = true;
            releaseLine();
            LOG.log(Level.FINE, "Scoring sounds unavailable", unavailableAudio);
        }
    }

    private void playCue(Cue cue, Consumer<byte[]> synthesizedOutput) {
        String resource = sampleResource(cue.kind());
        if (resource != null && !missingSamples.containsKey(cue.kind())) {
            try {
                AudioClip clip = sampleClips.get(cue.kind());
                if (clip == null) {
                    var url = ScoringSounds.class.getResource(resource);
                    if (url == null) {
                        missingSamples.put(cue.kind(), true);
                    } else {
                        clip = new AudioClip(url.toExternalForm());
                        sampleClips.put(cue.kind(), clip);
                    }
                }
                if (clip != null) {
                    playRecording(clip, cue);
                    return;
                }
            } catch (RuntimeException | LinkageError unavailableSample) {
                missingSamples.put(cue.kind(), true);
                LOG.log(Level.WARNING, "Bundled sound unavailable for " + cue.kind(), unavailableSample);
            }
        }
        synthesizedOutput.accept(synthesize(cue));
    }

    /** Returns the bundled recording for a cue, or {@code null} to keep its synthesized sound. */
    static String sampleResource(Cue.Kind kind) {
        return switch (kind) {
            case CARD -> "/audio/sfx/card_tap.mp3";
            case PAIR -> "/audio/sfx/pair_bell.mp3";
            case GAIN -> "/audio/sfx/gain.mp3";
            case IMPACT -> "/audio/sfx/impact.mp3";
            case CHAIN -> "/audio/sfx/chain_link.mp3";
            case SPELL -> "/audio/sfx/spell_shimmer.mp3";
            case FLAME -> "/audio/sfx/flame_crackle.mp3";
            case TALLY -> "/audio/sfx/tally_coin.mp3";
            default -> null;
        };
    }

    private static double sampleRate(Cue cue) {
        if (cue.kind() != Cue.Kind.PAIR && cue.kind() != Cue.Kind.TALLY) return 1.0;
        return Math.pow(2, Math.min(cue.step(), 12) * 2 / 12.0);
    }

    /** Starts a recording with the rising pitch used by combo bells and score chips. */
    static void playRecording(AudioClip clip, Cue cue) {
        // Named setters avoid confusing AudioClip.play's volume/balance/rate arguments.
        clip.setRate(sampleRate(cue));
        clip.play();
    }

    private void releaseLine() {
        sampleClips.values().forEach(AudioClip::stop);
        if (line == null) return;
        try { line.stop(); line.flush(); line.close(); }
        finally { line = null; }
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
            case SEAL -> mix(tone(new double[]{98}, 0.3, 0.5, false), tone(new double[]{392, 587.33}, 0.34, 0.1, false));
            case CHIME -> concat(tone(new double[]{1046.5}, 0.08, 0.16, false), tone(new double[]{1568, 2093}, 0.4, 0.13, false));
            case FLAME -> crackle(0.5, 0.3);
            case RIP -> rip(0.32, 0.35);
            case WARP -> glissando(880, 196, 0.46, 0.2);
            case TALLY -> {
                double pitch = 1046.5 * Math.pow(2, Math.min(cue.step(), 12) * 2 / 12.0);
                yield mix(tone(new double[]{pitch, pitch * 1.5}, 0.12, 0.13, false), tone(new double[]{pitch * 2}, 0.03, 0.12, true));
            }
            case GLINT -> concat(tone(new double[]{2093}, 0.05, 0.1, false), tone(new double[]{3136}, 0.22, 0.09, false));
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

    /** Low rushing noise with random pops, like a card catching fire. */
    private static byte[] crackle(double seconds, double volume) {
        int samples = (int) (RATE * seconds);
        byte[] pcm = new byte[samples * 2];
        java.util.Random noise = new java.util.Random(23);
        double low = 0, pop = 0;
        for (int i = 0; i < samples; i++) {
            double t = (double) i / samples, envelope = Math.min(1, t * 8) * (1 - t);
            low += 0.06 * (noise.nextGaussian() - low);
            // Pops arrive at random and die away within a few milliseconds.
            if (noise.nextDouble() < 0.0012) pop = noise.nextBoolean() ? 1 : -1;
            pop *= 0.92;
            write(pcm, i, (low * 0.8 + pop) * envelope * volume * 30_000);
        }
        return pcm;
    }

    /** Bright noise chopped into fast grains, like paper fibres giving way as a card is torn. */
    private static byte[] rip(double seconds, double volume) {
        int samples = (int) (RATE * seconds);
        byte[] pcm = new byte[samples * 2];
        java.util.Random noise = new java.util.Random(31);
        double low = 0, grain = 0;
        for (int i = 0; i < samples; i++) {
            double t = (double) i / samples, envelope = Math.min(1, t * 6) * Math.pow(1 - t, 1.5);
            double white = noise.nextGaussian();
            // Subtracting a low-passed copy leaves the hiss; each fibre snaps as a short grain.
            low += 0.1 * (white - low);
            if (noise.nextDouble() < 0.004) grain = 0.6 + noise.nextDouble() * 0.4;
            grain *= 0.996;
            write(pcm, i, (white - low) * (0.25 + grain) * envelope * volume * 30_000);
        }
        return pcm;
    }

    /** A wavering sine sliding from one pitch to another, for a card pulled into a vortex. */
    private static byte[] glissando(double from, double to, double seconds, double volume) {
        int samples = (int) (RATE * seconds);
        byte[] pcm = new byte[samples * 2];
        double phase = 0;
        for (int i = 0; i < samples; i++) {
            double t = (double) i / samples, frequency = from * Math.pow(to / from, t) * (1 + 0.03 * Math.sin(i / RATE * 40));
            phase += 2 * Math.PI * frequency / RATE;
            write(pcm, i, Math.sin(phase) * Math.sin(Math.PI * t) * volume * 12_000);
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

    /** Lets queued cues finish, then releases the audio device. Safe to call repeatedly and from any thread. */
    @Override public void close() {
        synchronized (lifecycle) {
            if (closed) return;
            closed = true;
            worker.execute(this::releaseLine);
            worker.shutdown();
        }
    }
}
