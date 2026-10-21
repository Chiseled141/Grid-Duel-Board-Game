import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/** Compares native production exports and makes a before/after review sheet. */
public final class CheckAxemenArms {
    public static void main(String[] args) throws Exception {
        Path before = Path.of(args[0]), after = Path.of(args[1]);
        List<String> changed = new ArrayList<>(), unchanged = new ArrayList<>();
        try (var files = Files.list(after)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith("-native.png")).sorted().toList()) {
                String name = file.getFileName().toString();
                BufferedImage a = ImageIO.read(before.resolve(name).toFile());
                BufferedImage b = ImageIO.read(file.toFile());
                boolean differs = false;
                for (int y = 0; y < 120; y++) for (int x = 0; x < 80; x++) {
                    if (a.getRGB(x, y) == b.getRGB(x, y)) continue;
                    differs = true;
                    if (!name.endsWith("-axemen-native.png") || x < 13 || x > 58 || y < 61 || y > 87)
                        throw new AssertionError("Unexpected change in " + name + " at " + x + "," + y);
                }
                (differs ? changed : unchanged).add(name);
            }
        }
        if (changed.size() != 7 || unchanged.size() != 30) throw new AssertionError("Wrong change scope");
        comparison(before, after);
        String results = "Changed: " + changed.size() + " Axemen faces, only in the arm region.\n"
                + "Unchanged: " + unchanged.size() + " other native faces.\n"
                + "All seven heads, axes, lower bodies, borders, names and point values unchanged.\n\n"
                + "Changed files:\n" + String.join("\n", changed) + "\n\n"
                + "Unchanged files:\n" + String.join("\n", unchanged) + "\n";
        Files.writeString(after.resolve("scope-checks.txt"), results);
        System.out.println(results);
    }

    private static void comparison(Path before, Path after) throws Exception {
        BufferedImage sheet = new BufferedImage(1170, 714, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x172D30)); g.fillRect(0, 0, 1170, 714);
        g.setColor(new Color(0xF4E9CE)); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        g.drawString("AXEMEN / COVERED SLEEVES + CLEAR GAUNTLET GRIPS", 28, 35);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        g.drawString("Same head, axe and four-pixel border / Java renderer / 104 x 156 below", 28, 60);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        String[] ids = {"pl-axemen", "pl-axemen", "hu-axemen", "hu-axemen"};
        for (int i = 0; i < ids.length; i++) {
            int x = 28 + 286 * i;
            boolean old = i % 2 == 0;
            BufferedImage card = ImageIO.read((old ? before : after).resolve(ids[i] + "-native.png").toFile());
            g.drawImage(card, x, 88, 240, 360, null);
            g.setColor(new Color(0xF4E9CE)); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            g.drawString((old ? "BEFORE / " : "AFTER / ") + (i < 2 ? "POLAND" : "HUNGARY"), x, 479);
            g.drawImage(card, x + 68, 509, 104, 156, null);
        }
        g.dispose(); ImageIO.write(sheet, "png", after.resolve("before-after.png").toFile());
    }
}
