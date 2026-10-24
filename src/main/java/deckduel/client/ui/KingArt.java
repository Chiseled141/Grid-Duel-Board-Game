package deckduel.client.ui;

import deckduel.core.card.King;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javafx.scene.image.Image;

/** Original King paintings, cached once and decoded away from both UI threads. No cropping. */
final class KingArt {
    private static final Logger LOG = Logger.getLogger(KingArt.class.getName());
    private static final Map<King, Image> FX = new ConcurrentHashMap<>();
    private static final Map<King, BufferedImage> SWING = new ConcurrentHashMap<>();
    private static final CompletableFuture<Void> LOADING = CompletableFuture.runAsync(() -> {
        for (King king : King.values()) {
            try {
                var path = KingArt.class.getResource(path(king));
                if (path == null) throw new IllegalStateException("Missing King painting: " + king.id());
                SWING.put(king, ImageIO.read(path));
            } catch (Exception e) { LOG.log(Level.WARNING, "King art unavailable", e); }
        }
    });
    private KingArt() { }
    static void preload() { /* Class initialization starts the background decode. */ }
    static String path(King king) { return "/assets/cards/kings/" + king.id() + ".png"; }
    static Image image(King king) {
        return FX.computeIfAbsent(king, k -> new Image(KingArt.class.getResource(path(k)).toExternalForm(), true));
    }
    static void paint(Graphics2D g, King king, Rectangle at) {
        BufferedImage image = SWING.get(king);
        if (image == null) return;
        double scale = Math.min(at.width / (double) image.getWidth(), at.height / (double) image.getHeight());
        int w = (int) (image.getWidth() * scale), h = (int) (image.getHeight() * scale);
        g.drawImage(image, at.x + (at.width - w) / 2, at.y + (at.height - h) / 2, w, h, null);
    }
}
