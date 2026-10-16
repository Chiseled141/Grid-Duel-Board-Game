package onitama.client.ui;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import onitama.core.PlayerColor;

/** Crisp heraldic tabletop pieces; every piece on a side has the same identity. */
final class MedievalPieces {
    private static final Map<String, BufferedImage> IMAGES = new ConcurrentHashMap<>();
    private MedievalPieces() { }

    static Image image(PlayerColor color, int style) {
        return IMAGES.computeIfAbsent(color.name() + style, key -> render(color, style));
    }

    private static BufferedImage render(PlayerColor color, int style) {
        var image = new BufferedImage(240, 240, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color side = Theme.playerTextColor(color);
            g.setColor(new Color(20, 15, 10, 85)); g.fillOval(28, 194, 184, 30);
            g.setColor(new Color(61, 46, 33)); g.fillRoundRect(39, 185, 162, 29, 14, 14);
            g.setColor(side); g.fillRoundRect(42, 178, 156, 28, 13, 13);
            g.setStroke(new BasicStroke(5)); g.setColor(new Color(48, 35, 25));
            g.drawRoundRect(42, 178, 156, 28, 13, 13);
            if (style == 2) {
                var shield = new Path2D.Double();
                shield.moveTo(57, 43); shield.lineTo(183, 43); shield.lineTo(178, 123);
                shield.quadTo(169, 161, 120, 191); shield.quadTo(71, 161, 62, 123); shield.closePath();
                g.setColor(new Color(208, 181, 125)); g.fill(shield);
                g.setColor(Theme.INK); g.draw(shield);
                var field = new Path2D.Double();
                field.moveTo(69, 55); field.lineTo(171, 55); field.lineTo(166, 122);
                field.quadTo(155, 155, 120, 177); field.quadTo(85, 155, 74, 122); field.closePath();
                g.setColor(side); g.fill(field);
                g.setColor(Theme.CREAM); g.setStroke(new BasicStroke(9));
                g.drawLine(120, 67, 120, 156); g.drawLine(87, 101, 153, 101);
            } else if (style == 3) {
                g.setColor(side);
                g.fillOval(86, 31, 68, 68); g.fillRoundRect(84, 97, 72, 25, 9, 9);
                var body = new Path2D.Double();
                body.moveTo(96, 117); body.lineTo(144, 117); body.lineTo(168, 176);
                body.lineTo(72, 176); body.closePath(); g.fill(body);
                g.setColor(Theme.INK); g.drawOval(86, 31, 68, 68); g.draw(body);
                g.setColor(new Color(224, 197, 147)); g.drawLine(83, 172, 157, 172);
            } else {
                var helm = new Path2D.Double();
                helm.moveTo(64, 170); helm.lineTo(67, 77); helm.quadTo(78, 40, 120, 20);
                helm.quadTo(163, 40, 173, 77); helm.lineTo(176, 170);
                helm.quadTo(120, 207, 64, 170); helm.closePath();
                g.setPaint(new GradientPaint(68, 50, new Color(210, 213, 201), 172, 170, new Color(119, 130, 129)));
                g.fill(helm); g.setColor(Theme.INK); g.draw(helm);
                g.setColor(side); g.fillRect(75, 98, 90, 25);
                g.setColor(new Color(31, 31, 29)); g.fillRect(78, 97, 37, 12); g.fillRect(125, 97, 37, 12);
                g.setColor(new Color(225, 205, 163)); g.fillRect(116, 41, 8, 137);
                g.setColor(Theme.INK); g.setStroke(new BasicStroke(2));
                for (int x = 84; x <= 156; x += 18) g.fillOval(x, 151, 4, 4);
                g.drawLine(116, 45, 116, 177); g.drawLine(124, 45, 124, 177);
            }
        } finally { g.dispose(); }
        return image;
    }
}
