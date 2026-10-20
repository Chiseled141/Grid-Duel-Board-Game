import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Writes runtime copies of full-size source artwork: small enough to keep the jar and memory
 * modest while staying sharp on HiDPI screens. JPEG for opaque art; PNG keeps transparency.
 *
 * <pre>
 * java tools/ResizeArt.java source-folder runtime-folder 320 480 jpg
 * </pre>
 */
public final class ResizeArt {
    private static final float QUALITY = 0.88f;

    public static void main(String[] args) throws IOException {
        Path source = Path.of(args[0]), target = Path.of(args[1]);
        int width = Integer.parseInt(args[2]), height = Integer.parseInt(args[3]);
        boolean png = args[4].equals("png");
        Files.createDirectories(target);
        try (Stream<Path> files = Files.list(source)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".png")).sorted().toList()) {
                BufferedImage image = ImageIO.read(file.toFile());
                if (image == null) throw new IOException("Unreadable " + file);
                BufferedImage resized = resize(image, width, height, png);
                File out = target.resolve(file.getFileName().toString().replace(".png", png ? ".png" : ".jpg")).toFile();
                if (png) ImageIO.write(resized, "png", out);
                else writeJpeg(resized, out);
            }
        }
    }

    /** Halves with bilinear filtering until close, then finishes bicubic, avoiding aliasing. */
    private static BufferedImage resize(BufferedImage image, int width, int height, boolean alpha) {
        BufferedImage current = image;
        while (current.getWidth() / 2 >= width) current = draw(current, current.getWidth() / 2, current.getHeight() / 2,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR, alpha);
        return draw(current, width, height, RenderingHints.VALUE_INTERPOLATION_BICUBIC, alpha);
    }

    private static BufferedImage draw(BufferedImage image, int width, int height, Object interpolation, boolean alpha) {
        BufferedImage scaled = new BufferedImage(width, height, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(image, 0, 0, width, height, null);
        g.dispose();
        return scaled;
    }

    private static void writeJpeg(BufferedImage image, File file) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(QUALITY);
        try (ImageOutputStream out = ImageIO.createImageOutputStream(file)) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }
}
