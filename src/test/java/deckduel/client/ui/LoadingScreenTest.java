package deckduel.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.Scene;
import org.junit.jupiter.api.Test;

class LoadingScreenTest {
    @Test void theLoadingScreenCoversTheTableThenHandsBack() throws Exception {
        assertNotNull(LoadingScreen.class.getResource("/assets/loading-castle-hall-animation-ready-v2.png"));
        boolean[] done = {false};
        FxTesting.run(() -> {
            var screen = new LoadingScreen();
            new Scene(screen, 800, 450);
            screen.play(() -> done[0] = true);
        });
        Thread.sleep(LoadingScreen.HOLD_MILLIS / 2);
        FxTesting.run(() -> assertFalse(done[0], "it stays while the table gets ready"));
        Thread.sleep(LoadingScreen.HOLD_MILLIS / 2 + 500);
        FxTesting.run(() -> assertTrue(done[0], "then it hands back to the table"));
    }
}
