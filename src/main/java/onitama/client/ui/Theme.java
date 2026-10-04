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
    /** Board light square. */
    public static final Color BOARD_LIGHT = new Color(0xF3EAD3);
    /** Warm wooden board frame band. */
    public static final Color BOARD_FRAME = new Color(0xE5D3A8);
    /** Tile outlines. */
    public static final Color OUTLINE = new Color(0x231F20);
    /** Hard offset shadow fill (never blurred). */
    public static final Color SHADOW = new Color(0x231F20);
    /** Muted text on dark surfaces. */
    public static final Color MUTED = new Color(0x8A8580);

    // --- extended accents (fireship.dev brand colors) ---
    /** Success, victory, connection-ready ("brand green"). */
    public static final Color GREEN = new Color(0x0BA95B);
    /** Disabled controls (flat grey, no contrast pull). */
    public static final Color DISABLED = new Color(0xBDBDBD);
    /** Pale gold wash behind the last-played squares. */
    public static final Color LAST_MOVE = new Color(0xFFF8E1);
    /** Leaderboard medal tints: gold reuses AMBER, silver and bronze below. */
    public static final Color MEDAL_SILVER = new Color(0xC9C2B8);
    public static final Color MEDAL_BRONZE = new Color(0xB0793C);
    /** Table grid lines on the dark surface. */
    public static final Color GRID_LINE = new Color(0x3A3634);

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

    // --- shape tokens ---
    /** Medium radius (cards, headings). */
    public static final int RADIUS_MD = 12;
    /** Hard shadow offset in px. */
    public static final int SHADOW_OFFSET = 4;
    /** The 8px base unit every panel inset and gap snaps to. */
    public static final int GRID = 8;
    /** Standard outer padding for a screen-filling panel. */
    public static final int PANEL_PAD = 16;

    /**
     * Darker shade of a player color for TEXT on light parchment — the plain
     * player colors (sky, coral) fail contrast on light backgrounds.
     */
    public static Color playerTextColor(PlayerColor color) {
        return color == PlayerColor.BLUE ? P1_DARK : P2_DARK;
    }

    /** Blends two colors 50/50 (for muted variants). */
    public static Color blend(Color a, Color b) {
        return new Color((a.getRed() + b.getRed()) / 2,
                (a.getGreen() + b.getGreen()) / 2,
                (a.getBlue() + b.getBlue()) / 2);
    }

    /** The same color at a new alpha (washes and scrims over painted art). */
    public static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
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

    /** Monospaced font for numerals (turn counter, piece counts, ELO). */
    public static Font mono(float size) {
        return new Font(Font.MONOSPACED, Font.BOLD, 14).deriveFont(size);
    }
}
