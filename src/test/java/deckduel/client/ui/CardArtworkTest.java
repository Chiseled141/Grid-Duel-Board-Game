package deckduel.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import deckduel.cards.CatalogLoader;
import deckduel.core.card.CardDef;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;

class CardArtworkTest {
    @Test void everyCatalogCardHasArtUnitsPaintedAndSpellsInPixels() {
        var catalog = CatalogLoader.loadBundled();
        assertTrue(catalog.cards().stream().allMatch(card -> PaintedCardArt.supports(card) != PixelSpellArt.supports(card)),
                "each of the 45 cards has exactly one kind of art");
    }

    @Test void everyCatalogCardRendersAnOpaqueFace() throws Exception {
        var catalog = CatalogLoader.loadBundled();
        for (CardDef card : catalog.cards()) {
            Rectangle r = new Rectangle(0, 0, 104, 156);
            var face = new BufferedImage(r.width, r.height, BufferedImage.TYPE_INT_ARGB);
            var g = face.createGraphics();
            CardFace.paint(g, card, r, false, List.of());
            g.dispose();
            assertEquals(255, face.getRGB(52, 78) >>> 24, card.id() + " has an opaque face");
            assertNotEquals(face.getRGB(52, 78), face.getRGB(0, 0), card.id() + " has a visible card body");
        }
    }

    @Test void facesShowSelectionAndSuppressionAndBacksConcealThem() throws Exception {
        var catalog = CatalogLoader.loadBundled();
        CardDef card = catalog.card("sylvaren-knight");
        var image = new BufferedImage(240, 180, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        CardFace.paint(g, card, new Rectangle(4, 4, 104, 156), true, List.of(deckduel.core.card.Suppression.TAGS));
        CardFace.paint(g, card, new Rectangle(120, 4, 104, 156), false, List.of());
        g.dispose();
        assertNotEquals(image.getRGB(56, 80), image.getRGB(172, 80), "suppression shades the face");
        BufferedImage back = CardFace.backSprite(104, 156, 1);
        assertEquals(255, back.getRGB(52, 78) >>> 24, "the shared back is opaque");
        assertNotEquals(image.getRGB(172, 80), back.getRGB(52, 78), "the back conceals the face");
    }
}
