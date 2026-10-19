package deckduel.client.ui;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import static org.junit.jupiter.api.Assertions.*;

/** Preserved menu shell and the card-table placeholder. */
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

    @Test void emptyCardTableDoesNotInventScoresOrSendGameplay() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var back = new java.util.concurrent.atomic.AtomicInteger();
            var table = new CardTablePanel(back::incrementAndGet);
            table.setSize(1280, 720); table.doLayout();
            DuelScorePanel score = null;
            for (var component : table.getComponents()) {
                if (component instanceof DuelScorePanel panel) score = panel;
                if (component instanceof JButton button) button.doClick();
            }
            assertNotNull(score);
            assertEquals("Waiting for match scores", score.getAccessibleContext().getAccessibleDescription());
            assertEquals(1, back.get());
            assertTrue(score.getBounds().x + score.getWidth() <= 1280);
            var image = new java.awt.image.BufferedImage(1280, 720, java.awt.image.BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics(); table.paint(graphics); graphics.dispose();
        });
    }
}
