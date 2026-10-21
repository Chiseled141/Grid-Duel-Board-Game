package deckduel.client.ui;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;

/** Offline check that pxl's exported PNGs and the runtime Java drawing instructions agree. */
public final class VerifyPxlArt {
    /** Takes the pxl output's sprites directory; compares all eleven designs pixel by pixel. */
    public static void main(String[] args) throws Exception {
        for (String id : List.of("disable-tags", "disable-points", "destroy-unit", "random-discard",
                "random-steal", "draw-two", "exchange", "reconnaissance", "spy", "clear-disable", "card-back")) {
            BufferedImage expected = ImageIO.read(Path.of(args[0], id + ".png").toFile());
            BufferedImage actual = new BufferedImage(80, 120, BufferedImage.TYPE_INT_ARGB);
            var g = actual.createGraphics();
            try { PxlCardArt.paint(g, id); }
            finally { g.dispose(); }
            if (expected.getWidth() != 80 || expected.getHeight() != 120)
                throw new AssertionError(id + ": expected an 80x120 pxl source");
            for (int y = 0; y < 120; y++) for (int x = 0; x < 80; x++)
                if (expected.getRGB(x, y) != actual.getRGB(x, y))
                    throw new AssertionError(id + ": pixel mismatch at " + x + "," + y);
        }
        System.out.println("PASS: all 11 pxl designs match runtime Java at every pixel (105600 pixels)");
    }
}
