import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;

/** Offline compositor for the sixteen-second Golden fields menu loop, authored at 25 fps. */
public final class RenderBattlefieldLoop {
    private static final int WIDTH = 1280, HEIGHT = 720, FRAMES = 400;
    private final BufferedImage plate, flag, cloud;

    private RenderBattlefieldLoop() throws Exception {
        Path assets = Path.of("src/main/resources/assets");
        plate = ImageIO.read(assets.resolve("battlefield-animation/clean-plate.png").toFile());
        flag = ImageIO.read(assets.resolve("battlefield-animation/flag.png").toFile());
        cloud = ImageIO.read(assets.resolve("valley-animation/cloud.png").toFile());
    }

    /** Writes 400 PNG frames into the supplied directory, for offline GIF/MP4 encoding. */
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        var renderer = new RenderBattlefieldLoop();
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
            driftingCloud(g, cycle, 525, 70, 750, 185, .14f);
            driftingCloud(g, cycle + 1.5, 840, 280, 780, 190, .12f);
            rotor(g, 1380, 521, 78, cycle + .5);
            rotor(g, 1103, 525, 33, -cycle + .7);
            wavingFlag(g, 218, 48, 620, 510, cycle, 0);
            wavingFlag(g, 27, 230, 52, 140, cycle, .7);
            wavingFlag(g, 77, 270, 38, 95, cycle, 1.4);
            wavingFlag(g, 143, 313, 68, 160, cycle, 2.1);
            wavingFlag(g, 264, 362, 60, 120, cycle, 1.2);
            wavingFlag(g, 346, 392, 55, 115, cycle, 2.8);
            wavingFlag(g, 772, 552, 35, 60, cycle, 1.6);
            wavingFlag(g, 1225, 614, 29, 48, cycle, 2.4);
            wavingFlag(g, 1548, 514, 85, 202, cycle, .9);
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
