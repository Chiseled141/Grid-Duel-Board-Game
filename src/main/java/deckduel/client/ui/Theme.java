package deckduel.client.ui;

import java.awt.Color;
import java.awt.Font;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import deckduel.core.PlayerColor;

/** Parchment, walnut, muted heraldic colors and old brass for the medieval setting. */
public final class Theme {

    private static final Logger LOG = Logger.getLogger(Theme.class.getName());

    // --- mountain landscape palette ---
    /** Deep jade app background. */
    public static final Color BG = new Color(0x2C2925);
    /** Jade panel and list-row surfaces. */
    public static final Color SURFACE = new Color(0x403A31);
    /** Warm ivory text and light surfaces. */
    public static final Color CREAM = new Color(0xFFF2D7);
    /** Deep teal text on light surfaces. */
    public static final Color INK = new Color(0x382A20);
    /** Highlight: primary buttons, selection, turn banner ("gold"). */
    public static final Color AMBER = new Color(0xF4BF60);
    /** Blue player pieces and labels ("brand blue"). */
    public static final Color SKY = new Color(0x8AB1CB);
    /** Red player pieces, errors, danger ("brand red"). */
    public static final Color CORAL = new Color(0xDA9B8A);
    /** Board light square. */
    public static final Color BOARD_LIGHT = new Color(0xF8EDCF);
    /** Jade ceramic squares, paired with warm ivory. */
    public static final Color BOARD_JADE = new Color(0x807461);
    /** Deep lacquered jade frame. */
    public static final Color BOARD_FRAME = new Color(0x634832);
    /** Brass inlay on the board frame. */
    public static final Color BOARD_GOLD = new Color(0xDBB77B);
    /** Tile outlines. */
    public static final Color OUTLINE = new Color(0x382A20);
    /** Hard offset shadow fill (never blurred). */
    public static final Color SHADOW = new Color(0x241E19);
    /** Muted text on dark surfaces. */
    public static final Color MUTED = new Color(0xC2B49C);

    /** Muted text on ivory, celadon and light input surfaces. */
    public static final Color MUTED_INK = new Color(0x74664F);
    /** Dark honey text on light caption plates. */
    public static final Color GOLD_INK = new Color(0x87551A);
    /** Pine-jade secondary buttons. */
    public static final Color JADE = new Color(0x5D6447);
    /** Soft celadon inspector surface. */
    public static final Color MIST = new Color(0xE9DFC7);
    /** Warm red destructive buttons, with ivory text. */
    public static final Color VERMILION = new Color(0xAA4438);

    // --- extended accents ---
    /** Success, victory, connection-ready ("brand green"). */
    public static final Color GREEN = new Color(0x0BA95B);
    /** Muted sage disabled controls. */
    public static final Color DISABLED = new Color(0xB3AB98);
    /** Pale gold wash behind the last-played squares. */
    public static final Color LAST_MOVE = new Color(0xFFF8E1);
    /** Leaderboard medal tints: gold reuses AMBER, silver and bronze below. */
    public static final Color MEDAL_SILVER = new Color(0xC9C2B8);
    public static final Color MEDAL_BRONZE = new Color(0xB0793C);
    /** Table grid lines on the dark surface. */
    public static final Color GRID_LINE = new Color(0x756650);

    // --- player identities: blue system vs coral system ---
    /** Player 1 (Blue) primary. */
    public static final Color P1 = SKY;
    /** Player 1 darker shade (two-tone pawns, pressed states). */
    public static final Color P1_DARK = new Color(0x31566D);
    /** Player 1 light tint (selection tints, watermarks). */
    public static final Color P1_LIGHT = new Color(0xDAE5EB);
    /** Player 2 (Red) primary. */
    public static final Color P2 = CORAL;
    /** Player 2 darker shade (two-tone pawns, pressed states). */
    public static final Color P2_DARK = new Color(0x8B3D35);
    /** Player 2 light tint (selection tints, watermarks). */
    public static final Color P2_LIGHT = new Color(0xFFE0CF);

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

    /** Old-book serif titles give card faces a printed rather than UI-label character. */
    public static Font cardTitle(float size) {
        return new Font(Font.SERIF, Font.BOLD, 14).deriveFont(size);
    }
}
