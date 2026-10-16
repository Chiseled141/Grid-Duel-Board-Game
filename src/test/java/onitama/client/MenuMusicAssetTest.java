package onitama.client;

import javax.sound.sampled.AudioSystem;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuMusicAssetTest {
    @Test void medievalThemeHasADecodableLoopWithoutAnExternalPlayer() throws Exception {
        assertEquals(1, MenuTheme.values().length);
        for (MenuTheme theme : MenuTheme.values()) {
            var resource = MenuMusic.class.getResource(theme.music());
            assertNotNull(resource);
            try (var input = AudioSystem.getAudioInputStream(resource)) {
                assertTrue(input.getFrameLength() > input.getFormat().getFrameRate() * 60);
                assertEquals(16, input.getFormat().getSampleSizeInBits());
                assertEquals(2, input.getFormat().getChannels());
            }
        }
    }
}
