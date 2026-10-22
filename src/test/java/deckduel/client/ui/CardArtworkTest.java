package deckduel.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import deckduel.cards.CatalogLoader;
import deckduel.core.card.CardDef;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;

class CardArtworkTest {
    @Test void everyCatalogCardIsDrawnFromCodeAndNoPaintedArtShips() {
        var catalog = CatalogLoader.loadBundled();
        assertTrue(catalog.cards().stream().allMatch(PixelCardFace::supports), "all 45 cards render from code");
        for (CardDef card : catalog.cards())
            assertNull(CardArtworkTest.class.getResource("/assets/cards/runtime/" + card.id() + ".jpg"), card.id());
        for (String retired : List.of("card-back", "sabotage", "hu-battering-ram", "hu-footmen", "hu-envoy", "pl-chaplain"))
            assertNull(CardArtworkTest.class.getResource("/assets/cards/runtime/" + retired + ".jpg"), retired);
    }

    @Test void everyCatalogCardRendersAnOpaqueFace() throws Exception {
        var catalog = CatalogLoader.loadBundled();
        for (CardDef card : catalog.cards()) {
            Rectangle r = new Rectangle(0, 0, 104, 156);
            var face = new BufferedImage(r.width, r.height, BufferedImage.TYPE_INT_ARGB);
            var g = face.createGraphics();
            CardFace.paint(g, card, r, false, List.of());
            g.dispose();
            assertEquals(255, face.getRGB(52, 78) >>> 24, card.id() + " has an opaque fallback face");
            assertNotEquals(face.getRGB(52, 78), face.getRGB(0, 0), card.id() + " has a visible card body");
        }
    }

    @Test void fallbackShowsSelectionAndSuppressionAndConcealsCardBacks() throws Exception {
        var catalog = CatalogLoader.loadBundled();
        CardDef card = catalog.card("hu-knight");
        var image = new BufferedImage(240, 180, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        CardFace.paint(g, card, new Rectangle(4, 4, 104, 156), true, List.of(deckduel.core.card.Suppression.TAGS));
        CardFace.paint(g, card, new Rectangle(120, 4, 104, 156), false, List.of());
        g.dispose();
        assertNotEquals(image.getRGB(56, 80), image.getRGB(172, 80), "suppression shades the fallback face");
        BufferedImage back = CardFace.backSprite(104, 156, 1);
        assertEquals(255, back.getRGB(52, 78) >>> 24, "the shared fallback back remains visible");
        assertNotEquals(image.getRGB(172, 80), back.getRGB(52, 78), "the back conceals the face");
    }
}
