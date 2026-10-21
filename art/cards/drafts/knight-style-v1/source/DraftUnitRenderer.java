import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.Map;

/** Offline draft snapshot. Knights are unchanged; other four figures await user approval. */
public final class DraftUnitRenderer {
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

    private static final Map<String, String> NATIONS = Map.of(
            "hu", "Hungary", "pl", "Poland", "bo", "Bohemia", "au", "Austria",
            "st", "Styria", "cr", "Croatia", "tr", "Transylvania");
    private static final Map<String, String> CLASSES = Map.of(
            "pikemen", "Pikemen", "bowmen", "Bowmen", "riders", "Riders",
            "knight", "Knights", "axemen", "Axemen");

    private DraftUnitRenderer() { }

    private record Cloth(Color main, Color light, Color dark) { }

    private static Cloth cloth(String nation) {
        return switch (nation) {
            case "Hungary" -> new Cloth(new Color(0x397E69), new Color(0x75B787), new Color(0x28514C));
            case "Austria" -> new Cloth(new Color(0x4D5065), new Color(0x87859A), new Color(0x343345));
            case "Poland" -> new Cloth(RED, RED_LIGHT, RED_DARK);
            case "Bohemia" -> new Cloth(new Color(0x80538C), new Color(0xB787B8), new Color(0x513954));
            case "Styria" -> new Cloth(new Color(0x637C3C), new Color(0xA1BB62), new Color(0x3A512E));
            case "Croatia" -> new Cloth(new Color(0x426C9D), new Color(0x83B4CC), new Color(0x31455E));
            case "Transylvania" -> new Cloth(new Color(0xB27B35), new Color(0xE0B45E), new Color(0x715033));
            default -> throw new IllegalArgumentException("No nation palette for " + nation);
        };
    }

    /** Immutable metadata read from the existing catalog, never game balance definitions. */
    public record Unit(String id, String name, int points, String nation, String unitClass) { }

    /** Catalog Unit identity and tags must agree before selecting a design. */
    static boolean supports(Unit card) {
        if (card == null) return false;
        int split = card.id().indexOf('-');
        if (split < 0) return false;
        return card.nation().equals(NATIONS.get(card.id().substring(0, split)))
                && card.unitClass().equals(CLASSES.get(card.id().substring(split + 1)));
    }

    /** Composes the frame, heraldry, figure and bitmap type at native resolution. */
    static BufferedImage render(Unit card) {
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
        frame(g, cloth);
        backdrop(g);
        switch (card.unitClass()) {
            case "Knights" -> {
                cape(g, cloth); armour(g, cloth); helmet(g, cloth); sword(g, cloth); shield(g, card.nation(), cloth);
            }
            case "Pikemen" -> pikeman(g, cloth, card.nation());
            case "Bowmen" -> bowman(g, cloth, card.nation());
            case "Riders" -> rider(g, cloth, card.nation());
            case "Axemen" -> axeman(g, cloth, card.nation());
            default -> throw new IllegalArgumentException("No design for class " + card.unitClass());
        }
        text(g, Integer.toString(card.points()), 7, 8, 2, cloth.dark());
        nationLabel(g, card.nation());
        classIcon(g, card.unitClass());
        crest(g, 66, 9, card.nation(), cloth.dark());
        String label = card.unitClass().toUpperCase(Locale.ROOT);
        text(g, label, (WIDTH - (label.length() * 6 - 1)) / 2, 106, 1, INK);
    }

    private static void nationLabel(Graphics2D g, String nation) {
        String label = nation.toUpperCase(Locale.ROOT);
        // Seven glyphs fit between the points and crest. Keep long names complete on two lines.
        if (label.length() > 7) {
            text(g, label.substring(0, 7), 22, 5, 1, INK);
            String rest = label.substring(7);
            text(g, rest, 22 + (7 - rest.length()) * 3, 14, 1, INK);
        } else text(g, label, 25 - (label.length() - 6) * 3, 11, 1, INK);
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

    private static void pikeman(Graphics2D g, Cloth c, String nation) {
        cape(g, c);
        armour(g, c);
        // A broad kettle helmet and tall pavise distinguish the infantry from Knights.
        polygon(g, INK, 31,37, 39,32, 47,35, 52,43, 50,54, 43,59, 33,56, 29,48);
        polygon(g, SHADE, 32,40, 40,35, 47,39, 49,48, 46,54, 41,57, 34,53, 31,47);
        polygon(g, STEEL, 33,42, 39,39, 42,43, 41,54, 35,52, 32,47);
        rect(g, INK, 32, 44, 16, 3);
        rect(g, LIGHT, 35, 48, 2, 4); rect(g, STEEL, 44, 48, 2, 3);
        polygon(g, INK, 29,40, 31,35, 39,30, 48,33, 52,39, 56,42, 56,45, 26,45, 26,42);
        polygon(g, STEEL, 30,40, 33,36, 40,32, 46,35, 50,41);
        polygon(g, LIGHT, 31,40, 34,36, 39,33, 39,39);
        rect(g, LIGHT, 28, 42, 25, 1); rect(g, SHADE, 28, 43, 26, 1);
        rect(g, GOLD, 41, 33, 1, 6);
        // The entire pike remains visible from its leaf-shaped point to the grounded shaft.
        rect(g, INK, 15, 43, 4, 55); rect(g, WOOD, 16, 44, 2, 53);
        rect(g, WOOD_LIGHT, 16, 48, 1, 43);
        polygon(g, INK, 17,25, 22,37, 21,42, 18,47, 14,43, 12,38);
        polygon(g, STEEL, 17,29, 20,37, 19,41, 17,44, 14,39);
        polygon(g, LIGHT, 17,29, 17,42, 14,38); rect(g, WHITE, 16, 34, 1, 5);
        rect(g, GOLD, 15, 45, 4, 2);
        polygon(g, INK, 16,70, 24,69, 28,72, 26,78, 17,79, 14,76);
        polygon(g, STEEL, 17,72, 24,71, 26,73, 24,76, 17,77, 16,75);
        rect(g, LIGHT, 17, 72, 6, 2);
        polygon(g, INK, 48,64, 59,61, 70,65, 69,88, 60,98, 49,91, 46,78);
        polygon(g, GOLD, 50,66, 59,63, 68,67, 67,87, 60,95, 51,90, 48,78);
        polygon(g, c.dark(), 52,68, 59,65, 66,69, 65,86, 60,92, 53,88, 50,78);
        polygon(g, c.main(), 52,68, 59,65, 59,90, 54,86, 52,78);
        rect(g, WHITE, 50, 68, 1, 10); crest(g, 54, 74, nation, WHITE);
    }

    private static void bowman(Graphics2D g, Cloth c, String nation) {
        // A large covered torso matches the Knights' scale; quiver, hood and resting bow
        // provide a clean class silhouette without the confusing horizontal aiming arm.
        polygon(g, INK, 24,55, 39,52, 49,58, 54,70, 49,81, 52,96, 22,96, 25,79, 19,70);
        polygon(g, c.dark(), 26,57, 38,54, 47,60, 51,70, 46,80, 49,94, 25,94, 28,78, 22,69);
        polygon(g, c.main(), 26,58, 34,56, 38,75, 34,94, 25,94, 29,78, 24,68);
        polygon(g, c.light(), 26,59, 29,58, 31,70, 28,74, 25,67);
        // Brown leather jerkin sits over coloured sleeves; no bare arms.
        polygon(g, INK, 30,56, 37,54, 44,58, 46,76, 28,77, 26,64);
        polygon(g, WOOD, 31,58, 37,56, 42,60, 43,74, 30,75, 29,64);
        polygon(g, WOOD_LIGHT, 31,59, 35,58, 36,71, 31,72);
        polygon(g, WOOD_DARK, 37,58, 41,61, 42,73, 38,74);
        // Three fletched shafts above a separate quiver on the left shoulder.
        polygon(g, INK, 15,48, 23,45, 29,73, 22,83, 16,80, 12,54);
        polygon(g, WOOD_DARK, 16,50, 22,48, 26,72, 21,79, 18,77, 14,54);
        rect(g, WOOD_LIGHT, 15, 53, 2, 16); rect(g, GOLD, 16, 54, 7, 2);
        for (int i = 0; i < 3; i++) {
            int x = 14 + i * 4, y = 30 + i * 2;
            rect(g, INK, x, y + 1, 1, 23);
            polygon(g, LIGHT, x-1,y, x+2,y+2, x+2,y+7, x-1,y+4);
            rect(g, WHITE, x, y+1, 1, 4);
        }
        // Hood opening, nose, brow and chin are large enough to survive hand-size sampling.
        polygon(g, INK, 28,33, 37,28, 46,32, 50,42, 47,54, 39,60, 28,56, 24,46);
        polygon(g, c.dark(), 29,35, 37,30, 44,34, 48,42, 45,52, 39,57, 29,54, 26,46);
        polygon(g, c.main(), 29,35, 36,31, 40,33, 33,38, 29,47, 30,53, 27,46);
        polygon(g, c.light(), 29,36, 35,33, 37,34, 32,38, 29,44, 28,43);
        polygon(g, INK, 34,38, 42,36, 46,41, 46,46, 49,47, 46,49, 44,54, 38,56, 32,51, 30,45);
        polygon(g, WOOD_LIGHT, 35,39, 41,38, 44,42, 44,46, 47,47, 44,48, 43,51, 38,53, 34,49, 32,44);
        polygon(g, WOOD, 40,39, 43,42, 43,47, 45,48, 42,51, 38,52, 38,49, 40,47);
        rect(g, INK, 35, 42, 3, 1); rect(g, INK, 42, 42, 2, 1);
        rect(g, WHITE, 43, 46, 2, 1);
        rect(g, WOOD_DARK, 37, 51, 5, 2);
        // Raised left sleeve crosses the chest; glove sits on the quiver strap.
        polygon(g, INK, 23,62, 29,63, 34,70, 38,66, 42,67, 42,72, 34,77, 28,74, 22,69);
        polygon(g, c.main(), 24,64, 28,65, 34,72, 38,69, 39,71, 34,75, 29,72, 24,68);
        rect(g, c.light(), 24, 64, 3, 2); rect(g, LIGHT, 37, 67, 4, 4);
        rect(g, INK, 27, 81, 21, 3); rect(g, GOLD, 35, 81, 5, 3);
        polygon(g, INK, 24,94, 34,94, 34,98, 21,98); rect(g, STEEL, 25, 95, 7, 1);
        polygon(g, INK, 41,94, 50,94, 54,98, 41,98); rect(g, SHADE, 43, 95, 6, 1);
        restingBow(g);
        // Right sleeve and glove meet the wooden bow at its middle, below the face.
        polygon(g, INK, 46,60, 51,63, 55,66, 65,65, 70,66, 70,72, 63,75, 53,75, 47,71, 44,66);
        polygon(g, c.main(), 47,63, 49,64, 54,69, 64,68, 65,72, 54,73, 49,69, 46,66);
        rect(g, c.light(), 47, 63, 2, 4); rect(g, c.light(), 54, 69, 9, 1);
        rect(g, LIGHT, 65, 67, 4, 5); rect(g, INK, 68, 69, 1, 2);
    }

    private static void restingBow(Graphics2D g) {
        // The unmistakable D contour and one taut straight string keep open paper inside.
        polygon(g, INK, 54,31, 60,35, 66,43, 70,55, 72,66, 70,79, 65,89, 55,98,
                54,94, 61,86, 66,77, 68,66, 66,56, 62,45, 57,37, 53,34);
        polygon(g, WOOD, 55,34, 59,37, 64,44, 68,56, 70,66, 68,78, 63,88, 55,95,
                62,85, 66,76, 68,66, 66,55, 62,45, 57,37);
        polygon(g, WOOD_LIGHT, 57,36, 61,40, 65,47, 68,57, 69,66, 67,76, 67,65, 65,55, 61,44);
        rect(g, INK, 54, 34, 1, 61); rect(g, WHITE, 55, 35, 1, 59);
        rect(g, WOOD_DARK, 67, 63, 3, 8);
    }

    private static void rider(Graphics2D g, Cloth c, String nation) {
        // A close mounted portrait gives the rider the same head size as Knights. The
        // foreground horse's head, ears, bridle and neck are the main class marker.
        polygon(g, INK, 24,49, 39,48, 47,58, 45,74, 28,87, 15,86, 19,65);
        polygon(g, c.dark(), 25,51, 38,50, 44,59, 41,72, 28,83, 18,83, 22,65);
        polygon(g, c.main(), 25,52, 31,51, 28,73, 20,80, 23,64);
        polygon(g, c.light(), 24,56, 26,54, 25,69, 23,72);
        polygon(g, INK, 28,52, 41,52, 48,60, 45,75, 36,83, 25,76, 22,63);
        polygon(g, STEEL, 29,54, 39,54, 44,61, 41,72, 34,77, 27,73, 25,63);
        polygon(g, LIGHT, 29,55, 34,54, 34,68, 29,70, 26,62);
        polygon(g, SHADE, 35,56, 39,55, 43,61, 40,72, 35,75);
        polygon(g, INK, 24,55, 29,54, 32,59, 30,65, 23,65, 20,62);
        polygon(g, LIGHT, 24,57, 28,56, 30,60, 28,62, 23,62);
        // Small crested open riding helm; no Knights plume or closed faceplate.
        polygon(g, INK, 27,33, 34,29, 42,33, 45,42, 41,51, 33,55, 26,50, 23,42);
        polygon(g, SHADE, 27,36, 34,32, 40,35, 42,42, 39,49, 33,52, 28,48, 26,42);
        polygon(g, WOOD_LIGHT, 29,39, 38,37, 41,41, 41,45, 38,49, 33,50, 28,46);
        rect(g, INK, 33, 41, 7, 2); rect(g, WOOD_DARK, 33, 48, 5, 2);
        polygon(g, STEEL, 26,37, 28,33, 34,30, 40,34, 43,39, 26,42);
        polygon(g, LIGHT, 27,37, 29,34, 34,32, 34,38);
        rect(g, INK, 25, 40, 18, 2); rect(g, GOLD, 35, 33, 1, 6);
        polygon(g, INK, 29,30, 31,26, 37,26, 42,30, 39,32, 34,30);
        polygon(g, c.main(), 31,29, 32,27, 36,27, 39,29, 35,28);
        // Lance behind the horse, with a compact flag clear of both faces.
        rect(g, INK, 62, 29, 3, 62); rect(g, WOOD_LIGHT, 63, 30, 1, 60);
        polygon(g, INK, 63,24, 66,30, 63,34, 61,30);
        polygon(g, LIGHT, 63,26, 64,30, 63,32, 62,30);
        polygon(g, INK, 65,29, 74,29, 71,33, 74,37, 65,37);
        polygon(g, c.main(), 65,30, 72,30, 69,33, 72,35, 65,35);
        rect(g, WHITE, 66, 31, 5, 1);
        // Saddle and near leg sit in front of the horse's barrel at the left.
        polygon(g, INK, 13,79, 28,74, 44,77, 46,96, 13,98, 9,91);
        polygon(g, WOOD, 14,81, 28,77, 41,79, 43,94, 14,96, 11,90);
        polygon(g, c.dark(), 15,79, 28,76, 36,79, 35,95, 15,96);
        polygon(g, c.main(), 17,81, 26,78, 28,93, 17,94);
        rect(g, GOLD, 16, 93, 19, 2); crest(g, 18, 82, nation, WHITE);
        polygon(g, INK, 32,74, 39,76, 41,82, 36,94, 30,94, 30,90, 34,82, 30,79);
        polygon(g, SHADE, 34,77, 37,78, 38,82, 34,91, 32,91, 36,82, 33,79);
        rect(g, LIGHT, 34, 78, 2, 4); rect(g, INK, 29, 93, 9, 4);
        foregroundHorse(g);
        // The gloved hand holds visibly separate reins across the horse's shoulder.
        g.setColor(INK); g.drawLine(40, 69, 65, 76); g.drawLine(40, 70, 57, 84);
        rect(g, INK, 35, 65, 9, 7); rect(g, STEEL, 36, 66, 6, 4);
        rect(g, LIGHT, 36, 66, 4, 1);
    }

    private static void foregroundHorse(Graphics2D g) {
        polygon(g, INK, 39,96, 37,84, 43,73, 47,62, 46,49, 49,44, 53,50,
                57,48, 59,42, 62,46, 62,55, 66,60, 73,65, 74,73, 70,79,
                63,78, 60,75, 58,86, 61,98);
        polygon(g, WOOD, 41,96, 39,85, 45,74, 49,63, 49,51, 50,48, 52,53,
                57,51, 59,47, 60,48, 60,56, 64,62, 71,67, 72,72, 69,76,
                64,75, 59,71, 56,84, 58,96);
        polygon(g, WOOD_LIGHT, 50,54, 56,53, 59,57, 60,63, 56,69, 53,80,
                50,93, 44,95, 43,86, 48,75, 52,64);
        polygon(g, WOOD_DARK, 54,74, 58,75, 55,86, 58,96, 51,96);
        // A stepped mane, two sharp ears and pale facial blaze read at 104×156.
        polygon(g, INK, 47,50, 50,53, 49,62, 46,74, 42,82, 42,74, 45,64);
        polygon(g, INK, 55,49, 57,46, 58,51, 55,55, 52,55);
        polygon(g, LIGHT, 57,55, 60,57, 62,63, 65,67, 63,70, 60,67, 58,61);
        rect(g, WHITE, 62, 62, 3, 2); rect(g, INK, 63, 62, 2, 2);
        rect(g, INK, 69, 70, 2, 2);
        polygon(g, INK, 61,70, 64,67, 72,69, 73,72, 70,75, 63,73);
        polygon(g, WOOD_DARK, 63,70, 65,69, 71,70, 71,72, 69,73, 64,72);
        rect(g, GOLD, 64, 74, 2, 2);
        g.setColor(INK); g.drawLine(59, 57, 64, 74);
    }

    private static void axeman(Graphics2D g, Cloth c, String nation) {
        cape(g, c); armour(g, c);
        // A low unplumed helmet, broad mail collar and a large diagonal battleaxe.
        polygon(g, INK, 31,36, 39,31, 48,35, 51,43, 48,54, 41,59, 32,55, 28,46);
        polygon(g, SHADE, 32,38, 39,33, 47,37, 49,44, 46,53, 41,56, 34,53, 30,46);
        polygon(g, STEEL, 32,39, 38,36, 41,39, 40,53, 34,51, 31,45);
        polygon(g, WOOD_LIGHT, 34,41, 44,39, 47,44, 45,50, 40,53, 34,49, 32,44);
        polygon(g, WOOD_DARK, 34,48, 38,50, 43,48, 46,47, 44,53, 40,57, 35,53);
        rect(g, INK, 34, 43, 3, 1); rect(g, INK, 42, 42, 3, 1);
        polygon(g, INK, 28,40, 31,34, 39,29, 47,33, 51,39, 51,42, 29,43);
        polygon(g, STEEL, 31,39, 33,35, 39,31, 46,35, 49,40);
        polygon(g, LIGHT, 32,38, 35,35, 39,32, 39,39);
        rect(g, INK, 30, 41, 20, 2); rect(g, GOLD, 39, 32, 2, 15);
        rect(g, LIGHT, 39, 34, 1, 5); rect(g, STEEL, 30, 44, 2, 8);
        // Two overlapping shoulder plates and mail clusters instead of an empty shoulder.
        rect(g, SHADE, 30, 56, 17, 3);
        for (int x = 32; x < 46; x += 4) rect(g, LIGHT, x, 57, 2, 1);
        crest(g, 33, 65, nation, WHITE);
        // Shaft crosses the figure and both gloves; the heavy crescent stays in open paper.
        polygon(g, INK, 59,35, 63,37, 33,98, 28,98);
        polygon(g, WOOD, 60,38, 61,39, 32,96, 30,96);
        g.setColor(WOOD_LIGHT); g.drawLine(59, 42, 34, 92);
        polygon(g, INK, 55,32, 62,30, 68,25, 73,25, 71,32, 73,41, 72,48,
                67,54, 61,50, 58,44, 52,40, 48,40, 48,36);
        polygon(g, STEEL, 56,34, 63,32, 69,28, 70,28, 69,33, 71,41, 70,47,
                66,51, 63,48, 60,42, 54,38, 51,38);
        polygon(g, LIGHT, 68,29, 70,28, 69,33, 71,41, 70,47, 66,51,
                65,48, 68,44, 68,38, 67,34);
        rect(g, WHITE, 70, 38, 1, 6); rect(g, GOLD, 56, 35, 3, 4);
        // Two covered arms point to separate grip positions along the same shaft.
        polygon(g, INK, 20,69, 26,68, 31,74, 40,78, 41,83, 36,86, 28,80, 21,78, 18,73);
        polygon(g, STEEL, 21,71, 25,70, 30,76, 37,79, 37,83, 29,78, 23,76, 20,73);
        rect(g, LIGHT, 21, 71, 4, 2); rect(g, INK, 34, 79, 6, 2);
        polygon(g, INK, 50,63, 56,65, 55,72, 49,76, 44,71, 44,65);
        polygon(g, STEEL, 49,65, 53,66, 53,70, 49,73, 46,70, 46,67);
        rect(g, LIGHT, 47, 66, 4, 2); rect(g, INK, 49, 69, 4, 1);
    }

    private static void frame(Graphics2D g, Cloth cloth) {
        polygon(g, INK, 3,0, 76,0, 79,3, 79,116, 76,119, 3,119, 0,116, 0,3);
        polygon(g, PAPER, 4,1, 75,1, 78,4, 78,115, 75,118, 4,118, 1,115, 1,4);
        rect(g, WHITE, 4, 2, 71, 1); rect(g, WHITE, 2, 4, 1, 111);
        rect(g, EDGE, 4, 116, 71, 2); rect(g, EDGE, 76, 4, 2, 111);
        rect(g, EDGE, 20, 23, 53, 1); rect(g, EDGE, 7, 101, 66, 1);
        rect(g, cloth.dark(), 7, 108, 5, 1); rect(g, cloth.dark(), 68, 108, 5, 1);
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

    private static void sword(Graphics2D g, Cloth cloth) {
        polygon(g, INK, 16,37, 19,42, 19,68, 23,68, 23,72, 18,73, 18,82, 14,82, 14,73, 10,72, 10,68, 14,68, 14,42);
        polygon(g, LIGHT, 16,40, 17,43, 17,68, 15,68, 15,44);
        rect(g, WHITE, 15, 44, 1, 23); rect(g, STEEL, 17, 44, 1, 23);
        rect(g, GOLD, 11, 69, 11, 2); rect(g, WHITE, 12, 69, 4, 1);
        rect(g, cloth.dark(), 15, 73, 2, 7); rect(g, GOLD, 15, 81, 2, 1);
        rect(g, INK, 16, 74, 6, 4); rect(g, STEEL, 17, 74, 4, 3);
        rect(g, LIGHT, 17, 74, 3, 1);
    }

    private static void shield(Graphics2D g, String nation, Cloth cloth) {
        polygon(g, INK, 48,68, 57,65, 68,69, 67,83, 64,90, 58,96, 51,90, 47,82);
        polygon(g, GOLD, 49,70, 57,67, 66,70, 65,83, 62,89, 58,93, 53,89, 49,81);
        polygon(g, cloth.dark(), 51,72, 57,69, 64,72, 63,82, 61,87, 58,90, 54,87, 51,80);
        polygon(g, cloth.main(), 51,72, 57,69, 57,88, 54,85, 52,80);
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
            case "Poland" -> eagle(g, x, y, color);
            case "Bohemia" -> crestPixels(g, x, y, color, new String[] {
                "...###...", "..##.#...", "...###..#", "##.##..##", ".######..",
                "...##.###", "..###...#", ".##.##...", "##...##..", "#.....##."});
            case "Styria" -> crestPixels(g, x, y, color, new String[] {
                "..##.....", ".####..#.", "...##.##.", "..#####..", "###.##...",
                "#..####..", "..##..##.", ".##....#.", ".#...###.", "###..#..."});
            case "Croatia" -> {
                for (int row = 0; row < 4; row++) for (int col = 0; col < 4; col++)
                    if ((row + col) % 2 == 0) rect(g, color, x + col * 2, y + row * 2, 2, 2);
                rect(g, color, x + 2, y + 8, 4, 1);
            }
            case "Transylvania" -> crestPixels(g, x, y, color, new String[] {
                "#.#.#.#.#", "#########", ".##...##.", ".##...##.", ".#######.",
                "..##.##..", "..##.##..", "..#...#..", "..#...#..", ".###.###."});
            default -> throw new IllegalArgumentException("No crest for " + nation);
        }
    }

    private static void eagle(Graphics2D g, int x, int y, Color color) {
        crestPixels(g, x, y, color, new String[] {"...##....", "....###..", "#...#...#", "##.###.##", "#########",
                ".#######.", "#..###..#", "...###...", "..#.#.#..", ".##.#.##."});
    }

    private static void crestPixels(Graphics2D g, int x, int y, Color color, String[] rows) {
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
