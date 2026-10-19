package deckduel.core.card;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An immutable, validated card library injected into every match: every nation has one Unit of
 * each shared class, a class prints the same points in every nation, and Landmarks and Spells
 * are neutral. The content itself is configuration supplied from outside core.
 */
public final class Catalog {
    /** Shared Unit classes; each nation has exactly one Unit of each. */
    public static final int CLASSES = 5;

    private final String version;
    private final List<String> regions;
    private final Map<String, CardDef> cards;

    /** Validates unique ids, the nation × class Unit grid, consistent class points and enough Landmark offers. */
    public Catalog(String version, List<String> regions, List<CardDef> cards) {
        if (version == null || version.isBlank()) throw new IllegalArgumentException("Catalog needs a version");
        this.version = version;
        this.regions = List.copyOf(regions);
        Map<String, CardDef> byId = new LinkedHashMap<>();
        for (CardDef card : cards) {
            if (byId.put(card.id(), card) != null) throw new IllegalArgumentException("Duplicate card id " + card.id());
            if (card.kind() == CardKind.UNIT && !this.regions.contains(card.nation()))
                throw new IllegalArgumentException(card.id() + " names an unknown nation");
        }
        this.cards = java.util.Collections.unmodifiableMap(byId);
        validateUnits();
        if (landmarks().size() < Rules.LANDMARK_OFFERS)
            throw new IllegalArgumentException("At least " + Rules.LANDMARK_OFFERS + " Landmarks are needed for the offers");
    }

    private void validateUnits() {
        Map<String, Integer> classPoints = new HashMap<>();
        for (CardDef unit : units())
            if (classPoints.merge(unit.unitClass(), unit.points(), (a, b) -> a.equals(b) ? a : -1) == -1)
                throw new IllegalArgumentException(unit.unitClass() + " must print the same points in every nation");
        if (classPoints.size() != CLASSES) throw new IllegalArgumentException("The Unit library needs exactly " + CLASSES + " classes");
        for (String region : regions) {
            List<CardDef> units = units(region);
            if (units.size() != CLASSES || units.stream().map(CardDef::unitClass).distinct().count() != CLASSES)
                throw new IllegalArgumentException(region + " needs exactly one Unit of each class");
        }
    }

    /** Rules/content version recorded with every match result. */
    public String version() { return version; }
    /** Nations, in display order. */
    public List<String> regions() { return regions; }
    /** Every card, in catalog order. */
    public List<CardDef> cards() { return List.copyOf(cards.values()); }
    /** A card by id, or null. */
    public CardDef card(String id) { return cards.get(id); }

    /** Every Unit, in catalog order. */
    public List<CardDef> units() { return of(CardKind.UNIT); }
    /** One nation's Units. */
    public List<CardDef> units(String region) { return units().stream().filter(card -> card.nation().equals(region)).toList(); }
    /** Every neutral Spell. */
    public List<CardDef> spells() { return of(CardKind.SPELL); }
    /** Every neutral Landmark; offers at match start come from these. */
    public List<CardDef> landmarks() { return of(CardKind.LANDMARK); }

    private List<CardDef> of(CardKind kind) { return cards.values().stream().filter(card -> card.kind() == kind).toList(); }
}
