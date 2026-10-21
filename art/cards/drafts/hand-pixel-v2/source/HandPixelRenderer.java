import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.Map;

/**
 * Draft "hand-pixel v2" Unit faces: each figure is a hand-placed character map of chunky 2×2
 * pixels on the 80×120 card, instead of free polygons. Frame, header, crests and lettering are
 * copied unchanged from the runtime PixelCardFace. Standalone snapshot; not used by the game.
 */
public final class HandPixelRenderer {
    static final int WIDTH = 80, HEIGHT = 120;
    private static final Color INK = new Color(0x232B3C), PAPER = new Color(0xF4E9CE);
    private static final Color RED = new Color(0xC54846), RED_LIGHT = new Color(0xEC7460);
    private static final Color RED_DARK = new Color(0x793949), GOLD = new Color(0xD7A95B);
    private static final Color STEEL = new Color(0x8DABAE), LIGHT = new Color(0xC8D8CC);
    private static final Color SHADE = new Color(0x506777);
    private static final Color WOOD = new Color(0x916246), WOOD_LIGHT = new Color(0xBD8E5B);
    // Real skin tones replace the wood colour the runtime faces borrowed.
    private static final Color SKIN = new Color(0xE8B48A), SKIN_SHADE = new Color(0xB97A57);
    private static final Color GROUND = new Color(0xCBBE9E);
    /** Figures start here; one map cell is two native pixels square. */
    private static final int SPRITE_X = 10, SPRITE_Y = 26;

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

    private HandPixelRenderer() { }

    private record Cloth(Color main, Color light, Color dark) { }

    /** Catalog metadata supplied by the exporter; no gameplay definitions. */
    public record Unit(String id, String name, int points, String nation, String unitClass) { }

    /** Renders one Unit face at native 80×120. */
    public static BufferedImage render(Unit card) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            Cloth cloth = cloth(card.nation());
            frame(g, cloth);
            String[] figure = figure(card.unitClass());
            shadow(g, figure);
            sprite(g, figure, cloth);
            badge(g, card.unitClass(), card.nation());
            text(g, Integer.toString(card.points()), 7, 8, 2, cloth.dark());
            nationLabel(g, card.nation());
            crest(g, 65, 9, card.nation(), cloth.dark());
            String label = card.unitClass().toUpperCase(Locale.ROOT);
            text(g, label, (WIDTH - (label.length() * 6 - 1)) / 2, 106, 1, INK);
        } finally { g.dispose(); }
        return image;
    }

    private static String[] figure(String unitClass) {
        return switch (unitClass) {
            case "Knights" -> KNIGHT;
            case "Pikemen" -> PIKEMEN;
            case "Bowmen" -> BOWMEN;
            case "Riders" -> RIDERS;
            case "Axemen" -> AXEMEN;
            default -> throw new IllegalArgumentException("No design for class " + unitClass);
        };
    }

    /** The nation crest in paper colour: on the Knights' and Axemen's shields, or on the chest of the others. */
    private static void badge(Graphics2D g, String unitClass, String nation) {
        switch (unitClass) {
            case "Knights" -> crest(g, 55, 63, nation, PAPER);
            case "Pikemen" -> crest(g, 35, 63, nation, PAPER);
            case "Bowmen" -> crest(g, 32, 56, nation, PAPER);
            case "Axemen" -> crest(g, 53, 66, nation, PAPER);
            default -> { }  // the rider's chest is too small; the header crest and border carry the nation
        }
    }

    /** A flat stepped shadow under the figure's lowest row, so it stands on the card. */
    private static void shadow(Graphics2D g, String[] rows) {
        int last = rows.length - 1;
        while (last > 0 && rows[last].chars().allMatch(c -> c == '.')) last--;
        int left = rows[last].indexOf('O'), right = rows[last].lastIndexOf('O');
        int y = SPRITE_Y + last * 2 + 1, x0 = SPRITE_X + left * 2, x1 = SPRITE_X + right * 2 + 2;
        rect(g, GROUND, x0 - 4, y, x1 - x0 + 8, 2);
        rect(g, GROUND, x0, y + 2, x1 - x0, 1);
    }

    /**
     * Paints a character map: O outline, s/S skin, m/M/n steel light/mid/dark, c/C/d nation
     * cloth light/mid/dark, w/W wood light/dark, g gold, '.' leaves the paper showing.
     */
    private static void sprite(Graphics2D g, String[] rows, Cloth cloth) {
        for (int y = 0; y < rows.length; y++)
            for (int x = 0; x < rows[y].length(); x++) {
                Color color = switch (rows[y].charAt(x)) {
                    case 'O' -> INK;
                    case 's' -> SKIN; case 'S' -> SKIN_SHADE;
                    case 'm' -> LIGHT; case 'M' -> STEEL; case 'n' -> SHADE;
                    case 'c' -> cloth.light(); case 'C' -> cloth.main(); case 'd' -> cloth.dark();
                    case 'w' -> WOOD_LIGHT; case 'W' -> WOOD;
                    case 'g' -> GOLD;
                    default -> null;
                };
                if (color != null) rect(g, color, SPRITE_X + x * 2, SPRITE_Y + y * 2, 2, 2);
            }
    }

    private static final String[] KNIGHT = {
            "...............OO.............",
            "..............OccO............",
            "..O..........OcCCdO...........",
            ".OmO.........OcCCdO...........",
            ".OmO.......OOOOCCOOOO.........",
            ".OmO......OmmmmmMMMMnO........",
            ".OmO.....OmmmmmMMMMMnnO.......",
            ".OmO.....OmmMMMMMMMMnnO.......",
            ".OmO.....OmOOOOOOOOOOnO.......",
            ".OmO.....OmMMMOOMMMMnnO.......",
            ".OmO.....OmmMMMOMMMMnnO.......",
            ".OmO.....OmmMMMMMMMnnnO.......",
            ".OmO......OnMMMMMMnnnO........",
            "OOgOO......OOOOOOOOOO.........",
            "OgggO....OOmmmMMMMMMnOO.......",
            ".OWO...OOmmMOcccCCdOMMnO......",
            ".OWO..OmmMMOccccCCCdOMnO......",
            "OOmOOOmmMMOOccccCCCdddOOOOOOO.",
            "OmmMMMMMOOOcccCCCCCdOOcccCCCdO",
            "OmMMMnOOdOOccCCCCCCdOcccCCCCdO",
            ".OOOOO.OdOOcCCCCCCCdOccCCCCCdO",
            ".......OdOOOgggggggOOccCCCCCdO",
            ".......OddOcCCCCCCCdOccCCCCCdO",
            ".......OddOcCCCCCCCdOcCCCCCddO",
            ".......OddOcCCCCCCCCdOcCCCCddO",
            ".......OddOccCCCCCCCdOOcCCCdO.",
            ".......OdddOOOOOOOOOOdOOcCddO.",
            "........OddOmMnOOmMnOddOOcdO..",
            "........OddOmMnOOmMnOdd.OOO...",
            ".........OdOmMnOOmMnOdO.......",
            ".........OOOmMnOOmMnOO........",
            "...........OmMnOOmMnO.........",
            "..........OOmMnOOmMnOO........",
            ".........OmmMMnOOmmMMnO.......",
            ".........OOOOOOOOOOOOOO.......",
            "..............................",
            "..............................",
            ".............................."};
    private static final String[] PIKEMEN = {
            "...O..........................",
            "..OmO.........................",
            "..OmO.........................",
            ".OmmnO........................",
            ".OmMnO........................",
            "..OmO.........................",
            "..OwO.......OOOOOO............",
            "..OwO......OmmmMMnO...........",
            "..OwO.....OmmmMMMMnO..........",
            "..OwO...OOOOOOOOOOOOOO........",
            "..OwO..OmmmmMMMMMMMMnnO.......",
            "..OwO...OOOOOOOOOOOOOO........",
            "..OwO.....OsssssSSSO..........",
            "..OwO.....OsOsssOSSO..........",
            "..OwO.....OsssssSSSO..........",
            "..OwO.....OSssSSSSSO..........",
            "..OwO......OOSSSSOO...........",
            "..OwO....OOOOOOOOOOOO.........",
            "..OwOOOOOccccCCCCCCdOO........",
            "..OwwssOccOccCCCCCdOdO........",
            "..OwwssOccOcccCCCCdOddO.......",
            "..OWOOOcccOcccCCCCdOddO.......",
            "..OwO..OcOOccCCCCCdOOsO.......",
            "..OwO..OcOOccCCCCCdO.OO.......",
            "..OwO...O.OcCCCCCCdO..........",
            "..OwO.....OOgggggOOO..........",
            "..OwO.....OcCCCCCCdO..........",
            "..OwO.....OcCCCCCCdO..........",
            "..OwO.....OcCCdOcCdO..........",
            "..OwO.....OOOOOOOOOO..........",
            "..OwO......OWwOOWwO...........",
            "..OwO......OWwOOWwO...........",
            "..OwO......OWwOOWwO...........",
            "..OwO.....OOWwOOWwOO..........",
            "..OWO.....OWWwWOWWwWO.........",
            "..OOO.....OOOOOOOOOOO.........",
            "..............................",
            ".............................."};
    private static final String[] BOWMEN = {
            "..............................",
            "..................OO..........",
            "......OO..........OwO.........",
            ".....OmO.OO........OwO........",
            "....OmmOOmO.OOOO....OwO.......",
            "....OOmOmmOOcCCdO...OwO.......",
            ".....OOOmOOccCCCdO...OwO......",
            "......OWOOccCCCCCdO..OwO......",
            "......OWOOcCOOOOOCdO.OwO......",
            ".....OWWOOcOsssSSOdO..OwO.....",
            ".....OWWOOcOsOsSOOdO..OwO.....",
            ".....OWWOOcOsssSSSdO..OwO.....",
            "....OWWWOOOOSsSSSOdO..OwO.....",
            "....OWWOO.OOOSSSOOdO..OwO.....",
            "....OWWOOOOOOOOOOOOOOOOwO.....",
            "....OOOOcccCCCCCCdnnnnnOwO....",
            "......OccccCCCCCdOOOOmmOwmmO..",
            ".....OccOcCCCCCCdO..OOOOWOO...",
            ".....OcOOcCCCCCCdO....OwO.....",
            ".....OnnOcCCCCCCdO....OwO.....",
            ".....OnnOcCCCCCCdO....OwO.....",
            "......OOOOgggggggO...OwO......",
            ".........OcCCCCCdO...OwO......",
            ".........OcCCCCCCdO..OwO......",
            "........OcCCCCCCCdO.OwO.......",
            "........OcCCCdCCCCdOOwO.......",
            "........OOOOOOOOOOOOwO........",
            ".........OWwOOOOWwO.OO........",
            ".........OWwO..OWwO...........",
            ".........OWwO..OWwO...........",
            ".........OWwO..OWwO...........",
            "........OOWwO..OWwOO..........",
            ".......OWWWwO..OWWwWO.........",
            ".......OOOOOO..OOOOOO.........",
            "..............................",
            "..............................",
            "..............................",
            ".............................."};
    private static final String[] RIDERS = {
            "...O..........................",
            "..OmO.........................",
            "..OcccO.......................",
            "..OcCCCO..OOOOO...............",
            "..OcCO...OmmmMnO..............",
            "..OwO...OmmmMMMnO.............",
            "..OwO..OOOOOOOOOOO............",
            "..OwO...OsOsOSSSO.....OO.OO...",
            "..OwO...OsssSSSSO....OWWOWWO..",
            "..OwO....OOSSSOO....OWwwwwWWO.",
            "..OwO..OOOOOOOOOOO.OWwOwwwwWO.",
            "..OwOOmmMMMMMMMnnOOWwwwwwwwwWO",
            "..OwnnmMOccCCCdOnOOWwwwOOwwwWO",
            "..OwnnOOOcCCCCdOOOWwwwO..OwwwO",
            "..OwO..OOcCCCCdO.OWwwwO...OOO.",
            "..OwO...OgggggO..OWwwwO.......",
            "..OwO..OcccCCCdO.OWwwwO.......",
            "..OwO.OcOcCCCCdOOWwwwwO.......",
            "..OwOOOcOOOOOOOOwwwwwwO.......",
            "..OwOWWOwwwwwwwwwwwwwWO.......",
            "..OwOWwOnnOwwwwwwwwwwWO.......",
            "..OwOwwOnnOwwwwwwwwwWWO.......",
            "..OwOwwwOOwwwwwwwwwWWWO.......",
            "..OwOWwwwwwwwwwwwwWWWWO.......",
            "..OwO.OWWWWWWWWWWWWWWO........",
            "..OwO.OWWO.OWWO.OWWOWWO.......",
            "..OwO.OWWO.OWWO.OWWO.OWO......",
            "..OwO.OWO..OWO..OWO...OWO.....",
            "..OwO.OWO..OWO..OWO...OWO.....",
            "..OwO.OWO..OWO..OWO...OWO.....",
            "..OWOOWWO.OWWO.OWWO...OWWO....",
            "..OOOOOOO.OOOO.OOOO...OOOO....",
            "..............................",
            "..............................",
            "..............................",
            "..............................",
            "..............................",
            ".............................."};
    private static final String[] AXEMEN = {
            "..............................",
            "...OOOO.......................",
            "..OmmnO.......................",
            ".OmMMnO.......................",
            "OmMMMnO.....OOOOO.............",
            "OmMMMnO....OmmmMMnO...........",
            "OmMMnOO...OmmmMMMMnO..........",
            "OmMnnOO...OmmMMMMMnnO.........",
            ".OmnOWO...OOOOOOOOOOO.........",
            ".OmOwWO...OsssOgOSSSO.........",
            "..OOwWO...OsOsOgOsSSO.........",
            "...OwWO...OsssSgSSSSO.........",
            "...OwWO...OWWWWWWWWWO.........",
            "...OwWO...OWWwwwwwWWO.........",
            "...OwWO....OWWWWWWWO..........",
            "...OwWO..OOOOOOOOOOOOO........",
            "...OwWOOOOmMmMMnMnMnnO........",
            "..OOOOOcccOccCCCCCCCdO........",
            "..OnMnnOCdOcCCCCCCCCdOOOOOO...",
            "..OnnnnOOOccCCCCCCCCOcccccCO..",
            "..OOOOO..OccCCCCCCCOcccccCCCO.",
            "...OwWO..OOggggggggOccccCCCCO.",
            "...OwWO..OccCCCCCCCOcccCCCCdO.",
            "...OwWO..OcCCCCCCCCOccCCCCCdO.",
            "....OO...OcCCCCdOCCOcCCCCCCdO.",
            ".........OOOOOOOOOOOCCCCCCddO.",
            "..........OmMnO.OmMnOCCddddO..",
            "..........OmMnO.OmMnOOOOOOO...",
            "..........OWWwO.OWWwO.........",
            ".........OOWWwO.OWWwOO........",
            ".........OWWWwO.OWWWwO........",
            ".........OOOOOO.OOOOOO........",
            "..............................",
            "..............................",
            "..............................",
            "..............................",
            "..............................",
            ".............................."};

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
    private static void nationLabel(Graphics2D g, String nation) {
        String label = nation.toUpperCase(Locale.ROOT);
        // Seven glyphs fit between the points and crest. Keep long names complete on two lines.
        if (label.length() > 7) {
            text(g, label.substring(0, 7), 22, 5, 1, INK);
            String rest = label.substring(7);
            text(g, rest, 22 + (7 - rest.length()) * 3, 14, 1, INK);
        } else text(g, label, 25 - (label.length() - 6) * 3, 11, 1, INK);
    }
    private static void frame(Graphics2D g, Cloth cloth) {
        polygon(g, INK, 3,0, 76,0, 79,3, 79,116, 76,119, 3,119, 0,116, 0,3);
        // Reuse the cloth palette so the nation remains visible along overlapped card edges.
        // Keep a light paper face for the existing dark text and approved figure colours.
        polygon(g, cloth.dark(), 4,1, 75,1, 78,4, 78,115, 75,118, 4,118, 1,115, 1,4);
        polygon(g, cloth.main(), 5,2, 74,2, 77,5, 77,114, 74,117, 5,117, 2,114, 2,5);
        polygon(g, PAPER, 7,5, 72,5, 74,7, 74,112, 72,114, 7,114, 5,112, 5,7);
        rect(g, cloth.light(), 4, 2, 71, 1); rect(g, cloth.light(), 2, 4, 1, 111);
        rect(g, cloth.dark(), 4, 116, 71, 2); rect(g, cloth.dark(), 76, 4, 2, 111);
        rect(g, cloth.main(), 20, 23, 53, 1); rect(g, cloth.main(), 7, 101, 66, 1);
        rect(g, cloth.dark(), 7, 108, 5, 1); rect(g, cloth.dark(), 68, 108, 5, 1);
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
