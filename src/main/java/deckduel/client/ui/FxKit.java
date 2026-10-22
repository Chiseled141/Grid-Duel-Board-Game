package deckduel.client.ui;

import deckduel.core.card.CardDef;
import deckduel.core.card.Suppression;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.control.Button;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

/**
 * Shared JavaFX pieces for the match table: the game's fonts and colours, themed
 * buttons, card images converted from the shared Java2D sprites, and toolkit start-up.
 * Everything except {@link #start()} is used on the JavaFX Application Thread.
 */
final class FxKit {
    private static final Logger LOG = Logger.getLogger(FxKit.class.getName());
    static final String STYLESHEET = FxKit.class.getResource("/fx/deck-duel.css").toExternalForm();

    static final Color CREAM = fx(Theme.CREAM), MUTED = fx(Theme.MUTED), AMBER = fx(Theme.AMBER), INK = fx(Theme.INK);
    static final Color CORAL = fx(Theme.CORAL), SHADOW = fx(Theme.SHADOW);

    private static String regular, bold, black, decorative;
    // Converted copies keyed by the sprite they came from: CardFace keeps its sprites in a small
    // cache, and an evicted sprite lets its converted copy go too.
    private static final Map<BufferedImage, Image> CONVERTED = new WeakHashMap<>();

    private FxKit() { }

    /**
     * Starts the JavaFX toolkit once, from any thread, and returns when it is ready. Closing the
     * last JavaFX window must not end the toolkit: the Swing menus come back after a match.
     */
    static void start() {
        CountDownLatch ready = new CountDownLatch(1);
        try {
            Platform.startup(ready::countDown);
        } catch (IllegalStateException alreadyRunning) {
            ready.countDown();
        }
        Platform.setImplicitExit(false);
        try { ready.await(); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }

    // ---------------------------------------------------------------- fonts and colours

    private static synchronized void loadFonts() {
        if (regular != null) return;
        regular = load("outfit-400.ttf");
        bold = load("outfit-700.ttf");
        black = load("outfit-900.ttf");
        decorative = load("cinzel-decorative-bold.ttf");
    }

    private static String load(String file) {
        try (InputStream in = FxKit.class.getResourceAsStream("/fonts/" + file)) {
            Font font = in == null ? null : Font.loadFont(in, 12);
            if (font != null) return font.getName();
        } catch (java.io.IOException exception) {
            LOG.log(Level.WARNING, "could not load " + file, exception);
        }
        LOG.warning("falling back to the system font for " + file);
        return Font.getDefault().getName();
    }

    /** Body text, as {@link Theme#normal}. */
    static Font normal(double size) { loadFonts(); return new Font(regular, size); }
    /** Emphasised text, as {@link Theme#bold}. */
    static Font bold(double size) { loadFonts(); return new Font(bold, size); }
    /** Display weight, as {@link Theme#display}. */
    static Font display(double size) { loadFonts(); return new Font(black, size); }
    /** Engraved capitals, as {@link Theme#decorative}. */
    static Font decorative(double size) { loadFonts(); return new Font(decorative, size); }

    static Color fx(java.awt.Color colour) {
        return Color.rgb(colour.getRed(), colour.getGreen(), colour.getBlue(), colour.getAlpha() / 255.0);
    }

    static Color rgb(int rgb, double alpha) { return Color.rgb(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, alpha); }

    /** The width of {@code text} in {@code font}. */
    static double width(String text, Font font) {
        Text measure = new Text(text);
        measure.setFont(font);
        return measure.getLayoutBounds().getWidth();
    }

    /** {@code text} shortened with an ellipsis until it fits {@code width}. */
    static String fit(String text, Font font, double width) {
        String fitted = text;
        while (width(fitted, font) > width && fitted.length() > 3) fitted = fitted.substring(0, fitted.length() - 2) + "…";
        return fitted;
    }

    /** A pill button styled like {@link UiKit#pill}; {@code variant} is a class in the stylesheet. */
    static Button pill(String text, String variant) {
        Button button = new Button(text.toUpperCase(java.util.Locale.ROOT));
        button.getStyleClass().addAll("pill", variant);
        button.setFont(display(14));
        button.setFocusTraversable(false);
        return button;
    }

    // ---------------------------------------------------------------- images

    /** A card face for JavaFX, from the same sprite the Swing screens draw. */
    static Image face(CardDef card, List<Suppression> marks, int width, int height, boolean emphasized, double scale) {
        return convert(CardFace.sprite(card, marks, width, height, emphasized, scale));
    }

    /** Draws a face without smoothing away a pixel Spell's native grid. */
    static void drawFace(GraphicsContext g, CardDef card, List<Suppression> marks, int width, int height,
                         boolean emphasized, double scale, double x, double y) {
        boolean smoothing = g.isImageSmoothing();
        try {
            if (PixelSpellArt.supports(card)) g.setImageSmoothing(false);
            g.drawImage(face(card, marks, width, height, emphasized, scale), x, y,
                    width + CardFace.SHADOW_X, height + CardFace.SHADOW_Y);
        } finally { g.setImageSmoothing(smoothing); }
    }

    /** The shared card back. */
    static Image back(int width, int height, double scale) { return convert(CardFace.backSprite(width, height, scale)); }

    /** Draws the shared card back with its shadow. */
    static void drawBack(GraphicsContext g, int width, int height, double scale, double x, double y) {
        g.drawImage(back(width, height, scale), x, y, width + CardFace.SHADOW_X, height + CardFace.SHADOW_Y);
    }

    /** A JavaFX copy of a Java2D image, converted once per image. */
    static Image convert(BufferedImage image) {
        synchronized (CONVERTED) { return CONVERTED.computeIfAbsent(image, source -> SwingFXUtils.toFXImage(source, null)); }
    }
}
