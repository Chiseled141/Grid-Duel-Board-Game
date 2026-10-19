import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

/** Exports researched map geometry on one equal-area grid, with disjoint transparent layers. */
public class ExportAtlasLayers {
    private static final int W = 3840, H = 3840, PAD = 100;
    private static final double R = 6371.0088, LAT = Math.toRadians(49), LON = Math.toRadians(20);
    private static final String[] NAMES = {"poland", "bohemia", "austria", "styria", "hungary", "croatia", "transylvania"};
    private static final int[] COLORS = {0xC49C98, 0x9B859D, 0xBC8774, 0x8B9C77, 0xC2A46F, 0x83978C, 0x819FA5};
    private static final double[][] LABELS = {{20,52},{15.3,50.2},{14.8,48.35},{14.9,47.25},{19.7,47.1},{16,45.1},{24.4,46.35}};
    private final Path root;
    private final LinkedHashMap<String, Area> areas = new LinkedHashMap<>();
    private final Map<String, Path2D> outlines = new LinkedHashMap<>();
    private final AffineTransform toPixel;
    private final double scale;
    private final int[] owner = new int[W * H];

    private ExportAtlasLayers(Path root) throws Exception {
        this.root = root;
        Map<String, Area> base = load(root.resolve("sources/base-polygons.tsv"));
        Map<String, Area> cuts = load(root.resolve("sources/regional-cuts.tsv"));
        areas.put("poland", base.get("poland"));
        areas.put("bohemia", base.get("bohemia"));
        for (String name : List.of("austria", "styria")) {
            Area region = new Area(base.get("habsburg-parent"));
            region.intersect(cuts.get(name));
            areas.put(name, region);
        }
        Area hungary = new Area(base.get("hungary-parent"));
        for (String name : List.of("croatia", "transylvania")) {
            Area region = new Area(hungary);
            region.intersect(cuts.get(name));
            areas.put(name, region);
            hungary.subtract(region);
        }
        areas.put("hungary", hungary);
        // Detailed modern land geometry supplies physical coastlines, not political frontiers.
        Area land = load(root.resolve("sources/land-projected.tsv"), false).get("land");
        areas.values().forEach(area -> area.intersect(land));
        // The base dataset can have tiny overlaps. Give smaller named layers ownership first.
        Area occupied = new Area();
        for (String name : List.of("austria", "styria", "croatia", "transylvania", "bohemia", "poland", "hungary")) {
            Area region = areas.get(name);
            region.subtract(occupied);
            occupied.add(region);
        }
        Rectangle2D bounds = occupied.getBounds2D();
        scale = Math.min((W - 2.0 * PAD) / bounds.getWidth(), (H - 2.0 * PAD) / bounds.getHeight());
        toPixel = new AffineTransform();
        toPixel.translate((W - bounds.getWidth() * scale) / 2, (H - bounds.getHeight() * scale) / 2);
        toPixel.scale(scale, scale);
        toPixel.translate(-bounds.getX(), -bounds.getY());
        areas.replaceAll((name, area) -> new Area(toPixel.createTransformedShape(area)));
        BufferedImage ownership = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D raster = ownership.createGraphics();
        raster.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        for (int i = 0; i < NAMES.length; i++) {
            raster.setColor(new Color(i + 1));
            raster.fill(areas.get(NAMES[i]));
        }
        raster.dispose();
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) owner[y * W + x] = (ownership.getRGB(x, y) & 0xFFFFFF) - 1;
        Files.createDirectories(root.resolve("layers"));
        Files.createDirectories(root.resolve("cropped"));
        // Raster ownership is the final authority: traced integer edges cannot contain
        // zero-width boolean-operation slivers, and clicks match the PNGs pixel for pixel.
        for (int i = 0; i < NAMES.length; i++) outlines.put(NAMES[i], pixelContours(i));
        writeSvg();
    }

    private Path2D pixelContours(int region) {
        Map<Integer, ArrayDeque<Integer>> edges = new LinkedHashMap<>();
        int stride = W + 1;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (owner[y * W + x] == region) {
            int a = y * stride + x, b = a + 1, d = a + stride, c = d + 1;
            if (y == 0 || owner[(y - 1) * W + x] != region) edge(edges, a, b);
            if (x == W - 1 || owner[y * W + x + 1] != region) edge(edges, b, c);
            if (y == H - 1 || owner[(y + 1) * W + x] != region) edge(edges, c, d);
            if (x == 0 || owner[y * W + x - 1] != region) edge(edges, d, a);
        }
        Path2D path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        while (!edges.isEmpty()) {
            int start = edges.keySet().iterator().next(), at = start;
            List<Integer> points = new ArrayList<>();
            do {
                points.add(at);
                ArrayDeque<Integer> outgoing = edges.get(at);
                if (outgoing == null) throw new IllegalStateException("Open raster boundary");
                int next = outgoing.removeFirst();
                if (outgoing.isEmpty()) edges.remove(at);
                at = next;
            } while (at != start);
            boolean first = true;
            for (int i = 0; i < points.size(); i++) {
                int previous = points.get(Math.floorMod(i - 1, points.size()));
                int current = points.get(i), next = points.get((i + 1) % points.size());
                int ax = current % stride - previous % stride, ay = current / stride - previous / stride;
                int bx = next % stride - current % stride, by = next / stride - current / stride;
                if (ax * by == ay * bx) continue;
                if (first) { path.moveTo(current % stride, current / stride); first = false; }
                else path.lineTo(current % stride, current / stride);
            }
            path.closePath();
        }
        return path;
    }

    private static void edge(Map<Integer, ArrayDeque<Integer>> edges, int start, int end) {
        edges.computeIfAbsent(start, key -> new ArrayDeque<>()).add(end);
    }

    private static Map<String, Area> load(Path path) throws Exception {
        return load(path, true);
    }

    private static Map<String, Area> load(Path path, boolean projected) throws Exception {
        Map<String, Area> result = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            String[] fields = line.split("\t");
            Path2D polygon = new Path2D.Double(Path2D.WIND_EVEN_ODD);
            for (String ring : fields[1].split("\\|")) {
                boolean first = true;
                for (String pair : ring.split(";")) {
                    String[] xy = pair.split(",");
                    double x = Double.parseDouble(xy[0]), y = Double.parseDouble(xy[1]);
                    Point2D p = projected ? project(x, y) : new Point2D.Double(x, y);
                    if (first) { polygon.moveTo(p.getX(), p.getY()); first = false; }
                    else polygon.lineTo(p.getX(), p.getY());
                }
                polygon.closePath();
            }
            result.computeIfAbsent(fields[0], key -> new Area()).add(new Area(polygon));
        }
        return result;
    }


    /** Spherical Lambert azimuthal equal-area, centred on 20 E, 49 N; coordinates in km. */
    private static Point2D project(double longitude, double latitude) {
        double lon = Math.toRadians(longitude) - LON, lat = Math.toRadians(latitude);
        double k = Math.sqrt(2 / (1 + Math.sin(LAT) * Math.sin(lat) + Math.cos(LAT) * Math.cos(lat) * Math.cos(lon)));
        return new Point2D.Double(R * k * Math.cos(lat) * Math.sin(lon),
                -R * k * (Math.cos(LAT) * Math.sin(lat) - Math.sin(LAT) * Math.cos(lat) * Math.cos(lon)));
    }

    private static double[] inverse(double x, double y) {
        y = -y;
        double rho = Math.hypot(x, y);
        if (rho < 1e-9) return new double[]{20, 49};
        double c = 2 * Math.asin(Math.min(1, rho / (2 * R))), s = Math.sin(c), co = Math.cos(c);
        double lat = Math.asin(co * Math.sin(LAT) + y * s * Math.cos(LAT) / rho);
        double lon = LON + Math.atan2(x * s, rho * Math.cos(LAT) * co - y * Math.sin(LAT) * s);
        return new double[]{Math.toDegrees(lon), Math.toDegrees(lat)};
    }

    private BufferedImage relief(Path path) throws Exception {
        return relief(path, W, H, 0, 0);
    }

    private BufferedImage relief(Path path, int width, int height, double offsetX, double offsetY) throws Exception {
        BufferedImage earth = ImageIO.read(path.toFile());
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        AffineTransform inv = toPixel.createInverse();
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            Point2D p = inv.transform(new Point2D.Double(x + offsetX + .5, y + offsetY + .5), null);
            double[] ll = inverse(p.getX(), p.getY());
            int sx = (int) ((ll[0] + 180) / 360 * earth.getWidth());
            int sy = (int) ((90 - ll[1]) / 180 * earth.getHeight());
            out.setRGB(x, y, earth.getRGB(Math.max(0, Math.min(earth.getWidth() - 1, sx)), Math.max(0, Math.min(earth.getHeight() - 1, sy))));
        }
        return out;
    }

    private void guide() throws Exception {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setColor(new Color(0xDDD5C2)); g.fillRect(0, 0, W, H);
        for (int i = 0; i < NAMES.length; i++) {
            g.setColor(new Color(COLORS[i])); g.fill(outlines.get(NAMES[i]));
            g.setColor(new Color(0x4B4336)); g.setStroke(new BasicStroke(4)); g.draw(outlines.get(NAMES[i]));
        }
        labels(g);
        g.dispose(); ImageIO.write(im, "png", root.resolve("boundary-guide.png").toFile());
    }

    private void labels(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        for (int i = 0; i < NAMES.length; i++) {
            Point2D p = toPixel.transform(project(LABELS[i][0], LABELS[i][1]), null);
            String name = NAMES[i].toUpperCase(Locale.ROOT);
            g.setFont(new Font(Font.SERIF, Font.BOLD, i == 2 || i == 3 ? 30 : 42));
            int x = (int) p.getX() - g.getFontMetrics().stringWidth(name) / 2, y = (int) p.getY();
            g.setColor(new Color(0xF9F0D8));
            for (int dx = -2; dx <= 2; dx++) for (int dy = -2; dy <= 2; dy++) g.drawString(name, x + dx, y + dy);
            g.setColor(new Color(0x352F23)); g.drawString(name, x, y);
        }
    }

    private void export(BufferedImage terrain, boolean context) throws Exception {
        BufferedImage canvas = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D cg = canvas.createGraphics();
        cg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        if (context) cg.drawImage(terrain, -1600, -400, 8192, 4608, null);
        else cg.drawImage(terrain, 0, 0, W, H, null);
        cg.dispose();
        BufferedImage combined = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        BufferedImage identities = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        StringBuilder manifest = new StringBuilder("{\n  \"year\":1444,\n  \"precision\":\"historical reconstruction, not surveyed\",\n  \"projection\":\"spherical LAEA lat0=49 lon0=20 R=6371.0088km\",\n  \"canvas\":[3840,3840],\n  \"pixelsPerKm\":" + scale + ",\n  \"layers\":[\n");
        long total = 0;
        for (int i = 0; i < NAMES.length; i++) {
            BufferedImage layer = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
            int minX = W, minY = H, maxX = 0, maxY = 0; long count = 0;
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (owner[y * W + x] == i) {
                int rgb = canvas.getRGB(x, y) | 0xFF000000;
                layer.setRGB(x, y, rgb); combined.setRGB(x, y, rgb); identities.setRGB(x, y, 0xFF000000 | i + 1);
                minX = Math.min(minX, x); minY = Math.min(minY, y); maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); count++;
            }
            if (count == 0 || minX == 0 || minY == 0 || maxX == W - 1 || maxY == H - 1)
                throw new IllegalStateException("Empty or clipped layer: " + NAMES[i]);
            ImageIO.write(layer, "png", root.resolve("layers/" + NAMES[i] + ".png").toFile());
            int width = maxX - minX + 1, height = maxY - minY + 1;
            ImageIO.write(layer.getSubimage(minX, minY, width, height), "png", root.resolve("cropped/" + NAMES[i] + ".png").toFile());
            if (i > 0) manifest.append(",\n");
            manifest.append("    {\"name\":\"").append(NAMES[i]).append("\",\"offset\":[").append(minX).append(',').append(minY)
                    .append("],\"size\":[").append(width).append(',').append(height).append("],\"opaquePixels\":").append(count).append('}');
            total += count;
            System.out.println(NAMES[i] + " " + width + "x" + height + " offset=" + minX + "," + minY + " pixels=" + count);
        }
        Files.writeString(root.resolve("manifest.json"), manifest.append("\n  ]\n}\n").toString());
        ImageIO.write(combined, "png", root.resolve("assembled.png").toFile());
        ImageIO.write(identities, "png", root.resolve("ownership-mask.png").toFile());
        Graphics2D g = combined.createGraphics();
        g.setStroke(new BasicStroke(3)); g.setColor(new Color(0xEFE4CE));
        for (Shape outline : outlines.values()) g.draw(outline);
        labels(g); g.dispose();
        BufferedImage preview = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        g = preview.createGraphics(); g.setColor(new Color(0x292B25)); g.fillRect(0, 0, W, H); g.drawImage(combined, 0, 0, null); g.dispose();
        ImageIO.write(preview, "png", root.resolve("preview.png").toFile());
        Files.writeString(root.resolve("verification.txt"), "7 nonempty layers; all inside canvas; one owner per occupied pixel; transparent background.\n"
                + "Total occupied pixels: " + total + ". Cropped offsets are exact.\nHistorical correctness is limited by the cited source data and regional reconstruction.\n");
    }

    private void writeSvg() throws Exception {
        StringBuilder svg = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"3840\" height=\"3840\" viewBox=\"0 0 3840 3840\">\n<title>Circa-1444 reconstruction: seven game regions</title>\n");
        double[] c = new double[6];
        for (int i = 0; i < NAMES.length; i++) {
            Point2D label = toPixel.transform(project(LABELS[i][0], LABELS[i][1]), null);
            svg.append("<path id=\"").append(NAMES[i]).append("\" data-label-x=\"").append(label.getX())
                    .append("\" data-label-y=\"").append(label.getY()).append("\" fill=\"#")
                    .append(Integer.toHexString(COLORS[i])).append("\" fill-rule=\"evenodd\" d=\"");
            PathIterator path = outlines.get(NAMES[i]).getPathIterator(null, .25);
            while (!path.isDone()) {
                int type = path.currentSegment(c);
                if (type == PathIterator.SEG_CLOSE) svg.append("Z ");
                else svg.append(type == PathIterator.SEG_MOVETO ? "M " : "L ").append(c[0]).append(' ').append(c[1]).append(' ');
                path.next();
            }
            svg.append("\"/>\n");
        }
        Files.writeString(root.resolve("boundaries.svg"), svg.append("</svg>\n").toString());
    }

    /** Run with root directory, Natural Earth TIFF, and optionally an illustrated terrain image. */
    public static void main(String[] args) throws Exception {
        ExportAtlasLayers exporter = new ExportAtlasLayers(Path.of(args[0]));
        exporter.guide();
        if (args.length > 2 && args[2].equals("--context")) {
            ImageIO.write(exporter.relief(Path.of(args[1]), 8192, 4608, -1600, -400), "png",
                    exporter.root.resolve("context-reference.png").toFile());
            return;
        }
        if (args.length > 1) {
            BufferedImage terrain = args.length > 2 ? ImageIO.read(Path.of(args[2]).toFile()) : exporter.relief(Path.of(args[1]));
            if (args.length == 2) ImageIO.write(terrain, "png", exporter.root.resolve("relief-reference.png").toFile());
            exporter.export(terrain, args.length > 3 && args[3].equals("--wide"));
        }
    }
}
