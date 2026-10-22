package deckduel.client.ui;

import static org.junit.jupiter.api.Assertions.*;
import deckduel.cards.CatalogLoader;
import deckduel.core.card.CardDef;
import deckduel.core.card.Suppression;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaintedCardArtTest {
    private static final CardDef CARD = CatalogLoader.loadBundled().card("nerath-knight");

    @Test void everyCatalogUnitHasAPaintingAndNoSpellDoes() {
        var catalog = CatalogLoader.loadBundled();
        assertEquals(catalog.units(), catalog.cards().stream().filter(PaintedCardArt::supports).toList());
        for (CardDef unit : catalog.units())
            assertNotNull(PaintedCardArtTest.class.getResource("/assets/cards/" + unit.id() + ".png"), unit.id());
        assertNotNull(PaintedCardArtTest.class.getResource("/assets/cards/card-back.png"));
        assertFalse(PaintedCardArt.supports(null), "face-down cards never expose a painting");
    }

    @Test void aPaintingIsNotUsedWhenTheCatalogDisagreesWithWhatItPrints() {
        assertFalse(PaintedCardArt.supports(new CardDef(CARD.id(), CARD.name(), CARD.kind(), 6,
                CARD.regions(), CARD.classes(), null)), "printed points must match");
        assertFalse(PaintedCardArt.supports(new CardDef("dravorn-knight", CARD.name(), CARD.kind(), 5,
                CARD.regions(), CARD.classes(), null)), "identity must agree with nation");
        assertFalse(PaintedCardArt.supports(new CardDef("nerath-wizard", CARD.name(), CARD.kind(), 5,
                CARD.regions(), CARD.classes(), null)), "identity must agree with class");
        var drawn = CardFace.sprite(new CardDef(CARD.id(), CARD.name(), CARD.kind(), 6, CARD.regions(), CARD.classes(), null),
                List.of(), 104, 156, false, 1);
        assertNotSame(CardFace.sprite(CARD, List.of(), 104, 156, false, 1), drawn, "changed points get their own face");
    }

    @Test void facesFillTheCardWithRoundedCornersAndKeepTheDisableOverlay() {
        var sprite = CardFace.sprite(CARD, List.of(), 104, 156, false, 2);
        assertEquals((104 + CardFace.SHADOW_X) * 2, sprite.getWidth());
        assertSame(sprite, CardFace.sprite(CARD, List.of(), 104, 156, false, 2), "sprites are cached");
        assertEquals(255, sprite.getRGB(104, 156) >>> 24, "opaque painted body");
        assertTrue((sprite.getRGB(0, 0) >>> 24) < 255, "rounded corner shows the table");
        var disabled = CardFace.sprite(CARD, List.of(Suppression.TAGS), 104, 156, false, 2);
        assertNotEquals(sprite.getRGB(104, 156), disabled.getRGB(104, 156), "disables still shade the face");
    }

    @Test void theBackIsThePaintedDesign() {
        var back = CardFace.backSprite(104, 156, 1);
        assertSame(back, CardFace.backSprite(104, 156, 1), "backs use the shared sprite cache");
        int centre = back.getRGB(52, 78);
        assertEquals(255, centre >>> 24, "no holes in the card body");
        assertTrue((centre & 0xFF) > (centre >> 16 & 0xFF), "the back's centre gem is blue");
    }
}
