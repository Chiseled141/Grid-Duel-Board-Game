package deckduel.client;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

/** Loops the bundled menu track off the EDT, retaining its position while muted. */
public final class MenuMusic implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(MenuMusic.class.getName());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "menu-music");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean playingRequested;
    private volatile boolean closed;
    private volatile MenuTheme requestedTheme = MenuTheme.GOLDEN_FIELDS;
    private MenuTheme loadedTheme;
    // Clip ownership belongs exclusively to the audio worker, including loading and closing.
    private Clip clip;
    private boolean unavailable;
    private long pausedMicros;
    private final ClipFactory clipFactory;

    @FunctionalInterface
    interface ClipFactory { Clip create() throws LineUnavailableException; }

    /** Uses the default audio output; playback remains on the audio worker. */
    public MenuMusic() { this(AudioSystem::getClip); }

    MenuMusic(ClipFactory clipFactory) { this.clipFactory = clipFactory; }

    /** Requests playback or pause without loading audio or waiting for a mixer on the caller. */
    public synchronized void setPlaying(boolean playing, MenuTheme theme) {
        if (closed) {
            return;
        }
        requestedTheme = java.util.Objects.requireNonNull(theme);
        playingRequested = playing;
        worker.execute(this::updatePlayback);
    }

    private void updatePlayback() {
        if (closed) {
            return;
        }
        MenuTheme theme = requestedTheme;
        if (theme != loadedTheme) {
            releaseClip();
            pausedMicros = 0;
            loadedTheme = theme;
            unavailable = false;
        }
        if (playingRequested && clip == null && !unavailable) {
            loadClip(theme);
        }
        if (clip == null) {
            return;
        }
        // Loading can take time: honor the latest screen/mute choice before starting sound.
        if (playingRequested && !closed && requestedTheme == loadedTheme) {
            if (!clip.isRunning()) {
                clip.loop(Clip.LOOP_CONTINUOUSLY);
            }
        } else {
            // Muting must release the output, not leave a paused native line open indefinitely.
            pausedMicros = clip.getMicrosecondPosition();
            releaseClip();
        }
    }

    private void loadClip(MenuTheme theme) {
        Clip candidate = null;
        try {
            URL resource = MenuMusic.class.getResource(theme.music());
            if (resource == null) {
                throw new IOException("Missing bundled menu music");
            }
            try (AudioInputStream input = AudioSystem.getAudioInputStream(resource)) {
                candidate = clipFactory.create();
                candidate.open(input);
            }
            candidate.setMicrosecondPosition(Math.min(pausedMicros, Math.max(0, candidate.getMicrosecondLength() - 1)));
            if (candidate.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gain = (FloatControl) candidate.getControl(FloatControl.Type.MASTER_GAIN);
                gain.setValue(Math.max(gain.getMinimum(), Math.min(-12f, gain.getMaximum())));
            }
            clip = candidate;
        } catch (IOException | UnsupportedAudioFileException | LineUnavailableException
                 | IllegalArgumentException | SecurityException exception) {
            if (candidate != null) {
                candidate.close();
            }
            // Audio is optional: an absent output device must not prevent playing the game.
            unavailable = true;
            LOG.log(Level.WARNING, "Menu music unavailable; continuing without audio", exception);
        }
    }

    private void releaseClip() {
        if (clip == null) return;
        clip.stop();
        clip.flush();
        clip.close();
        clip = null;
    }

    /** Releases audio resources asynchronously; later playback requests are ignored. */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        playingRequested = false;
        worker.execute(this::releaseClip);
        worker.shutdown();
    }
}
