package deckduel.client.ui;

import static org.junit.jupiter.api.Assertions.*;
import deckduel.cards.CatalogLoader;
import deckduel.core.card.CardDef;
import deckduel.core.card.Effect;
import deckduel.core.card.Suppression;
import deckduel.core.card.TargetSide;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaintedCardArtTest {
    private static final CardDef CARD = CatalogLoader.loadBundled().card("nerath-knight");

    @Test void everyCatalogCardHasAPainting() {
        var catalog = CatalogLoader.loadBundled();
        assertEquals(catalog.cards(), catalog.cards().stream().filter(PaintedCardArt::supports).toList());
        for (CardDef unit : catalog.units())
            assertNotNull(PaintedCardArtTest.class.getResource("/assets/cards/" + unit.id() + ".png"), unit.id());
        for (CardDef spell : catalog.spells())
            assertNotNull(PaintedCardArtTest.class.getResource("/assets/cards/" + spell.id() + ".jpg"), spell.id());
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

    @Test void aSpellPaintingIsNotUsedForAChangedCountOrTarget() {
        var catalog = CatalogLoader.loadBundled();
        var changes = java.util.Map.of(
                "draw-two", new Effect.Draw(3),
                "spy", new Effect.Spy(1),
                "reconnaissance", new Effect.Reconnaissance(2),
                "exchange", new Effect.Exchange(1, 3),
                "random-discard", new Effect.RandomDiscard(2),
                "random-steal", new Effect.RandomSteal(2),
                "destroy-unit", new Effect.Destroy(TargetSide.OWN),
                "disable-tags", new Effect.Disable(TargetSide.OWN, Suppression.TAGS),
                "disable-points", new Effect.Disable(TargetSide.OPPONENT, Suppression.TAGS));
        changes.forEach((id, effect) -> {
            var original = catalog.card(id);
            var changed = new CardDef(id, original.name(), original.kind(), 0, List.of(), List.of(), effect);
            assertTrue(PaintedCardArt.supports(original), id);
            assertFalse(PaintedCardArt.supports(changed), id + " must not show a painting of a different effect");
        });
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
