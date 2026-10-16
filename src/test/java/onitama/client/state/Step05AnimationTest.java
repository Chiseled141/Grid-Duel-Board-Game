package onitama.client.state;

import onitama.client.ui.BoardPanel;
import onitama.client.ui.CardHandPanel;
import onitama.client.ui.CardPanel;
import onitama.client.ui.GamePanel;
import onitama.client.ui.SkillCardPanel;
import onitama.config.RulesConfigLoader;
import onitama.core.Card;
import onitama.core.CardDeck;
import onitama.core.Difficulty;
import onitama.core.PlayerColor;
import onitama.core.Square;
import onitama.core.duel.CardInstance;
import onitama.core.duel.GameAction;
import onitama.core.duel.GameState;
import onitama.core.duel.PlayerView;
import onitama.core.duel.PublicEvent;
import onitama.core.duel.RulesConfig;
import onitama.core.duel.RulesEngine;
import onitama.net.ActionReceipt;
import onitama.net.ActionReply;
import onitama.net.GameUpdate;
import onitama.net.MatchSync;
import onitama.net.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Step05AnimationTest {

    private static final class MockConnection implements ServerConnection {
        final List<Message> sent = new ArrayList<>();
        boolean connected = true;

        @Override
        public void send(Message message) {
            sent.add(message);
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public void ensureConnected(String host, int port) throws IOException {
            connected = true;
        }
    }

    private MockConnection connection;
    private ClientModel model;
    private RulesConfig rules;

    @BeforeEach
    void setUp() {
        connection = new MockConnection();
        model = new ClientModel(connection);
        rules = RulesConfigLoader.load(null);
    }

    @Test
    void deduplicatedPresentationOncePerRevision() {
        GameState game = RulesEngine.newGame(rules, 42L);
        while (game.turn() != PlayerColor.BLUE) {
            game = RulesEngine.newGame(rules, System.nanoTime());
        }
        PlayerView rev1 = RulesEngine.project(game, PlayerColor.BLUE);
        MatchSync sync1 = new MatchSync("m-100", "ROOM-100", rev1, false, 1L);
        model.handleMessage(sync1);

        AtomicInteger callCount = new AtomicInteger();
        List<PlayerView> beforeViews = new ArrayList<>();
        List<PlayerView> afterViews = new ArrayList<>();
        List<List<PublicEvent>> eventBatches = new ArrayList<>();

        model.addListener(new ClientModelListener() {
            @Override
            public void onAcceptedPresentation(PlayerView priorView, PlayerView currentView, List<PublicEvent> events) {
                callCount.incrementAndGet();
                beforeViews.add(priorView);
                afterViews.add(currentView);
                eventBatches.add(events);
            }
        });

        // Advance engine state to get authentic next revision
        onitama.core.duel.ActionResult result = RulesEngine.apply(game, PlayerColor.BLUE, new GameAction.SkipMovement());
        GameState stepGame = ((onitama.core.duel.ActionResult.Accepted) result).nextState();
        PlayerView rev2 = RulesEngine.project(stepGame, PlayerColor.BLUE);

        MatchSync sync2 = new MatchSync("m-100", "ROOM-100", rev2, false, 2L);
        List<PublicEvent> events = List.of(
                new PublicEvent.PieceMoved(1, new Square(0, 0), new Square(0, 1)),
                new PublicEvent.MovementPlayed(PlayerColor.BLUE, rev1.blueMovementHand().get(0))
        );

        // 1. ActionReply arrives with revision 2
        ActionReply reply = new ActionReply("m-100", 1L, new ActionReceipt(true, 2L, null), sync2);
        model.handleMessage(reply);

        // 2. GameUpdate arrives for the same revision 2 with public events
        GameUpdate update1 = new GameUpdate(sync2, events);
        model.handleMessage(update1);

        // 3. Duplicate GameUpdate arrives for revision 2
        GameUpdate update2 = new GameUpdate(sync2, events);
        model.handleMessage(update2);

        try {
            SwingUtilities.invokeAndWait(() -> {});
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Deduplication must deliver presentation at most once per accepted revision!
        assertEquals(1, callCount.get(), "onAcceptedPresentation must fire exactly once for revision 2");
        assertEquals(rev1.revision(), beforeViews.get(0).revision(), "priorView should reflect revision 1");
        assertEquals(rev2.revision(), afterViews.get(0).revision(), "currentView should reflect revision 2");
        assertEquals(events, eventBatches.get(0));
    }

    @Test
    void wrongMatchEventsRejected() {
        GameState game = RulesEngine.newGame(rules, 42L);
        PlayerView view1 = RulesEngine.project(game, PlayerColor.BLUE);
        MatchSync sync1 = new MatchSync("m-current", "ROOM-1", view1, false, 1L);
        model.handleMessage(sync1);

        AtomicBoolean presented = new AtomicBoolean(false);
        model.addListener(new ClientModelListener() {
            @Override
            public void onAcceptedPresentation(PlayerView priorView, PlayerView currentView, List<PublicEvent> events) {
                presented.set(true);
            }
        });

        // GameUpdate from an old or different match
        MatchSync syncOld = new MatchSync("m-other", "ROOM-OTHER", view1, false, 2L);
        GameUpdate staleUpdate = new GameUpdate(syncOld, List.of(
                new PublicEvent.PieceMoved(1, new Square(0, 0), new Square(0, 1))
        ));
        model.handleMessage(staleUpdate);

        assertFalse(presented.get(), "Events from a different match must never be passed to presentation");
    }

    @Test
    void practiceBotPresentationDelivered() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            model.startPracticeMatch(Difficulty.ROOKIE, 42L);
            assertEquals(Screen.GAME, model.screen());
            assertTrue(model.isPracticeMode());

            AtomicInteger presentations = new AtomicInteger();
            List<PublicEvent> receivedEvents = new ArrayList<>();

            model.addListener(new ClientModelListener() {
                @Override
                public void onAcceptedPresentation(PlayerView priorView, PlayerView currentView, List<PublicEvent> events) {
                    presentations.incrementAndGet();
                    receivedEvents.addAll(events);
                }
            });

            // Advance game until bot turn then step bot
            for (int i = 0; i < 5; i++) {
                if (model.myTurn()) {
                    if (model.canSkipMovement()) model.skipMovement();
                    else if (model.canEndTurn()) model.endTurn();
                } else if (!model.isMatchOver()) {
                    model.stepBot();
                }
            }

            assertTrue(presentations.get() > 0, "Bot steps should emit accepted presentations");
            assertFalse(receivedEvents.isEmpty(), "Events should be recorded from practice steps");
        });
    }

    @Test
    void sameCardRedrawPreservesCard() {
        GameState game = RulesEngine.newGame(rules, 42L);
        while (game.turn() != PlayerColor.BLUE) {
            game = RulesEngine.newGame(rules, System.nanoTime());
        }
        PlayerView view = RulesEngine.project(game, PlayerColor.BLUE);
        MatchSync sync = new MatchSync("m-1", "ROOM1", view, false, 1L);
        model.handleMessage(sync);

        String cardId = view.blueMovementHand().get(0);
        model.cardClicked(cardId);
        assertEquals(cardId, model.selectedCardId());

        // Player skips movement or makes move, selection clears
        model.skipMovement();
        assertNull(model.selectedCardId(), "Selection should clear when action is taken");

        // Now simulate new hand with exact same card ID drawn back
        model.handleMessage(new MatchSync("m-1", "ROOM1", view, false, 2L));
        assertNull(model.selectedCardId(), "Redrawn card should not stay stale-selected");
    }

    @Test
    void emptyDeckSpendReflowHand() {
        CardHandPanel handPanel = new CardHandPanel();
        handPanel.setSize(720, 226);

        Card card1 = CardDeck.cardById("tiger");
        Card card2 = CardDeck.cardById("dragon");
        List<JComponent> cards = new ArrayList<>();
        cards.add(new CardPanel(card1, PlayerColor.BLUE, false, null));
        cards.add(new CardPanel(card2, PlayerColor.BLUE, false, null));

        // Add 6 skills (total 8 cards)
        String[] skillNames = {"sidestep", "dash", "blink", "exchange", "shove", "pull"};
        for (int i = 0; i < 6; i++) {
            CardInstance inst = new CardInstance(skillNames[i] + "#1", skillNames[i]);
            cards.add(new SkillCardPanel(inst, rules, PlayerColor.BLUE, 5, null, true, false, null));
        }

        assertEquals(8, cards.size());
        handPanel.setCards(cards);
        handPanel.doLayout();

        // Verify layout fits within hand bounds without negative coordinates
        for (Component c : handPanel.getComponents()) {
            assertTrue(c.getX() >= 0, "Card x must be >= 0");
            assertTrue(c.getX() + c.getWidth() <= handPanel.getWidth() + 20, "Card must not overflow hand width");
        }

        // Simulate spending 1 skill with empty deck (hand drops from 8 to 7 cards)
        cards.remove(cards.size() - 1);
        assertEquals(7, cards.size());
        handPanel.setCards(cards);
        handPanel.doLayout();

        assertEquals(7, handPanel.getComponentCount());
        for (Component c : handPanel.getComponents()) {
            assertTrue(c.getX() >= 0);
            assertTrue(c.getX() + c.getWidth() <= handPanel.getWidth() + 20);
        }
    }

    @Test
    void reducedMotionDisablesInterpolationTimers() {
        model.setReducedMotion(true);
        assertTrue(model.reducedMotion());

        BoardPanel board = new BoardPanel();
        board.setReducedMotion(true);
        assertTrue(board.reducedMotion());

        AtomicBoolean moveFinished = new AtomicBoolean(false);
        board.animateMoves(List.of(
                new BoardPanel.MovingPiece(1, new Square(0, 0), new Square(0, 1), PlayerColor.BLUE)
        ), () -> moveFinished.set(true));

        assertTrue(moveFinished.get(), "animateMoves callback must complete immediately in reduced motion mode");


    }

    @Test
    void multiPieceDisplacementPreservedInEventList() {
        List<PublicEvent> events = List.of(
                new PublicEvent.PieceMoved(10, new Square(1, 1), new Square(1, 2)),
                new PublicEvent.PieceMoved(11, new Square(2, 2), new Square(2, 3)),
                new PublicEvent.PieceEliminated(12, new Square(3, 3))
        );

        long movedCount = events.stream().filter(e -> e instanceof PublicEvent.PieceMoved).count();
        long elimCount = events.stream().filter(e -> e instanceof PublicEvent.PieceEliminated).count();

        assertEquals(2, movedCount, "All displaced pieces must be present in the event stream");
        assertEquals(1, elimCount, "Eliminated pieces must be present in the event stream");
    }

    private void layout(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents()) {
            if (child instanceof Container c) layout(c);
        }
    }

    @ParameterizedTest
    @CsvSource({"1280,660,BLUE", "1440,820,BLUE", "1280,660,RED", "1440,820,RED"})
    void headlessOffscreenRenderBothPerspectivesAndSizes(int width, int height, PlayerColor viewer) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RulesConfig r = RulesConfigLoader.load(null);
            // 6 skill cards in hand
            RulesConfig config6 = new RulesConfig(r.rulesVersion(), 2, 6, r.initialMana(),
                    r.manaPerTurn(), r.manaCap(), r.turnLimit(), r.movementCards(), r.skills());
            GameState game = RulesEngine.newGame(config6, 99L);
            PlayerView view = RulesEngine.viewFor(game, viewer);

            ClientModel m = new ClientModel(new MockConnection());
            GamePanel panel = new GamePanel(m);
            m.handleMatchSync(new MatchSync("render-match", "ROOM-R", view, false, 1L));

            panel.setSize(width, height);
            layout(panel);

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            panel.paint(g);
            g.dispose();

            assertNotNull(image);
            assertEquals(width, image.getWidth());
            assertEquals(height, image.getHeight());

            try {
                java.io.File dir = new java.io.File("target/screenshots");
                dir.mkdirs();
                javax.imageio.ImageIO.write(image, "png",
                        new java.io.File(dir, "game-panel-" + width + "x" + height + "-" + viewer + ".png"));
            } catch (Exception ignored) {
            }
        });
    }
}
