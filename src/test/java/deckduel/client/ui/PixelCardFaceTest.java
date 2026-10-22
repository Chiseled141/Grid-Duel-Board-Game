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

class PixelCardFaceTest {
    private static final CardDef CARD = CatalogLoader.loadBundled().card("pl-knight");

    @Test void fixedGridHasOnlyFlatPaletteColoursAndTransparentCorners() {
        for (CardDef card : CatalogLoader.loadBundled().cards().stream().filter(PixelCardFace::supports).toList())
            assertPalette(card);
    }

    private static void assertPalette(CardDef card) {
        var nativeFace = PixelCardFace.render(card);
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

    @Test void actualGameSpriteScalesEveryInteriorPixelWithoutBlendingEvenAfterPhotoLoads() throws Exception {
        for (CardDef card : CatalogLoader.loadBundled().cards().stream().filter(PixelCardFace::supports).toList())
            assertScaling(card);
    }

    private static void assertScaling(CardDef card) throws Exception {
        var nativeFace = PixelCardFace.render(card);
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

    @Test void sampleUsesCatalogPointsAndOnlyReplacesTheRequestedCards() {
        CardDef changed = new CardDef(CARD.id(), CARD.name(), CARD.kind(), 6, CARD.regions(), CARD.classes(), null);
        var five = PixelCardFace.render(CARD);
        var six = PixelCardFace.render(changed);
        int differences = 0;
        for (int y = 0; y < 120; y++)
            for (int x = 0; x < 80; x++)
                if (five.getRGB(x, y) != six.getRGB(x, y)) {
                    assertTrue(x >= 7 && x < 17 && y >= 8 && y < 22, "only the printed value changes");
                    differences++;
                }
        assertTrue(differences > 0);
        var catalog = CatalogLoader.loadBundled();
        var expected = new HashSet<>(catalog.units().stream().map(CardDef::id).toList());
        expected.addAll(catalog.cards().stream().map(CardDef::id).toList());
        assertEquals(45, expected.size());
        assertEquals(expected, catalog.cards().stream().filter(PixelCardFace::supports)
                .map(CardDef::id).collect(java.util.stream.Collectors.toSet()));
        assertFalse(PixelCardFace.supports(null), "face-down cards never expose a design");
        assertFalse(PixelCardFace.supports(new CardDef("bo-knight", CARD.name(), CARD.kind(), 5,
                CARD.regions(), CARD.classes(), null)), "identity must agree with nation");
        assertFalse(PixelCardFace.supports(new CardDef("pl-axemen", CARD.name(), CARD.kind(), 5,
                CARD.regions(), CARD.classes(), null)), "identity must agree with class");
    }

    @Test void shieldsMatchTheirNationClothAndAllSevenNationsAreDistinct() {
        var catalog = CatalogLoader.loadBundled();
        var shieldColours = new HashSet<Integer>();
        for (String prefix : List.of("pl", "hu", "au", "bo", "st", "cr", "tr")) {
            var image = PixelCardFace.render(catalog.card(prefix + "-knight"));
            int shield = image.getRGB(64, 66);
            assertEquals(image.getRGB(40, 62), shield, prefix + " shield follows its tabard cloth");
            shieldColours.add(shield);
        }
        assertEquals(7, shieldColours.size(), "each nation keeps a distinct shield colour");
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
            assertTrue(PixelCardFace.supports(original), id);
            assertFalse(PixelCardFace.supports(changed), id + " must not illustrate a different effect");
        });
    }

    @Test void sharedPxlBackHasFlatPixelsAndScalesWithoutBlending() {
        var nativeBack = new java.awt.image.BufferedImage(80, 120, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var g = nativeBack.createGraphics();
        try { PxlCardArt.paint(g, "card-back"); }
        finally { g.dispose(); }
        assertEquals(0, nativeBack.getRGB(0, 0) >>> 24);
        var colours = new HashSet<Integer>();
        var scaled = CardFace.backSprite(240, 360, 1);
        assertSame(scaled, CardFace.backSprite(240, 360, 1), "backs use the shared sprite cache");
        for (int y = 4; y < 115; y++) for (int x = 4; x < 75; x++) {
            int pixel = nativeBack.getRGB(x, y);
            assertEquals(255, pixel >>> 24, "no holes in the card body");
            colours.add(pixel);
            for (int dy = 0; dy < 3; dy++) for (int dx = 0; dx < 3; dx++)
                assertEquals(pixel, scaled.getRGB(x * 3 + dx, y * 3 + dy));
        }
        assertTrue(colours.size() <= 15, "limited opaque back palette");
    }

    @Test void axemenPrintFourPointsAndTheirOwnClassLabelUnlikeRidersOfTheSameValue() {
        var catalog = CatalogLoader.loadBundled();
        CardDef axemen = catalog.card("pl-axemen"), riders = catalog.card("pl-riders");
        assertEquals(4, axemen.points());
        assertEquals(axemen.points(), riders.points());
        var a = PixelCardFace.render(axemen);
        var r = PixelCardFace.render(riders);
        boolean sameNumeral = true, differentLabel = false, differentFigure = false;
        for (int y = 0; y < 120; y++)
            for (int x = 0; x < 80; x++) {
                boolean differs = a.getRGB(x, y) != r.getRGB(x, y);
                if (x >= 7 && x < 17 && y >= 8 && y < 22 && differs) sameNumeral = false;
                if (y >= 106 && y < 113 && differs) differentLabel = true;
                if (y >= 30 && y < 95 && differs) differentFigure = true;
            }
        assertTrue(sameNumeral, "both print the catalog value 4");
        assertTrue(differentLabel, "AXEMEN and RIDERS labels differ");
        assertTrue(differentFigure, "the axeman is a different figure");
    }
}
