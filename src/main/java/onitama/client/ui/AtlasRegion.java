package onitama.client.ui;

import java.awt.Color;
import java.awt.Polygon;
import java.util.List;

/** Historical reading entries and approximate hit areas on the supplied reference illustration. */
record AtlasRegion(String name, String status, String seat, String description,
                   Color color, Polygon area) {
    static final List<AtlasRegion> ALL = List.of(
        region("Hungary", "KINGDOM", "Buda · Esztergom · Szeged",
            "The Hungarian crown anchors the middle Danube basin. In 1444 the kingdom faced both a contested succession and the Ottoman frontier. Transylvania belonged to this kingdom; a separate game faction does not imply independence.",
            0xBF6849, 360,300, 460,267, 565,252, 650,285, 745,288, 863,366, 878,435, 827,479, 850,560, 880,635, 810,691, 755,726, 642,680, 559,695, 487,649, 458,581, 415,533, 375,446),
        region("Poland", "KINGDOM", "Kraków · Tarnów · Lviv",
            "The Polish crown lies north of the Carpathians. Władysław III also held the Hungarian crown in 1444 and died at Varna that November. The atlas shows a regional view, not the full extent of Poland.",
            0xA7564A, 641,22, 1173,15, 1225,99, 1200,225, 1144,253, 1061,228, 973,228, 875,221, 801,197, 742,194, 680,145),
        region("Bohemia", "KINGDOM · HOLY ROMAN EMPIRE", "Prague · Brno · Olomouc",
            "The lands of the Bohemian Crown included Bohemia and Moravia. The Hussite wars shaped the region before 1444. This illustration groups the crown lands for browsing; its boundaries are indicative.",
            0xB99541, 193,17, 433,15, 490,66, 601,90, 627,169, 588,229, 518,246, 455,268, 390,290, 320,264, 266,219, 192,207, 166,147),
        region("Austria", "DUCHY · HABSBURG LANDS", "Vienna · Danube valley",
            "The Austrian duchy was part of the Holy Roman Empire and held by the Habsburgs. Their lands were divided among dynastic branches. Austria and Styria are distinct atlas entries, not modern national borders.",
            0xBAB5A1, 20,230, 98,193, 166,205, 249,228, 312,264, 370,298, 374,369, 338,416, 247,425, 176,394, 77,414, 20,377),
        region("Styria", "DUCHY · INNER AUSTRIA", "Graz · Alpine approaches",
            "Styria was a Habsburg duchy of the Holy Roman Empire, associated with the Inner Austrian lands. Graz was its ducal seat. Its distinct game identity should not be read as a separate sovereign nation.",
            0x647A51, 22,409, 81,419, 176,400, 245,429, 337,423, 371,447, 353,507, 301,541, 248,550, 197,568, 144,587, 103,563, 47,539, 22,495),
        region("Croatia", "KINGDOM · LINKED TO THE HUNGARIAN CROWN", "Zagreb · Southern approaches",
            "Croatia shared its monarch with Hungary while retaining its own institutions. The supplied artwork groups Croatia and nearby lands schematically; its shaded area is not a surveyed frontier for 1444.",
            0x957698, 144,601, 206,573, 267,552, 327,545, 361,520, 408,553, 452,594, 489,654, 560,704, 638,730, 631,768, 560,755, 486,713, 415,717, 340,691, 275,705, 222,661, 168,647),
        region("Transylvania", "VOIVODESHIP · KINGDOM OF HUNGARY", "Kolozsvár · Gyulafehérvár",
            "Transylvania was governed by a voivode within the Kingdom of Hungary in 1444. It was not yet the later independent principality. Its separate faction is a game design choice, not a statement of sovereignty.",
            0xC39A3C, 884,402, 961,380, 1040,412, 1112,442, 1151,498, 1190,545, 1181,615, 1203,656, 1157,691, 1082,701, 1015,684, 942,703, 880,674, 859,609, 838,540, 852,475)
    );

    private static AtlasRegion region(String name, String status, String seat, String description,
                                      int color, int... points) {
        Polygon polygon = new Polygon();
        for (int i = 0; i < points.length; i += 2) polygon.addPoint(points[i], points[i + 1]);
        return new AtlasRegion(name, status, seat, description, new Color(color), polygon);
    }

    static AtlasRegion at(double x, double y) {
        return ALL.stream().filter(region -> region.area.contains(x, y)).findFirst().orElse(null);
    }
}
