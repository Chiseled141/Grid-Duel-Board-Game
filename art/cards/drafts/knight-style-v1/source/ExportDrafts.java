import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/** Offline export and verification of four draft Unit designs and one unchanged Knights reference. Does not modify the running game. */
public final class ExportDrafts {
    private static final List<String> IDS = List.of("pl-knight", "pl-pikemen", "pl-bowmen", "pl-riders", "pl-axemen");

    private ExportDrafts() { }

    /** Args: output pack directory, existing card catalog path. */
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Expected output directory and catalog.txt");
        Path folder = Path.of(args[0]), catalog = Path.of(args[1]);
        Map<String, DraftUnitRenderer.Unit> units = readCatalog(catalog);
        Files.createDirectories(folder.resolve("native"));
        Files.createDirectories(folder.resolve("png-4x"));
        List<String> entries = new ArrayList<>();
        StringBuilder checks = new StringBuilder("# Export verification — 2026-10-21\n\n")
                .append("Rendered offline with Java2D; these are exported assets, not a live-game screenshot.\n\n")
                .append("Runtime: ").append(System.getProperty("java.version")).append("; ")
                .append(System.getProperty("os.name")).append(" ").append(System.getProperty("os.arch"))
                .append(". Source compiles with Java 17 release target.\n\n")
                .append("| Card | Native | Colours incl. alpha | Binary alpha / opaque body | Exact 4x / deterministic | Status |\n")
                .append("| --- | --- | ---: | --- | --- | --- |\n");
        for (String id : IDS) {
            var unit = units.get(id);
            BufferedImage nativeArt = DraftUnitRenderer.render(unit);
            BufferedImage large = enlarge(nativeArt, 4);
            int colours = verify(nativeArt, large, DraftUnitRenderer.render(unit));
            Path smallPath = folder.resolve("native/" + id + ".png");
            Path largePath = folder.resolve("png-4x/" + id + ".png");
            ImageIO.write(nativeArt, "png", smallPath.toFile());
            ImageIO.write(large, "png", largePath.toFile());
            entries.add("    {\"id\": " + json(id) + ", \"name\": " + json(unit.name())
                    + ", \"nation\": " + json(unit.nation()) + ", \"class\": " + json(unit.unitClass())
                    + ", \"points\": " + unit.points() + ", \"native\": " + json("native/" + id + ".png")
                    + ", \"png4x\": " + json("png-4x/" + id + ".png")
                    + ", \"sha256_native\": " + json(sha256(smallPath))
                    + ", \"sha256_png4x\": " + json(sha256(largePath)) + "}");
            checks.append("| ").append(id).append(" | 80×120 | ").append(colours)
                    .append(" | PASS | PASS | PASS |\n");
        }
        String manifest = "{\n  \"pack\": \"knight-style-v1\",\n  \"catalog_sha256\": " + json(sha256(catalog))
                + ",\n  \"integrated_this_batch\": false,\n  \"native_size\": [80, 120],"
                + "\n  \"png4x_size\": [320, 480],\n  \"cards\": [\n" + String.join(",\n", entries) + "\n  ]\n}\n";
        Files.writeString(folder.resolve("manifest.json"), manifest, StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("VERIFICATION.md"), checks, StandardCharsets.UTF_8);
        gallery(folder.resolve("preview.png"), IDS, units, 5, "KNIGHTS REFERENCE + FOUR DRAFTS");
        smallGallery(folder.resolve("hand-size.png"), units);
        BufferedImage reference = ImageIO.read(Path.of("docs/verification/2026-10-21-bowmen-clothed/pl-knight-native.png").toFile());
        BufferedImage knight = DraftUnitRenderer.render(units.get("pl-knight"));
        for (int y = 0; y < 120; y++) for (int x = 0; x < 80; x++)
            if (reference.getRGB(x, y) != knight.getRGB(x, y)) throw new AssertionError("Knights reference changed");
        Files.writeString(folder.resolve("VERIFICATION.md"), "\nKnights reference: pixel-identical to the existing runtime export. PASS.\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        System.out.println("Exported and verified " + IDS.size() + " units to " + folder.toAbsolutePath());
    }

    private static Map<String, DraftUnitRenderer.Unit> readCatalog(Path catalog) throws Exception {
        Map<String, DraftUnitRenderer.Unit> result = new LinkedHashMap<>();
        for (String line : Files.readAllLines(catalog, StandardCharsets.UTF_8)) {
            String[] fields = line.split("\\|");
            if (fields.length != 6 || !fields[0].trim().equals("unit")) continue;
            String id = fields[1].trim();
            if (IDS.contains(id)) result.put(id, new DraftUnitRenderer.Unit(id, fields[2].trim(),
                    Integer.parseInt(fields[3].trim()), fields[4].trim(), fields[5].trim()));
        }
        if (!result.keySet().containsAll(IDS)) throw new IllegalArgumentException("Missing catalog IDs");
        return result;
    }

    private static BufferedImage enlarge(BufferedImage original, int scale) {
        BufferedImage image = new BufferedImage(original.getWidth() * scale, original.getHeight() * scale,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(original, 0, 0, image.getWidth(), image.getHeight(), null);
        g.dispose();
        return image;
    }

    private static int verify(BufferedImage nativeArt, BufferedImage large, BufferedImage repeat) {
        if (nativeArt.getWidth() != 80 || nativeArt.getHeight() != 120) throw new AssertionError("Native size");
        var colours = new HashSet<Integer>();
        for (int y = 0; y < 120; y++) for (int x = 0; x < 80; x++) {
            int pixel = nativeArt.getRGB(x, y), alpha = pixel >>> 24;
            colours.add(pixel);
            if (alpha != 0 && alpha != 255) throw new AssertionError("Blended native alpha");
            if (x >= 4 && x <= 74 && y >= 4 && y <= 114 && alpha != 255)
                throw new AssertionError("Transparent interior");
            if (pixel != repeat.getRGB(x, y)) throw new AssertionError("Non-deterministic art");
            for (int dy = 0; dy < 4; dy++) for (int dx = 0; dx < 4; dx++)
                if (pixel != large.getRGB(x * 4 + dx, y * 4 + dy)) throw new AssertionError("Blended enlargement");
        }
        if (nativeArt.getRGB(0, 0) >>> 24 != 0 || colours.size() > 16) throw new AssertionError("Corners/palette");
        return colours.size();
    }

    private static void gallery(Path path, List<String> ids, Map<String, DraftUnitRenderer.Unit> units,
                                int columns, String title) throws Exception {
        int rows = (ids.size() + columns - 1) / columns;
        BufferedImage sheet = new BufferedImage(32 + columns * 264, 90 + rows * 418, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x172D30)); g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setColor(new Color(0xF4E9CE)); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        g.drawString(title, 28, 34);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.drawString("JAVA2D CODE / REVIEW ONLY / KNIGHTS UNCHANGED / POLAND PALETTE", 28, 59);
        for (int i = 0; i < ids.size(); i++) {
            var unit = units.get(ids.get(i));
            int x = 28 + (i % columns) * 264, y = 82 + (i / columns) * 418;
            g.setColor(new Color(0x0E1D20)); g.fillRect(x + 4, y + 6, 240, 360);
            g.drawImage(enlarge(DraftUnitRenderer.render(unit), 3), x, y, null);
            g.setColor(new Color(0xF4E9CE)); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            g.drawString((i == 0 ? "REFERENCE / " : "DRAFT / ") + unit.unitClass(), x, y + 382);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            g.drawString(unit.points() + " points / " + unit.id(), x, y + 401);
        }
        g.dispose(); ImageIO.write(sheet, "png", path.toFile());
    }

    private static void smallGallery(Path path, Map<String, DraftUnitRenderer.Unit> units) throws Exception {
        BufferedImage sheet = new BufferedImage(700, 238, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x172D30)); g.fillRect(0, 0, 700, 238);
        g.setColor(new Color(0xF4E9CE)); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        g.drawString("HAND SIZE / 104 x 156 / REVIEW ONLY", 22, 28);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        for (int i = 0; i < IDS.size(); i++) {
            int x = 22 + i * 137;
            var unit = units.get(IDS.get(i));
            g.drawImage(DraftUnitRenderer.render(unit), x, 47, 104, 156, null);
            g.setColor(new Color(0xF4E9CE)); g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            g.drawString(unit.unitClass() + (i == 0 ? " (ref)" : ""), x, 223);
        }
        g.dispose(); ImageIO.write(sheet, "png", path.toFile());
    }

    private static String sha256(Path file) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }

    private static String json(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
