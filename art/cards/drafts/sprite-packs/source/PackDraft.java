import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Draft: third-party pixel sprites placed in the current card frame. The frame, header, crest and
 * label come from HandPixelRenderer; the figure area is cleared and the pack's first idle frame
 * is trimmed and drawn at the largest whole-number scale that fits. MtPixls cloth is recoloured
 * to each nation. Pack files are read from a directory outside the repository.
 */
public final class PackDraft {
    private static final Color PAPER = new Color(0xF4E9CE);
    private static final int AREA_X = 8, AREA_Y = 26, AREA_W = 64, AREA_H = 74;

    record Sprite(String file, int frameWidth, int frameHeight, boolean recolour) { }

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]), catalog = Path.of(args[1]), packs = Path.of(args[2]);
        Map<String, HandPixelRenderer.Unit> units = new LinkedHashMap<>();
        for (String line : Files.readAllLines(catalog)) {
            if (!line.startsWith("unit")) continue;
            String[] f = line.split("\\|");
            units.put(f[1].trim(), new HandPixelRenderer.Unit(f[1].trim(), f[2].trim(), Integer.parseInt(f[3].trim()), f[4].trim(), f[5].trim()));
        }
        String mt = "mtpixls/x/Medieval Army Pack_V3/Sprite Sheets/";
        Map<String, Sprite> mtpixls = Map.of(
                "Pikemen", new Sprite(mt + "Spear Soldier_Idle.png", 64, 64, true),
                "Bowmen", new Sprite(mt + "Archer Soldier_Idle.png", 64, 64, true),
                "Knights", new Sprite(mt + "Sword&Shield Soldier_Idle.png", 128, 80, true),
                "Axemen", new Sprite(mt + "Halbert_Soldier.png", 64, 64, true));
        Map<String, Sprite> angav = Map.of(
                "Pikemen", new Sprite("angav/x/Spearman/Idle/Spearman_Idle1.png", 32, 32, false),
                "Bowmen", new Sprite("angav/x/Archer/Idle/Archer_idle1.png", 32, 32, false));
        List<String> poland = List.of("pl-pikemen", "pl-bowmen", "pl-riders", "pl-knight", "pl-axemen");
        List<String> nations = List.of("pl-knight", "hu-knight", "au-knight", "bo-knight", "st-knight", "cr-knight", "tr-knight");
        Files.createDirectories(out);
        sheet(out.resolve("compare-poland.png").toFile(), units, packs, poland, Map.of(
                "CURRENT GAME: HAND-PIXEL V2", Map.of(),
                "MTPIXLS MEDIEVAL ARMY (RIDERS: NOT IN PACK, V2 SHOWN)", mtpixls,
                "ANGAV MEDIEVAL PIXEL CHARACTERS (ONLY PIKEMEN + BOWMEN)", angav));
        sheet(out.resolve("mtpixls-nations.png").toFile(), units, packs, nations, Map.of("MTPIXLS KNIGHTS / SEVEN NATIONS", mtpixls));
    }

    private static BufferedImage card(HandPixelRenderer.Unit unit, Sprite sprite, Path packs) throws Exception {
        BufferedImage face = HandPixelRenderer.render(unit);
        if (sprite == null) return face;
        Graphics2D g = face.createGraphics();
        g.setColor(PAPER);
        g.fillRect(AREA_X - 1, AREA_Y - 1, AREA_W + 1, AREA_H + 1);
        BufferedImage figure = trim(ImageIO.read(packs.resolve(sprite.file()).toFile()).getSubimage(0, 0, sprite.frameWidth(), sprite.frameHeight()));
        if (sprite.recolour()) recolour(figure, cloth(unit.nation()));
        int scale = Math.max(1, Math.min(AREA_W / figure.getWidth(), AREA_H / figure.getHeight()));
        int w = figure.getWidth() * scale, h = figure.getHeight() * scale;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(figure, AREA_X + (AREA_W - w) / 2, AREA_Y + AREA_H - h, w, h, null);
        g.dispose();
        return face;
    }

    private static BufferedImage trim(BufferedImage image) {
        int x0 = image.getWidth(), y0 = image.getHeight(), x1 = -1, y1 = -1;
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < image.getWidth(); x++)
                if (image.getRGB(x, y) >>> 24 > 0) { x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y); }
        BufferedImage copy = new BufferedImage(x1 - x0 + 1, y1 - y0 + 1, BufferedImage.TYPE_INT_ARGB);
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) copy.setRGB(x - x0, y - y0, image.getRGB(x, y));
        return copy;
    }

    /** Swaps the pack's green and deep-blue cloth for the nation's cloth ramp by lightness; steel and wood stay. */
    private static void recolour(BufferedImage image, Color[] cloth) {
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < image.getWidth(); x++) {
                int p = image.getRGB(x, y);
                if (p >>> 24 == 0) continue;
                float[] hsb = Color.RGBtoHSB(p >> 16 & 255, p >> 8 & 255, p & 255, null);
                float hue = hsb[0] * 360;
                // Green and deep-blue cloth only; teal-grey steel (around 160-210 degrees) keeps its colour.
                boolean green = hue >= 70 && hue < 160, blue = hue >= 210 && hue <= 270;
                if (hsb[1] < 0.25f || !(green || blue)) continue;
                Color c = hsb[2] < 0.35f ? cloth[2] : hsb[2] < 0.6f ? cloth[0] : cloth[1];
                image.setRGB(x, y, (p & 0xFF000000) | (c.getRGB() & 0xFFFFFF));
            }
    }

    private static Color[] cloth(String nation) {
        int[] v = switch (nation) {
            case "Hungary" -> new int[]{0x397E69, 0x75B787, 0x28514C};
            case "Austria" -> new int[]{0x4D5065, 0x87859A, 0x343345};
            case "Bohemia" -> new int[]{0x80538C, 0xB787B8, 0x513954};
            case "Styria" -> new int[]{0x637C3C, 0xA1BB62, 0x3A512E};
            case "Croatia" -> new int[]{0x426C9D, 0x83B4CC, 0x31455E};
            case "Transylvania" -> new int[]{0xB27B35, 0xE0B45E, 0x715033};
            default -> new int[]{0xC54846, 0xEC7460, 0x793949};
        };
        return new Color[]{new Color(v[0]), new Color(v[1]), new Color(v[2])};
    }

    private static void sheet(File file, Map<String, HandPixelRenderer.Unit> units, Path packs, List<String> ids,
                              Map<String, Map<String, Sprite>> rows) throws Exception {
        List<String> order = rows.keySet().stream().sorted((a, b) -> a.startsWith("CURRENT") ? -1 : b.startsWith("CURRENT") ? 1 : a.compareTo(b)).toList();
        int cw = 240, ch = 360, w = 24 + ids.size() * (cw + 24), h = 24 + order.size() * (ch + 64);
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x172D30)); g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int y = 24;
        for (String title : order) {
            g.setColor(PAPER); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
            g.drawString(title, 24, y + 20);
            Map<String, Sprite> sprites = rows.get(title);
            for (int i = 0; i < ids.size(); i++) {
                HandPixelRenderer.Unit unit = units.get(ids.get(i));
                Sprite sprite = sprites.get(unit.unitClass());
                if (!sprites.isEmpty() && sprite == null && title.startsWith("ANGAV")) continue;
                BufferedImage face = card(unit, sprite, packs);
                var colours = new HashSet<Integer>();
                for (int yy = 0; yy < 120; yy++) for (int xx = 0; xx < 80; xx++) colours.add(face.getRGB(xx, yy));
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g.drawImage(face, 24 + i * (cw + 24), y + 32, cw, ch, null);
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12)); g.setColor(new Color(0xB9A47E));
                g.drawString(colours.size() + " colours", 24 + i * (cw + 24), y + 32 + ch + 16);
            }
            y += ch + 64;
        }
        g.dispose();
        ImageIO.write(image, "png", file);
    }
}
