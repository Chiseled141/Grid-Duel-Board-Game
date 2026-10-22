package deckduel.client.ui;

import java.awt.Color;
import java.awt.Font;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;


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
    /** Muted red accent. */
    public static final Color CORAL = new Color(0xDA9B8A);
    /** Hard offset shadow fill (never blurred). */
    public static final Color SHADOW = new Color(0x241E19);
    /** Muted text on dark surfaces. */
    public static final Color MUTED = new Color(0xC2B49C);

    /** Muted text on ivory, celadon and light input surfaces. */
    public static final Color MUTED_INK = new Color(0x74664F);
    /** Dark honey text on light caption plates. */
    public static final Color GOLD_INK = new Color(0x87551A);


    // --- iron and brass, shared with the match table's plates (TablePlaque) ---
    /** Blackened iron plate faces. */
    public static final Color IRON = new Color(0x2B2725);
    /** The dark foot of an iron plate's gradient. */
    public static final Color IRON_DARK = new Color(0x1A1716);
    /** The near-black outline around plates and slots. */
    public static final Color WOOD_LINE = new Color(0x0F0D0C);
    /** A dark slot sunk into a plate, behind text fields. */
    public static final Color SLOT = new Color(0x141110);
    /** Worn gold trim. */
    public static final Color BRASS = new Color(0xA9843F);
    /** The shadowed side of brass. */
    public static final Color BRASS_DARK = new Color(0x5E4520);
    /** Lit brass: headings and hovered labels. */
    public static final Color BRASS_LIGHT = new Color(0xD9B56A);
    /** Faded cream for labels and hints on iron. */
    public static final Color FADED = new Color(0xB4A890);

    /** The same color at a new alpha (washes and scrims over painted art). */
    public static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    // --- fonts: Outfit (SIL OFL), bundled in src/main/resources/fonts ---
    private static Font regular;
    private static Font bold;
    private static Font black;
    private static Font decorative;

    static {
        regular = loadFont("outfit-400.ttf");
        bold = loadFont("outfit-700.ttf");
        black = loadFont("outfit-900.ttf");
        decorative = loadFont("cinzel-decorative-bold.ttf");
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

    /** Engraved capitals for the menu title and map lettering. */
    public static Font decorative(float size) {
        return decorative.deriveFont(size);
    }
}
