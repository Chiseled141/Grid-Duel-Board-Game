package onitama.client;

import javax.sound.sampled.AudioSystem;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuMusicAssetTest {
    @Test void eachThemeHasItsOwnDecodableLoopWithoutAnExternalPlayer() throws Exception {
        assertNotEquals(MenuTheme.VALLEY.music(), MenuTheme.GOLDEN_FIELDS.music());
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
