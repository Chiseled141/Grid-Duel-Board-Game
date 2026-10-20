package deckduel.client.ui;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

/**
 * Card artwork from {@code /assets/cards/runtime/<card id>.jpg}, decoded once off the EDT.
 * Until an image arrives, or when a card has none, callers draw the procedural face instead.
 */
final class CardImages {
    static final String DIRECTORY = "/assets/cards/runtime/";
    static final String BACK = "card-back";
    private static final Logger LOG = Logger.getLogger(CardImages.class.getName());
    private static final CardImages SHARED = new CardImages();
    private static final int SCALED_CACHE = 256;

    private final Map<String, BufferedImage> images = new ConcurrentHashMap<>();
    private final Set<String> requested = ConcurrentHashMap.newKeySet();
    private final ExecutorService loader = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "card-images");
        thread.setDaemon(true);
        return thread;
    });
    // EDT only: device-resolution copies for painting.
    private final List<Runnable> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final Map<String, Image> scaled = new LinkedHashMap<>(64, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) { return size() > SCALED_CACHE; }
    };

    static CardImages shared() { return SHARED; }

    /** Starts decoding these cards' art in the background. */
    void preload(Collection<String> ids) { ids.forEach(this::request); request(BACK); }

    /** Runs {@code listener} on the loader thread whenever more art finishes loading; for non-Swing screens. */
    void onLoaded(Runnable listener) { listeners.add(listener); }

    /** The decoded art of a card, or null while loading or when the card has no art. */
    BufferedImage face(String id) {
        BufferedImage image = images.get(id);
        if (image == null) request(id);
        return image;
    }

    /** The shared card back, or null while loading. */
    BufferedImage back() { return face(BACK); }

    /** Draws art into a rectangle, using a copy pre-scaled to the device's real pixel size. */
    void draw(Graphics2D g, String id, BufferedImage image, int x, int y, int width, int height) {
        int deviceWidth = Math.max(1, (int) Math.round(width * g.getTransform().getScaleX()));
        int deviceHeight = Math.max(1, (int) Math.round(height * g.getTransform().getScaleY()));
        Image copy = scaled.computeIfAbsent(id + "@" + deviceWidth + "x" + deviceHeight,
                key -> scale(image, deviceWidth, deviceHeight));
        g.drawImage(copy, x, y, width, height, null);
    }

    private static Image scale(BufferedImage image, int width, int height) {
        BufferedImage copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = copy.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(image, 0, 0, width, height, null);
        g.dispose();
        return copy;
    }

    private void request(String id) {
        if (!requested.add(id)) return;
        loader.execute(() -> {
            try {
                if (decode(id) == null) return;
                listeners.forEach(Runnable::run);
            } catch (IOException exception) {
                LOG.log(Level.WARNING, "Could not load card art " + id, exception);
            }
        });
    }

    /** Decodes synchronously; returns null when the card has no art. For tests and offline rendering. */
    BufferedImage decode(String id) throws IOException {
        var resource = CardImages.class.getResource(DIRECTORY + id + ".jpg");
        if (resource == null) return null;
        BufferedImage image = ImageIO.read(resource);
        if (image == null) throw new IOException("Unreadable card art " + id);
        images.put(id, image);
        requested.add(id);
        return image;
    }
}
