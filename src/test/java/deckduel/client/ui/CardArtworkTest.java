package deckduel.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import deckduel.cards.CatalogLoader;
import deckduel.core.card.CardDef;
import deckduel.core.card.CardKind;
import java.awt.Rectangle;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;

class CardArtworkTest {
    @Test void everyCatalogCardAndTheSharedBackHaveRuntimeArtwork() throws Exception {
        for (CardDef card : CatalogLoader.loadBundled().cards()) {
            BufferedImage art = CardImages.shared().decode(card.id());
            assertNotNull(art, "missing art for " + card.id());
            assertEquals(320, art.getWidth(), card.id());
            assertEquals(480, art.getHeight(), card.id());
        }
        assertNotNull(CardImages.shared().decode(CardImages.BACK));
    }

    @Test void retiredArtworkIsNoLongerShipped() throws Exception {
        for (String retired : List.of("hu-envoy", "pl-chaplain", "pl-siege", "st-sabotage", "hu-banner-hall", "au-chancery"))
            assertNull(CardImages.shared().decode(retired), retired + " was replaced");
        assertNull(CardImages.class.getResource("/assets/cards/manuscript-v1/hu-footmen.png"), "full-size sources stay out of the build");
    }

    @Test void printedNeutralCardsShowTheirArtWithoutDuplicatedTitlesOrText() throws Exception {
        var catalog = CatalogLoader.loadBundled();
        for (String id : List.of("spy", "embassy")) {
            CardDef card = catalog.card(id);
            BufferedImage art = CardImages.shared().decode(id);
            Rectangle r = new Rectangle(0, 0, 104, 156);
            var face = new BufferedImage(r.width, r.height, BufferedImage.TYPE_INT_ARGB);
            var g = face.createGraphics();
            CardFace.paint(g, card, r, false, List.of());
            g.dispose();
            var bare = new BufferedImage(r.width, r.height, BufferedImage.TYPE_INT_ARGB);
            var b = UiKit.nice(bare.createGraphics());
            b.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            b.clip(new RoundRectangle2D.Float(r.x, r.y, r.width, r.height, 9, 9));
            b.drawImage(art, r.x, r.y, r.width, r.height, null);
            b.dispose();
            int differing = 0;
            // Inside the card's edge stroke, the face must be the art alone: no chip, band or summary on top.
            for (int y = 4; y < r.height - 4; y++)
                for (int x = 4; x < r.width - 4; x++) if (face.getRGB(x, y) != bare.getRGB(x, y)) differing++;
            assertEquals(0, differing, id + " has no overlay over its printed text");
        }
    }

    @Test void cardsWithAndWithoutArtworkBothRender() throws Exception {
        var catalog = CatalogLoader.loadBundled();
        CardDef illustrated = catalog.card("hu-knight");
        CardImages.shared().decode(illustrated.id());
        CardDef withoutArt = new CardDef("future-card", "Future Card", CardKind.UNIT, 3,
                List.of("Hungary"), List.of("Bowmen"), null);
        assertNull(CardImages.shared().decode(withoutArt.id()));
        var image = new BufferedImage(240, 180, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        CardFace.paint(g, illustrated, new Rectangle(4, 4, 104, 156), true, List.of(deckduel.core.card.Suppression.TAGS));
        CardFace.paint(g, withoutArt, new Rectangle(120, 4, 104, 156), false, List.of());
        CardFace.paintBack(g, new Rectangle(4, 4, 22, 33));
        g.dispose();
        assertNotEquals(image.getRGB(56, 80), image.getRGB(172, 80), "art and the procedural face differ");
    }
}
