package deckduel.client.ui;

import static org.junit.jupiter.api.Assertions.*;
import deckduel.cards.CatalogLoader;
import deckduel.core.card.CardDef;
import deckduel.core.card.Effect;
import deckduel.core.card.Suppression;
import deckduel.core.card.TargetSide;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class PixelSpellArtTest {
    @Test void everySpellAndNoUnitHasAPixelFace() {
        var catalog = CatalogLoader.loadBundled();
        assertEquals(catalog.spells(), catalog.cards().stream().filter(PixelSpellArt::supports).toList());
        assertFalse(PixelSpellArt.supports(null), "face-down cards never expose a design");
    }

    @Test void fixedGridHasOnlyFlatPaletteColoursAndTransparentCorners() {
        for (CardDef card : CatalogLoader.loadBundled().spells()) {
            var nativeFace = PixelSpellArt.render(card);
            assertEquals(80, nativeFace.getWidth());
            assertEquals(120, nativeFace.getHeight());
            assertEquals(0, nativeFace.getRGB(0, 0) >>> 24);
            var colours = new HashSet<Integer>();
            for (int y = 0; y < nativeFace.getHeight(); y++)
                for (int x = 0; x < nativeFace.getWidth(); x++) {
                    int pixel = nativeFace.getRGB(x, y);
                    assertTrue(pixel >>> 24 == 0 || pixel >>> 24 == 255, "no antialiasing in the native sprite");
                    colours.add(pixel);
                }
            assertTrue(colours.size() <= 16, "limited palette including transparency");
        }
    }

    @Test void gameSpriteScalesEveryInteriorPixelWithoutBlending() {
        for (CardDef card : CatalogLoader.loadBundled().spells()) {
            var nativeFace = PixelSpellArt.render(card);
            var sprite = CardFace.sprite(card, List.of(), 240, 360, false, 1);
            assertSame(sprite, CardFace.sprite(card, List.of(), 240, 360, false, 1), "sprites are cached");
            for (int y = 4; y < 115; y++)
                for (int x = 4; x < 75; x++)
                    for (int dy = 0; dy < 3; dy++)
                        for (int dx = 0; dx < 3; dx++)
                            assertEquals(nativeFace.getRGB(x, y), sprite.getRGB(x * 3 + dx, y * 3 + dy));
            var disabled = CardFace.sprite(card, List.of(Suppression.POINTS), 240, 360, false, 1);
            assertNotEquals(sprite.getRGB(120, 160), disabled.getRGB(120, 160), "existing disable overlay still applies");
        }
    }

    @Test void spellArtRejectsChangedCountsAndTargetDirections() {
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
            assertTrue(PixelSpellArt.supports(original), id);
            assertFalse(PixelSpellArt.supports(changed), id + " must not illustrate a different effect");
        });
    }
}
