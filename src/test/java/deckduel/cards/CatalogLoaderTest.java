package deckduel.cards;

import static org.junit.jupiter.api.Assertions.*;

import deckduel.core.card.CardDef;
import deckduel.core.card.CardKind;
import deckduel.core.card.Catalog;
import deckduel.core.card.DeckRules;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CatalogLoaderTest {
    private final Catalog catalog = CatalogLoader.loadBundled();

    @Test void theUnitLibraryIsSevenNationsTimesFiveClassesWithFixedPoints() {
        assertEquals("cards-neutral-3", catalog.version());
        assertEquals(List.of("Hungary", "Poland", "Bohemia", "Austria", "Styria", "Croatia", "Transylvania"), catalog.regions());
        assertEquals(35, catalog.units().size());
        Map<String, Integer> points = Map.of("Pikemen", 2, "Bowmen", 3, "Riders", 4, "Knights", 5, "Battering Ram", 6);
        for (CardDef unit : catalog.units()) {
            assertEquals(points.get(unit.unitClass()), unit.points(), unit.id());
            assertEquals(1, unit.regions().size());
            assertEquals(1, unit.classes().size());
        }
        for (String nation : catalog.regions())
            assertEquals(points.keySet(), catalog.units(nation).stream().map(CardDef::unitClass).collect(Collectors.toSet()), nation);
        assertTrue(catalog.cards().stream().noneMatch(card -> card.name().contains("Chaplain") || card.name().contains("Envoy")),
                "Chaplain and Envoy are retired");
        assertTrue(catalog.units().stream().noneMatch(unit -> unit.unitClass().equals("Footmen")), "Battering Ram replaced Footmen");
    }

    @Test void theTenSpellsAreNeutralAndLandmarksAndSabotageAreGone() {
        assertEquals(List.of("disable-tags", "disable-points", "destroy-unit", "random-discard", "random-steal", "draw-two",
                "exchange", "reconnaissance", "spy", "clear-disable"), catalog.spells().stream().map(CardDef::id).toList());
        for (CardDef card : catalog.cards())
            if (card.kind() != CardKind.UNIT) assertTrue(card.regions().isEmpty() && card.classes().isEmpty(), card.id() + " is neutral");
        assertEquals(35 + 10, catalog.cards().size(), "only Units and Spells");
        assertNull(catalog.card("sabotage"));
    }

    @Test void theAutomaticDeckIsEveryUnitPlusOneOfEachSpell() {
        var deck = DeckRules.deck(catalog);
        assertEquals(45, deck.size());
        assertEquals(35, deck.stream().filter(card -> card.kind() == CardKind.UNIT).distinct().count());
        assertEquals(10, deck.stream().filter(card -> card.kind() == CardKind.SPELL).distinct().count());
    }

    @Test void malformedConfigurationNamesTheLine() {
        var error = assertThrows(IllegalArgumentException.class, () -> CatalogLoader.parse(new StringReader(
                "version | v\nregions | North\nunit | a | A | 9 | North | Knights\n")));
        assertTrue(error.getMessage().contains("line 3"), error.getMessage());
        var unknown = assertThrows(IllegalArgumentException.class, () -> CatalogLoader.parse(new StringReader(
                "version | v\nregions | North\nspell | s | S | SABOTAGE\n")));
        assertTrue(unknown.getMessage().contains("line 3"), unknown.getMessage());
    }

    @Test void aLibraryMissingAClassOrWithInconsistentPointsIsRejected() {
        StringBuilder text = new StringBuilder("version | v\nregions | North;South\n");
        String[] classes = {"Footmen", "Pikemen", "Bowmen", "Riders", "Knights"};
        for (String nation : List.of("North", "South"))
            for (int c = 0; c < 5; c++)
                text.append("unit | ").append(nation).append(c).append(" | x | ")
                        .append(nation.equals("South") && c == 0 ? 2 : c + 1).append(" | ").append(nation).append(" | ").append(classes[c]).append('\n');
        var error = assertThrows(IllegalArgumentException.class, () -> CatalogLoader.parse(new StringReader(text.toString())));
        assertTrue(error.getMessage().contains("same points"), error.getMessage());
    }
}
