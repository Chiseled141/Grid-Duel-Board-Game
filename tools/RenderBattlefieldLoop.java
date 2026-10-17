import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;

/** Offline compositor for the sixteen-second Golden fields menu loop, authored at 25 fps. */
public final class RenderBattlefieldLoop {
    private static final int WIDTH = 1280, HEIGHT = 720, FRAMES = 400;
    private final BufferedImage plate, flag, cloud;
    private final java.util.List<Flower> flowers = new java.util.ArrayList<>();
    private int originalFlowerCount;
    private final java.util.List<Grass> grasses = new java.util.ArrayList<>();
    private final BufferedImage village;

    private RenderBattlefieldLoop() throws Exception {
        Path assets = Path.of("src/main/resources/assets");
        plate = ImageIO.read(assets.resolve("battlefield-animation/clean-plate.png").toFile());
        extractFlowers();
        extractGrass();
        village = croppedVillage(ImageIO.read(assets.resolve("battlefield-animation/distant-village.png").toFile()));
        flag = ImageIO.read(assets.resolve("battlefield-animation/flag.png").toFile());
        cloud = ImageIO.read(assets.resolve("battlefield-animation/cloud.png").toFile());
    }

    /** Writes 400 frames; an optional second directory receives individual source cutouts for inspection. */
    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("--verify")) {
            new RenderBattlefieldLoop().exportDetails(Path.of(args[1]));
            return;
        }
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        var renderer = new RenderBattlefieldLoop();
        if (args.length > 1) renderer.exportDetails(Path.of(args[1]));
        for (int frame = 0; frame < FRAMES; frame++)
            ImageIO.write(renderer.frame(frame), "png", output.resolve(String.format("%03d.png", frame)).toFile());
    }

    private BufferedImage frame(int frame) {
        double cycle = frame * Math.PI * 2 / FRAMES;
        var result = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        var g = result.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.scale(WIDTH / 1672.0, HEIGHT / 941.0);
            g.drawImage(plate, 0, 0, 1672, 941, null);
            distantVillage(g);
            swayingGrass(g, cycle);
            swayingFlowers(g, cycle);
            driftingCloud(g, cycle, 525, 70, 750, 185, .14f);
            driftingCloud(g, cycle + 1.5, 840, 280, 780, 190, .12f);
            rotor(g, 1380, 521, 78, cycle + .5);
            rotor(g, 1103, 525, 33, -cycle + .7);
            wavingFlag(g, 218, 48, 620, 510, cycle, 0);
            driftingSeeds(g, cycle);
            windWisps(g, cycle);
        } finally { g.dispose(); }
        return result;
    }

    private void driftingCloud(Graphics2D parent, double cycle, int x, int y, int w, int h, float opacity) {
        var g = (Graphics2D) parent.create();
        try {
            g.setComposite(AlphaComposite.SrcOver.derive(opacity));
            g.drawImage(cloud, new AffineTransform(w / (double) cloud.getWidth(), 0, 0,
                    h / (double) cloud.getHeight(), x + 70 * Math.sin(cycle), y), null);
        } finally { g.dispose(); }
    }

    private void extractFlowers() {
        // Coordinates refer to existing flower heads; every cutout pixel comes from this plate.
        int[][] heads = {{180, 657, 13, 10}, {227, 676, 13, 10}, {260, 699, 14, 11},
                {111, 763, 15, 10}, {205, 796, 28, 17}, {21, 794, 16, 13},
                {78, 782, 15, 14}, {106, 809, 15, 14}, {280, 813, 16, 12},
                {318, 834, 18, 15}, {363, 845, 15, 13},
                {138, 811, 14, 11}, {341, 815, 13, 10},
                {366, 809, 12, 10}, {460, 809, 13, 10},
                {687, 839, 13, 10}, {734, 832, 12, 10},
                {838, 909, 19, 14}, {914, 908, 15, 13},
                {970, 917, 14, 12}, {999, 908, 16, 12},
                {1028, 899, 12, 11}, {1050, 915, 12, 11},
                {449, 923, 16, 12}, {574, 922, 14, 11},
                {808, 936, 11, 4}, {427, 783, 8, 6},
                {500, 771, 8, 6}, {543, 744, 8, 6},
                {586, 756, 7, 6}, {617, 785, 8, 6},
                {767, 800, 8, 6}, {802, 823, 8, 6},
                {852, 843, 8, 6}, {889, 822, 8, 6},
                {945, 799, 8, 6}, {981, 850, 8, 6},
                {1081, 830, 8, 6}, {1117, 852, 8, 6},
                {1172, 878, 8, 6}, {1214, 829, 8, 6},
                {1274, 779, 7, 5}, {1320, 767, 7, 5},
                {1370, 800, 7, 5}, {1465, 851, 8, 6},
                {1511, 822, 8, 6}, {1588, 810, 8, 6}};
        BufferedImage source = new BufferedImage(plate.getWidth(), plate.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D copy = source.createGraphics();
        copy.drawImage(plate, 0, 0, null);
        copy.dispose();
        for (int index = 0; index < heads.length; index++) {
            int[] head = heads[index];
            int cx = head[0], cy = head[1], rx = head[2], ry = head[3];
            BufferedImage cutout = new BufferedImage(rx * 2 + 1, ry * 2 + 1, BufferedImage.TYPE_INT_ARGB);
            for (int y = -ry; y <= ry; y++) for (int x = -rx; x <= rx; x++) {
                if (x * x / (double) (rx * rx) + y * y / (double) (ry * ry) > 1) continue;
                int rgb = source.getRGB(cx + x, cy + y);
                int red = (rgb >> 16) & 255, green = (rgb >> 8) & 255, blue = rgb & 255;
                boolean petal = red > 145 && green > 110 && green > red * .62
                        && (blue > green * .50 || (red > 180 && green > 135));
                if (!petal) continue;
                cutout.setRGB(x + rx, y + ry, rgb);
                // Reveal nearby painted foliage, rather than leaving a stationary duplicate head.
                plate.setRGB(cx + x, cy + y, nearbyFoliage(source, cx + x, cy + y, ry));
            }
            flowers.add(new Flower(cutout, cx - rx, cy - ry, rx, ry + 34, index * .35, 1));
        }
        originalFlowerCount = flowers.size();
        extractRemainingFlowers(source, heads);
    }

    private void extractRemainingFlowers(BufferedImage source, int[][] existingHeads) {
        int width = source.getWidth(), height = source.getHeight();
        var candidates = new java.util.BitSet(width * height);
        for (int y = 640; y < height; y++) for (int x = 0; x < width; x++) {
            if (!flowerGround(x, y)) continue;
            boolean reserved = false;
            for (int[] head : existingHeads) {
                double dx = (x - head[0]) / (double) (head[2] + 4);
                double dy = (y - head[1]) / (double) (head[3] + 4);
                if (dx * dx + dy * dy <= 1) { reserved = true; break; }
            }
            if (reserved) continue;
            int rgb = source.getRGB(x, y), red = (rgb >> 16) & 255, green = (rgb >> 8) & 255, blue = rgb & 255;
            boolean cream = red > 185 && green > 155 && blue > green * .64 && red < green * 1.32;
            boolean gold = red > 215 && green > 145 && green < red * .91 && blue < green * .61;
            if (cream || gold) candidates.set(y * width + x);
        }
        var groups = new java.util.ArrayList<Bloom>();
        for (int seed = candidates.nextSetBit(0); seed >= 0; seed = candidates.nextSetBit(0)) {
            var pixels = new java.util.ArrayList<Point>(); var queue = new java.util.ArrayDeque<Integer>();
            candidates.clear(seed); queue.add(seed);
            while (!queue.isEmpty()) {
                int pixel = queue.removeFirst(), x = pixel % width, y = pixel / width;
                pixels.add(new Point(x, y));
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx, ny = y + dy;
                    if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                    int neighbor = ny * width + nx;
                    if (candidates.get(neighbor)) { candidates.clear(neighbor); queue.add(neighbor); }
                }
            }
            Rectangle bounds = bounds(pixels);
            if (pixels.size() >= 2 && pixels.size() <= 240 && bounds.width <= 34 && bounds.height <= 26
                    && bounds.width <= bounds.height * 3 && bounds.height <= bounds.width * 3) groups.add(new Bloom(pixels, bounds));
        }
        // Adjacent petal fragments share a root, so a daisy is never animated petal by petal.
        for (int i = 0; i < groups.size(); i++) {
            Rectangle box = new Rectangle(groups.get(i).bounds()); box.grow(3, 3);
            for (int j = i + 1; j < groups.size(); j++) {
                Rectangle other = groups.get(j).bounds(); Rectangle merged = groups.get(i).bounds().union(other);
                if (box.intersects(other) && merged.width <= 34 && merged.height <= 26) {
                    groups.get(i).pixels().addAll(groups.remove(j--).pixels()); groups.get(i).bounds().setBounds(merged);
                    box = new Rectangle(merged); box.grow(3, 3);
                }
            }
        }
        BufferedImage before = copy(plate);
        int firstNew = flowers.size();
        for (var group : groups) {
            var pixels = group.pixels(); Rectangle box = group.bounds();
            if (pixels.size() < 3) continue;
            var cutout = new BufferedImage(box.width, box.height, BufferedImage.TYPE_INT_ARGB);
            for (Point point : pixels) {
                cutout.setRGB(point.x - box.x, point.y - box.y, source.getRGB(point.x, point.y));
                plate.setRGB(point.x, point.y, nearbyFoliage(source, point.x, point.y, Math.max(3, box.height / 2)));
            }
            int root = Math.max(7, Math.min(24, box.height * 2));
            double strength = Math.min(.65, .28 + box.height / 65.0);
            flowers.add(new Flower(cutout, box.x, box.y, box.width / 2, box.height + root,
                    box.x * .013 + box.y * .017, strength));
        }
        var restored = copy(plate); var g = restored.createGraphics();
        for (int i = firstNew; i < flowers.size(); i++) {
            Flower flower = flowers.get(i); g.drawImage(flower.image(), flower.x(), flower.y(), null);
        }
        g.dispose();
        if (!java.util.Arrays.equals(before.getRGB(0, 0, width, height, null, 0, width),
                restored.getRGB(0, 0, width, height, null, 0, width)))
            throw new IllegalStateException("New flower cutouts must exactly reconstruct their source");
    }

    private record Bloom(java.util.List<Point> pixels, Rectangle bounds) { }

    private static Rectangle bounds(java.util.List<Point> pixels) {
        Rectangle bounds = new Rectangle(pixels.get(0).x, pixels.get(0).y, 1, 1);
        for (Point point : pixels) bounds.add(new Rectangle(point.x, point.y, 1, 1));
        return bounds;
    }

    private static final Shape[] FLOWER_EXCLUSIONS = {
            new Rectangle(0,440,190,210), new Rectangle(200,580,175,160), new Rectangle(577,555,278,210),
            new Rectangle(865,675,135,82), new Rectangle(1140,790,250,125), new Rectangle(445,850,200,90),
            new Rectangle(130,670,70,48), new Rectangle(570,690,60,50), new Rectangle(940,680,115,52),
            new Rectangle(1100,700,95,50), new Rectangle(1200,720,175,48),
            new Rectangle(1340,485,80,100), new Rectangle(1080,500,50,75),
            new Rectangle(1030,600,140,80), new Rectangle(150,850,250,91),
            new Rectangle(1130,752,155,92), new Rectangle(1280,775,220,90),
            new Polygon(new int[] {0,780,850,0}, new int[] {430,685,760,590},4),
            new Polygon(new int[] {780,955,1480,1398,930,780}, new int[] {697,721,880,916,777,743},6),
            new Polygon(new int[] {1270,1672,1672,1470,1370,1270}, new int[] {630,648,840,790,753,726},6),
            new Polygon(new int[] {1360,1672,1672,1400}, new int[] {895,828,941,941},4)};

    private boolean flowerGround(int x, int y) {
        if (y < 490 + Math.min(x, 600) * .10) return false;
        for (Shape shape : FLOWER_EXCLUSIONS) if (shape.contains(x, y)) return false;
        return true;
    }

    private int nearbyFoliage(BufferedImage source, int x, int y, int radius) {
        for (int offset = radius + 5; offset < radius + 30; offset++) {
            int sy = Math.min(source.getHeight() - 1, y + offset);
            int rgb = source.getRGB(x, sy);
            int red = (rgb >> 16) & 255, green = (rgb >> 8) & 255, blue = rgb & 255;
            if (red < 150 && green > blue * 1.2) return rgb;
        }
        return source.getRGB(x, Math.min(source.getHeight() - 1, y + radius + 20));
    }

    private void swayingFlowers(Graphics2D parent, double cycle) {
        for (Flower flower : flowers) {
            Graphics2D g = (Graphics2D) parent.create();
            try {
                g.translate(flower.x(), flower.y());
                double angle = .20 * Math.sin(3 * cycle + flower.phase())
                        + .035 * Math.sin(5 * cycle + flower.phase());
                g.rotate(angle * flower.strength(), flower.pivotX(), flower.pivotY());
                g.drawImage(flower.image(), 0, 0, null);
            } finally { g.dispose(); }
        }
    }

    private record Flower(BufferedImage image, int x, int y, int pivotX, int pivotY, double phase, double strength) { }

    private record Grass(BufferedImage image, int x, int y, double amplitude, double phase) { }

    private void extractGrass() {
        // Each path traces ONE visible blade in the source: root, curve control and tip.
        // Paths only select pixels; no vector grass is drawn into the scene.
        double[][] blades = {{166,708,165,672,190,686,2.8}, {165,708,164,692,173,681,2.3},
                {142,698,145,677,155,684,2.7}, {159,706,154,686,144,688,2.6},
                {174,708,181,690,185,695,2.3}, {603,732,602,690,616,703,2.5},
                {590,732,589,712,580,714,2.5}, {591,733,594,713,600,718,2.2},
                {606,733,610,714,615,720,2.1}, {969,710,970,690,975,687,2.4},
                {974,715,985,698,991,699,2.6}, {980,714,993,704,1002,704,2.6},
                {960,707,957,695,949,699,2.5}, {1020,720,1024,697,1038,702,2.5},
                {1028,727,1025,710,1015,713,2.5}, {1037,727,1040,711,1048,716,2.4},
                {1123,724,1126,699,1141,713,2.7}, {1113,727,1110,714,1105,718,2.2},
                {1164,743,1168,726,1178,733,2.5}, {1162,743,1157,730,1151,734,2.4},
                {1225,756,1223,731,1210,734,2.7}, {1239,758,1240,735,1253,731,2.8},
                {1243,759,1255,730,1265,736,2.6}, {1233,758,1230,741,1224,744,2.4},
                {1277,758,1278,738,1288,736,2.6}, {1295,756,1300,739,1307,745,2.6},
                {1254,749,1258,732,1264,732,2.4}, {1288,759,1287,745,1281,744,2.2},
                {1337,746,1340,718,1347,722,2.8}, {1327,746,1324,730,1318,728,2.7},
                {1344,746,1352,732,1358,736,2.6}, {1350,749,1355,736,1364,740,2.3}};
        BufferedImage source = copy(plate);
        var claimed = new java.util.BitSet(source.getWidth() * source.getHeight());
        for (int index = 0; index < blades.length; index++) {
            double[] blade = blades[index];
            var path = new Path2D.Double(); path.moveTo(blade[0], blade[1]);
            path.quadTo(blade[2], blade[3], blade[4], blade[5]);
            Shape mask = new BasicStroke((float) blade[6], BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(path);
            Rectangle bounds = mask.getBounds(); bounds.grow(1, 1);
            var cutout = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < bounds.height; y++) for (int x = 0; x < bounds.width; x++) {
                int sx = bounds.x + x, sy = bounds.y + y, pixel = sy * source.getWidth() + sx;
                if (claimed.get(pixel) || !mask.contains(sx + .5, sy + .5) || sy >= blade[1] - 2) continue;
                int rgb = source.getRGB(sx, sy);
                int red = (rgb >> 16) & 255, green = (rgb >> 8) & 255, blue = rgb & 255;
                if (green < 15 || red < 15 || blue > green * .95 || red > green * 1.6) continue;
                claimed.set(pixel); cutout.setRGB(x, y, rgb);
                plate.setRGB(sx, sy, nearbyFoliage(source, sx, sy, bounds.height / 2));
            }
            grasses.add(new Grass(cutout, bounds.x, bounds.y, 2.5 + index % 4 * .4, index * .47));
        }
        // Source pixels are copied exactly, then restored once: no duplicated standing blades.
        BufferedImage restored = copy(plate);
        Graphics2D g = restored.createGraphics();
        for (Grass grass : grasses) g.drawImage(grass.image(), grass.x(), grass.y(), null);
        g.dispose();
        if (!java.util.Arrays.equals(source.getRGB(0, 0, source.getWidth(), source.getHeight(), null, 0, source.getWidth()),
                restored.getRGB(0, 0, restored.getWidth(), restored.getHeight(), null, 0, restored.getWidth())))
            throw new IllegalStateException("Grass cutouts must reconstruct their source exactly");
    }

    private static BufferedImage copy(BufferedImage source) {
        var image = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics(); g.drawImage(source, 0, 0, null); g.dispose(); return image;
    }

    private void swayingGrass(Graphics2D parent, double cycle) {
        for (Grass grass : grasses) {
            double wind = grass.amplitude() * (Math.sin(2 * cycle + grass.phase())
                    + .18 * Math.sin(3 * cycle + grass.phase()));
            int height = grass.image().getHeight();
            for (int row = 0; row < height; row += 2) {
                double free = 1 - row / (double) (height - 1);
                double offset = wind * free * free;
                Graphics2D g = (Graphics2D) parent.create();
                g.clip(new Rectangle2D.Double(grass.x() - 5, grass.y() + row,
                        grass.image().getWidth() + 10, Math.min(2, height - row)));
                g.drawImage(grass.image(), AffineTransform.getTranslateInstance(grass.x() + offset, grass.y()), null);
                g.dispose();
            }
        }
    }

    private static BufferedImage croppedVillage(BufferedImage source) {
        Rectangle bounds = null;
        for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) {
            if ((source.getRGB(x, y) >>> 24) == 0) continue;
            if (bounds == null) bounds = new Rectangle(x, y, 1, 1);
            else bounds.add(new Rectangle(x, y, 1, 1));
        }
        if (bounds == null) throw new IllegalArgumentException("Village asset is empty");
        var crop = source.getSubimage(bounds.x, bounds.y, bounds.width, bounds.height);
        int width = 102, height = (int) Math.round(width * bounds.height / (double) bounds.width);
        var reduced = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var g = reduced.createGraphics();
        g.drawImage(crop.getScaledInstance(width, height, Image.SCALE_AREA_AVERAGING), 0, 0, null);
        g.dispose();
        return reduced;
    }

    private void distantVillage(Graphics2D parent) {
        var g = (Graphics2D) parent.create();
        try {
            // Small, hazy roofs sit on the distant ridge between the original mills.
            g.setComposite(AlphaComposite.SrcOver.derive(.76f));
            int width = 102, height = (int) Math.round(width * village.getHeight() / (double) village.getWidth());
            g.drawImage(village, 1157, 557 - height, width, height, null);
        } finally { g.dispose(); }
    }

    private void exportDetails(Path directory) throws Exception {
        Files.createDirectories(directory);
        ImageIO.write(plate, "png", directory.resolve("prepared-plate.png").toFile());
        var flowerInventory = new StringBuilder("index,x,y,width,height,pixels,strength,original\n");
        for (int i = 0; i < flowers.size(); i++) {
            Flower flower = flowers.get(i); int count = 0;
            for (int y = 0; y < flower.image().getHeight(); y++) for (int x = 0; x < flower.image().getWidth(); x++)
                if ((flower.image().getRGB(x, y) >>> 24) != 0) count++;
            ImageIO.write(flower.image(), "png", directory.resolve(String.format("flower-%03d.png", i)).toFile());
            flowerInventory.append(i).append(',').append(flower.x()).append(',').append(flower.y()).append(',')
                    .append(flower.image().getWidth()).append(',').append(flower.image().getHeight()).append(',')
                    .append(count).append(',').append(flower.strength()).append(',').append(i < originalFlowerCount).append('\n');
        }
        Files.writeString(directory.resolve("flower-inventory.csv"), flowerInventory);
        var inventory = new StringBuilder("index,x,y,width,height,copiedPixels,tipAmplitude\n");
        for (int i = 0; i < grasses.size(); i++) {
            Grass grass = grasses.get(i);
            ImageIO.write(grass.image(), "png", directory.resolve(String.format("grass-%02d.png", i)).toFile());
            int count = 0;
            for (int y = 0; y < grass.image().getHeight(); y++) for (int x = 0; x < grass.image().getWidth(); x++)
                if ((grass.image().getRGB(x, y) >>> 24) != 0) count++;
            if (count < 4) throw new IllegalStateException("Empty grass blade: " + i);
            inventory.append(i).append(',').append(grass.x()).append(',').append(grass.y()).append(',')
                    .append(grass.image().getWidth()).append(',').append(grass.image().getHeight()).append(',')
                    .append(count).append(',').append(grass.amplitude()).append('\n');
        }
        Files.writeString(directory.resolve("grass-inventory.csv"), inventory);
    }

    private void wavingFlag(Graphics2D parent, int x, int y, int width, int height, double cycle, double phase) {
        double sx = width / (double) flag.getWidth(), sy = height / (double) flag.getHeight();
        int strips = width > 200 ? 40 : 12;
        for (int i = 0; i < strips; i++) {
            double u0 = i / (double) strips, u1 = (i + 1.0) / strips;
            double sourceX0 = u0 * flag.getWidth(), sourceX1 = u1 * flag.getWidth();
            double d0 = ripple(u0, cycle, phase) * height, d1 = ripple(u1, cycle, phase) * height;
            double slope = (d1 - d0) / (sourceX1 - sourceX0);
            var transform = new AffineTransform(sx, slope, 0, sy, x, y + d0 - slope * sourceX0);
            var clip = new Path2D.Double();
            clip.moveTo(x + u0 * width, y + d0);
            clip.lineTo(x + u1 * width + .4, y + d1);
            clip.lineTo(x + u1 * width + .4, y + height + d1);
            clip.lineTo(x + u0 * width, y + height + d0);
            clip.closePath();
            var g = (Graphics2D) parent.create();
            try {
                // Only the isolated cloth ripples; poles and landscape stay on the fixed clean plate.
                g.clip(clip);
                g.drawImage(flag, transform, null);
            } finally { g.dispose(); }
        }
    }

    private double ripple(double u, double cycle, double phase) {
        double free = Math.max(0, (u - .10) / .90);
        return free * (.035 * Math.sin(3 * cycle - 8 * u + phase)
                + .013 * Math.sin(5 * cycle - 5 * u + phase));
    }

    private void rotor(Graphics2D parent, int x, int y, int radius, double angle) {
        var g = (Graphics2D) parent.create();
        try {
            g.translate(x, y);
            g.scale(1, .94);
            g.rotate(angle);
            g.setStroke(new BasicStroke(radius / 55f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int arm = 0; arm < 4; arm++) {
                g.setColor(new Color(91, 72, 45));
                g.draw(new Line2D.Double(-6, 0, radius, 0));
                g.setColor(new Color(184, 151, 96, 160));
                g.fill(new Rectangle2D.Double(radius * .28, 0, radius * .72, radius * .15));
                g.setColor(new Color(105, 83, 49));
                g.draw(new Rectangle2D.Double(radius * .28, 0, radius * .72, radius * .15));
                g.draw(new Line2D.Double(radius * .28, radius * .075, radius, radius * .075));
                for (int rib = 0; rib <= 6; rib++) {
                    double bx = radius * (.28 + rib * .12);
                    g.draw(new Line2D.Double(bx, 0, bx, radius * .15));
                }
                g.rotate(Math.PI / 2);
            }
            g.setColor(new Color(80, 62, 39));
            g.fill(new Ellipse2D.Double(-3, -3, 6, 6));
        } finally { g.dispose(); }
    }

    private void driftingSeeds(Graphics2D g, double cycle) {
        g.setColor(new Color(255, 239, 171, 155));
        for (int i = 0; i < 28; i++) {
            double p = (cycle / (Math.PI * 2) + i * .173) % 1;
            double x = -60 + p * 1800;
            double y = 450 + (i * 61 % 450) - 22 * Math.sin(p * Math.PI * 2 + i);
            g.fill(new Ellipse2D.Double(x, y, 2.8, 1.4));
        }
    }

    private void windWisps(Graphics2D parent, double cycle) {
        var g = (Graphics2D) parent.create();
        try {
            g.setStroke(new BasicStroke(.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < 5; i++) {
                double progress = (cycle / (Math.PI * 2) + i * .219) % 1;
                double x = -140 + progress * 1950;
                double y = 560 + i * 61 + 10 * Math.sin(progress * Math.PI * 2 + i);
                int alpha = (int) (32 * Math.pow(Math.sin(progress * Math.PI), 2));
                g.setColor(new Color(255, 242, 202, alpha));
                var wisp = new Path2D.Double();
                wisp.moveTo(x, y);
                wisp.curveTo(x + 24, y - 5, x + 55, y + 4, x + 82, y - 3);
                g.draw(wisp);
            }
        } finally { g.dispose(); }
    }
}
