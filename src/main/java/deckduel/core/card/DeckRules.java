package deckduel.core.card;

import java.util.ArrayList;
import java.util.List;

/**
 * The automatic deck (§5.1): every player receives the same composition, all Units and one copy
 * of each neutral Spell, which the server shuffles independently for each player. Players never
 * assemble it; Landmarks are chosen separately and are not part of it.
 */
public final class DeckRules {
    private DeckRules() { }

    /** Every Unit followed by one copy of every Spell. */
    public static List<CardDef> deck(Catalog catalog) {
        List<CardDef> deck = new ArrayList<>(catalog.units());
        deck.addAll(catalog.spells());
        return List.copyOf(deck);
    }
}
