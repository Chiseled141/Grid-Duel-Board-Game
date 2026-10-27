package deckduel.client;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MenuMusicTest {
    @Test void muteReleasesTheDeviceAndUnmuteResumesFromTheSamePosition() throws Exception {
        var first = new AudioLineStub();
        var second = new AudioLineStub();
        var opened = new AtomicInteger();
        try (var music = new MenuMusic(() -> opened.getAndIncrement() == 0 ? first : second)) {
            music.setPlaying(false, MenuTheme.GOLDEN_FIELDS);
            music.setPlaying(true, MenuTheme.GOLDEN_FIELDS);
            assertTrue(first.looping.await(3, TimeUnit.SECONDS));
            first.position = 12_345_000;
            music.setPlaying(false, MenuTheme.GOLDEN_FIELDS);
            assertTrue(first.closed.await(3, TimeUnit.SECONDS));
            assertFalse(first.isOpen(), "mute must release the native output");
            assertFalse(first.isRunning());
            assertEquals(java.util.List.of("open", "seek", "loop", "stop", "flush", "close"), first.calls);
            music.setPlaying(true, MenuTheme.GOLDEN_FIELDS);
            assertTrue(second.looping.await(3, TimeUnit.SECONDS));
            assertEquals(12_345_000, second.position, "unmute resumes the track rather than restarting it");
        }
        assertTrue(second.closed.await(3, TimeUnit.SECONDS));
    }

    @Test void mutingWhileTheClipLoadsClosesItWithoutStartingPlayback() throws Exception {
        var creating = new java.util.concurrent.CountDownLatch(1);
        var loaded = new java.util.concurrent.CountDownLatch(1);
        var line = new AudioLineStub();
        try (var music = new MenuMusic(() -> {
            creating.countDown();
            try {
                if (!loaded.await(3, TimeUnit.SECONDS)) throw new AssertionError("load not released");
            } catch (InterruptedException interrupted) { throw new AssertionError(interrupted); }
            return line;
        })) {
            music.setPlaying(true, MenuTheme.GOLDEN_FIELDS);
            assertTrue(creating.await(3, TimeUnit.SECONDS));
            music.setPlaying(false, MenuTheme.GOLDEN_FIELDS);
            loaded.countDown();
            assertTrue(line.closed.await(3, TimeUnit.SECONDS));
            assertFalse(line.calls.contains("loop"), "mute during loading must never start the prepared clip");
        } finally { loaded.countDown(); }
    }
}
