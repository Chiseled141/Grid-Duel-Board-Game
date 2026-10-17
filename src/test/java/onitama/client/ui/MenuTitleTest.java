package onitama.client.ui;

import java.awt.*;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuTitleTest {
    @Test void titleStaysInUpperSkyAcrossMenuWindowSizes() throws Exception {
        try (var stream = getClass().getResourceAsStream("/fonts/cinzel-decorative-bold.ttf")) {
            assertNotNull(stream);
            var font = Font.createFont(Font.TRUETYPE_FONT, stream);
            assertTrue(font.getFamily().contains("Cinzel"));
            assertEquals(-1, font.canDisplayUpTo("Deck Duel"));
        }
        for (Dimension size : java.util.List.of(new Dimension(1280, 660), new Dimension(1280, 800), new Dimension(1960, 1180))) {
            Rectangle bounds = MenuTitle.bounds(size.width, size.height);
            assertTrue(bounds.x > size.width * .25);
            assertTrue(bounds.x + bounds.width < size.width - 80);
            assertTrue(bounds.y >= 0 && bounds.y + bounds.height < size.height * .27);
            var image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
            var g = image.createGraphics(); MenuTitle.paint(g, size.width, size.height); g.dispose();
            int pixels = 0;
            for (int y = 0; y < size.height; y++) for (int x = 0; x < size.width; x++) {
                if ((image.getRGB(x, y) >>> 24) == 0) continue;
                assertTrue(bounds.contains(x, y), "title must stay inside its sky bounds"); pixels++;
            }
            assertTrue(pixels > 1000);
        }
    }
}
