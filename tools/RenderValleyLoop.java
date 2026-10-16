import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;

/** Offline artwork compositor: renders a smooth sixteen-second loop for GIF/video encoding. */
public final class RenderValleyLoop {
    private static final int WIDTH = 1280, HEIGHT = 800, FPS = 25, SECONDS = 16;
    private static final int FRAMES = FPS * SECONDS;
    private final BufferedImage plate, upper, lower, cloud;

    private RenderValleyLoop(Path assets) throws Exception {
        plate = ImageIO.read(assets.resolve("clean-plate.png").toFile());
        var branches = ImageIO.read(assets.resolve("branches.png").toFile());
        int middle = branches.getHeight() / 2;
        upper = branches.getSubimage(0, 0, branches.getWidth(), middle);
        lower = branches.getSubimage(0, middle, branches.getWidth(), branches.getHeight() - middle);
        cloud = ImageIO.read(assets.resolve("cloud.png").toFile());
    }

    /** Renders frames to the supplied directory; run from the repository root with Java 17. */
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        var renderer = new RenderValleyLoop(Path.of("src/main/resources/assets/valley-animation"));
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
            g.drawImage(plate, 0, 0, WIDTH, HEIGHT, null);
            cloud(g, cycle, 530, 85, 750, 145, .18f, .3);
            cloud(g, cycle, 285, 315, 880, 165, .26f, 1.7);
            cloud(g, cycle, 225, 525, 1030, 225, .32f, 3.2);
            water(g, cycle, 1180, 330, 1171, 415);
            water(g, cycle, 348, 402, 359, 473);
            branch(g, upper, -18, -22, 565, 245, 0, 155, cycle, .014);
            branch(g, lower, 780, 610, 565, 245, 565, 125, cycle + 1, -.018);
            leaves(g, cycle);
        } finally { g.dispose(); }
        return result;
    }

    private void cloud(Graphics2D parent, double cycle, int x, int y, int w, int h, float opacity, double phase) {
        var g = (Graphics2D) parent.create();
        try {
            g.setComposite(AlphaComposite.SrcOver.derive(opacity));
            int drift = (int) Math.round(90 * Math.sin(cycle + phase));
            int rise = (int) Math.round(5 * Math.cos(cycle + phase));
            g.drawImage(cloud, x + drift, y + rise, w, h, null);
        } finally { g.dispose(); }
    }

    private void branch(Graphics2D parent, BufferedImage branch, int x, int y, int w, int h,
                        int pivotX, int pivotY, double cycle, double amplitude) {
        var g = (Graphics2D) parent.create();
        try {
            g.translate(x, y);
            // Each isolated branch rotates as one rigid layer; landscape pixels never deform.
            g.rotate(amplitude * (Math.sin(cycle) + .25 * Math.sin(2 * cycle)), pivotX, pivotY);
            g.drawImage(branch, 0, 0, w, h, null);
        } finally { g.dispose(); }
    }

    private void water(Graphics2D parent, double cycle, int x1, int y1, int x2, int y2) {
        var g = (Graphics2D) parent.create();
        try {
            var path = new Line2D.Double(x1, y1, x2, y2);
            g.clip(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(path));
            g.setColor(new Color(244, 250, 243, 115));
            double offset = (cycle / (Math.PI * 2) * 240) % 20;
            for (double y = y1 - 20 + offset; y < y2 + 20; y += 20)
                g.fill(new Rectangle2D.Double(Math.min(x1, x2) - 5, y, 30, 5));
        } finally { g.dispose(); }
    }

    private void leaves(Graphics2D parent, double cycle) {
        for (int i = 0; i < 18; i++) {
            double progress = (cycle / (Math.PI * 2) + i * .137) % 1;
            double x = -100 + progress * (WIDTH + 200);
            double y = 65 + (i * 97 % 620) + 36 * Math.sin(progress * Math.PI * 2 + i);
            var g = (Graphics2D) parent.create();
            try {
                g.translate(x, y);
                g.rotate(cycle * (i % 2 == 0 ? 2 : -2) + i);
                g.scale(.4 + .5 * Math.abs(Math.cos(cycle * 2 + i)), .7);
                var leaf = new Path2D.Double();
                leaf.moveTo(-8, 0); leaf.lineTo(-4, -3); leaf.lineTo(-5, -7);
                leaf.lineTo(0, -4); leaf.lineTo(4, -8); leaf.lineTo(5, -3);
                leaf.lineTo(9, 0); leaf.lineTo(3, 3); leaf.lineTo(0, 7); leaf.lineTo(-2, 3); leaf.closePath();
                g.setColor(i % 3 == 0 ? new Color(192, 98, 33, 215) : new Color(215, 157, 54, 215));
                g.fill(leaf);
                g.setColor(new Color(113, 66, 30, 165));
                g.drawLine(-5, 0, 6, 0);
            } finally { g.dispose(); }
        }
    }
}
