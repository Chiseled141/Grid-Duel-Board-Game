package deckduel.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import deckduel.cards.CatalogLoader;
import deckduel.client.ClientSettings;
import deckduel.client.ScoringSounds;
import deckduel.client.state.ClientModel;
import deckduel.client.state.Screen;
import deckduel.client.state.ServerConnection;
import deckduel.core.card.DeckRules;
import deckduel.core.card.MatchState;
import deckduel.core.card.PlayCard;
import deckduel.core.card.PlayerView;
import deckduel.net.Message;
import deckduel.net.MatchUpdate;
import deckduel.net.MoveRequest;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** The hand-over between the Swing menu window and the JavaFX table window, across both UI threads. */
class FxScreensTest {
    private static void waitFor(BooleanSupplier condition, String what) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) fail("timed out waiting until " + what);
            Thread.sleep(25);
        }
    }

    private static boolean onEdt(BooleanSupplier check) throws Exception {
        boolean[] result = new boolean[1];
        SwingUtilities.invokeAndWait(() -> result[0] = check.getAsBoolean());
        return result[0];
    }

    @Test void aMatchMovesToTheJavaFxWindowPlaysGoThroughTheModelAndLeavingReturnsToSwing() throws Exception {
        List<Message> sent = new CopyOnWriteArrayList<>();
        ClientModel[] model = new ClientModel[1];
        JFrame[] frame = new JFrame[1];
        FxScreens[] screens = new FxScreens[1];
        SwingUtilities.invokeAndWait(() -> {
            model[0] = new ClientModel(new ServerConnection() {
                public void send(Message message) { sent.add(message); }
                public boolean isConnected() { return true; }
                public void ensureConnected(deckduel.client.startup.ServerRoute route) { }
            });
            model[0].setReducedMotion(true);
            frame[0] = new JFrame("menu");
            frame[0].setBounds(40, 40, 1280, 720);
            frame[0].setVisible(true);
            screens[0] = new FxScreens(model[0], new ClientSettings(), new ScoringSounds(() -> false), frame[0], () -> { },
                    () -> false, bounds -> { });
        });
        try {
            var catalog = CatalogLoader.loadBundled();
            var deck = DeckRules.deck(catalog);
            MatchState state = MatchState.start(deck, deck, 3);
            PlayerView view = state.view(state.turn());
            SwingUtilities.invokeAndWait(() -> model[0].handleMessage(
                    new MatchUpdate("M1", 1, "alice", "bob", false, true, false, false, false, view)));
            assertEquals(Screen.TABLE, model[0].screen());
            waitFor(() -> { try { return FxTesting.call(screens[0]::stageShowing); } catch (Exception e) { return false; } },
                    "the JavaFX window shows the table");
            waitFor(() -> { try { return !onEdt(frame[0]::isVisible); } catch (Exception e) { return false; } },
                    "the Swing frame steps aside");

            int unit = 0;
            while (!view.hand().get(unit).def().occupiesSpace()) unit++;
            PlayCard play = PlayCard.place(view.hand().get(unit).id(), 0);
            int chosen = unit;
            FxTesting.run(() -> {
                CardTableView table = screens[0].table();
                assertTrue(table.rail().description().contains("Round 1 of 3"), "the table received the snapshot");
                var card = table.handBounds(chosen);
                var space = table.slotBounds(TableZone.OWN, 0);
                for (var target : List.of(card, space)) {
                    var point = new javafx.geometry.Point2D(target.getCenterX(), target.getCenterY());
                    table.pressAt(point);
                    table.releaseAt(point);
                }
            });
            waitFor(() -> !sent.isEmpty(), "the play reaches the model on the EDT");
            assertEquals(play, ((MoveRequest) sent.get(0)).move());

            SwingUtilities.invokeAndWait(model[0]::leaveMatch);
            waitFor(() -> { try { return onEdt(frame[0]::isVisible); } catch (Exception e) { return false; } },
                    "the Swing menu comes back");
            waitFor(() -> { try { return !FxTesting.call(screens[0]::stageShowing); } catch (Exception e) { return true; } },
                    "the JavaFX window hides");
        } finally {
            SwingUtilities.invokeAndWait(() -> { screens[0].dispose(); frame[0].dispose(); });
        }
    }
}
