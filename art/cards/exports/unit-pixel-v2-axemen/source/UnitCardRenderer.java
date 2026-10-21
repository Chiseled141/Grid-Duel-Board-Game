import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Map;

/** Standalone original pixel Unit artwork for later game integration. No runtime dependencies. */
public final class UnitCardRenderer {
    static final int WIDTH = 80, HEIGHT = 120;
    private static final Color INK = new Color(0x232B3C), PAPER = new Color(0xF4E9CE);
    private static final Color EDGE = new Color(0xCBBE9E), WHITE = new Color(0xFFF6DF);
    private static final Color RED = new Color(0xC54846), RED_LIGHT = new Color(0xEC7460);
    private static final Color RED_DARK = new Color(0x793949), GOLD = new Color(0xD7A95B);
    private static final Color STEEL = new Color(0x8DABAE), LIGHT = new Color(0xC8D8CC);
    private static final Color WOOD = new Color(0x916246), WOOD_LIGHT = new Color(0xBD8E5B);
    private static final Color WOOD_DARK = new Color(0x5B4341);
    private static final Color SHADE = new Color(0x506777), FIELD = new Color(0xDFD7B9);

    // A tiny bitmap alphabet keeps lettering on the same grid as the illustration.
    private static final Map<Character, String> GLYPHS = Map.ofEntries(
            Map.entry('A', "01110/10001/10001/11111/10001/10001/10001"),
            Map.entry('B', "11110/10001/10001/11110/10001/10001/11110"),
            Map.entry('C', "01111/10000/10000/10000/10000/10000/01111"),
            Map.entry('E', "11111/10000/10000/11110/10000/10000/11111"),
            Map.entry('M', "10001/11011/10101/10101/10001/10001/10001"),
            Map.entry('R', "11110/10001/10001/11110/10100/10010/10001"),
            Map.entry('U', "10001/10001/10001/10001/10001/10001/01110"),
            Map.entry('V', "10001/10001/10001/10001/10001/01010/00100"),
            Map.entry('W', "10001/10001/10001/10101/10101/11011/10001"),
            Map.entry('X', "10001/10001/01010/00100/01010/10001/10001"),
            Map.entry('Y', "10001/10001/01010/00100/00100/00100/00100"),
            Map.entry(' ', "00000/00000/00000/00000/00000/00000/00000"),
            Map.entry('D', "11110/10001/10001/10001/10001/10001/11110"),
            Map.entry('G', "01110/10001/10000/10111/10001/10001/01110"),
            Map.entry('H', "10001/10001/10001/11111/10001/10001/10001"),
            Map.entry('I', "11111/00100/00100/00100/00100/00100/11111"),
            Map.entry('K', "10001/10010/10100/11000/10100/10010/10001"),
            Map.entry('L', "10000/10000/10000/10000/10000/10000/11111"),
            Map.entry('N', "10001/11001/11001/10101/10011/10011/10001"),
            Map.entry('O', "01110/10001/10001/10001/10001/10001/01110"),
            Map.entry('P', "11110/10001/10001/11110/10000/10000/10000"),
            Map.entry('S', "01111/10000/10000/01110/00001/00001/11110"),
            Map.entry('T', "11111/00100/00100/00100/00100/00100/00100"),
            Map.entry('1', "00100/01100/00100/00100/00100/00100/01110"),
            Map.entry('2', "01110/10001/00001/00110/01000/10000/11111"),
            Map.entry('3', "11110/00001/00001/01110/00001/00001/11110"),
            Map.entry('4', "00010/00110/01010/10010/11111/00010/00010"),
            Map.entry('5', "11111/10000/10000/11110/00001/00001/11110"),
            Map.entry('6', "01110/10000/10000/11110/10001/10001/01110"));

    private UnitCardRenderer() { }

    private record Cloth(Color main, Color light, Color dark) { }

    private static Cloth cloth(String nation) {
        return switch (nation) {
            case "Hungary" -> new Cloth(new Color(0x397E69), new Color(0x75B787), new Color(0x28514C));
            case "Austria" -> new Cloth(new Color(0x4D5065), new Color(0x87859A), new Color(0x343345));
            default -> new Cloth(RED, RED_LIGHT, RED_DARK);
        };
    }

    /** Catalog metadata supplied by the exporter; no new gameplay definitions. */
    public record Unit(String id, String name, int points, String nation, String unitClass) { }

    /** The exact seven designs included in this export pack. */
    public static boolean supports(Unit card) {
        if (card == null) return false;
        return switch (card.id()) {
            case "pl-pikemen", "pl-bowmen", "pl-riders", "pl-knight", "pl-axemen" -> card.nation().equals("Poland");
            case "hu-knight" -> card.nation().equals("Hungary") && card.unitClass().equals("Knights");
            case "au-knight" -> card.nation().equals("Austria") && card.unitClass().equals("Knights");
            default -> false;
        };
    }

    /** Composes original frame, heraldry, armour, and bitmap type at native resolution. */
    public static BufferedImage render(Unit card) {
        if (!supports(card)) throw new IllegalArgumentException("No pixel design for " + card.id());
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            unit(g, card);
        } finally { g.dispose(); }
        return image;
    }

    private static void unit(Graphics2D g, Unit card) {
        Cloth cloth = cloth(card.nation());
        frame(g);
        backdrop(g);
        switch (card.unitClass()) {
            case "Knights" -> {
                cape(g, cloth); armour(g, cloth); helmet(g, cloth); sword(g); shield(g, card.nation());
            }
            case "Pikemen" -> pikeman(g, cloth);
            case "Bowmen" -> bowman(g, cloth);
            case "Riders" -> rider(g, cloth);
            case "Axemen" -> axeman(g, cloth);
            default -> throw new IllegalArgumentException("No design for class " + card.unitClass());
        }
        text(g, Integer.toString(card.points()), 7, 8, 2, cloth.dark());
        text(g, card.nation().toUpperCase(java.util.Locale.ROOT), 25 - (card.nation().length() - 6) * 3, 11, 1, INK);
        classIcon(g, card.unitClass());
        crest(g, 66, 9, card.nation(), cloth.dark());
        String label = card.unitClass().toUpperCase(java.util.Locale.ROOT);
        text(g, label, (WIDTH - (label.length() * 6 - 1)) / 2, 106, 1, INK);
    }

    private static void classIcon(Graphics2D g, String unitClass) {
        switch (unitClass) {
            case "Knights" -> { rect(g, INK, 10, 26, 2, 10); rect(g, INK, 7, 32, 8, 2); }
            case "Pikemen" -> {
                rect(g, INK, 10, 28, 1, 10);
                polygon(g, INK, 11,24, 14,30, 7,30);
            }
            case "Bowmen" -> {
                polygon(g, INK, 8,25, 12,27, 14,31, 12,35, 8,37, 11,33, 12,31, 11,29);
                rect(g, INK, 8, 26, 1, 11); rect(g, INK, 6, 31, 9, 1);
            }
            case "Riders" -> {
                polygon(g, INK, 7,25, 10,25, 10,33, 12,35, 14,33, 14,25, 16,25,
                        16,34, 13,38, 10,38, 7,34);
            }
            case "Axemen" -> {
                rect(g, INK, 10, 25, 2, 13);
                polygon(g, INK, 12,25, 17,24, 17,31, 12,30);
            }
            default -> throw new IllegalArgumentException("No class marker for " + unitClass);
        }
    }

    private static void pikeman(Graphics2D g, Cloth cloth) {
        cape(g, cloth);
        // Long pike, simple kettle helmet and tall shield distinguish this from the knight.
        polygon(g, INK, 34,54, 48,54, 56,65, 52,81, 55,96, 25,96, 28,80, 25,64);
        polygon(g, cloth.dark(), 34,57, 46,57, 52,65, 47,80, 51,94, 28,94, 32,78, 28,64);
        polygon(g, cloth.main(), 35,58, 40,58, 39,91, 31,93, 34,79, 30,65);
        rect(g, LIGHT, 31, 58, 8, 5); rect(g, SHADE, 40, 58, 7, 7);
        rect(g, GOLD, 31, 76, 17, 2);
        polygon(g, INK, 32,36, 43,32, 51,38, 52,47, 47,54, 35,54, 30,47);
        polygon(g, LIGHT, 33,38, 42,35, 48,39, 49,46, 45,50, 35,50, 32,46);
        rect(g, SHADE, 42, 37, 5, 11);
        rect(g, INK, 27, 42, 28, 3); rect(g, STEEL, 28, 41, 25, 2);
        rect(g, INK, 34, 47, 13, 3); rect(g, WHITE, 34, 38, 7, 2);
        rect(g, INK, 23, 96, 15, 2); rect(g, INK, 44, 96, 13, 2);
        // Pike behind the gripping hand, with a forked steel head near the top margin.
        rect(g, INK, 17, 40, 4, 57); rect(g, WOOD, 18, 41, 2, 55);
        polygon(g, INK, 19,25, 23,37, 20,43, 16,40, 15,36);
        polygon(g, LIGHT, 19,29, 21,37, 19,40, 17,37);
        rect(g, WHITE, 18, 33, 1, 5);
        rect(g, INK, 20, 64, 12, 7); rect(g, STEEL, 20, 65, 10, 4);
        rect(g, LIGHT, 21, 65, 7, 1);
        polygon(g, INK, 46,64, 55,61, 66,65, 65,85, 56,96, 46,88);
        polygon(g, GOLD, 48,66, 55,63, 64,67, 63,84, 56,93, 48,87);
        polygon(g, cloth.main(), 50,68, 55,66, 62,68, 61,83, 56,90, 50,85);
        crest(g, 51, 74, "Poland", WHITE);
    }

    private static void bowman(Graphics2D g, Cloth cloth) {
        // A cloth hood, visible quiver and bent bow give the ranged silhouette.
        polygon(g, INK, 25,53, 40,49, 50,60, 47,79, 53,96, 20,96, 24,76, 20,62);
        polygon(g, cloth.dark(), 27,55, 39,52, 47,61, 43,78, 49,94, 23,94, 28,75, 23,63);
        polygon(g, cloth.main(), 28,56, 34,54, 35,76, 31,93, 25,93, 30,74, 26,63);
        polygon(g, cloth.light(), 29,57, 31,56, 31,71, 28,69);
        polygon(g, INK, 24,34, 35,28, 44,32, 48,43, 45,54, 35,58, 25,52, 21,42);
        polygon(g, cloth.main(), 26,36, 35,31, 41,34, 45,42, 42,51, 34,54, 26,49, 24,42);
        polygon(g, cloth.light(), 26,37, 35,32, 38,34, 31,39, 26,46);
        polygon(g, INK, 30,38, 39,36, 43,42, 41,49, 34,52, 28,47);
        polygon(g, WOOD_LIGHT, 32,39, 38,38, 40,42, 38,49, 33,49, 30,45);
        rect(g, INK, 34, 42, 5, 1); rect(g, WOOD_DARK, 33, 47, 5, 2);
        polygon(g, INK, 16,57, 21,54, 26,57, 25,80, 19,83, 16,78);
        rect(g, WOOD, 18, 59, 5, 20); rect(g, WOOD_LIGHT, 18, 60, 1, 17);
        for (int x = 16; x <= 22; x += 3) {
            rect(g, INK, x, 42, 1, 19); rect(g, LIGHT, x - 1, 43, 3, 4);
        }
        // String remains a one-pixel line; the bow itself is a deliberate stepped curve.
        polygon(g, INK, 58,34, 63,38, 68,48, 70,61, 68,74, 63,84, 58,89,
                59,83, 64,73, 66,61, 64,49, 60,39);
        polygon(g, WOOD_LIGHT, 59,36, 62,39, 66,49, 68,61, 66,74, 62,82, 60,85,
                64,73, 66,61, 64,49, 60,40);
        rect(g, EDGE, 58, 37, 1, 50);
        polygon(g, INK, 36,59, 39,55, 57,59, 60,64, 56,67, 40,63);
        polygon(g, WOOD_LIGHT, 38,59, 40,57, 56,61, 57,64, 41,61);
        rect(g, INK, 35, 75, 11, 3); rect(g, GOLD, 37, 75, 4, 3);
        rect(g, WOOD, 42, 61, 29, 1);
        polygon(g, LIGHT, 72,61, 67,58, 67,64);
        rect(g, WHITE, 43, 61, 14, 1);
        rect(g, INK, 19, 96, 14, 2); rect(g, INK, 40, 96, 15, 2);
        crest(g, 33, 64, "Poland", WHITE);
    }

    private static void rider(Graphics2D g, Cloth cloth) {
        // Side-on horse with clear head, four separated legs, saddlecloth and mounted lancer.
        polygon(g, INK, 18,66, 43,64, 53,53, 57,39, 61,37, 65,42, 69,43, 72,52,
                68,57, 62,56, 60,73, 55,81, 54,97, 49,97, 48,82, 36,83,
                31,97, 26,97, 29,79, 21,79, 18,94, 13,94, 16,76, 12,73, 10,85, 7,85, 10,70);
        polygon(g, WOOD_DARK, 18,68, 44,67, 54,55, 59,42, 62,40, 64,45, 68,46, 70,51,
                66,54, 59,52, 58,70, 52,79, 50,93, 49,80, 34,80, 29,94, 28,94, 31,78, 20,76, 16,90, 18,74);
        polygon(g, WOOD, 20,69, 43,69, 53,58, 58,47, 61,43, 61,54, 55,71, 48,77, 30,77, 20,74);
        polygon(g, WOOD_LIGHT, 55,57, 58,48, 60,46, 59,56, 54,66, 46,73, 33,73, 33,71, 46,70);
        rect(g, WHITE, 64, 47, 2, 1); rect(g, INK, 67, 50, 2, 1);
        polygon(g, INK, 55,43, 58,38, 60,38, 58,49, 55,57, 51,62, 51,58);
        polygon(g, cloth.dark(), 24,67, 43,67, 46,78, 36,87, 22,81);
        polygon(g, cloth.main(), 26,68, 41,68, 42,77, 35,83, 25,79);
        rect(g, GOLD, 26, 79, 11, 2); crest(g, 29, 70, "Poland", WHITE);
        polygon(g, INK, 29,44, 39,43, 45,51, 42,64, 44,72, 39,76, 34,72, 36,62, 28,59, 25,51);
        polygon(g, cloth.main(), 30,46, 38,45, 42,51, 39,61, 30,58, 28,51);
        polygon(g, LIGHT, 30,46, 35,45, 37,51, 34,57, 29,54);
        polygon(g, STEEL, 36,61, 40,62, 39,70, 42,72, 40,74, 36,71);
        polygon(g, INK, 29,31, 35,28, 41,32, 42,41, 36,46, 29,42, 27,37);
        polygon(g, LIGHT, 30,33, 35,30, 39,33, 40,39, 35,43, 30,40);
        rect(g, INK, 29, 36, 12, 2); rect(g, SHADE, 36, 33, 3, 8);
        polygon(g, cloth.light(), 32,29, 33,25, 38,25, 43,30, 38,28);
        rect(g, INK, 48, 26, 2, 40); rect(g, WOOD_LIGHT, 48, 27, 1, 39);
        polygon(g, LIGHT, 49,24, 52,31, 49,34, 47,31);
        polygon(g, cloth.main(), 50,33, 66,36, 60,40, 50,39);
        rect(g, WHITE, 52, 35, 7, 1);
        polygon(g, STEEL, 39,49, 45,52, 49,49, 50,52, 45,56, 39,53);
        rect(g, INK, 47, 95, 8, 3); rect(g, INK, 24, 96, 8, 2);
    }

    private static void axeman(Graphics2D g, Cloth cloth) {
        // Grounded infantry silhouette, open-faced helmet, mail and a large two-handed axe.
        polygon(g, INK, 27,56, 39,52, 53,57, 60,67, 55,79, 56,96, 22,96, 25,77, 20,66);
        polygon(g, cloth.dark(), 28,58, 39,55, 51,59, 56,67, 51,78, 53,93, 25,93, 29,77, 23,65);
        polygon(g, cloth.main(), 29,59, 37,56, 40,75, 36,92, 27,92, 31,76, 25,66);
        polygon(g, cloth.light(), 29,60, 32,58, 33,70, 30,72, 27,65);
        // Broad chainmail shoulders use a repeating cluster, not soft stippling.
        polygon(g, INK, 25,56, 34,54, 39,58, 38,65, 30,68, 23,64, 21,60);
        polygon(g, STEEL, 25,58, 33,56, 36,59, 35,64, 29,65, 24,62);
        polygon(g, INK, 44,54, 51,57, 56,63, 52,68, 44,65, 40,59);
        polygon(g, SHADE, 44,56, 49,58, 53,62, 51,65, 45,63, 42,59);
        for (int y = 58; y < 64; y += 3) {
            rect(g, LIGHT, 25, y, 2, 1); rect(g, LIGHT, 30, y + 1, 2, 1);
            rect(g, LIGHT, 45, y, 2, 1); rect(g, STEEL, 49, y + 2, 2, 1);
        }
        // Open steel helmet and beard distinguish the head from the closed Knights visor.
        polygon(g, INK, 31,34, 39,30, 48,34, 51,43, 48,54, 39,59, 31,54, 28,43);
        polygon(g, WOOD_LIGHT, 33,39, 45,37, 48,43, 45,51, 39,55, 33,51, 31,44);
        polygon(g, WOOD_DARK, 33,47, 37,49, 43,48, 47,46, 45,53, 39,57, 34,53);
        rect(g, INK, 34, 43, 3, 1); rect(g, INK, 42, 42, 3, 1);
        rect(g, WHITE, 38, 47, 3, 1);
        polygon(g, STEEL, 31,36, 39,32, 46,35, 49,41, 31,43, 29,41);
        polygon(g, LIGHT, 32,36, 39,33, 40,39, 31,41);
        rect(g, INK, 29, 41, 21, 2); rect(g, GOLD, 39, 33, 2, 8);
        rect(g, STEEL, 30, 44, 2, 8); rect(g, SHADE, 46, 43, 2, 7);
        // Long shaft stands clear of the body; hands overlap it at two heights.
        rect(g, INK, 16, 32, 4, 65); rect(g, WOOD, 17, 33, 2, 63);
        rect(g, WOOD_LIGHT, 17, 40, 1, 49); rect(g, GOLD, 16, 93, 4, 3);
        // Single heavy crescent blade, with separate outline, steel and cutting edge.
        polygon(g, INK, 18,32, 24,31, 30,26, 35,25, 34,30, 35,36, 34,43,
                30,48, 25,45, 23,41, 18,40, 14,39, 14,34);
        polygon(g, STEEL, 20,34, 25,33, 31,28, 32,28, 31,32, 33,36, 32,42,
                29,45, 26,43, 25,39, 20,38);
        polygon(g, LIGHT, 30,29, 32,28, 31,32, 33,36, 32,42, 29,45, 28,43, 30,40, 31,36, 29,33);
        rect(g, WHITE, 32, 35, 1, 5); rect(g, GOLD, 17, 34, 3, 4);
        polygon(g, INK, 25,63, 29,67, 25,73, 17,72, 15,68, 18,65);
        polygon(g, WOOD_LIGHT, 24,65, 26,67, 23,70, 18,70, 17,68, 20,66);
        polygon(g, INK, 44,65, 48,70, 41,81, 29,83, 17,81, 15,77, 18,74, 29,77, 37,75);
        polygon(g, WOOD_LIGHT, 44,68, 45,70, 39,78, 29,80, 18,79, 17,77, 20,76, 29,78, 38,76);
        rect(g, SHADE, 39, 72, 5, 4); rect(g, LIGHT, 40, 72, 3, 1);
        rect(g, INK, 27, 83, 24, 3); rect(g, GOLD, 38, 83, 5, 3);
        crest(g, 35, 64, "Poland", WHITE);
        polygon(g, INK, 27,94, 36,94, 35,98, 23,98);
        polygon(g, INK, 44,94, 53,94, 57,98, 44,98);
        rect(g, STEEL, 28, 94, 6, 2); rect(g, SHADE, 46, 94, 6, 2);
    }

    private static void frame(Graphics2D g) {
        polygon(g, INK, 3,0, 76,0, 79,3, 79,116, 76,119, 3,119, 0,116, 0,3);
        polygon(g, PAPER, 4,1, 75,1, 78,4, 78,115, 75,118, 4,118, 1,115, 1,4);
        rect(g, WHITE, 4, 2, 71, 1); rect(g, WHITE, 2, 4, 1, 111);
        rect(g, EDGE, 4, 116, 71, 2); rect(g, EDGE, 76, 4, 2, 111);
        rect(g, EDGE, 20, 23, 53, 1); rect(g, EDGE, 7, 101, 66, 1);
        rect(g, RED_DARK, 7, 108, 5, 1); rect(g, RED_DARK, 68, 108, 5, 1);
    }

    private static void backdrop(Graphics2D g) {
        polygon(g, FIELD, 27,28, 59,28, 70,39, 70,94, 11,94, 11,43);
        // Sparse cross stitches give the art a printed playing-card texture.
        for (int y = 34; y < 94; y += 8)
            for (int x = 15; x < 70; x += 8) {
                rect(g, PAPER, x, y, 1, 3); rect(g, PAPER, x - 1, y + 1, 3, 1);
            }
        rect(g, EDGE, 13, 96, 55, 2);
    }

    private static void cape(Graphics2D g, Cloth cloth) {
        polygon(g, INK, 31,52, 51,53, 61,62, 66,95, 20,95, 23,65);
        polygon(g, cloth.dark(), 29,56, 50,55, 58,63, 63,93, 22,93, 25,66);
        polygon(g, cloth.main(), 29,58, 36,56, 32,90, 24,93);
        polygon(g, cloth.light(), 28,63, 30,61, 28,83, 26,87);
    }

    private static void armour(Graphics2D g, Cloth cloth) {
        // Broad pauldrons and asymmetrical arms keep the small silhouette readable.
        polygon(g, INK, 30,56, 47,55, 56,60, 60,73, 54,81, 51,95, 28,95, 26,79, 18,75, 20,62);
        polygon(g, STEEL, 30,59, 45,58, 52,63, 51,77, 45,85, 30,81, 27,66);
        polygon(g, LIGHT, 30,59, 38,58, 38,74, 33,77, 29,69);
        polygon(g, SHADE, 39,60, 46,59, 51,64, 48,77, 40,81);
        polygon(g, INK, 25,59, 30,60, 32,66, 29,71, 20,70, 18,66, 20,62);
        polygon(g, LIGHT, 22,61, 27,61, 29,65, 28,67, 21,67, 20,65);
        rect(g, STEEL, 21, 68, 8, 2);
        polygon(g, INK, 48,57, 54,59, 59,65, 56,70, 49,69, 46,64);
        polygon(g, LIGHT, 49,59, 53,61, 56,64, 54,66, 49,65, 48,62);
        polygon(g, STEEL, 21,72, 27,72, 27,78, 23,81, 18,78, 16,72, 19,70);
        rect(g, LIGHT, 19, 72, 4, 3);
        polygon(g, cloth.dark(), 33,75, 45,75, 49,94, 28,94);
        polygon(g, cloth.main(), 34,76, 39,76, 39,92, 30,92);
        polygon(g, cloth.light(), 34,79, 36,79, 34,90, 32,90);
        rect(g, INK, 30, 80, 19, 3); rect(g, GOLD, 37, 80, 5, 3);
        rect(g, INK, 39, 81, 1, 1);
        polygon(g, INK, 29,94, 38,94, 38,98, 26,98);
        polygon(g, INK, 43,94, 52,94, 55,98, 43,98);
        rect(g, LIGHT, 29, 95, 6, 1); rect(g, STEEL, 45, 95, 6, 1);
    }

    private static void helmet(Graphics2D g, Cloth cloth) {
        polygon(g, INK, 36,28, 42,25, 51,27, 57,32, 59,42, 54,40, 53,35, 45,33, 40,37);
        polygon(g, cloth.dark(), 37,29, 43,27, 50,28, 55,33, 57,39, 53,33, 45,31, 39,35);
        polygon(g, cloth.light(), 38,29, 44,27, 49,28, 45,29, 40,33);
        polygon(g, INK, 32,36, 40,32, 47,35, 51,41, 50,52, 43,59, 34,56, 30,50, 30,41);
        polygon(g, STEEL, 33,38, 40,34, 46,37, 49,42, 48,51, 42,56, 35,54, 32,49, 32,41);
        polygon(g, LIGHT, 33,39, 40,35, 40,42, 33,45);
        polygon(g, WHITE, 33,39, 38,36, 38,38, 33,42);
        polygon(g, SHADE, 42,36, 47,40, 49,44, 47,51, 42,55);
        polygon(g, INK, 31,44, 40,41, 49,43, 50,46, 40,45, 32,48);
        rect(g, cloth.light(), 34, 45, 4, 1);
        polygon(g, LIGHT, 33,49, 40,46, 41,55, 36,53);
        rect(g, INK, 35, 49, 1, 2); rect(g, INK, 38, 48, 1, 3);
        rect(g, INK, 44, 47, 1, 3); rect(g, INK, 46, 47, 1, 2);
        rect(g, GOLD, 40, 36, 1, 4); rect(g, GOLD, 40, 43, 2, 2);
    }

    private static void sword(Graphics2D g) {
        polygon(g, INK, 16,37, 19,42, 19,68, 23,68, 23,72, 18,73, 18,82, 14,82, 14,73, 10,72, 10,68, 14,68, 14,42);
        polygon(g, LIGHT, 16,40, 17,43, 17,68, 15,68, 15,44);
        rect(g, WHITE, 15, 44, 1, 23); rect(g, STEEL, 17, 44, 1, 23);
        rect(g, GOLD, 11, 69, 11, 2); rect(g, WHITE, 12, 69, 4, 1);
        rect(g, RED_DARK, 15, 73, 2, 7); rect(g, GOLD, 15, 81, 2, 1);
        rect(g, INK, 16, 74, 6, 4); rect(g, STEEL, 17, 74, 4, 3);
        rect(g, LIGHT, 17, 74, 3, 1);
    }

    private static void shield(Graphics2D g, String nation) {
        polygon(g, INK, 48,68, 57,65, 68,69, 67,83, 64,90, 58,96, 51,90, 47,82);
        polygon(g, GOLD, 49,70, 57,67, 66,70, 65,83, 62,89, 58,93, 53,89, 49,81);
        polygon(g, RED_DARK, 51,72, 57,69, 64,72, 63,82, 61,87, 58,90, 54,87, 51,80);
        polygon(g, RED, 51,72, 57,69, 57,88, 54,85, 52,80);
        crest(g, 53, 74, nation, WHITE);
        rect(g, WHITE, 50, 71, 1, 8); rect(g, EDGE, 63, 73, 1, 6);
    }

    private static void crest(Graphics2D g, int x, int y, String nation, Color color) {
        switch (nation) {
            case "Hungary" -> {
                rect(g, color, x + 4, y, 1, 8);
                rect(g, color, x + 2, y + 2, 5, 1);
                rect(g, color, x + 1, y + 4, 7, 1);
                rect(g, color, x + 3, y + 7, 3, 1);
                rect(g, color, x + 1, y + 8, 7, 2);
            }
            case "Austria" -> {
                rect(g, color, x, y + 3, 9, 3);
                rect(g, color, x, y, 9, 1);
                rect(g, color, x, y, 1, 8); rect(g, color, x + 8, y, 1, 8);
                polygon(g, color, x,y+7, x+4,y+10, x+9,y+7, x+7,y+7, x+4,y+9, x+2,y+7);
            }
            default -> eagle(g, x, y, color);
        }
    }

    private static void eagle(Graphics2D g, int x, int y, Color color) {
        String[] rows = {"...##....", "....###..", "#...#...#", "##.###.##", "#########",
                ".#######.", "#..###..#", "...###...", "..#.#.#..", ".##.#.##."};
        for (int row = 0; row < rows.length; row++)
            for (int col = 0; col < rows[row].length(); col++)
                if (rows[row].charAt(col) == '#') rect(g, color, x + col, y + row, 1, 1);
    }

    static void text(Graphics2D g, String value, int x, int y, int scale, Color color) {
        for (char letter : value.toCharArray()) {
            String[] rows = GLYPHS.get(letter).split("/");
            for (int row = 0; row < rows.length; row++)
                for (int col = 0; col < 5; col++)
                    if (rows[row].charAt(col) == '1') rect(g, color, x + col * scale, y + row * scale, scale, scale);
            x += 6 * scale;
        }
    }

    static void rect(Graphics2D g, Color color, int x, int y, int width, int height) {
        g.setColor(color); g.fillRect(x, y, width, height);
    }

    static void polygon(Graphics2D g, Color color, int... xy) {
        Polygon polygon = new Polygon();
        for (int i = 0; i < xy.length; i += 2) polygon.addPoint(xy[i], xy[i + 1]);
        g.setColor(color); g.fillPolygon(polygon);
    }
}
