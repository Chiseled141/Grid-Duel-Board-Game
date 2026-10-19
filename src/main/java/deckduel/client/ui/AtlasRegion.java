package deckduel.client.ui;

import java.awt.Color;
import java.util.List;

/**
 * The seven playable factions: historical reading text and a map tint chosen to echo each
 * faction's card frame while keeping neighbours distinct. Shapes are loaded by {@link AtlasMapData}.
 */
record AtlasRegion(String name, String status, String seat, String description, Color color) {
    static final List<AtlasRegion> ALL = List.of(
        region("Hungary", "KINGDOM", "Buda · Esztergom · Szeged",
            "The Hungarian crown anchors the middle Danube basin. In 1444 the kingdom faced both a contested succession and the Ottoman frontier. Transylvania belonged to this kingdom; a separate game faction does not imply independence.",
            0xB8693E),
        region("Poland", "KINGDOM", "Kraków · Tarnów · Lviv",
            "The Polish crown lies north of the Carpathians. Władysław III also held the Hungarian crown in 1444 and died at Varna that November. The map uses a generalized reconstruction of the crown’s lands.",
            0xC98F8C),
        region("Bohemia", "KINGDOM · HOLY ROMAN EMPIRE", "Prague · Brno · Olomouc",
            "The Bohemian Crown included Bohemia, Moravia, Silesian principalities and Lusatia. The Hussite wars shaped the region before 1444. This illustration groups the crown lands for browsing; its boundaries are indicative.",
            0x8A5A86),
        region("Austria", "DUCHY · HABSBURG LANDS", "Vienna · Danube valley",
            "The Austrian duchy was part of the Holy Roman Empire and held by the Habsburgs. Their lands were divided among dynastic branches. Austria and Styria are distinct atlas entries, not modern national borders.",
            0xB23E3A),
        region("Styria", "DUCHY · INNER AUSTRIA", "Graz · Alpine approaches",
            "Styria was a Habsburg duchy of the Holy Roman Empire, associated with the Inner Austrian lands. Graz was its ducal seat. Its distinct game identity should not be read as a separate sovereign nation.",
            0x5B8A4C),
        region("Croatia", "KINGDOM · LINKED TO THE HUNGARIAN CROWN", "Zagreb · Southern approaches",
            "Croatia shared its monarch with Hungary while retaining its own institutions. This game region groups Croatia and Slavonia; its frontier boundaries are reconstructed.",
            0x5F6B78),
        region("Transylvania", "VOIVODESHIP · KINGDOM OF HUNGARY", "Kolozsvár · Gyulafehérvár",
            "Transylvania was governed by a voivode within the Kingdom of Hungary in 1444. It was not yet the later independent principality. Its separate faction is a game design choice, not a statement of sovereignty.",
            0x3F6EA8)
    );

    private static AtlasRegion region(String name, String status, String seat, String description, int color) {
        return new AtlasRegion(name, status, seat, description, new Color(color));
    }

    /** The faction with this name, or null. */
    static AtlasRegion named(String name) {
        return ALL.stream().filter(region -> region.name().equals(name)).findFirst().orElse(null);
    }
}
