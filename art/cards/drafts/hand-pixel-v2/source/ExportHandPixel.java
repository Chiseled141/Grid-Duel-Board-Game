import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/** Exports the hand-pixel v2 draft: natives, before/after, hand size and a 35-card grid. */
public final class ExportHandPixel {
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]), catalog = Path.of(args[1]), before = Path.of(args[2]);
        Map<String, HandPixelRenderer.Unit> units = new LinkedHashMap<>();
        for (String line : Files.readAllLines(catalog)) {
            if (!line.startsWith("unit")) continue;
            String[] f = line.split("\\|");
            units.put(f[1].trim(), new HandPixelRenderer.Unit(f[1].trim(), f[2].trim(), Integer.parseInt(f[3].trim()), f[4].trim(), f[5].trim()));
        }
        Files.createDirectories(out.resolve("native"));
        Map<String, BufferedImage> faces = new LinkedHashMap<>();
        for (var unit : units.values()) {
            BufferedImage face = HandPixelRenderer.render(unit);
            check(unit.id(), face);
            faces.put(unit.id(), face);
            ImageIO.write(face, "png", out.resolve("native/" + unit.id() + ".png").toFile());
        }
        List<String> poland = List.of("pl-pikemen", "pl-bowmen", "pl-riders", "pl-knight", "pl-axemen");
        beforeAfter(out.resolve("before-after.png").toFile(), poland, faces, before);
        strip(out.resolve("hand-size.png").toFile(), poland, faces, 104, 156, "DRAFT AT HAND SIZE 104 x 156");
        grid(out.resolve("all-units.png").toFile(), new ArrayList<>(faces.values()));
        System.out.println(faces.size() + " faces exported and checked");
    }

    private static void check(String id, BufferedImage face) {
        if (face.getWidth() != 80 || face.getHeight() != 120) throw new IllegalStateException(id + " size");
        if (face.getRGB(0, 0) >>> 24 != 0) throw new IllegalStateException(id + " corner");
        var colours = new HashSet<Integer>();
        for (int y = 0; y < 120; y++)
            for (int x = 0; x < 80; x++) {
                int p = face.getRGB(x, y), a = p >>> 24;
                if (a != 0 && a != 255) throw new IllegalStateException(id + " alpha");
                colours.add(p);
            }
        if (colours.size() > 16) throw new IllegalStateException(id + " uses " + colours.size() + " colours");
    }

    private static BufferedImage canvas(int w, int h) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x172D30)); g.fillRect(0, 0, w, h);
        g.dispose();
        return image;
    }

    private static void draw(Graphics2D g, BufferedImage face, int x, int y, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(face, x, y, w, h, null);
    }

    private static void title(Graphics2D g, String text, int x, int y) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0xF4E9CE)); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        g.drawString(text, x, y);
    }

    private static void beforeAfter(File file, List<String> ids, Map<String, BufferedImage> faces, Path before) throws Exception {
        int w = 24 + ids.size() * 264, h = 24 + 2 * (360 + 48);
        BufferedImage image = canvas(w, h);
        Graphics2D g = image.createGraphics();
        title(g, "CURRENT RUNTIME (3x)", 24, 36);
        title(g, "DRAFT HAND-PIXEL V2 (3x)", 24, 36 + 408);
        for (int i = 0; i < ids.size(); i++) {
            draw(g, ImageIO.read(before.resolve(ids.get(i) + "-native.png").toFile()), 24 + i * 264, 48, 240, 360);
            draw(g, faces.get(ids.get(i)), 24 + i * 264, 48 + 408, 240, 360);
        }
        g.dispose();
        ImageIO.write(image, "png", file);
    }

    private static void strip(File file, List<String> ids, Map<String, BufferedImage> faces, int w, int h, String caption) throws Exception {
        BufferedImage image = canvas(24 + ids.size() * (w + 16), h + 72);
        Graphics2D g = image.createGraphics();
        title(g, caption, 24, 32);
        for (int i = 0; i < ids.size(); i++) draw(g, faces.get(ids.get(i)), 24 + i * (w + 16), 48, w, h);
        g.dispose();
        ImageIO.write(image, "png", file);
    }

    private static void grid(File file, List<BufferedImage> faces) throws Exception {
        int cw = 160, ch = 240, cols = 5, rows = (faces.size() + cols - 1) / cols;
        BufferedImage image = canvas(24 + cols * (cw + 16), 56 + rows * (ch + 16));
        Graphics2D g = image.createGraphics();
        title(g, "DRAFT HAND-PIXEL V2 / ALL 35 UNITS (2x)", 24, 34);
        for (int i = 0; i < faces.size(); i++) draw(g, faces.get(i), 24 + (i % cols) * (cw + 16), 48 + (i / cols) * (ch + 16), cw, ch);
        g.dispose();
        ImageIO.write(image, "png", file);
    }
}
