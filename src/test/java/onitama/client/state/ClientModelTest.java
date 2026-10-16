package onitama.client.state;

import onitama.config.RulesConfigLoader;
import onitama.core.PlayerColor;
import onitama.core.Square;
import onitama.core.duel.ActionError;
import onitama.core.duel.CardInstance;
import onitama.core.duel.GameAction;
import onitama.core.duel.GameState;
import onitama.core.duel.PlayerView;
import onitama.core.duel.RulesConfig;
import onitama.core.duel.RulesEngine;
import onitama.core.duel.Targeting;
import onitama.core.duel.TurnPhase;
import onitama.net.ActionReceipt;
import onitama.net.ActionReply;
import onitama.net.ActionRequest;
import onitama.net.MatchSync;
import onitama.net.Message;
import onitama.client.ui.BoardPanel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientModelTest {

    private static final class RecordingConnection implements ServerConnection {
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

    private RecordingConnection connection;
    private ClientModel model;
    private RulesConfig rules;

    @BeforeEach
    void setUp() {
        connection = new RecordingConnection();
        model = new ClientModel(connection);
        rules = RulesConfigLoader.load(null);
    }

    @Test
    void matchSyncInitializesGameScreenAndSequence() {
        GameState game = RulesEngine.newGame(rules, 42L);
        PlayerView blueView = RulesEngine.project(game, PlayerColor.BLUE);
        MatchSync sync = new MatchSync("m-1", "ROOM1", blueView, false, 1L);

        model.handleMessage(sync);

        assertEquals(Screen.GAME, model.screen());
        assertEquals(blueView, model.view());
        assertNull(model.inFlightRequest());
        assertEquals("ROOM1", model.roomCode());
    }

    @Test
    void allowsOnlyOneInFlightRequestUntilReplyArrives() {
        GameState game = RulesEngine.newGame(rules, 42L);
        // Force BLUE turn for testing
        while (game.turn() != PlayerColor.BLUE) {
            game = RulesEngine.newGame(rules, System.nanoTime());
        }
        PlayerView view = RulesEngine.project(game, PlayerColor.BLUE);
        MatchSync sync = new MatchSync("m-1", "ROOM1", view, false, 1L);
        model.handleMessage(sync);

        assertTrue(model.canSkipMovement());
        model.skipMovement();

        assertNotNull(model.inFlightRequest());
        assertEquals(1, connection.sent.size());
        ActionRequest req1 = (ActionRequest) connection.sent.get(0);
        assertEquals(1L, req1.sequence());
        assertTrue(req1.action() instanceof GameAction.SkipMovement);

        // Attempting another action while in flight must be rejected locally
        assertFalse(model.sendAction(new GameAction.SkipMovement()));
        assertEquals(1, connection.sent.size(), "no second request while first is in flight");

        // ActionReply arrives for sequence 1
        PlayerView nextView = new PlayerView(
                view.viewer(), view.rules(), view.revision() + 1, view.pieces(),
                view.turn(), TurnPhase.SKILL, view.startedTurns(), view.completedTurns(),
                view.blueMovementHand(), view.redMovementHand(), view.ownSkillHand(),
                view.opponentSkillCount(), view.blueMana(), view.redMana(), view.reservedMana(),
                view.movementDeckCount(), view.skillDeckCount(), view.spentSkills(),
                null, null);
        MatchSync nextSync = new MatchSync("m-1", "ROOM1", nextView, false, 2L);
        ActionReply reply = new ActionReply("m-1", 1L, new ActionReceipt(true, view.revision() + 1, null), nextSync);

        model.handleMessage(reply);

        assertNull(model.inFlightRequest(), "in-flight request cleared after matching reply");
        assertEquals(TurnPhase.SKILL, model.view().phase());
        assertTrue(model.canEndTurn());
    }

    @Test
    void actionRejectionClearsInFlightAndFiresError() throws Exception {
        GameState game = RulesEngine.newGame(rules, 42L);
        while (game.turn() != PlayerColor.BLUE) {
            game = RulesEngine.newGame(rules, System.nanoTime());
        }
        PlayerView view = RulesEngine.project(game, PlayerColor.BLUE);
        MatchSync sync = new MatchSync("m-1", "ROOM1", view, false, 1L);
        model.handleMessage(sync);

        List<String> errors = new ArrayList<>();
        model.addListener(new ClientModelListener() {
            @Override
            public void onError(String text) {
                errors.add(text);
            }
        });

        model.skipMovement();
        assertNotNull(model.inFlightRequest());

        ActionReply rejectReply = new ActionReply("m-1", 1L,
                new ActionReceipt(false, view.revision(), ActionError.WRONG_PHASE), sync);
        model.handleMessage(rejectReply);

        assertNull(model.inFlightRequest());
        javax.swing.SwingUtilities.invokeAndWait(() -> {});
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("WRONG_PHASE"));
    }

    @Test
    void boardPanelCoordinateConversionIsBijectiveForBothColors() {
        BoardPanel panel = new BoardPanel();
        panel.setSize(560, 560);
        panel.computeGeometry();

        // 1. Blue viewer
        panel.setPieces(List.of(), PlayerColor.BLUE);

        // Blue home row y=0 sits at display bottom
        assertEquals(0, panel.displayX(0));
        assertEquals(6, panel.displayY(0));
        // Canonical (0,0) mouse click in cell (col=0, row=6)
        int blueClickX = panel.originX() + 0 * panel.cell() + panel.cell() / 2;
        int blueClickY = panel.originY() + 6 * panel.cell() + panel.cell() / 2;
        Square blueSquare = panel.squareAt(blueClickX, blueClickY);
        assertEquals(new Square(0, 0), blueSquare);

        // Canonical (6,6) (Red row) sits at display top
        assertEquals(6, panel.displayX(6));
        assertEquals(0, panel.displayY(6));
        int redRowClickX = panel.originX() + 6 * panel.cell() + panel.cell() / 2;
        int redRowClickY = panel.originY() + 0 * panel.cell() + panel.cell() / 2;
        assertEquals(new Square(6, 6), panel.squareAt(redRowClickX, redRowClickY));

        // 2. Red viewer
        panel.setPieces(List.of(), PlayerColor.RED);

        // Red home row y=6 sits at display bottom
        assertEquals(6 - 6, panel.displayX(6)); // col 0
        assertEquals(6, panel.displayY(6));     // row 6
        int redClickX = panel.originX() + 0 * panel.cell() + panel.cell() / 2;
        int redClickY = panel.originY() + 6 * panel.cell() + panel.cell() / 2;
        Square redHomeSquare = panel.squareAt(redClickX, redClickY);
        assertEquals(new Square(6, 6), redHomeSquare);

        // Blue home row y=0 sits at display top
        assertEquals(6 - 0, panel.displayX(0)); // col 6
        assertEquals(0, panel.displayY(0));     // row 0
        int blueRowClickX = panel.originX() + 6 * panel.cell() + panel.cell() / 2;
        int blueRowClickY = panel.originY() + 0 * panel.cell() + panel.cell() / 2;
        assertEquals(new Square(0, 0), panel.squareAt(blueRowClickX, blueRowClickY));
    }

    @Test
    void practiceMatchExecutesLocallyWithoutServerConnection() throws Exception {
        model.startPracticeMatch(onitama.core.Difficulty.ROOKIE);
        assertEquals(Screen.GAME, model.screen());
        assertTrue(model.isPracticeMode());
        assertNotNull(model.view());
        assertEquals("PRACTICE", model.roomCode());
        assertEquals(0, connection.sent.size(), "practice mode sends no network messages");

        // Blue human plays locally
        if (model.view().turn() == PlayerColor.BLUE && model.view().phase() == TurnPhase.MOVEMENT) {
            model.skipMovement();
            assertEquals(0, connection.sent.size(), "action applied locally");
            assertEquals(TurnPhase.SKILL, model.view().phase());
            model.endTurn();
            assertEquals(0, connection.sent.size());
            assertEquals(PlayerColor.RED, model.view().turn());
        }
    }
    @Test
    void botActsWhenPracticeStartsOnRed() throws Exception {
        long seed = 0;
        while (RulesEngine.newGame(rules, seed).turn() != PlayerColor.RED) seed++;
        final long redSeed = seed;
        var acted = new java.util.concurrent.CountDownLatch(1);
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            model.addListener(new ClientModelListener() {
                @Override public void onMatchChanged() {
                    if (model.view() != null && model.view().revision() > 0) acted.countDown();
                }
            });
            model.startPracticeMatch(onitama.core.Difficulty.ROOKIE, redSeed);
            assertEquals(PlayerColor.RED, model.view().turn());
        });
        try {
            assertTrue(acted.await(3, java.util.concurrent.TimeUnit.SECONDS), "opening bot turn must run");
        } finally {
            javax.swing.SwingUtilities.invokeAndWait(model::resign);
        }
    }

    @Test
    void reconnectResendsOriginalUnacknowledgedRequestUntilReceipt() {
        GameState game = RulesEngine.newGame(rules, 42L);
        var view = RulesEngine.viewFor(game, game.turn());
        var sync = new MatchSync("retry-match", "ROOM", view, false, 1);
        model.handleMatchSync(sync);
        model.skipMovement();
        ActionRequest original = model.inFlightRequest();
        assertNotNull(original);
        // Server may have applied it before the connection dropped.
        var accepted = (onitama.core.duel.ActionResult.Accepted)
                RulesEngine.apply(game, game.turn(), original.action());
        var recovered = new MatchSync(sync.matchId(), "ROOM",
                RulesEngine.viewFor(accepted.nextState(), game.turn()), false, 2);
        model.handleMatchSync(recovered);
        assertEquals(List.of(original, original), connection.sent);
        assertEquals(original, model.inFlightRequest());
        assertFalse(model.sendAction(new GameAction.EndTurn()));
        model.handleActionReply(new ActionReply(sync.matchId(), original.sequence(),
                new ActionReceipt(true, recovered.view().revision(), null), recovered));
        assertNull(model.inFlightRequest());
        assertEquals(2, model.currentSync().nextSequence());
    }

    @Test
    void newMatchClearsPendingActionAndOlderViewsCannotRewindIt() {
        GameState game = RulesEngine.newGame(rules, 42L);
        var view = RulesEngine.viewFor(game, game.turn());
        model.handleMatchSync(new MatchSync("old", "ROOM", view, false, 1));
        model.skipMovement();
        assertNotNull(model.inFlightRequest());
        model.handleMatchSync(new MatchSync("new", "ROOM", view, false, 1));
        assertNull(model.inFlightRequest());
        assertEquals(1, connection.sent.size(), "never resend old match actions");
        var accepted = (onitama.core.duel.ActionResult.Accepted)
                RulesEngine.apply(game, game.turn(), new GameAction.SkipMovement());
        var advanced = new MatchSync("new", "ROOM",
                RulesEngine.viewFor(accepted.nextState(), game.turn()), false, 2);
        model.adoptSync(advanced);
        model.adoptSync(new MatchSync("new", "ROOM", view, false, 1));
        assertEquals(advanced, model.currentSync());
    }

}
