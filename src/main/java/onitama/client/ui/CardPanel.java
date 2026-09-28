package onitama.client.ui;

import onitama.core.Board;
import onitama.core.Card;
import onitama.core.PlayerColor;
import onitama.core.Square;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Map;

import javax.swing.JComponent;

/**
 * One movement card rendered after the physical Onitama card template, in
 * the retro-pop theme: the animal kanji in muted gold, the card name in
 * display capitals, the 5x5 pattern grid with filled squares, a flavor-quote
 * band and the player-color stamp seal. The pattern always comes from the
 * engine's {@link Card} data (single source of truth), drawn from
 * {@code viewerColor}'s perspective; opponent cards are dimmed. A designed
 * card-face template from the AssetStore replaces the drawn face when
 * present. Clicking the panel fires the given callback (own cards only).
 */
public final class CardPanel extends JComponent {

    private static final int CELL = 17;
    private static final int GRID = Board.SIZE * CELL;
    private static final int W = 192;
    private static final int H = 150;

    /** The animal kanji for every card id (falls back to a Latin letter). */
    private static final Map<String, String> KANJI = Map.ofEntries(
            Map.entry("tiger", "虎"), Map.entry("dragon", "龍"),
            Map.entry("frog", "蛙"), Map.entry("rabbit", "兎"),
            Map.entry("crab", "蟹"), Map.entry("elephant", "象"),
            Map.entry("goose", "雁"), Map.entry("rooster", "鶏"),
            Map.entry("monkey", "猴"), Map.entry("mantis", "螳"),
            Map.entry("horse", "馬"), Map.entry("ox", "牛"),
            Map.entry("crane", "鶴"), Map.entry("boar", "豕"),
            Map.entry("eel", "鰻"), Map.entry("cobra", "蛇"));

    /**
     * Each card's own accent color — a fixed, distinct retro tone per animal
     * (documented in docs/DESIGN_DECISIONS.md). It colors the kanji, the
     * watermark and the pattern squares, so every card is recognizable at a
     * glance.
     */
    private static final Map<String, Color> ACCENTS = Map.ofEntries(
            Map.entry("tiger", new Color(0xED203D)),
            Map.entry("dragon", new Color(0x7B5EA7)),
            Map.entry("frog", new Color(0x0BA95B)),
            Map.entry("rabbit", new Color(0xF38BA3)),
            Map.entry("crab", new Color(0xFC7428)),
            Map.entry("elephant", new Color(0x12B5E5)),
            Map.entry("goose", new Color(0xFCBA28)),
            Map.entry("rooster", new Color(0x8D2F23)),
            Map.entry("monkey", new Color(0x9CCC65)),
            Map.entry("mantis", new Color(0x00838F)),
            Map.entry("horse", new Color(0x3949AB)),
            Map.entry("ox", new Color(0x6D4C41)),
            Map.entry("crane", new Color(0x26C6DA)),
            Map.entry("boar", new Color(0xC2185B)),
            Map.entry("eel", new Color(0x2E7D32)),
            Map.entry("cobra", new Color(0x546E7A)));

    /** Original flavor lines for the quote band (one per animal). */
    private static final Map<String, String> QUOTES = Map.ofEntries(
            Map.entry("tiger", "Strike far, or step back and pounce."),
            Map.entry("dragon", "Leap wide from the hills, and never the same way twice."),
            Map.entry("frog", "Hop to the side, then spring ahead."),
            Map.entry("rabbit", "Sprint forward, then dart back out of reach."),
            Map.entry("crab", "Sideways never loses its way."),
            Map.entry("elephant", "Steady steps carry the greatest weight."),
            Map.entry("goose", "Fly in lines, land in curves."),
            Map.entry("rooster", "Circle the coop with care."),
            Map.entry("monkey", "Swing around whatever stands in your path."),
            Map.entry("mantis", "Bow first, then strike deep."),
            Map.entry("horse", "Gallop ahead, shy aside."),
            Map.entry("ox", "Push forward and hold the line."),
            Map.entry("crane", "Rise above the pond, then descend."),
            Map.entry("boar", "Charge, then keep the ground you take."),
            Map.entry("eel", "Slip aside, then slide out of reach."),
            Map.entry("cobra", "Wait patiently, then strike from afar."));

    private final Card card;
    private final PlayerColor viewerColor;
    private final boolean dimmed;
    private boolean selected;

    /**
     * Creates the card panel.
     *
     * @param card the card to render
     * @param viewerColor the side whose orientation the pattern is drawn in
     * @param dimmed true for the opponent's cards (drawn muted, no shadow)
     * @param onSelect fired when the panel is clicked (null for read-only cards)
     */
    public CardPanel(Card card, PlayerColor viewerColor, boolean dimmed, Runnable onSelect) {
        this.card = card;
        this.viewerColor = viewerColor;
        this.dimmed = dimmed;
        setPreferredSize(new Dimension(W + 6, H + 6));
        setToolTipText(card.name() + " — " + quote()
                + (dimmed ? " (opponent's card)" : ""));
        if (onSelect != null) {
            setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    onSelect.run();
                }
            });
        }
    }

    /** Marks this card as the currently selected one (gold frame + shadow). */
    public void setSelected(boolean selected) {
        this.selected = selected;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = UiKit.nice(graphics);
        g.setColor(Theme.BG);
        g.fillRect(0, 0, getWidth(), getHeight());

        int x0 = 2;
        int y0 = 2;

        if (selected) {
            g.setColor(Theme.SHADOW);
            g.fillRoundRect(x0 + 4, y0 + 4, W, H, 14, 14);
        }

        Image face = AssetStore.optional("card-face-template.png");
        g.setColor(dimmed ? new Color(0xE3DCC4) : Theme.CREAM);
        g.fillRoundRect(x0, y0, W, H, 14, 14);
        if (face != null) {
            g.drawImage(face, x0, y0, W, H, null);
        }
        g.setColor(Theme.INK);
        g.setStroke(new BasicStroke(selected ? 3f : 1.8f));
        if (selected) {
            g.setColor(Theme.AMBER);
        }
        g.drawRoundRect(x0, y0, W - 1, H - 1, 14, 14);

        drawKanji(g, x0, y0);
        drawGrid(g, x0, y0);
        drawName(g, x0, y0);
        drawQuoteBand(g, x0, y0);
        drawSeal(g, x0, y0);

        if (dimmed) {
            g.setColor(new Color(15, 13, 14, 70));
            g.fillRoundRect(x0, y0, W, H, 14, 14);
        }
        g.dispose();
    }

    /** The card's own accent color, muted when the card is dimmed. */
    private Color accent() {
        Color accent = ACCENTS.getOrDefault(card.id(), Theme.AMBER);
        if (!dimmed) {
            return accent;
        }
        Color base = new Color(0xE3DCC4);
        return new Color((accent.getRed() + base.getRed()) / 2,
                (accent.getGreen() + base.getGreen()) / 2,
                (accent.getBlue() + base.getBlue()) / 2);
    }

    /** The big animal calligraphy in the card's accent color, upper-left. */
    private void drawKanji(Graphics2D g, int x0, int y0) {
        Font kanjiFont = new Font(Font.SANS_SERIF, Font.BOLD, 44);
        String glyph = KANJI.get(card.id());
        if (glyph == null || kanjiFont.canDisplayUpTo(glyph) != -1) {
            // System has no CJK glyphs: fall back to the animal's initial.
            glyph = card.name().substring(0, 1).toUpperCase();
        }
        // Faint circular watermark behind the calligraphy, like the real cards.
        g.setColor(new Color(accent().getRed(), accent().getGreen(),
                accent().getBlue(), 46));
        g.fillOval(x0 + 8, y0 + 12, 76, 76);
        g.setFont(kanjiFont);
        g.setColor(accent());
        var metrics = g.getFontMetrics();
        int cx = x0 + 6 + (84 - metrics.stringWidth(glyph)) / 2;
        int cy = y0 + 8 + (82 - metrics.getHeight()) / 2 + metrics.getAscent();
        g.drawString(glyph, cx, cy);
    }

    /** The 5x5 grid with filled destination squares (engine data). */
    private void drawGrid(Graphics2D g, int x0, int y0) {
        int gx = x0 + 94;
        int gy = y0 + 10;
        Color fill = accent();
        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                int px = gx + col * CELL;
                int py = gy + row * CELL;
                g.setColor(Theme.BOARD_LIGHT);
                g.fillRect(px, py, CELL, CELL);
                g.setColor(new Color(35, 31, 32, 60));
                g.drawRect(px, py, CELL, CELL);
            }
        }
        // The moving piece's square: bold ink ring on the center cell.
        int centerX = gx + 2 * CELL;
        int centerY = gy + 2 * CELL;
        g.setColor(Theme.INK);
        g.setStroke(new BasicStroke(2.5f));
        g.drawOval(centerX + 3, centerY + 3, CELL - 6, CELL - 6);
        // Destination squares in the card's accent color, full-bleed so the
        // moves read clearly even at lobby size.
        for (Square destination : card.destinationsFrom(new Square(2, 2), viewerColor)) {
            int px = gx + destination.x() * CELL;
            int py = gy + (Board.SIZE - 1 - destination.y()) * CELL;
            g.setColor(fill);
            g.fillRect(px + 1, py + 1, CELL - 2, CELL - 2);
            g.setColor(Theme.INK);
            g.setStroke(new BasicStroke(1.5f));
            g.drawRect(px + 1, py + 1, CELL - 3, CELL - 3);
        }
    }

    /** Card name in display capitals, under the kanji. */
    private void drawName(Graphics2D g, int x0, int y0) {
        g.setFont(Theme.display(15f));
        g.setColor(Theme.INK);
        var metrics = g.getFontMetrics();
        String name = card.name().toUpperCase();
        g.drawString(name, x0 + 8, y0 + 96 + metrics.getAscent() / 2 + 4);
    }

    /** The flavor-quote band across the bottom, on two lines. */
    private void drawQuoteBand(Graphics2D g, int x0, int y0) {
        int bandX = x0 + 6;
        int bandY = y0 + 118;
        int bandW = W - 12;
        int bandH = 26;
        g.setColor(dimmed ? new Color(0xD5CBA8) : new Color(0xF6E7B4));
        g.fillRoundRect(bandX, bandY, bandW, bandH, 8, 8);
        g.setColor(Theme.INK);
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(bandX, bandY, bandW - 1, bandH - 1, 8, 8);

        g.setFont(Theme.normal(8f));
        var metrics = g.getFontMetrics();
        String quote = quote();
        String first = quote;
        String second = "";
        int space = quote.lastIndexOf(' ', quote.length() / 2 + 4);
        if (space > 0 && metrics.stringWidth(quote) > bandW - 16) {
            first = quote.substring(0, space);
            second = quote.substring(space + 1);
        }
        g.setColor(dimmed ? new Color(0x6E685C) : Theme.INK);
        g.drawString(first, bandX + 8, bandY + 11);
        if (!second.isEmpty()) {
            g.drawString(second, bandX + 8, bandY + 21);
        }
    }

    /** The stamp seal in the color that decided the first move. */
    private void drawSeal(Graphics2D g, int x0, int y0) {
        int sealX = x0 + W - 16;
        int sealY = y0 + H - 16;
        g.setColor(dimmed ? new Color(0xA9A396)
                : card.stamp() == PlayerColor.BLUE ? Theme.SKY : Theme.CORAL);
        g.fillOval(sealX - 6, sealY - 6, 12, 12);
        g.setColor(Theme.INK);
        g.setStroke(new BasicStroke(1.5f));
        g.drawOval(sealX - 6, sealY - 6, 12, 12);
    }

    private String quote() {
        return QUOTES.getOrDefault(card.id(), "Move with intention.");
    }
}
