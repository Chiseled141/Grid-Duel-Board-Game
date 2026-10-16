package onitama.client.ui;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import onitama.config.RulesConfigLoader;
import onitama.core.CardDeck;
import onitama.core.PlayerColor;
import onitama.core.duel.RulesEngine;
import onitama.client.state.*;
import onitama.net.*;

/** Offline visual QA sheets and a complete practice-layout preview for the medieval reskin. */
public final class RenderMedievalPreview {
    /** Writes card, skill, board-style and game previews to the supplied directory. */
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]); Files.createDirectories(output);
        var rules = RulesConfigLoader.load(null);
        var movements = new BufferedImage(1000, 1200, BufferedImage.TYPE_INT_RGB);
        var g = movements.createGraphics(); g.setColor(new Color(185, 172, 151)); g.fillRect(0, 0, 1000, 1200);
        int index = 0;
        for (var card : rules.movementCards()) {
            g.drawImage(CardArt.movementFace(card, PlayerColor.BLUE), 16 + index % 4 * 248,
                    12 + index / 4 * 296, 230, 276, null); index++;
        }
        g.dispose(); ImageIO.write(movements, "png", output.resolve("movement-cards.png").toFile());
        var skills = new BufferedImage(1260, 1040, BufferedImage.TYPE_INT_RGB);
        g = skills.createGraphics(); g.setColor(new Color(185, 172, 151)); g.fillRect(0, 0, 1260, 1040);
        index = 0;
        for (var skill : rules.skills()) {
            g.drawImage(CardArt.skillFace(skill), 10 + index % 6 * 208,
                    10 + index / 6 * 256, 200, 240, null); index++;
        }
        g.dispose(); ImageIO.write(skills, "png", output.resolve("skill-cards.png").toFile());
        var reference = new java.util.concurrent.atomic.AtomicReference<GamePanel>();
        SwingUtilities.invokeAndWait(() -> {
            var model = new ClientModel(new ServerConnection() {
                public void send(Message message) { }
                public boolean isConnected() { return true; }
                public void ensureConnected(String host, int port) { }
            });
            var panel = new GamePanel(model);
            var game = RulesEngine.newGame(rules, 42);
            model.handleMatchSync(new MatchSync("preview", "LOCAL", RulesEngine.viewFor(game, game.turn()), false, 1));
            panel.setSize(1440, 900); layout(panel);
            int played = 0;
            for (var field : GamePanel.class.getDeclaredFields()) {
                if (field.getType() != PlayedCardArea.class) continue;
                try {
                    field.setAccessible(true);
                    ((PlayedCardArea) field.get(panel)).showFace(CardArt.skillFace(rules.skill(played++ == 0 ? "rally" : "pierce")));
                } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
            }
            reference.set(panel);
        });
        Thread.sleep(600);
        SwingUtilities.invokeAndWait(() -> {
            try {
                var panel = reference.get(); layout(panel);
                var image = new BufferedImage(1440, 900, BufferedImage.TYPE_INT_RGB);
                var graphics = image.createGraphics(); panel.paint(graphics); graphics.dispose();
                ImageIO.write(image, "png", output.resolve("game.png").toFile());
                panel.removeNotify();
            } catch (Exception exception) { throw new IllegalStateException(exception); }
        });
    }

    private static void layout(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents()) if (child instanceof Container container) layout(container);
    }
}
