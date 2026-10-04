package onitama.client.ui;

import java.awt.Color;
import java.util.Map;

/**
 * Per-card visual identity: a fixed accent color and an original flavor
 * line for each of the 16 movement cards (§10: cards feel
 * collectible; §21: colored accent borders). Documented in
 * docs/DESIGN_DECISIONS.md.
 */
public final class CardArt {

    private static final Map<String, Color> ACCENTS = Map.ofEntries(
            Map.entry("tiger", new Color(0xED203D)),
            Map.entry("dragon", new Color(0x7B5EA7)),
            Map.entry("frog", new Color(0x0BA95B)),
            Map.entry("rabbit", new Color(0xF38BA3)),
            Map.entry("crab", new Color(0xFC7428)),
            Map.entry("elephant", new Color(0x12B5E5)),
            Map.entry("goose", new Color(0xFCBA28)),
            Map.entry("rooster", new Color(0x8D2F23)),
            Map.entry("monkey", new Color(0x9CCC65)),
            Map.entry("mantis", new Color(0x00838F)),
            Map.entry("horse", new Color(0x3949AB)),
            Map.entry("ox", new Color(0x6D4C41)),
            Map.entry("crane", new Color(0x26C6DA)),
            Map.entry("boar", new Color(0xC2185B)),
            Map.entry("eel", new Color(0x2E7D32)),
            Map.entry("cobra", new Color(0x546E7A)));

    private static final Map<String, String> FLAVOR = Map.ofEntries(
            Map.entry("tiger", "Strike far, or step back and pounce."),
            Map.entry("dragon", "Leap wide from the hills, and never the same way twice."),
            Map.entry("frog", "Hop to the side, then spring ahead."),
            Map.entry("rabbit", "Sprint forward, then dart back out of reach."),
            Map.entry("crab", "Sideways never loses its way."),
            Map.entry("elephant", "Steady steps carry the greatest weight."),
            Map.entry("goose", "Fly in lines, land in curves."),
            Map.entry("rooster", "Circle the coop with care."),
            Map.entry("monkey", "Swing around whatever stands in your path."),
            Map.entry("mantis", "Bow first, then strike deep."),
            Map.entry("horse", "Gallop ahead, shy aside."),
            Map.entry("ox", "Push forward and hold the line."),
            Map.entry("crane", "Rise above the pond, then descend."),
            Map.entry("boar", "Charge, then keep the ground you take."),
            Map.entry("eel", "Slip aside, then slide out of reach."),
            Map.entry("cobra", "Wait patiently, then strike from afar."));

    private CardArt() {
    }

    /** The card's own accent color (defaults to gold). */
    public static Color of(String cardId) {
        return ACCENTS.getOrDefault(cardId, Theme.AMBER);
    }

    /** The original flavor line for the quote band. */
    public static String flavorFor(String cardId) {
        return FLAVOR.getOrDefault(cardId, "Move with intention.");
    }
}
