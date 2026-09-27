package onitama.client.ui;

import java.awt.Color;
import java.awt.Font;

/**
 * Shared look-and-feel constants for the Swing client. One place for every
 * color and font so the panels stay consistent.
 */
public final class Theme {

    /** Light board square. */
    public static final Color BOARD_LIGHT = new Color(0xF0E6D2);
    /** Dark board square. */
    public static final Color BOARD_DARK = new Color(0xD8C4A0);
    /** Fill of blue pieces. */
    public static final Color BLUE_PIECE = new Color(0x2C5F8A);
    /** Fill of red pieces. */
    public static final Color RED_PIECE = new Color(0xB03A2E);
    /** Ring around the selected piece. */
    public static final Color SELECTION = new Color(0xF2C037);
    /** Dots marking legal destinations. */
    public static final Color TARGET_DOT = new Color(0x3E8E5A);
    /** Translucent overlay on the last move's squares. */
    public static final Color LAST_MOVE = new Color(0, 0, 0, 36);
    /** Panel background. */
    public static final Color BACKGROUND = new Color(0x2B2B2B);
    /** Panel foreground (text). */
    public static final Color FOREGROUND = new Color(0xEEEEEE);
    /** Accent color for headers and buttons. */
    public static final Color ACCENT = new Color(0xC0392B);
    /** Error text. */
    public static final Color ERROR = new Color(0xE74C3C);

    /** Regular UI font. */
    public static final Font FONT_NORMAL = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
    /** Bold UI font (headers, card names). */
    public static final Font FONT_BOLD = new Font(Font.SANS_SERIF, Font.BOLD, 14);
    /** Large title font. */
    public static final Font FONT_TITLE = new Font(Font.SANS_SERIF, Font.BOLD, 24);

    private Theme() {
    }
}
