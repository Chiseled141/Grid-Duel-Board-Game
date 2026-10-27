package deckduel.client.ui;

import deckduel.core.card.King;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KingArtTest {
    private static final Map<King, String> ORIGINALS = Map.of(
            King.VARKHAN, "dd546646787056a2663d8f0226dcc4c1b26c2c891fb2247dabb7b611eb771218",
            King.ALTHARION, "1aef61154ae7dd4cc5fb0205384ed7c906457a4c56c86c6014384ee3aa7258b5",
            King.MORDRAVEN, "c85ea21cfa2fc312eaeb1f04556efde462e94e70d1aa14f2d3cc5317709c6a67",
            King.NETHRION, "d6eca7366e15bf7b700176d94445ab5ffed1e7a6a6a305e59791fe9211468e50",
            King.AURENDOR, "3b7b949fdbc27879a1ee3fffa816948313237f69e22ca628e1569c5795559e9f",
            King.THALVAREN, "5ebf19e7351fd5a0486667b2c3d7d82a0337e8cbe2760724c2593006d4f83e9c",
            King.VAELTHOR, "748fa027c602211c9b970e3fdcb9b1679e037d621e3d55696d2d5ee1205834b4");

    @Test void everyPackagedKingIsAnUnmodifiedCopyOfTheSuppliedPainting() throws Exception {
        for (King king : King.values()) {
            try (var resource = KingArtTest.class.getResourceAsStream(KingArt.path(king))) {
                assertNotNull(resource);
                assertEquals(ORIGINALS.get(king), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(resource.readAllBytes())), king.title());
            }
            try (var resource = KingArtTest.class.getResourceAsStream(KingArt.path(king))) {
                var image = ImageIO.read(resource);
                assertTrue(image.getWidth() > 700 && image.getHeight() > 1000);
                assertEquals(1060.0 / 1482, image.getWidth() / (double) image.getHeight(), .01);
            }
        }
    }
}
