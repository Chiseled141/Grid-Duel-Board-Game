package onitama.client.ui;

import onitama.core.Card;
import onitama.core.Offset;
import onitama.core.PlayerColor;
import onitama.core.duel.SkillCategory;
import onitama.core.duel.SkillDefinition;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Medieval card faces, with movement diagrams drawn from the actual card offsets. */
public final class CardArt {
    private static final List<String> IDS = List.of("tiger", "dragon", "frog", "rabbit", "crab", "elephant",
            "goose", "rooster", "monkey", "mantis", "horse", "ox", "crane", "boar", "eel", "cobra");
    private static final List<String> NAMES = List.of("Lance", "Breach", "Left Feint", "Right Feint", "Bulwark",
            "Shieldwall", "Left Wheel", "Right Wheel", "Encircle", "Ambush", "Vanguard", "Rearguard",
            "Fallback", "Advance", "Left Guard", "Right Guard");
    private static final Map<FaceKey, BufferedImage> FACES = new ConcurrentHashMap<>();
    private static final Map<SkillDefinition, BufferedImage> SKILLS = new ConcurrentHashMap<>();
    private static final BufferedImage BACK = createBack();
    private record FaceKey(String id, String name, List<Offset> offsets, PlayerColor color, PlayerColor viewer) { }
    private CardArt() { }

    /** Presentation name; stable protocol IDs and all movement rules remain unchanged. */
    public static String nameFor(Card card) {
        int index = IDS.indexOf(card.id());
        return index < 0 ? card.name() : NAMES.get(index);
    }

    /** Plain tactical guidance without the retired animal artwork. */
    public static String flavorFor(String id) { return "Highlighted squares are the exact landing positions."; }

    /** Cached face includes custom rule offsets, not only the card ID. */
    public static BufferedImage movementFace(Card card, PlayerColor color) {
        return movementFace(card, color, color);
    }

    /** Renders destinations in the viewing player's board orientation, including public enemy cards. */
    public static BufferedImage movementFace(Card card, PlayerColor owner, PlayerColor viewer) {
        return FACES.computeIfAbsent(new FaceKey(card.id(), nameFor(card), card.offsets(), owner, viewer), CardArt::createMovement);
    }

    /** Neutral reverse shared by both decks and every hidden-card animation. */
    public static BufferedImage backFace() { return BACK; }

    /** Skill face contains live name/cost and art; full rules are reserved for inspection. */
    public static BufferedImage skillFace(SkillDefinition definition) {
        return SKILLS.computeIfAbsent(definition, CardArt::createSkill);
    }

    /** Muted print color also has a distinct corner ornament for each category. */
    public static Color categoryColor(SkillCategory category) {
        return switch (category) {
            case MOVEMENT -> new Color(43, 76, 92);
            case ATTACK -> new Color(133, 51, 42);
            case CONTROL -> new Color(87, 100, 61);
            case RESOURCE -> new Color(158, 117, 47);
        };
    }

    static void frame(Graphics2D g, int x, int y, int w, int h, Color edge) {
        g.setColor(new Color(235, 220, 185));
        g.fillRoundRect(x, y, w, h, 10, 10);
        g.setColor(new Color(59, 42, 30)); g.setStroke(new BasicStroke(2.5f));
        g.drawRoundRect(x + 5, y + 5, w - 10, h - 10, 4, 4);
        g.setColor(edge); g.setStroke(new BasicStroke(.9f));
        g.drawRect(x + 9, y + 9, w - 18, h - 18);
        for (int cx : new int[]{x + 6, x + w - 7}) for (int cy : new int[]{y + 6, y + h - 7}) {
            g.setColor(edge); g.drawLine(cx - 2, cy - 2, cx + 2, cy + 2);
            g.drawLine(cx - 2, cy + 2, cx + 2, cy - 2);
        }
    }

    private static BufferedImage createSkill(SkillDefinition definition) {
        var image = new BufferedImage(400, 480, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try {
            g.scale(2, 2);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color accent = categoryColor(definition.category());
            frame(g, 0, 0, 200, 240, accent);
            g.setColor(new Color(53, 39, 29));
            float font = 16;
            do { g.setFont(Theme.cardTitle(font--)); }
            while (g.getFontMetrics().stringWidth(definition.name().toUpperCase()) > 132 && font > 8);
            g.drawString(definition.name().toUpperCase(), 15, 36);
            g.setColor(accent); g.fillOval(151, 15, 33, 33);
            g.setColor(Theme.CREAM); g.setFont(Theme.cardTitle(21));
            centered(g, Integer.toString(definition.manaCost()), 167, 40);
            String path = definition.artworkPath();
            Image art = AssetStore.optional(path.startsWith("assets/") ? path.substring(7) : path);
            if (art == null) art = AssetStore.optional("medieval/skills/" + definition.id() + ".png");
            if (art != null) g.drawImage(art, 24, 53, 151, 151, null);
            g.setColor(accent); g.drawLine(18, 211, 182, 211);
            g.setFont(Theme.cardTitle(10)); centered(g, definition.category().name(), 100, 225);
            for (int x : new int[]{20, 174}) {
                int motif = definition.category().ordinal();
                if (motif == 1) {
                    g.drawLine(x - 3, 218, x + 3, 225); g.drawLine(x - 3, 225, x + 3, 218);
                } else if (motif == 2) {
                    g.drawOval(x - 3, 218, 5, 7); g.drawOval(x + 1, 218, 5, 7);
                } else if (motif == 3) {
                    g.drawLine(x - 3, 222, x + 3, 222); g.drawLine(x, 218, x, 226);
                    g.drawLine(x - 2, 219, x + 2, 225); g.drawLine(x - 2, 225, x + 2, 219);
                } else { g.drawLine(x - 3, 222, x + 3, 222); g.drawLine(x + 3, 222, x, 219); }
            }
        } finally { g.dispose(); }
        return image;
    }

    private static BufferedImage createMovement(FaceKey key) {
        var image = new BufferedImage(400, 480, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try {
            g.scale(2, 2);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color side = Theme.playerTextColor(key.color());
            frame(g, 0, 0, 200, 240, Theme.BOARD_GOLD);
            g.setColor(side); g.drawLine(15, 43, 185, 43);
            float font = 15;
            do { g.setFont(Theme.cardTitle(font--)); }
            while (g.getFontMetrics().stringWidth(key.name().toUpperCase()) > 160 && font > 9);
            g.setColor(Theme.INK); centered(g, key.name().toUpperCase(), 100, 34);
            heraldry(g, Math.max(0, IDS.indexOf(key.id())) % 4, 26, 52, 22, side);
            g.setColor(new Color(91, 69, 43)); g.setFont(Theme.bold(9)); g.drawString("MOVEMENT", 58, 65);
            int cell = 25, left = 37, top = 79;
            for (int row = 0; row < 5; row++) for (int col = 0; col < 5; col++) {
                g.setColor((row + col) % 2 == 0 ? new Color(232, 217, 182) : new Color(211, 192, 152));
                g.fillRect(left + col * cell, top + row * cell, cell - 1, cell - 1);
                g.setColor(new Color(75, 52, 34)); g.setStroke(new BasicStroke(1));
                g.drawRect(left + col * cell, top + row * cell, cell, cell);
            }
            for (Offset offset : key.offsets()) {
                int direction = key.color() == key.viewer() ? 1 : -1;
                int col = 2 + direction * offset.dx(), row = 2 - direction * offset.dy();
                if (col < 0 || col >= 5 || row < 0 || row >= 5) continue;
                g.setColor(side);
                g.fillRoundRect(left + col * cell + 2, top + row * cell + 2, cell - 5, cell - 5, 3, 3);
                g.setColor(Theme.CREAM); g.fillOval(left + col * cell + 9, top + row * cell + 9, 6, 6);
            }
            heraldry(g, 1, left + 2 * cell + 5, top + 2 * cell + 4, 15, new Color(70, 55, 37));
            g.setColor(new Color(102, 80, 48)); g.setFont(Theme.bold(8));
            centered(g, "EXACT LANDING SQUARES", 100, 220); g.drawLine(20, 228, 180, 228);
        } finally { g.dispose(); }
        return image;
    }

    private static BufferedImage createBack() {
        var image = new BufferedImage(400, 480, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try {
            g.scale(2, 2); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            frame(g, 0, 0, 200, 240, new Color(184, 146, 83));
            g.setColor(new Color(66, 41, 36)); g.fillRoundRect(14, 14, 172, 212, 4, 4);
            g.setColor(new Color(166, 127, 70)); g.setStroke(new BasicStroke(1)); g.drawRect(21, 21, 158, 198);
            for (int y = 35; y < 215; y += 22) for (int x = 32; x < 175; x += 22) {
                var diamond = new Path2D.Double();
                diamond.moveTo(x, y - 4); diamond.lineTo(x + 4, y); diamond.lineTo(x, y + 4);
                diamond.lineTo(x - 4, y); diamond.closePath(); g.draw(diamond);
            }
            g.setColor(new Color(66, 41, 36)); g.fillOval(48, 69, 104, 104);
            g.setColor(new Color(205, 171, 109)); g.drawOval(48, 69, 104, 104);
            heraldry(g, 0, 64, 85, 72, new Color(205, 171, 109));
            heraldry(g, 1, 82, 102, 36, new Color(228, 207, 162));
        } finally { g.dispose(); }
        return image;
    }

    private static void centered(Graphics2D g, String text, int x, int baseline) {
        g.drawString(text, x - g.getFontMetrics().stringWidth(text) / 2, baseline);
    }

    static void heraldry(Graphics2D parent, int type, int x, int y, int size, Color color) {
        var g = (Graphics2D) parent.create();
        try {
            g.translate(x, y); g.scale(size / 32.0, size / 32.0); g.setColor(color);
            g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (type == 1) {
                var shield = new Path2D.Double();
                shield.moveTo(4, 3); shield.lineTo(28, 3); shield.lineTo(27, 19);
                shield.quadTo(23, 27, 16, 31); shield.quadTo(8, 27, 5, 19); shield.closePath();
                g.draw(shield); g.drawLine(16, 7, 16, 24); g.drawLine(9, 14, 23, 14);
            } else if (type == 2) {
                g.drawRect(7, 11, 18, 20); g.drawLine(4, 11, 28, 11);
                for (int i = 5; i <= 25; i += 8) g.drawRect(i, 3, 4, 8);
                g.drawArc(12, 21, 8, 14, 0, 180);
            } else if (type == 3) {
                g.drawLine(7, 30, 7, 2); g.drawLine(7, 3, 26, 3); g.drawLine(26, 3, 20, 10);
                g.drawLine(20, 10, 27, 17); g.drawLine(27, 17, 7, 17);
            } else {
                g.drawLine(5, 29, 27, 3); g.drawLine(5, 3, 27, 29);
                g.drawLine(2, 22, 11, 30); g.drawLine(21, 30, 30, 22);
                g.drawLine(26, 3, 29, 2); g.drawLine(3, 2, 6, 3);
            }
        } finally { g.dispose(); }
    }
}
