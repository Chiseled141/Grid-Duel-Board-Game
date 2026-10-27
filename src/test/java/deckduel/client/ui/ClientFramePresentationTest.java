package deckduel.client.ui;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import static org.junit.jupiter.api.Assertions.*;

/** Preserved menu shell presentation. */
class ClientFramePresentationTest {
    @Test void windowHasNoMenuStripAndMusicFloatsOverTheScene() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var frame = new ClientFrame(null, null);
            try {
                assertEquals("Deck Duel", frame.getTitle());
                frame.setVisible(true); frame.validate();
                assertNull(frame.getJMenuBar());
                var scene = (JLayeredPane) frame.getContentPane().getComponent(0);
                var content = scene.getComponentsInLayer(JLayeredPane.DEFAULT_LAYER)[0];
                assertEquals(new java.awt.Rectangle(0, 0, scene.getWidth(), scene.getHeight()), content.getBounds());
                var sound = (JToggleButton) scene.getComponentsInLayer(JLayeredPane.PALETTE_LAYER)[0];
                assertEquals("menuMusicToggle", sound.getName());
                assertFalse(sound.isOpaque()); assertFalse(sound.isContentAreaFilled());
                assertTrue(sound.getY() >= 0 && sound.getX() + sound.getWidth() <= scene.getWidth());
                assertTrue(sound.getToolTipText().startsWith("Menu music:"));
            } finally { frame.dispose(); }
        });
    }
}
