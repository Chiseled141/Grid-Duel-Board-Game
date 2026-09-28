package onitama.client.ui;

import java.awt.Color;
import java.awt.Font;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shared look-and-feel constants, extracted from fireship.dev's own CSS
 * (coal background, beige text, gold pills with charcoal hard shadows).
 * One place for every color and font so the panels stay consistent.
 */
public final class Theme {

    private static final Logger LOG = Logger.getLogger(Theme.class.getName());

    // --- palette (values taken from fireship.dev's :root custom properties) ---
    /** App background ("coal"). */
    public static final Color BG = new Color(0x0F0D0E);
    /** Panels and list rows ("charcoal muted"). */
    public static final Color SURFACE = new Color(0x1B1918);
    /** Primary text and card faces ("beige"). */
    public static final Color CREAM = new Color(0xF9F4DA);
    /** Outlines and text on light fills ("charcoal"). */
    public static final Color INK = new Color(0x231F20);
    /** Highlight: primary buttons, selection, turn banner ("gold"). */
    public static final Color AMBER = new Color(0xFCBA28);
    /** Blue player pieces and labels ("brand blue"). */
    public static final Color SKY = new Color(0x12B5E5);
    /** Red player pieces, errors, danger ("brand red"). */
    public static final Color CORAL = new Color(0xED203D);
    /** Legal-move hints ("brand green"). */
    public static final Color TEAL = new Color(0x0BA95B);
    /** Board light square. */
    public static final Color BOARD_LIGHT = new Color(0xF0E6D2);
    /** Board dark square (warm charcoal). */
    public static final Color BOARD_DARK = new Color(0x221E1A);
    /** Tile outlines. */
    public static final Color OUTLINE = new Color(0x231F20);
    /** Hard offset shadow fill (never blurred). */
    public static final Color SHADOW = new Color(0x231F20);
    /** Muted text on dark surfaces. */
    public static final Color MUTED = new Color(0x8A8580);

    // --- fonts: Outfit (SIL OFL), bundled in src/main/resources/fonts ---
    private static Font regular;
    private static Font bold;
    private static Font black;

    static {
        regular = loadFont("outfit-400.ttf");
        bold = loadFont("outfit-700.ttf");
        black = loadFont("outfit-900.ttf");
    }

    private Theme() {
    }

    private static Font loadFont(String file) {
        try (InputStream in = Theme.class.getResourceAsStream("/fonts/" + file)) {
            if (in == null) {
                throw new IllegalStateException("font resource missing: " + file);
            }
            return Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "could not load " + file + ", falling back", e);
            return new Font(Font.SANS_SERIF, Font.PLAIN, 14);
        }
    }

    /** Body text font at the given size. */
    public static Font normal(float size) {
        return regular.deriveFont(size);
    }

    /** Emphasized text (section labels, buttons). */
    public static Font bold(float size) {
        return bold.deriveFont(size);
    }

    /** Display font: headlines, banners, buttons ("chunky" weight). */
    public static Font display(float size) {
        return black.deriveFont(size);
    }
}
