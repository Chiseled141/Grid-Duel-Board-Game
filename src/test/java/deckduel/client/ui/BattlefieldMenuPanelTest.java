package deckduel.client.ui;

import deckduel.client.ClientSettings;
import deckduel.client.MenuTheme;
import deckduel.client.state.*;
import deckduel.net.*;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class BattlefieldMenuPanelTest {
    @Test void additionalGrassAndFlowerBedsHaveLocalMotionInTheBundledLoop() throws Exception {
        var resource = getClass().getResource("/assets/" + MenuTheme.GOLDEN_FIELDS.clip());
        try (var stream = resource.openStream(); var input = ImageIO.createImageInputStream(stream)) {
            var reader = ImageIO.getImageReadersByFormatName("gif").next();
            try {
                reader.setInput(input);
                var first = reader.read(0); var later = reader.read(67);
                assertTrue(changed(first, later, new Rectangle(735, 520, 44, 30)) > 30,
                        "existing grass blades beside the middle-distance rock now sway");
                assertTrue(changed(first, later, new Rectangle(350, 575, 75, 55)) > 30,
                        "previously stationary flowers in the lower meadow now sway");
            } finally { reader.dispose(); }
        }
    }

    private int changed(BufferedImage first, BufferedImage later, Rectangle bounds) {
        int count = 0;
        for (int y = bounds.y; y < bounds.y + bounds.height; y++)
            for (int x = bounds.x; x < bounds.x + bounds.width; x++)
                if (first.getRGB(x, y) != later.getRGB(x, y)) count++;
        return count;
    }

    private ClientModel model() {
        return new ClientModel(new ServerConnection() {
            public void send(Message message) { }
            public boolean isConnected() { return false; }
            public void ensureConnected(String host, int port) { }
        });
    }

    @Test
    void bundledClipHasActualFramesAndLoops() throws Exception {
        for (MenuTheme theme : MenuTheme.values()) assertClip(theme);
    }

    private void assertClip(MenuTheme theme) throws Exception {
        var resource = getClass().getResource("/assets/" + theme.clip());
        assertNotNull(resource);
        try (var stream = resource.openStream(); var input = ImageIO.createImageInputStream(stream)) {
            var reader = ImageIO.getImageReadersByFormatName("gif").next();
            try {
                reader.setInput(input);
                int count = reader.getNumImages(true);
                assertEquals(400, count, "sixteen seconds of motion at 25 fps");
                for (int frame = 0; frame < count; frame++) {
                    var tree = reader.getImageMetadata(frame).getAsTree("javax_imageio_gif_image_1.0");
                    var controls = ((javax.imageio.metadata.IIOMetadataNode) tree)
                            .getElementsByTagName("GraphicControlExtension");
                    assertEquals("4", controls.item(0).getAttributes().getNamedItem("delayTime").getNodeValue(),
                            "uniform 40 ms frame timing avoids GIF cadence jitter");
                }
                assertTrue(reader.getWidth(0) >= 960);
                var first = reader.read(0);
                var later = reader.read(count / 4);
                assertNotEquals(java.util.Arrays.hashCode(first.getRGB(0, 0, first.getWidth(), first.getHeight(), null, 0, first.getWidth())),
                        java.util.Arrays.hashCode(later.getRGB(0, 0, later.getWidth(), later.getHeight(), null, 0, later.getWidth())));
                var metadata = reader.getImageMetadata(0).getAsTree("javax_imageio_gif_image_1.0");
                assertTrue(hasLoopExtension(metadata), "GIF must loop indefinitely");
            } finally { reader.dispose(); }
        }
    }

    @Test
    void theRemainingMedievalSceneLoadsOnAWorker() throws Exception {
        var settings = new ClientSettings();
        var reference = new java.util.concurrent.atomic.AtomicReference<BattlefieldMenuPanel>();
        SwingUtilities.invokeAndWait(() -> {
            var panel = new BattlefieldMenuPanel(model(), Screen.LOGIN, settings) { };
            panel.setSize(640, 400); reference.set(panel);
        });
        awaitBackground(reference.get());
        assertEquals(MenuTheme.GOLDEN_FIELDS, settings.menuTheme());
    }

    private BufferedImage awaitBackground(BattlefieldMenuPanel panel) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) {
            var image = new BufferedImage(640, 400, BufferedImage.TYPE_INT_RGB);
            SwingUtilities.invokeAndWait(() -> {
                var graphics = image.createGraphics();
                panel.paint(graphics);
                graphics.dispose();
            });
            if (image.getRGB(320, 200) != Theme.BG.getRGB()) return image;
            Thread.sleep(20);
        }
        throw new AssertionError("Background did not load on its worker");
    }

    private boolean hasLoopExtension(org.w3c.dom.Node node) {
        if (node instanceof javax.imageio.metadata.IIOMetadataNode value
                && "ApplicationExtension".equals(value.getNodeName())
                && value.getUserObject() instanceof byte[] data
                && data.length >= 3 && data[0] == 1 && data[1] == 0 && data[2] == 0) return true;
        for (var child = node.getFirstChild(); child != null; child = child.getNextSibling())
            if (hasLoopExtension(child)) return true;
        return false;
    }

    @Test
    void playbackIsDisabledForReducedMotionOtherScreensAndHiddenMenus() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var model = model();
            class Menu extends BattlefieldMenuPanel {
                boolean visible = true;
                Menu() { super(model, Screen.LOGIN, new ClientSettings()); }
                @Override public boolean isShowing() { return visible; }
            }
            var panel = new Menu();
            model.setReducedMotion(false);
            assertTrue(panel.animationEnabled());
            model.setReducedMotion(true);
            assertFalse(panel.animationEnabled());
            assertFalse(panel.imageUpdate(null, java.awt.image.ImageObserver.FRAMEBITS, 0, 0, 0, 0));
            model.setReducedMotion(false);
            panel.visible = false;
            assertFalse(panel.animationEnabled());
            panel.visible = true;
            assertTrue(panel.animationEnabled());
            model.handleMessage(new LoginResponse(true, null, new UserProfile("alice", 0, 0)));
            assertFalse(panel.animationEnabled());
        });
    }

    @Test
    void coverScalingDoesNotStretchOrAlterTheSource() {
        var source = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        var g = source.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, 200, 100);
        g.setColor(Color.BLUE);
        g.fillRect(100, 0, 100, 100);
        g.dispose();
        var canvas = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        var output = canvas.createGraphics();
        BattlefieldMenuPanel.paintCover(output, source, 100, 100, null);
        output.dispose();
        assertEquals(Color.ORANGE.getRGB(), canvas.getRGB(99, 99));
        assertEquals(Color.BLUE.getRGB(), source.getRGB(199, 99));
    }

}
