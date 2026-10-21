package deckduel.client.ui;

import deckduel.core.card.CardDef;
import deckduel.core.card.CardKind;
import deckduel.core.card.Effect;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.List;
import java.util.Locale;

/**
 * Procedural heraldic art derived from a card's real data: nation banner colours
 * and one emblem per class or Spell effect. It is a stand-in until card artwork
 * exists and carries no meaning beyond the card's tags.
 */
final class CardArt {
    private static final Color GOLD = new Color(0xC9A15E), DARK = new Color(0x2B211A);

    private CardArt() { }

    /** A thin banner along the card's top edge, split between the card's region colours. */
    static void banner(Graphics2D g, CardDef card, Rectangle r) {
        List<String> regions = card.regions();
        int height = Math.max(3, r.height / 30);
        for (int i = 0; i < regions.size(); i++) {
            int left = r.x + 5 + (r.width - 10) * i / regions.size();
            int right = r.x + 5 + (r.width - 10) * (i + 1) / regions.size();
            g.setColor(regionColor(regions.get(i)));
            g.fillRect(left, r.y + 2, right - left, height);
        }
    }

    /** A medallion with the card's class emblems, centred in the given area. */
    static void medallion(Graphics2D g, CardDef card, Rectangle area) {
        // Capped by width so the emblem is the same size whether the name takes one line or two.
        int size = Math.min(area.height, area.width * 2 / 5);
        if (size < 22) return;
        int x = area.x + (area.width - size) / 2, y = area.y + (area.height - size) / 2;
        g.setColor(Theme.withAlpha(card.kind() == CardKind.UNIT ? regionColor(card.nation()) : GOLD, 70));
        g.fillOval(x, y, size, size);
        g.setColor(Theme.withAlpha(DARK, 150));
        g.setStroke(new BasicStroke(1.2f));
        g.drawOval(x, y, size, size);
        List<String> emblems = card.kind() == CardKind.UNIT ? card.classes() : List.of(effectEmblem(card));
        int count = Math.min(2, emblems.size());
        int inner = (int) (size * (count == 1 ? .56 : .42));
        for (int i = 0; i < count; i++) {
            int cx = x + size / 2 + (count == 1 ? 0 : (i == 0 ? -1 : 1) * size / 5);
            emblem(g, emblems.get(i), new Rectangle(cx - inner / 2, y + (size - inner) / 2, inner, inner), DARK);
        }
    }

    /** A small light emblem for the corner of Spell cards. */
    static void cornerEmblem(Graphics2D g, CardDef card, Rectangle box) {
        emblem(g, effectEmblem(card), box, GOLD);
    }

    private static String effectEmblem(CardDef card) {
        if (card.effect() instanceof Effect.Destroy) return "Remove";
        return card.effect() instanceof Effect.Disable ? "Disable" : "Spell";
    }

    private static void emblem(Graphics2D g, String name, Rectangle box, Color color) {
        Graphics2D local = (Graphics2D) g.create();
        try {
            local.translate(box.x, box.y);
            local.scale(box.width, box.height);
            local.setColor(color);
            local.setStroke(new BasicStroke(.08f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (name) {
                case "Axemen" -> local.fill(shield());
                case "Riders" -> horseshoe(local);
                case "Bowmen" -> bow(local);
                case "Knights" -> local.fill(crown());
                case "Remove" -> local.fill(bolt());
                case "Disable" -> brokenLink(local);
                default -> initial(local, name);
            }
        } finally { local.dispose(); }
    }

    private static Shape shield() {
        Path2D path = new Path2D.Double();
        path.moveTo(.15, .1); path.lineTo(.85, .1); path.lineTo(.85, .5);
        path.quadTo(.82, .8, .5, .95); path.quadTo(.18, .8, .15, .5); path.closePath();
        return path;
    }

    private static void horseshoe(Graphics2D g) {
        g.setStroke(new BasicStroke(.16f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Double(.18, .12, .64, .7, -30, 240, Arc2D.OPEN));
    }

    private static void bow(Graphics2D g) {
        g.draw(new Arc2D.Double(.12, .08, .56, .84, -80, 160, Arc2D.OPEN));
        g.setStroke(new BasicStroke(.04f));
        g.draw(new Line2D.Double(.45, .1, .45, .9));
        g.setStroke(new BasicStroke(.06f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(.2, .5, .92, .5));
        Path2D head = new Path2D.Double();
        head.moveTo(.95, .5); head.lineTo(.8, .4); head.lineTo(.8, .6); head.closePath();
        g.fill(head);
    }

    private static Shape crown() {
        Path2D path = new Path2D.Double();
        path.moveTo(.1, .8); path.lineTo(.1, .3); path.lineTo(.3, .55); path.lineTo(.5, .2);
        path.lineTo(.7, .55); path.lineTo(.9, .3); path.lineTo(.9, .8); path.closePath();
        return path;
    }

    private static Shape bolt() {
        Path2D path = new Path2D.Double();
        path.moveTo(.6, .02); path.lineTo(.22, .55); path.lineTo(.48, .55);
        path.lineTo(.36, .98); path.lineTo(.8, .4); path.lineTo(.52, .4); path.closePath();
        return path;
    }

    private static void brokenLink(Graphics2D g) {
        g.setStroke(new BasicStroke(.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        AffineTransform tilt = AffineTransform.getRotateInstance(-Math.PI / 4, .5, .5);
        g.draw(tilt.createTransformedShape(new Arc2D.Double(.08, .3, .42, .4, 40, 280, Arc2D.OPEN)));
        g.draw(tilt.createTransformedShape(new Arc2D.Double(.5, .3, .42, .4, 220, 280, Arc2D.OPEN)));
    }

    private static void initial(Graphics2D g, String name) {
        g.fill(new Ellipse2D.Double(.2, .2, .6, .6));
        g.setColor(Theme.CREAM);
        g.setFont(Theme.bold(1f).deriveFont(.5f));
        String letter = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
        g.drawString(letter, .5f - g.getFontMetrics().stringWidth(letter) / 2f, .68f);
    }

    /** The card colour of a region, or a stable tint for other regions. */
    static Color regionColor(String region) {
        return switch (region) {
            case "Hungary" -> new Color(0xB8693E);
            case "Poland" -> new Color(0xC98F8C);
            case "Bohemia" -> new Color(0x8A5A86);
            case "Austria" -> new Color(0xB23E3A);
            case "Styria" -> new Color(0x5B8A4C);
            case "Croatia" -> new Color(0x5F6B78);
            case "Transylvania" -> new Color(0x3F6EA8);
            default -> Color.getHSBColor((region.hashCode() & 0xFF) / 255f, .35f, .7f);
        };
    }
}
