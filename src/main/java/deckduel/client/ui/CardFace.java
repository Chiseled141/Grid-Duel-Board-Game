package deckduel.client.ui;

import deckduel.core.card.CardDef;
import deckduel.core.card.CardKind;
import deckduel.core.card.Suppression;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Paints card faces and backs. Cards with artwork show it with the game data the art leaves out
 * (points, tags, a short effect) laid over it; cards without art get a procedural face.
 */
final class CardFace {
    private static final Color UNIT = new Color(0xE6D3A8), LANDMARK = new Color(0x4C5A42), SPELL = new Color(0x4B3346);
    private static final Color BRASS = new Color(0xAD8954), BACK = new Color(0x3A2C22);

    /** Room a sprite keeps right of and below the card for its drop shadow. */
    static final int SHADOW_X = 4, SHADOW_Y = 6;
    private static final int SPRITES = 96;
    // Finished card images by look and pixel size. Drawing a card from vector shapes and text took
    // most of each animation frame; a cached image is a single copy. The Swing deck builder and the
    // JavaFX table both use it, so every access holds the cache's monitor.
    private static final java.util.Map<String, BufferedImage> SPRITE_CACHE = new java.util.LinkedHashMap<>(64, .75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, BufferedImage> eldest) { return size() > SPRITES; }
    };

    private CardFace() { }

    /** Paints one face; suppressed parts are labeled on the card itself. */
    static void paint(Graphics2D g, CardDef card, Rectangle r, boolean emphasized, List<Suppression> suppressed) {
        BufferedImage sprite = sprite(card, suppressed, r.width, r.height, emphasized, pixelScale(g));
        g.drawImage(sprite, r.x, r.y, r.width + SHADOW_X, r.height + SHADOW_Y, null);
    }

    /** A card face, shadow included, rendered once for this size at {@code scale} pixels per unit. */
    static BufferedImage sprite(CardDef card, List<Suppression> suppressed, int width, int height, boolean emphasized, double scale) {
        boolean art = CardImages.shared().face(card.id()) != null;
        String key = card.id() + "|" + width + "x" + height + "|" + emphasized + "|" + suppressed + "|" + scale + "|" + art;
        return cached(key, () -> render(width + SHADOW_X, height + SHADOW_Y, scale,
                g -> renderFace(g, card, new Rectangle(0, 0, width, height), emphasized, suppressed)));
    }

    private static BufferedImage cached(String key, java.util.function.Supplier<BufferedImage> renderer) {
        synchronized (SPRITE_CACHE) { return SPRITE_CACHE.computeIfAbsent(key, k -> renderer.get()); }
    }

    /** The shared card back, shadow included, rendered once per size. */
    static BufferedImage backSprite(int width, int height, double scale) {
        String key = "back|" + width + "x" + height + "|" + scale + "|" + (CardImages.shared().back() != null);
        return cached(key, () -> render(width + SHADOW_X, height + SHADOW_Y, scale,
                g -> renderBack(g, new Rectangle(0, 0, width, height))));
    }

    /** Device pixels per unit of the graphics' current transform, at least 1. */
    static double pixelScale(Graphics2D g) {
        return Math.max(1, Math.round(g.getTransform().getScaleX() * 4) / 4.0);
    }

    private static BufferedImage render(int width, int height, double scale, java.util.function.Consumer<Graphics2D> painter) {
        BufferedImage image = new BufferedImage((int) Math.ceil(width * scale), (int) Math.ceil(height * scale), BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = UiKit.nice(image.getGraphics());
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.scale(scale, scale);
        painter.accept(g);
        g.dispose();
        return image;
    }

    private static void renderFace(Graphics2D g, CardDef card, Rectangle r, boolean emphasized, List<Suppression> suppressed) {
        g.setColor(new Color(0, 0, 0, 110));
        g.fillRoundRect(r.x + 3, r.y + 5, r.width, r.height, 9, 9);
        BufferedImage art = CardImages.shared().face(card.id());
        if (art != null) paintArtwork(g, card, art, r, emphasized, suppressed);
        else paintProcedural(g, card, r, emphasized, suppressed);
    }

    /**
     * The painted card. Unit art already shows the title and nation crest, and the neutral Spell
     * and Landmark art prints its kind, title and description, so only what the art lacks is
     * added: a Unit's points badge and any disable markers. Full rules stay in the detail line.
     */
    private static void paintArtwork(Graphics2D g, CardDef card, BufferedImage art, Rectangle r,
                                     boolean emphasized, List<Suppression> suppressed) {
        Graphics2D clipped = (Graphics2D) g.create();
        clipped.clip(new RoundRectangle2D.Float(r.x, r.y, r.width, r.height, 9, 9));
        clipped.drawImage(art, r.x, r.y, r.width, r.height, null);
        clipped.dispose();
        float scale = Math.max(.62f, Math.min(1.25f, r.width / 104f));
        int pad = Math.round(7 * scale);
        if (card.kind() == CardKind.UNIT) pointsBadge(g, card, r.x + pad, r.y + pad, Math.round(24 * scale), scale);
        g.setStroke(new BasicStroke(emphasized ? 2.5f : 1f));
        g.setColor(emphasized ? Theme.AMBER : new Color(0, 0, 0, 120));
        g.drawRoundRect(r.x, r.y, r.width, r.height, 9, 9);
        if (!suppressed.isEmpty()) paintSuppressed(g, r, suppressed, scale);
    }

    private static void pointsBadge(Graphics2D g, CardDef card, int x, int y, int size, float scale) {
        g.setColor(Theme.INK);
        g.fillOval(x, y, size, size);
        g.setColor(BRASS);
        g.setStroke(new BasicStroke(1.2f));
        g.drawOval(x, y, size, size);
        g.setColor(Theme.CREAM);
        g.setFont(Theme.bold(14 * scale));
        String points = String.valueOf(card.points());
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(points, x + (size - metrics.stringWidth(points)) / 2, y + (size + metrics.getAscent()) / 2 - 2);
    }

    private static void paintProcedural(Graphics2D g, CardDef card, Rectangle r, boolean emphasized, List<Suppression> suppressed) {
        g.setColor(card.kind() == CardKind.UNIT ? UNIT : card.kind() == CardKind.LANDMARK ? LANDMARK : SPELL);
        g.fillRoundRect(r.x, r.y, r.width, r.height, 9, 9);
        g.setStroke(new BasicStroke(emphasized ? 2.5f : 1.2f));
        g.setColor(emphasized ? Theme.AMBER : BRASS);
        g.drawRoundRect(r.x, r.y, r.width, r.height, 9, 9);
        CardArt.banner(g, card, r);
        Color ink = card.kind() == CardKind.UNIT ? Theme.INK : Theme.CREAM;
        float scale = Math.max(.62f, Math.min(1.25f, r.width / 104f));
        int pad = Math.round(7 * scale), y = r.y + pad;
        g.setFont(Theme.bold(8.5f * scale));
        g.setColor(card.kind() == CardKind.UNIT ? Theme.GOLD_INK : Theme.AMBER);
        g.drawString(card.kind().name(), r.x + pad, y + g.getFontMetrics().getAscent());
        if (card.kind() == CardKind.UNIT) {
            int size = Math.round(24 * scale);
            int badgeX = r.x + r.width - pad - size;
            g.setColor(Theme.INK);
            g.fillOval(badgeX, y - 2, size, size);
            g.setColor(Theme.CREAM);
            g.setFont(Theme.bold(14 * scale));
            String points = String.valueOf(card.points());
            FontMetrics metrics = g.getFontMetrics();
            g.drawString(points, badgeX + (size - metrics.stringWidth(points)) / 2, y - 2 + (size + metrics.getAscent()) / 2 - 2);
        } else {
            int size = Math.round(20 * scale);
            CardArt.cornerEmblem(g, card, new Rectangle(r.x + r.width - pad - size, y - 2, size, size));
        }
        y += Math.round(26 * scale);
        g.setColor(ink);
        g.setFont(Theme.bold(11.5f * scale));
        y = wrap(g, card.name(), r.x + pad, y, r.width - 2 * pad, 3);
        y += Math.round(4 * scale);
        g.setFont(Theme.normal(9.5f * scale));
        g.setColor(card.kind() == CardKind.UNIT ? Theme.MUTED_INK : Theme.withAlpha(Theme.CREAM, 225));
        if (card.kind() == CardKind.UNIT) {
            int bottom = r.y + r.height - pad;
            int line = g.getFontMetrics().getHeight();
            int artTop = y + 2, artBottom = bottom - 2 * line - 4;
            CardArt.medallion(g, card, new Rectangle(r.x + pad, artTop, r.width - 2 * pad, artBottom - artTop));
            g.setFont(Theme.normal(9.5f * scale));
            g.setColor(Theme.MUTED_INK);
            wrap(g, String.join(" · ", card.classes()), r.x + pad, bottom - line, r.width - 2 * pad, 1);
            wrap(g, String.join(" · ", card.regions()), r.x + pad, bottom - 2 * line, r.width - 2 * pad, 1);
        } else {
            wrap(g, card.effect().text(), r.x + pad, y, r.width - 2 * pad, 6);
        }
        if (!suppressed.isEmpty()) paintSuppressed(g, r, suppressed, scale);
    }

    private static void paintSuppressed(Graphics2D g, Rectangle r, List<Suppression> suppressed, float scale) {
        g.setColor(new Color(20, 14, 10, 150));
        g.fillRoundRect(r.x, r.y, r.width, r.height, 9, 9);
        g.setColor(Theme.CORAL);
        g.setFont(Theme.bold(9.5f * scale));
        // Below the name, so two-line names stay readable under the shade.
        int y = r.y + r.height * 2 / 3 - (suppressed.size() - 1) * g.getFontMetrics().getHeight();
        for (Suppression part : suppressed) {
            String text = part.name().toLowerCase(Locale.ROOT) + " disabled";
            g.drawString(text, r.x + (r.width - g.getFontMetrics().stringWidth(text)) / 2, y);
            y += g.getFontMetrics().getHeight();
        }
    }

    /** Paints a concealed card back; every card shares the same back, so it reveals nothing. */
    static void paintBack(Graphics2D g, Rectangle r) {
        g.drawImage(backSprite(r.width, r.height, pixelScale(g)), r.x, r.y, r.width + SHADOW_X, r.height + SHADOW_Y, null);
    }

    private static void renderBack(Graphics2D g, Rectangle r) {
        g.setColor(new Color(0, 0, 0, 100));
        g.fillRoundRect(r.x + 2, r.y + 3, r.width, r.height, 7, 7);
        BufferedImage back = CardImages.shared().back();
        if (back != null) {
            Graphics2D clipped = (Graphics2D) g.create();
            clipped.clip(new RoundRectangle2D.Float(r.x, r.y, r.width, r.height, 7, 7));
            clipped.drawImage(back, r.x, r.y, r.width, r.height, null);
            clipped.dispose();
            return;
        }
        g.setColor(BACK);
        g.fillRoundRect(r.x, r.y, r.width, r.height, 7, 7);
        g.setColor(BRASS);
        g.setStroke(new BasicStroke(1.2f));
        g.drawRoundRect(r.x, r.y, r.width, r.height, 7, 7);
        g.drawRoundRect(r.x + 4, r.y + 4, r.width - 8, r.height - 8, 5, 5);
        int size = Math.min(r.width, r.height) / 3;
        g.drawOval(r.x + (r.width - size) / 2, r.y + (r.height - size) / 2, size, size);
    }

    /** Draws word-wrapped text and returns the baseline below the last line. */
    static int wrap(Graphics2D g, String text, int x, int y, int width, int maxLines) {
        FontMetrics metrics = g.getFontMetrics();
        for (String value : lines(metrics, text, width, maxLines)) {
            y += metrics.getAscent();
            g.drawString(value, x, y);
            y += metrics.getDescent();
        }
        return y;
    }

    /** Word-wraps to at most {@code maxLines}, ending with an ellipsis when text is cut. */
    private static List<String> lines(FontMetrics metrics, String text, int width, int maxLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (metrics.stringWidth(candidate) <= width || line.length() == 0) line = new StringBuilder(candidate);
            else { lines.add(line.toString()); line = new StringBuilder(word); }
        }
        if (line.length() > 0) lines.add(line.toString());
        int shown = Math.min(maxLines, lines.size());
        List<String> fitted = new ArrayList<>();
        for (int i = 0; i < shown; i++) {
            String value = lines.get(i);
            // The full text stays available in the hover and keyboard detail line.
            if (i == shown - 1 && shown < lines.size()) value = value + "…";
            while (metrics.stringWidth(value) > width && value.length() > 2) value = value.substring(0, value.length() - 2) + "…";
            fitted.add(value);
        }
        return fitted;
    }

    /** Plain-text details for tooltips, accessibility and the detail line. */
    static String details(CardDef card) {
        String kind = card.kind().name().charAt(0) + card.kind().name().substring(1).toLowerCase(Locale.ROOT);
        return card.name() + " — " + kind + ". " + card.text();
    }
}
