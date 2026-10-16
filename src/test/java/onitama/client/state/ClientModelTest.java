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
    void publicOpponentSkillAppearsWhenCommittedWithoutRevealingTheirRemainingHand() {
        GameState game = null;
        for (int seed = 0; seed < 1000; seed++) {
            var candidate = RulesEngine.newGame(rules, seed);
            if (candidate.turn() == PlayerColor.RED && RulesEngine.project(candidate, PlayerColor.RED)
                    .ownSkillHand().stream().anyMatch(c -> c.definitionId().equals("foresight"))) {
                game = candidate;
                break;
            }
        }
        assertNotNull(game);
        var actor = game.turn();
        var hidden = RulesEngine.project(game, actor).ownSkillHand();
        var initial = RulesEngine.project(game, PlayerColor.BLUE);
        model.handleMatchSync(new MatchSync("skill-history", "ROOM", initial, false, 1));
        assertTrue(model.playedCards().isEmpty());
        var skip = (onitama.core.duel.ActionResult.Accepted) RulesEngine.apply(game, actor, new GameAction.SkipMovement());
        model.handleGameUpdate(new onitama.net.GameUpdate(new MatchSync("skill-history", "ROOM",
                RulesEngine.project(skip.nextState(), PlayerColor.BLUE), false, 1), skip.events()));
        var foresight = hidden.stream().filter(c -> c.definitionId().equals("foresight")).findFirst().orElseThrow();
        var cast = (onitama.core.duel.ActionResult.Accepted) RulesEngine.apply(skip.nextState(), actor,
                new GameAction.CastSkill(foresight.instanceId(), new onitama.core.duel.SkillTargets.None()));
        model.handleGameUpdate(new onitama.net.GameUpdate(new MatchSync("skill-history", "ROOM",
                RulesEngine.project(cast.nextState(), PlayerColor.BLUE), false, 1), cast.events()));
        assertEquals(List.of(new ClientModel.PlayedCard(initial.startedTurns(), actor, "foresight", true)), model.playedCards());
        assertNull(model.view().ownChoice(), "opponent's pending preview stays private");
    }

    @Test
    void playedCardsRecordOnlyPublicCardsOnceAndResetForNewMatches() {
        var game = RulesEngine.newGame(rules, 42);
        var initial = RulesEngine.project(game, game.turn());
        model.handleMatchSync(new MatchSync("plays", "ROOM", initial, false, 1));
        assertTrue(model.playedCards().isEmpty(), "hands and spent piles are not play events");
        var move = Targeting.legalMoves(initial).get(0);
        var accepted = (onitama.core.duel.ActionResult.Accepted) RulesEngine.apply(game, game.turn(), move);
        var after = RulesEngine.project(accepted.nextState(), game.turn());
        var sync = new MatchSync("plays", "ROOM", after, false, 2);
        var update = new onitama.net.GameUpdate(sync, accepted.events());
        model.handleGameUpdate(update);
        assertEquals(List.of(new ClientModel.PlayedCard(initial.startedTurns(), game.turn(), move.movementCardId(), false)),
                model.playedCards());
        model.handleGameUpdate(update);
        assertEquals(1, model.playedCards().size());
        model.handleMatchSync(sync);
        assertEquals(1, model.playedCards().size(), "same-match reconnect keeps locally seen plays");
        model.handleGameUpdate(new onitama.net.GameUpdate(new MatchSync("foreign", "ROOM", after, false, 2),
                List.of(new onitama.core.duel.PublicEvent.SkillCommitted(game.turn().opponent(),
                        new CardInstance("dash#1", "dash")))));
        assertEquals(1, model.playedCards().size());
        model.handleMatchSync(new MatchSync("fresh", "ROOM", initial, false, 1));
        assertTrue(model.playedCards().isEmpty());
    }

    @Test
    void chatUsesServerEchoAndPreservesHistoryOnlyForSameMatch() {
        var view = RulesEngine.project(RulesEngine.newGame(rules, 42), PlayerColor.BLUE);
        var sync = new MatchSync("chat-1", "ROOM", view, false, 1);
        model.handleMatchSync(sync);
        assertTrue(model.canChat());
        assertFalse(model.sendChat("  "));
        assertFalse(model.sendChat("x".repeat(281)));
        assertTrue(model.sendChat("hello"));
        assertEquals(new onitama.net.ChatRequest("chat-1", "hello"), connection.sent.get(0));
        assertTrue(model.chatMessages().isEmpty());
        model.handleMessage(new onitama.net.ChatMessage("foreign", "other", "ignored"));
        assertTrue(model.chatMessages().isEmpty());
        for (int i = 0; i < 205; i++) {
            model.handleMessage(new onitama.net.ChatMessage("chat-1", "alice", "message " + i));
        }
        assertEquals(200, model.chatMessages().size());
        assertEquals("message 5", model.chatMessages().get(0).text());
        model.handleMatchSync(sync);
        assertEquals(200, model.chatMessages().size());
        assertEquals(1, model.currentSync().nextSequence());
        assertEquals(view, model.view());
        model.handleMatchSync(new MatchSync("chat-2", "ROOM", view, false, 1));
        assertTrue(model.chatMessages().isEmpty());
        model.handleMatchSync(new MatchSync("chat-2", "ROOM", view, true, 1));
        assertFalse(model.canChat());
        assertFalse(model.sendChat("paused"));
        model.handleMatchSync(new MatchSync("chat-2", "ROOM", view, false, 1));
        connection.connected = false;
        assertFalse(model.sendChat("offline"));
        connection.connected = true;
        model.handleMessage(new onitama.net.GameOver(null, onitama.core.duel.EndReason.DRAW, 1000, 1000));
        assertFalse(model.canChat());
        model.leaveToLobby();
        assertTrue(model.chatMessages().isEmpty());
        model.startPracticeMatch(onitama.core.Difficulty.ROOKIE, 42);
        assertFalse(model.canChat());
        model.resign();
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

    @Test void practiceBotCommitsASkillAndPublishesItWithoutNetworkActions() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            long seed = 0;
            for (; seed < 1000; seed++) {
                var game = RulesEngine.newGame(rules, seed);
                if (game.turn() != PlayerColor.RED) continue;
                var view = RulesEngine.viewFor(game, PlayerColor.RED);
                if (view.ownSkillHand().stream().anyMatch(c -> c.definitionId().equals("mana-surge"))) break;
            }
            assertTrue(seed < 1000);
            var committed = new ArrayList<onitama.core.duel.PublicEvent.SkillCommitted>();
            model.addListener(new ClientModelListener() {
                @Override public void onAcceptedPresentation(PlayerView before, PlayerView after,
                        List<onitama.core.duel.PublicEvent> events) {
                    for (var event : events) if (event instanceof onitama.core.duel.PublicEvent.SkillCommitted skill)
                        committed.add(skill);
                }
            });
            model.startPracticeMatch(onitama.core.Difficulty.SENIOR, seed);
            try {
                for (int step = 0; step < 5 && model.view().turn() == PlayerColor.RED; step++) model.stepBot();
                assertEquals(1, committed.size());
                assertEquals(PlayerColor.RED, committed.get(0).actor());
                assertTrue(model.view().spentSkills().contains(committed.get(0).skill()));
                assertEquals(PlayerColor.BLUE, model.view().turn());
                assertTrue(connection.sent.isEmpty());
            } finally { model.leaveToLobby(); }
        });
    }

}
