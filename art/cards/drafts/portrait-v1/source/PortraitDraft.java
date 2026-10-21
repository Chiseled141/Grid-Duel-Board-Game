import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashSet;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Draft "portrait v1": one Poland Knights card drawn as a chest-up portrait in a large art
 * window, one native pixel at a time (no 2×2 cells). Frame, header, crest and lettering match
 * the runtime PixelCardFace. Standalone review snapshot; not used by the game.
 *
 * Usage: java PortraitDraft.java <out-dir> <current-pl-knight.png>
 */
public final class PortraitDraft {
    static final int WIDTH = 80, HEIGHT = 120;
    /** The art window interior, inside a one-pixel ink border at (7, 26). */
    static final int ART_X = 8, ART_Y = 27, ART_W = 64, ART_H = 72;

    static final Color INK = new Color(0x232B3C), PAPER = new Color(0xF4E9CE);
    static final Color RED = new Color(0xC54846), RED_LIGHT = new Color(0xEC7460), RED_DARK = new Color(0x793949);
    static final Color GOLD = new Color(0xD7A95B);
    static final Color STEEL = new Color(0x8DABAE), LIGHT = new Color(0xC8D8CC), SHADE = new Color(0x506777);
    static final Color SKIN = new Color(0xE8B48A), SKIN_SHADE = new Color(0xB97A57);
    static final Color WOOD = new Color(0x916246);
    // Dusk sky: darker and more violet than the steel so the helmet silhouette separates.
    static final Color SKY = new Color(0x5F6F99), GLOW = new Color(0xEDC37E), HILL = new Color(0x8C6E86);

    private static final Map<Character, String> GLYPHS = Map.ofEntries(
            Map.entry('A', "01110/10001/10001/11111/10001/10001/10001"),
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
            Map.entry('5', "11111/10000/10000/11110/00001/00001/11110"));

    private static final String[] EAGLE = {"...##....", "....###..", "#...#...#", "##.###.##", "#########",
            ".#######.", "#..###..#", "...###...", "..#.#.#..", ".##.#.##."};

    /** One drawing layer of the art window; 0 means transparent. */
    static final class Layer {
        final int[] px = new int[ART_W * ART_H];
        void set(int x, int y, Color c) { if (x >= 0 && y >= 0 && x < ART_W && y < ART_H) px[y * ART_W + x] = c.getRGB(); }
        int get(int x, int y) { return x < 0 || y < 0 || x >= ART_W || y >= ART_H ? 0 : px[y * ART_W + x]; }
        boolean on(int x, int y) { return get(x, y) != 0; }
        void rect(Color c, int x, int y, int w, int h) { for (int j = y; j < y + h; j++) for (int i = x; i < x + w; i++) set(i, j, c); }
        void polygon(Color c, int... xy) {
            Polygon p = new Polygon();
            for (int i = 0; i < xy.length; i += 2) p.addPoint(xy[i], xy[i + 1]);
            for (int y = 0; y < ART_H; y++) for (int x = 0; x < ART_W; x++) if (p.contains(x + 0.5, y + 0.5)) set(x, y, c);
        }
        /** A metal ellipse in three flat bands, lit from the upper left. */
        void plate(double cx, double cy, double rx, double ry) {
            for (int y = 0; y < ART_H; y++) for (int x = 0; x < ART_W; x++) {
                double nx = (x + 0.5 - cx) / rx, ny = (y + 0.5 - cy) / ry;
                if (nx * nx + ny * ny > 1) continue;
                double lit = -0.6 * nx - 0.8 * ny;
                set(x, y, lit > 0.45 ? LIGHT : lit < -0.3 ? SHADE : STEEL);
            }
        }
        /** Ink one pixel around the layer's silhouette, then composite it over the canvas. */
        void paintOutlined(Layer canvas) {
            for (int y = 0; y < ART_H; y++) for (int x = 0; x < ART_W; x++)
                if (!on(x, y) && (on(x - 1, y) || on(x + 1, y) || on(x, y - 1) || on(x, y + 1))) canvas.set(x, y, INK);
            paint(canvas);
        }
        void paint(Layer canvas) { for (int i = 0; i < px.length; i++) if (px[i] != 0) canvas.px[i] = px[i]; }
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        new File(out, "native").mkdirs();
        BufferedImage card = render();
        check(card);
        ImageIO.write(card, "png", new File(out, "native/pl-knight.png"));
        BufferedImage current = ImageIO.read(new File(args[1]));
        compare(new File(out, "compare-3x.png"), current, card);
        handSize(new File(out, "hand-size.png"), current, card);
        System.out.println("portrait draft exported and checked");
    }

    static BufferedImage render() {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        frame(g);
        rect(g, INK, 7, 26, 66, 74);
        Layer art = portrait();
        for (int y = 0; y < ART_H; y++) for (int x = 0; x < ART_W; x++) image.setRGB(ART_X + x, ART_Y + y, art.get(x, y));
        text(g, "5", 7, 8, 2, RED_DARK);
        text(g, "POLAND", 25, 11, 1, INK);
        crestPixels(g, 65, 9, RED_DARK, EAGLE);
        text(g, "KNIGHTS", (WIDTH - 41) / 2, 106, 1, INK);
        g.dispose();
        return image;
    }

    /** Back to front: sky, castle, banner, plume, surcoat, mail, pauldrons, helmet, face, sword, hand. */
    static Layer portrait() {
        Layer canvas = new Layer();
        sky(canvas);
        castle(canvas);
        banner().paintOutlined(canvas);
        plume().paintOutlined(canvas);
        surcoat().paintOutlined(canvas);
        aventail().paintOutlined(canvas);
        pauldron(14, -1).paintOutlined(canvas);
        pauldron(50, 1).paintOutlined(canvas);
        helmet().paintOutlined(canvas);
        face().paintOutlined(canvas);
        sword().paintOutlined(canvas);
        gauntlet().paintOutlined(canvas);
        return canvas;
    }

    private static final int[][] BAYER = {{0, 8, 2, 10}, {12, 4, 14, 6}, {3, 11, 1, 9}, {15, 7, 13, 5}};

    static void sky(Layer c) {
        for (int y = 0; y < ART_H; y++) for (int x = 0; x < ART_W; x++) {
            double t = (y - 17) / 6.0;    // a short ordered dither from dusk blue into the horizon glow
            c.set(x, y, BAYER[y & 3][x & 3] < t * 16 ? GLOW : SKY);
        }
        // Rolling hills on the horizon behind the shoulders.
        for (int x = 0; x < ART_W; x++) {
            int top = 43 + (int) Math.round(2 * Math.sin(x / 7.0));
            for (int y = top; y < ART_H; y++) c.set(x, y, HILL);
        }
    }

    static void castle(Layer c) {
        c.rect(HILL, 9, 36, 13, 8);           // curtain wall
        c.rect(HILL, 9, 31, 4, 6);            // left tower
        c.rect(HILL, 18, 29, 4, 8);           // keep
        for (int x = 9; x < 22; x += 2) c.set(x, 35, HILL);
        c.set(9, 30, HILL); c.set(11, 30, HILL); c.set(18, 28, HILL); c.set(20, 28, HILL);
        c.set(19, 32, GLOW);                  // a lit window
    }

    static Layer banner() {
        Layer l = new Layer();
        l.rect(WOOD, 4, 2, 2, 70);
        l.rect(GOLD, 4, 0, 2, 2);
        for (int x = 6; x < 20; x++) {
            int wave = (int) Math.round(1.5 * Math.sin((x - 6) / 2.5));
            for (int y = 3; y < 17; y++) l.set(x, y + wave, y < 10 ? PAPER : RED);
        }
        // A fold shadow down the cloth keeps it from reading as a flat rectangle.
        for (int y = 3; y < 18; y++) if (l.get(13, y) == RED.getRGB()) l.set(13, y, RED_DARK);
        return l;
    }

    static Layer plume() {
        Layer l = new Layer();
        // Feathers sweep up from the crest holder and fall back to the right.
        for (int i = 0; i <= 24; i++) {
            double t = i / 24.0;
            double x = (1 - t) * (1 - t) * 32 + 2 * (1 - t) * t * 40 + t * t * 51;
            double y = (1 - t) * (1 - t) * 7 + 2 * (1 - t) * t * -3 + t * t * 13;
            double r = 3.2 - 1.8 * t;
            for (int j = (int) (y - r); j <= y + r; j++) for (int k = (int) (x - r); k <= x + r; k++)
                if ((k - x) * (k - x) + (j - y) * (j - y) <= r * r) l.set(k, j, RED);
        }
        for (int y = 0; y < ART_H; y++) for (int x = 0; x < ART_W; x++) {
            if (!l.on(x, y)) continue;
            if (!l.on(x, y - 1) || !l.on(x - 1, y - 1)) l.set(x, y, RED_LIGHT);
            else if (!l.on(x, y + 1)) l.set(x, y, RED_DARK);
        }
        return l;
    }

    static Layer surcoat() {
        Layer l = new Layer();
        l.polygon(RED, 12, 44, 52, 44, 60, 72, 4, 72);
        for (int y = 56; y < ART_H; y++) { l.set(19 - (y - 56) / 5, y, RED_DARK); l.set(45 + (y - 56) / 5, y, RED_DARK); }
        for (int y = 46; y < ART_H; y++) if (l.on(11 - (y - 46) / 4, y)) l.set(12 - (y - 46) / 4 + 1, y, RED_LIGHT);
        // The white eagle of Poland on the chest, doubled from the header crest.
        for (int row = 0; row < EAGLE.length; row++) for (int col = 0; col < 9; col++)
            if (EAGLE[row].charAt(col) == '#') l.rect(PAPER, 23 + col * 2, 52 + row * 2, 2, 2);
        return l;
    }

    static Layer aventail() {
        Layer l = new Layer();
        l.polygon(STEEL, 21, 30, 43, 30, 50, 47, 14, 47);
        // Staggered rings read as mail even at hand size.
        for (int y = 30; y < 48; y++) for (int x = 14; x < 51; x++)
            if (l.on(x, y) && (x + (y / 2) * 1) % 2 == 0) l.set(x, y, (y & 1) == 0 ? SHADE : LIGHT);
        return l;
    }

    static Layer pauldron(int cx, int side) {
        Layer l = new Layer();
        l.plate(cx + side, 55, 10, 5);   // lower lame
        Layer top = new Layer();
        top.plate(cx, 49, 11, 7);
        top.paintOutlined(l);
        for (int x = cx - 7; x <= cx + 7; x++) l.set(x, 45, LIGHT);   // rolled rim catches the light
        return l;
    }

    static Layer helmet() {
        Layer l = new Layer();
        l.plate(32, 20, 11, 12);
        for (int y = 20; y < 34; y++) for (int x = 21; x < 44; x++) {
            double nx = (x + 0.5 - 32) / 11;
            l.set(x, y, nx < -0.55 ? LIGHT : nx > 0.35 ? SHADE : STEEL);
        }
        l.rect(GOLD, 21, 19, 23, 2);          // brass brow band
        l.rect(GOLD, 31, 5, 2, 4);            // plume holder
        l.set(26, 12, PAPER); l.set(27, 11, PAPER);   // specular glint on the dome
        return l;
    }

    static Layer face() {
        Layer l = new Layer();
        l.rect(SKIN, 26, 21, 13, 13);
        l.rect(SKIN, 27, 34, 11, 1);
        l.rect(SKIN, 29, 35, 7, 1);
        l.rect(SKIN_SHADE, 26, 21, 13, 1);    // shadow under the brow band
        l.rect(SKIN_SHADE, 37, 22, 2, 12); l.rect(SKIN_SHADE, 36, 34, 2, 1);
        l.rect(WOOD, 27, 23, 3, 1); l.rect(WOOD, 34, 23, 3, 1);     // brows
        l.rect(INK, 28, 25, 2, 1); l.rect(INK, 34, 25, 2, 1);       // eyes
        l.set(27, 25, PAPER); l.set(36, 25, PAPER);
        l.rect(SKIN_SHADE, 33, 26, 1, 4); l.rect(SKIN_SHADE, 31, 30, 3, 1);   // nose
        // A long drooping Polish moustache.
        l.rect(WOOD, 29, 31, 7, 1);
        l.rect(WOOD, 27, 32, 4, 1); l.rect(WOOD, 34, 32, 4, 1);
        l.rect(WOOD, 27, 33, 2, 1); l.rect(WOOD, 36, 33, 2, 1);
        l.set(27, 34, WOOD); l.set(37, 34, WOOD);
        l.rect(SKIN_SHADE, 31, 33, 3, 1);     // mouth
        return l;
    }

    static Layer sword() {
        Layer l = new Layer();
        l.rect(LIGHT, 52, 4, 1, 39); l.rect(STEEL, 53, 3, 1, 40); l.rect(SHADE, 54, 4, 1, 39);
        l.set(53, 2, LIGHT);
        l.rect(GOLD, 46, 43, 15, 2);
        l.set(46, 42, GOLD); l.set(60, 42, GOLD);
        l.rect(WOOD, 52, 45, 3, 3);
        l.rect(GOLD, 51, 54, 5, 2);           // pommel below the fist
        return l;
    }

    static Layer gauntlet() {
        Layer l = new Layer();
        l.plate(53, 50, 5, 4.5);
        l.rect(SHADE, 50, 49, 7, 1); l.rect(SHADE, 50, 51, 7, 1);   // finger plates
        return l;
    }

    static void frame(Graphics2D g) {
        polygon(g, INK, 3,0, 76,0, 79,3, 79,116, 76,119, 3,119, 0,116, 0,3);
        polygon(g, RED_DARK, 4,1, 75,1, 78,4, 78,115, 75,118, 4,118, 1,115, 1,4);
        polygon(g, RED, 5,2, 74,2, 77,5, 77,114, 74,117, 5,117, 2,114, 2,5);
        polygon(g, PAPER, 7,5, 72,5, 74,7, 74,112, 72,114, 7,114, 5,112, 5,7);
        rect(g, RED_LIGHT, 4, 2, 71, 1); rect(g, RED_LIGHT, 2, 4, 1, 111);
        rect(g, RED_DARK, 4, 116, 71, 2); rect(g, RED_DARK, 76, 4, 2, 111);
        rect(g, RED, 20, 23, 53, 1); rect(g, RED, 7, 101, 66, 1);
        rect(g, RED_DARK, 7, 108, 5, 1); rect(g, RED_DARK, 68, 108, 5, 1);
    }

    static void crestPixels(Graphics2D g, int x, int y, Color color, String[] rows) {
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
    static void rect(Graphics2D g, Color color, int x, int y, int w, int h) { g.setColor(color); g.fillRect(x, y, w, h); }
    static void polygon(Graphics2D g, Color color, int... xy) {
        Polygon p = new Polygon();
        for (int i = 0; i < xy.length; i += 2) p.addPoint(xy[i], xy[i + 1]);
        g.setColor(color); g.fillPolygon(p);
    }

    static void check(BufferedImage face) {
        var colours = new HashSet<Integer>();
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            int p = face.getRGB(x, y), a = p >>> 24;
            if (a != 0 && a != 255) throw new IllegalStateException("partial alpha at " + x + "," + y);
            colours.add(p);
        }
        if (face.getRGB(0, 0) >>> 24 != 0) throw new IllegalStateException("corner must stay transparent");
        if (colours.size() > 16) throw new IllegalStateException(colours.size() + " colours");
        System.out.println(colours.size() + " colours incl. transparent");
    }

    static BufferedImage canvas(int w, int h) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x172D30)); g.fillRect(0, 0, w, h);
        g.dispose();
        return image;
    }
    static void draw(Graphics2D g, BufferedImage face, int x, int y, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(face, x, y, w, h, null);
    }
    static void title(Graphics2D g, String text, int x, int y) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(PAPER); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        g.drawString(text, x, y);
    }
    static void compare(File file, BufferedImage current, BufferedImage draft) throws Exception {
        BufferedImage image = canvas(24 + 2 * 264, 48 + 360 + 24);
        Graphics2D g = image.createGraphics();
        title(g, "CURRENT (3x)", 24, 34); title(g, "PORTRAIT DRAFT (3x)", 288, 34);
        draw(g, current, 24, 48, 240, 360); draw(g, draft, 288, 48, 240, 360);
        g.dispose();
        ImageIO.write(image, "png", file);
    }
    static void handSize(File file, BufferedImage current, BufferedImage draft) throws Exception {
        BufferedImage image = canvas(24 + 2 * 120, 48 + 156 + 24);
        Graphics2D g = image.createGraphics();
        title(g, "HAND SIZE 104x156", 24, 32);
        draw(g, current, 24, 48, 104, 156); draw(g, draft, 144, 48, 104, 156);
        g.dispose();
        ImageIO.write(image, "png", file);
    }
}
