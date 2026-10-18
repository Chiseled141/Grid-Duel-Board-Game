package deckduel.client.ui;

import deckduel.client.state.*;
import deckduel.config.RulesConfigLoader;
import deckduel.core.PlayerColor;
import deckduel.core.duel.*;
import deckduel.net.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class OpeningHandPanelTest {
    private JFrame frame;
    private ClientModel model;
    private GamePanel game;
    private GameState state;
    private final List<Message> sent = new ArrayList<>();
    @BeforeEach void create() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            model = new ClientModel(new ServerConnection() {
                public void send(Message message) { sent.add(message); }
                public boolean isConnected() { return true; }
                public void ensureConnected(String host, int port) { }
            });
            model.setReducedMotion(true);
            state = RulesEngine.newOpeningGame(RulesConfigLoader.load(null), 42);
            game = new GamePanel(model);
            frame = new JFrame("Opening hand regression"); frame.setContentPane(game);
            frame.setSize(1440, 900); frame.setVisible(true); frame.validate();
            model.handleMatchSync(sync(1)); frame.validate();
        });
        await(() -> button("openingReplace0").isEnabled());
    }
    @AfterEach void close() throws Exception {
        SwingUtilities.invokeAndWait(() -> { model.leaveToLobby(); frame.dispose(); });
    }
    private MatchSync sync(long sequence) {
        return new MatchSync("opening-test", "TEST", RulesEngine.viewFor(state, PlayerColor.BLUE), false, sequence);
    }
    private <T> T find(Container parent, Class<T> type, String name) {
        for (Component child : parent.getComponents()) {
            if (type.isInstance(child) && (name == null || name.equals(child.getName()))) return type.cast(child);
            if (child instanceof Container nested) { T found = find(nested, type, name); if (found != null) return found; }
        }
        return null;
    }
    private JButton button(String name) { return find(game, JButton.class, name); }
    private OpeningHandPanel opening() { return find(game, OpeningHandPanel.class, null); }
    private void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + 3_000_000_000L;
        var ready = new AtomicBoolean();
        do { SwingUtilities.invokeAndWait(() -> ready.set(condition.getAsBoolean()));
            if (ready.get()) return;
            Thread.sleep(10);
        } while (System.nanoTime() < deadline);
        fail("Opening presentation did not reach its expected state");
    }
    private void accept(PlayerColor actor, GameAction action, long sequence) {
        var result = assertInstanceOf(ActionResult.Accepted.class, RulesEngine.apply(state, actor, action));
        state = result.nextState();
        model.handleMessage(new GameUpdate(sync(sequence), result.events()));
        if (actor == PlayerColor.BLUE) model.handleMessage(new ActionReply("opening-test", sequence - 1,
                new ActionReceipt(true, state.revision(), null), sync(sequence)));
    }
    @Test void markingIsLocalUndoWorksAndOnlyConfirmReplacesThenTransfersHand() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var original = model.view().ownSkillHand();
            button("openingReplace0").doClick(0); button("openingReplace2").doClick(0);
            button("openingReplace0").doClick(0);
            assertTrue(sent.isEmpty(), "marking must not send an action");
            assertEquals(original, model.view().ownSkillHand());
            button("confirmOpening").doClick(0); button("confirmOpening").doClick(0);
            assertEquals(1, sent.size(), "one in-flight confirmation only");
            var request = assertInstanceOf(ActionRequest.class, sent.get(0));
            var choice = assertInstanceOf(GameAction.ConfirmOpening.class, request.action());
            assertEquals(List.of(original.get(2).instanceId()), choice.replaceInstanceIds());
            assertEquals(original, model.view().ownSkillHand(), "no speculative replacement");
            accept(PlayerColor.BLUE, choice, 2);
        });
        await(() -> !button("confirmOpening").isVisible() && opening().isVisible());
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(model.view().openingConfirmed()); assertEquals(TurnPhase.OPENING, model.view().phase());
            accept(PlayerColor.RED, new GameAction.ConfirmOpening(List.of()), 2);
        });
        await(() -> !opening().isVisible());
        SwingUtilities.invokeAndWait(() -> {
            assertEquals(TurnPhase.MOVEMENT, model.view().phase());
            assertEquals(3, model.view().ownSkillHand().size());
            var playable = find(game, CardHandPanel.class, null);
            int skills = 0;
            for (Component component : playable.getComponents()) if (component instanceof SkillCardPanel card) {
                skills++; assertFalse(Boolean.TRUE.equals(card.getClientProperty("openingHidden")));
            }
            assertEquals(3, skills);
        });
    }
    @Test void acceptedReplacementReturnsThenDrawsBeforeTheFinalHandFlight() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            model.setReducedMotion(false);
            button("openingReplace0").doClick(0);
            button("confirmOpening").doClick(0);
            var choice = (GameAction.ConfirmOpening) ((ActionRequest) sent.get(0)).action();
            accept(PlayerColor.BLUE, choice, 2);
        });
        await(() -> find(opening(), DeckDrawOverlay.class, null).isFlyingTo("return:0"));
        await(() -> find(opening(), DeckDrawOverlay.class, null).isFlyingTo("draw:0"));
        SwingUtilities.invokeAndWait(() -> accept(PlayerColor.RED, new GameAction.ConfirmOpening(List.of()), 2));
        await(() -> find(opening(), DeckDrawOverlay.class, null).isFlyingTo("hand:0"));
        await(() -> !opening().isVisible());
        assertEquals(1, sent.size(), "animation callbacks must never submit another choice");
    }

    @Test void practiceBotWaitsThroughReplacementAndHandFlights() throws Exception {
        long seed = 0;
        var rules = RulesConfigLoader.load(null);
        while (RulesEngine.newGame(rules, seed).turn() != PlayerColor.RED) seed++;
        final long redSeed = seed;
        SwingUtilities.invokeAndWait(() -> {
            try {
                var start = ClientModel.class.getDeclaredMethod("startPracticeMatch", deckduel.core.Difficulty.class, long.class);
                start.setAccessible(true); start.invoke(model, deckduel.core.Difficulty.ROOKIE, redSeed);
            } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
            assertEquals(TurnPhase.OPENING, model.view().phase());
            assertEquals(0, model.view().startedTurns());
        });
        await(() -> model.view().opponentOpeningConfirmed());
        await(() -> button("openingReplace0").isEnabled());
        SwingUtilities.invokeAndWait(() -> {
            model.setReducedMotion(false);
            button("openingReplace0").doClick(0);
            button("confirmOpening").doClick(0);
            assertEquals(TurnPhase.MOVEMENT, model.view().phase());
            assertEquals(2, model.view().revision(), "bot must not act after Confirm while cards are returning");
        });
        await(() -> find(opening(), DeckDrawOverlay.class, null).isFlyingTo("hand:0"));
        SwingUtilities.invokeAndWait(() -> {
            assertEquals(2, model.view().revision(), "bot timer must stay stopped throughout replacement draws");
        });
        await(() -> !opening().isVisible());
        await(() -> model.view().revision() > 2);
    }

    @Test void dealStartsAfterLoadingAndNavigationCancelsWithoutSubmittingChoice() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            model.setReducedMotion(false);
            // A fresh match may first notify the game panel, then be covered by loading.
            state = RulesEngine.newOpeningGame(RulesConfigLoader.load(null), 19);
            model.handleMatchSync(new MatchSync("next-opening", "NEXT", RulesEngine.viewFor(state, PlayerColor.BLUE), false, 1));
            game.setEntryPending(true);
        });
        SwingUtilities.invokeAndWait(() -> { frame.validate(); game.setEntryPending(false); });
        await(() -> find(opening(), DeckDrawOverlay.class, null).isFlyingTo("draw:0"));
        SwingUtilities.invokeAndWait(() -> {
            assertFalse(button("confirmOpening").isEnabled());
            model.setReducedMotion(true);
        });
        await(() -> button("openingReplace0").isEnabled());
        SwingUtilities.invokeAndWait(() -> {
            model.leaveToLobby(); assertFalse(opening().isVisible()); assertTrue(sent.stream().noneMatch(m -> m instanceof ActionRequest));
            model.returnToParkedMatch(); assertTrue(opening().isVisible());
            assertTrue(button("openingReplace0").isEnabled(), "parked opening returns without redealing");
        });
    }
}
