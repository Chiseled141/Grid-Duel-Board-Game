package deckduel.client.ui;

import deckduel.core.card.CardDef;
import deckduel.core.card.CardKind;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.ref.SoftReference;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

/**
 * Painted Unit faces and the shared card back, read from {@code /assets/cards/<id>.png} in the
 * jar. The paintings print nation, class and points, so a Unit uses its painting only while the
 * catalog agrees with what is printed; any other card falls back to a drawn face.
 */
final class PaintedCardArt {
    static final String BACK = "card-back";
    private static final Logger LOG = Logger.getLogger(PaintedCardArt.class.getName());
    /** Points printed in every nation's painting of each class (art delivered 2026-10-22). */
    private static final Map<String, Integer> PRINTED = Map.of(
            "Assassin", 2, "Bowman", 3, "Guardman", 4, "Knight", 5, "Wizard", 6);
    private static final Map<String, Boolean> PRESENT = new ConcurrentHashMap<>();
    // Decoded paintings are about 3 MB each; soft references let the JVM reclaim them under memory
    // pressure, while the finished card sizes stay in CardFace's sprite cache.
    private static final Map<String, SoftReference<BufferedImage>> PAINTINGS = new ConcurrentHashMap<>();

    private PaintedCardArt() { }

    /** Whether {@code card} is a Unit whose painting exists and prints its catalog identity and points. */
    static boolean supports(CardDef card) {
        if (card == null || card.kind() != CardKind.UNIT) return false;
        String id = (card.nation() + "-" + card.unitClass()).toLowerCase(Locale.ROOT);
        return card.id().equals(id) && PRINTED.getOrDefault(card.unitClass(), -1) == card.points() && present(id);
    }

    private static boolean present(String id) {
        return PRESENT.computeIfAbsent(id, key -> PaintedCardArt.class.getResource(path(key)) != null);
    }

    /**
     * Decodes the paintings of {@code cards} and the back on a background thread. Decoding takes
     * about 30 ms per painting, which would otherwise stall the table's first frames.
     */
    static void preload(List<CardDef> cards) {
        Thread thread = new Thread(() -> {
            try {
                painting(BACK);
                for (CardDef card : cards) if (supports(card)) painting(card.id());
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "card art preload stopped; faces decode on first use", e);
            }
        }, "card-art-preload");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    /** Draws a painting centre-cropped to {@code r}'s shape with the card's rounded corners. */
    static void paint(Graphics2D g, String id, Rectangle r) {
        double scale = Math.max(1, g.getTransform().getScaleX());
        BufferedImage card = fit(painting(id), (int) Math.ceil(r.width * scale),
                (int) Math.ceil(r.height * scale), 9 * scale);
        g.drawImage(card, r.x, r.y, r.width, r.height, null);
    }

    private static BufferedImage fit(BufferedImage source, int width, int height, double arc) {
        // The paintings are slightly wider than the 2:3 table cards; trim the sides evenly.
        int cropW = Math.min(source.getWidth(), (int) Math.round(source.getHeight() * width / (double) height));
        int cropH = Math.min(source.getHeight(), (int) Math.round(source.getWidth() * height / (double) width));
        BufferedImage image = source.getSubimage((source.getWidth() - cropW) / 2, (source.getHeight() - cropH) / 2, cropW, cropH);
        // Halve in steps: one bicubic jump from ~700 px to a 100 px card drops detail and shimmers.
        while (image.getWidth() / 2 >= width) image = resize(image, image.getWidth() / 2, image.getHeight() / 2);
        BufferedImage card = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = card.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.fill(new RoundRectangle2D.Double(0, 0, width, height, arc, arc));
            g.setComposite(AlphaComposite.SrcIn);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(image, 0, 0, width, height, null);
        } finally { g.dispose(); }
        return card;
    }

    private static BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage half = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = half.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(source, 0, 0, width, height, null);
        } finally { g.dispose(); }
        return half;
    }

    private static BufferedImage painting(String id) {
        SoftReference<BufferedImage> held = PAINTINGS.get(id);
        BufferedImage image = held == null ? null : held.get();
        if (image != null) return image;
        try (InputStream in = PaintedCardArt.class.getResourceAsStream(path(id))) {
            image = in == null ? null : ImageIO.read(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read card art " + id, e);
        }
        if (image == null) throw new IllegalStateException("Missing card art " + path(id));
        PAINTINGS.put(id, new SoftReference<>(image));
        return image;
    }

    private static String path(String id) { return "/assets/cards/" + id + ".png"; }
}
