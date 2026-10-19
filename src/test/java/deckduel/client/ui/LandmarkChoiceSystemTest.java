package deckduel.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import deckduel.client.ClientSettings;
import deckduel.client.DeckDuelClient;
import deckduel.client.ScoringSounds;
import deckduel.client.state.ClientModel;
import deckduel.core.card.MatchPhase;
import deckduel.db.Database;
import deckduel.db.SqliteUserDao;
import deckduel.server.GameServer;
import deckduel.server.ServerConfig;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import javafx.geometry.Point2D;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The real client against a real server with production settings, in the player's 1470×923 window:
 * a practice match's Landmarks are chosen on the JavaFX table and round one is dealt.
 */
class LandmarkChoiceSystemTest {
    @TempDir Path temporary;

    private static void waitFor(BooleanSupplier condition, String what) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) fail("timed out waiting until " + what);
            Thread.sleep(25);
        }
    }

    private static void click(CardTableView table, java.awt.Rectangle target) {
        Point2D point = new Point2D(target.getCenterX(), target.getCenterY());
        table.pressAt(point);
        table.releaseAt(point);
    }

    @Test void clickingTwoOffersThenKeepStartsThePracticeMatch() throws Exception {
        try (var database = new Database(temporary.resolve("choice.db"))) {
            var server = new GameServer(ServerConfig.defaults(0), new SqliteUserDao(database));
            int port = server.start();
            ClientModel[] model = new ClientModel[1];
            JFrame[] frame = new JFrame[1];
            FxScreens[] screens = new FxScreens[1];
            DeckDuelClient[] client = new DeckDuelClient[1];
            try {
                SwingUtilities.invokeAndWait(() -> {
                    client[0] = new DeckDuelClient(message -> SwingUtilities.invokeLater(() -> model[0].handleMessage(message)), reason -> { });
                    model[0] = new ClientModel(client[0]);
                    frame[0] = new JFrame("menu");
                    frame[0].setBounds(0, 25, 1470, 923);
                    frame[0].setVisible(true);
                    screens[0] = new FxScreens(model[0], new ClientSettings(), new ScoringSounds(() -> false), frame[0], () -> { },
                            () -> false, bounds -> { });
                    model[0].register("127.0.0.1", port, "chooser", "secret123");
                });
                waitFor(() -> model[0].me() != null, "signed in");
                SwingUtilities.invokeAndWait(() -> model[0].startPractice(false));
                waitFor(() -> model[0].match() != null, "the practice match arrived");
                waitFor(() -> { try { return FxTesting.call(screens[0]::stageShowing); } catch (Exception e) { return false; } }, "the table shows");
                Thread.sleep(500);
                FxTesting.run(() -> {
                    CardTableView table = screens[0].table();
                    click(table, table.offerBounds(0, 5));
                    click(table, table.offerBounds(1, 5));
                    click(table, table.confirmBounds());
                });
                waitFor(() -> model[0].match().view().phase() == MatchPhase.IN_PROGRESS, "both Landmarks were kept and round one dealt");
                assertEquals(2, model[0].match().view().you().landmarks().size());
                assertEquals(10, model[0].match().view().hand().size());
            } finally {
                SwingUtilities.invokeAndWait(() -> { if (screens[0] != null) screens[0].dispose(); if (frame[0] != null) frame[0].dispose(); });
                if (client[0] != null) client[0].close();
                server.stop();
            }
        }
    }
}
