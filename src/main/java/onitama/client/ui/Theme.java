package onitama.client.ui;

import java.awt.Color;
import java.awt.Font;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import onitama.core.PlayerColor;

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

    // --- extended accents (fireship.dev brand colors) ---
    /** Secondary actions and special card states ("brand orange"). */
    public static final Color ORANGE = new Color(0xFC7428);
    /** Special game states and secondary identities ("brand purple"). */
    public static final Color PURPLE = new Color(0x7B5EA7);
    /** Success, victory, connection-ready ("brand green"). */
    public static final Color GREEN = new Color(0x0BA95B);

    // --- player identities: blue system vs coral system ---
    /** Player 1 (Blue) primary. */
    public static final Color P1 = SKY;
    /** Player 1 darker shade (two-tone pawns, pressed states). */
    public static final Color P1_DARK = new Color(0x0E7FA8);
    /** Player 1 light tint (selection tints, watermarks). */
    public static final Color P1_LIGHT = new Color(0xBDEBF7);
    /** Player 2 (Red) primary. */
    public static final Color P2 = CORAL;
    /** Player 2 darker shade (two-tone pawns, pressed states). */
    public static final Color P2_DARK = new Color(0xA81830);
    /** Player 2 light tint (selection tints, watermarks). */
    public static final Color P2_LIGHT = new Color(0xFBD5DA);

    /** Returns the player color family for a side. */
    public static Color playerColor(PlayerColor color) {
        return color == PlayerColor.BLUE ? P1 : P2;
    }

    /** Returns the darker shade of a player color family. */
    public static Color playerDark(PlayerColor color) {
        return color == PlayerColor.BLUE ? P1_DARK : P2_DARK;
    }

    /** Returns the light tint of a player color family. */
    public static Color playerLight(PlayerColor color) {
        return color == PlayerColor.BLUE ? P1_LIGHT : P2_LIGHT;
    }

    // --- shape tokens ---
    /** Small radius (fields, quote bands). */
    public static final int RADIUS_SM = 8;
    /** Medium radius (cards, headings). */
    public static final int RADIUS_MD = 12;
    /** Large radius (stickers). */
    public static final int RADIUS_LG = 18;
    /** Hard shadow offset in px. */
    public static final int SHADOW_OFFSET = 4;

    /** Blends two colors 50/50 (for muted variants). */
    public static Color blend(Color a, Color b) {
        return new Color((a.getRed() + b.getRed()) / 2,
                (a.getGreen() + b.getGreen()) / 2,
                (a.getBlue() + b.getBlue()) / 2);
    }

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
