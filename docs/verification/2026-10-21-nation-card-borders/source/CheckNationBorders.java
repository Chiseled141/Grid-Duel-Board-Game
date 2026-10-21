import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import javax.imageio.ImageIO;

/** Offline scope checks and a before/after contact sheet of already-exported Java art. */
public final class CheckNationBorders {
    public static void main(String[] args) throws Exception {
        Path old = Path.of(args[0]), out = Path.of(args[1]);
        List<String> nations = List.of("pl", "hu", "au", "bo", "st", "cr", "tr");
        List<String> types = List.of("pikemen", "bowmen", "riders", "knight", "axemen");
        var borderColours = new HashSet<Integer>();
        int changed = 0;
        for (String nation : nations) {
            Integer border = null;
            for (String type : types) {
                String name = nation + "-" + type + "-native.png";
                var a = ImageIO.read(old.resolve(name).toFile());
                var b = ImageIO.read(out.resolve(name).toFile());
                boolean differs = false;
                for (int y = 0; y < 120; y++) for (int x = 0; x < 80; x++) {
                    if (a.getRGB(x, y) == b.getRGB(x, y)) continue;
                    differs = true;
                    if (x >= 6 && x <= 73 && y >= 6 && y <= 113 && y != 23 && y != 101)
                        throw new AssertionError("Changed inner face: " + name + " at " + x + "," + y);
                }
                if (!differs) throw new AssertionError("Missing border: " + name);
                if (border != null && border.intValue() != b.getRGB(3, 60))
                    throw new AssertionError("Class border mismatch: " + name);
                border = b.getRGB(3, 60);
                changed++;
            }
            borderColours.add(border);
        }
        if (borderColours.size() != 7) throw new AssertionError("Nation borders must be distinct");
        for (String id : List.of("spy", "draw-two")) {
            var a = ImageIO.read(old.resolve(id + "-native.png").toFile());
            var b = ImageIO.read(out.resolve(id + "-native.png").toFile());
            for (int y = 0; y < 120; y++) for (int x = 0; x < 80; x++)
                if (a.getRGB(x, y) != b.getRGB(x, y)) throw new AssertionError("Changed neutral Spell");
        }
        comparison(old, out);
        String result = "35 Unit frames changed; interiors identical apart from the two separator lines.\n"
                + "Seven distinct nation borders; all five classes match within each nation.\n"
                + "Draw Two and Spy remain pixel-identical.\n";
        Files.writeString(out.resolve("scope-checks.txt"), result);
        System.out.print(result);
    }

    private static void comparison(Path old, Path out) throws Exception {
        BufferedImage sheet = new BufferedImage(1170, 714, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x172D30)); g.fillRect(0, 0, 1170, 714);
        g.setColor(new Color(0xF4E9CE)); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        g.drawString("NATION COLOUR / CARD BORDER", 28, 35);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        g.drawString("Same figures and cream face / Java renderer / 104 x 156 below", 28, 60);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        String[] ids = {"pl-knight", "pl-knight", "cr-knight", "cr-knight"};
        for (int i = 0; i < ids.length; i++) {
            int x = 28 + 286 * i;
            boolean before = i % 2 == 0;
            BufferedImage card = ImageIO.read((before ? old : out).resolve(ids[i] + "-native.png").toFile());
            g.drawImage(card, x, 88, 240, 360, null);
            g.setColor(new Color(0xF4E9CE)); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            g.drawString((before ? "BEFORE / " : "AFTER / ") + (i < 2 ? "POLAND" : "CROATIA"), x, 479);
            g.drawImage(card, x + 68, 509, 104, 156, null);
        }
        g.dispose(); ImageIO.write(sheet, "png", out.resolve("before-after.png").toFile());
    }
}
