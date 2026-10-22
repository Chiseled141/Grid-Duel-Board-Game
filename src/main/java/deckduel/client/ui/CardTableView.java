package deckduel.client.ui;

import deckduel.client.ClientSettings;
import deckduel.client.ScoringSounds;
import deckduel.core.card.CardDef;
import deckduel.core.card.CardInstance;
import deckduel.core.card.DrawCard;
import deckduel.core.card.Effect;
import deckduel.core.card.KeepCard;
import deckduel.core.card.MatchPhase;
import deckduel.core.card.Move;
import deckduel.core.card.PairBonus;
import deckduel.core.card.PlayCard;
import deckduel.core.card.PlayerView;
import deckduel.core.card.RoundResult;
import deckduel.core.card.Row;
import deckduel.core.card.RowCard;
import deckduel.core.card.Rules;
import deckduel.core.card.SideView;
import deckduel.core.card.SkipTurn;
import deckduel.core.card.TargetSide;
import deckduel.net.MatchUpdate;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.animation.AnimationTimer;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.PerspectiveCamera;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/**
 * The live card match in JavaFX, over the room background. It renders only the latest
 * authoritative {@link MatchUpdate}; choosing a destination, a draw or a card to keep
 * sends a move through {@link Actions}, and nothing changes on screen until the server's next view
 * arrives. Previews are labeled and computed from public information only. Every play is staged by {@link PlayAnimator} and performed by
 * {@link TableEffects} under a perspective camera. Used on the JavaFX Application Thread only.
 */
final class CardTableView extends Pane {
    private static final int GAP = 10;
    /** How long the pointer rests on a row card before its large preview appears, and the preview's height. */
    private static final int PREVIEW_DELAY_MS = 350, PREVIEW_H = 420;
    /** A light veil over row cards the player is not pointing at, so busy art does not all compete at once. */
    private static final Color QUIET = Color.rgb(0, 0, 0, .16);
    private static final long GAIN_MILLIS = 1200, NOTICE_MILLIS = 4500;
    private static final Color LINK = Color.rgb(244, 191, 96), DIM = Color.rgb(0, 0, 0, .22);

    /** What the table asks of the client. Called on the JavaFX thread; implementations hand the work to the EDT. */
    interface Actions {
        void move(Move move);
        void rematch();
        void leave();
    }

    /** The model's match as seen on the EDT when it changed; {@code match} is null between matches. */
    record State(MatchUpdate match, boolean reconnecting, boolean reducedMotion) { }

    record Hit(TableZone zone, int index) { }

    private final Actions actions;
    private final ClientSettings settings;
    private final ScoringSounds sounds;
    private final ImageView backdrop = new ImageView();
    private final javafx.scene.shape.Rectangle headerShade = new javafx.scene.shape.Rectangle();
    private final Canvas board = new Canvas();
    private final Pane effectsLayer = new Pane();
    private final SubScene effectsScene = new SubScene(effectsLayer, 1, 1, false, SceneAntialiasing.DISABLED);
    private final TableEffects effects;
    private final ScoreRail rail = new ScoreRail();
    private final Button leave = FxKit.pill("Menu", "gold-outline");
    private final VBox result = new VBox(8);
    private final Label resultTitle = new Label(), resultDetail = new Label();
    private final Label yourTotal = new Label(), theirTotal = new Label(), theirName = new Label();
    private final Button rematch = FxKit.pill("Rematch", "gold"), backToMenu = FxKit.pill("Back to menu", "cream-outline");
    private final StackPane confirm = new StackPane();
    private Long inspectedSpyCard;
    private final CardDetails cardDetails = new CardDetails(() -> {
        inspectedSpyCard = null;
        requestFocus();
    });
    private final AnimationTimer frames = new AnimationTimer() {
        @Override public void handle(long now) { tick(now); }
    };
    private boolean running;
    // Per hand card {lift in pixels, scale, x}, eased toward their resting, hovered or selected
    // values; easing x lets the hand close a gap or make room instead of jumping.
    private final Map<Long, double[]> handPose = new HashMap<>();
    // Row motion is keyed by card identity so replacements never inherit another card's position.
    private final Map<Long, double[]> rowPose = new HashMap<>();
    private long lastTick = System.nanoTime();
    private double dragLean;
    private Point2D lastPointer;
    private long lastPointerAt;
    private boolean staged, openingPending, onScreen;
    // A newly shown window takes a moment to appear; the opening deal waits that long to start.
    private final javafx.animation.PauseTransition settle = new javafx.animation.PauseTransition();
    private final PlayAnimator.Layout layout = new PlayAnimator.Layout() {
        @Override public Rectangle slot(TableZone zone, int slot) { return slotBounds(zone, slot); }
        @Override public Rectangle hand(List<CardInstance> cards, long cardId) {
            int index = Math.max(0, indexOf(arranged(cards), cardId)), count = cards.size();
            Rectangle slot = handSlot(index, count);
            slot.translate(0, fanDrop(index, count));
            return slot;
        }
        @Override public double handAngle(List<CardInstance> cards, long cardId) {
            return fanAngle(Math.max(0, indexOf(arranged(cards), cardId)), cards.size());
        }
        @Override public Rectangle pile(TableZone zone) { return new Rectangle(pilesX, (zone == TableZone.OWN ? ownTop : opponentTop) + 4, 54, 81); }
        @Override public Rectangle stage() {
            int w = cardW * 5 / 4, h = cardH * 5 / 4;
            return new Rectangle(tableLeft + (tableWidth - w) / 2, midY - h / 2 - 6, w, h);
        }
        @Override public Rectangle opponentHand() { return new Rectangle(opponentHandAnchorX() - 40, 20, 22, 33); }
        @Override public Rectangle table() { return new Rectangle(tableLeft, 0, tableWidth, (int) getHeight()); }
        @Override public java.awt.geom.Point2D pocket(ScoreRail.Pocket pocket) {
            Point2D at = rail.localToParent(rail.pocket(pocket));
            return new java.awt.geom.Point2D.Double(at.getX(), at.getY());
        }
    };

    private State state = new State(null, false, false);
    private MatchUpdate shown;
    // The opponent's moves that arrived while a play was still being shown, oldest first.
    private final java.util.ArrayDeque<State> held = new java.util.ArrayDeque<>();
    private Long selected, pressed;
    // The player's own arrangement, for easier reading only: the hand's order by card id, and which
    // real row space each of the seven places on their row shows. Nothing of it reaches the server.
    private List<Long> handOrder = new ArrayList<>();
    private final int[] rowOrder = {0, 1, 2, 3, 4, 5, 6};
    private Integer pressedSlot;
    private Integer rowPreview;
    private boolean dragging;
    private Point2D pointer;
    private Hit hover;
    // The card the pointer is resting on, and the one whose large preview is showing once the delay has passed.
    private Hit previewTarget, preview;
    private final javafx.animation.PauseTransition previewDelay = new javafx.animation.PauseTransition(javafx.util.Duration.millis(PREVIEW_DELAY_MS));
    private String notice;
    private long noticeUntil, traceStart;
    private ScoringTrace trace;
    private int beatsPlayed, roundsSeen;
    private int rowLeft, cardW, cardH, opponentTop, ownTop, handTop, midY, tableLeft, tableWidth, pilesX;
    private int handW, handH;

    /** Builds the table; it shows a match once {@link #show} receives one. */
    CardTableView(Actions actions, ClientSettings settings, ScoringSounds sounds) {
        this.actions = actions;
        this.settings = settings;
        this.sounds = sounds;
        setId("cardTable");
        setStyle("-fx-background-color: #2C2925;");
        setFocusTraversable(true);
        setAccessibleRole(AccessibleRole.PARENT);
        setAccessibleText("Card table");
        effects = new TableEffects(effectsLayer, sounds);
        effectsScene.setFill(Color.TRANSPARENT);
        // A sub-scene's root takes the stylesheet's opaque .root background; the table must show through.
        effectsLayer.setStyle("-fx-background-color: transparent;");
        // The perspective camera is what turns a card over in depth rather than squashing it flat.
        effectsScene.setCamera(new PerspectiveCamera());
        effectsScene.setMouseTransparent(true);
        var room = AssetStore.class.getResource("/assets/match-room.png");
        if (room != null) backdrop.setImage(new Image(room.toExternalForm(), true));
        backdrop.setPreserveRatio(false);
        headerShade.setMouseTransparent(true);
        leave.setOnAction(event -> confirmLeave());
        // The rail sits under the effects, so scoring chips are seen landing on it.
        getChildren().addAll(backdrop, headerShade, board, rail, effectsScene, leave);
        buildResult();
        buildConfirm();
        getChildren().add(cardDetails);
        installMouse();
        addEventHandler(KeyEvent.KEY_PRESSED, this::keyPressed);
    }

    // ---------------------------------------------------------------- state

    /**
     * Shows the model's latest match. The opponent's move waits while a play is still being shown,
     * so a quick reply, such as the practice bot's, follows the Spell and its effects instead of
     * playing over them; your own moves show at once. A new game or reduced motion clears them.
     */
    void show(State next) {
        if (holds(next)) {
            held.add(next);
            return;
        }
        held.clear();
        present(next);
    }

    /**
     * Whether {@code next} must wait for the table's animations: the opponent's change in the game
     * on screen while a play is still staged, or anything queued behind one.
     */
    private boolean holds(State next) {
        MatchUpdate update = next.match();
        if (update == null || shown == null || next.reducedMotion() || !onScreen) return false;
        if (!update.matchId().equals(shown.matchId()) || update.game() != shown.game()) return false;
        if (!held.isEmpty()) return true;
        return staged && update.view().revision() > shown.view().revision() && !shown.view().yourTurn();
    }

    /** Puts {@code next} on the table and stages the change that produced it. */
    private void present(State next) {
        state = next;
        MatchUpdate update = next.match();
        if (update == null) {
            cardDetails.close();
            shown = null; selected = null; rail.reset(); result.setVisible(false);
            effects.clear(); staged = false; trace = null;
            rowPose.clear(); handPose.clear();
            pressed = null; pressedSlot = null; rowPreview = null; dragging = false;
            redraw();
            return;
        }
        boolean newMatch = shown == null || !shown.matchId().equals(update.matchId());
        boolean newGame = newMatch || shown.game() != update.game();
        boolean newRound = newGame || shown.view().round() != update.view().round();
        if (newRound || pressedSlot != null && !java.util.Objects.equals(
                shown.view().you().row().at(pressedSlot), update.view().you().row().at(pressedSlot))) {
            pressed = null; pressedSlot = null; rowPreview = null; dragging = false;
        }
        if (newRound || next.reducedMotion()) rowPose.clear();
        ScoringTrace change = newGame ? null : ScoringTrace.between(shown.view(), update.view());
        PlayAnimator.Plan plan = plan(newGame, update);
        if (newGame) {
            cardDetails.close();
            rail.reset(); roundsSeen = 0; selected = null; trace = null;
            handOrder = new ArrayList<>();
            for (int i = 0; i < Row.SIZE; i++) rowOrder[i] = i;
        }
        if (newGame || next.reducedMotion()) { effects.clear(); staged = false; }
        announceRounds(update);
        if (selected != null && update.view().hand().stream().noneMatch(card -> card.id() == selected)) selected = null;
        shown = update;
        handOrder = arranged(update.view().hand()).stream().map(CardInstance::id).toList();
        int delay = plan == null ? 0 : plan.scoringDelay();
        startTrace(change, delay);
        List<ScoreRail.Tally> chips = plan != null && trace != null ? PlayAnimator.combos(plan.staging(), trace, delay, layout) : List.of();
        if (plan != null) stage(plan.staging());
        rail.update(display(update.view()), next.reducedMotion(),
                (int) Math.round((trace == null ? delay : delay + trace.finishAt) * Staging.TEMPO),
                chips.stream().map(chip -> new ScoreRail.Tally((int) Math.round(chip.atMillis() * Staging.TEMPO), chip.pocket(), chip.points())).toList());
        refreshResult();
        refreshCardDetails();
        wake();
    }

    /** Shows a short line in place of the status, as for a rejected play or a server error. */
    void notice(String text) {
        notice = text;
        noticeUntil = System.currentTimeMillis() + NOTICE_MILLIS;
        wake();
    }

    /** Called when the table becomes the visible screen. */
    void shown() { shown(350); }

    /**
     * Marks the table visible. A pending opening deal starts after {@code settleMillis}, so the
     * window has appeared first; its card faces are drawn meanwhile, not on its first frames.
     */
    void shown(int settleMillis) {
        onScreen = true;
        requestFocus();
        if (getScene() != null) { getScene().getRoot().applyCss(); getScene().getRoot().layout(); }
        if (shown != null && cardW > 0)
            for (CardInstance card : shown.view().hand()) FxKit.face(card.def(), List.of(), handW, handH, false, pixelScale());
        settle.stop();
        if (settleMillis <= 0) { startPendingOpening(); return; }
        settle.setDuration(javafx.util.Duration.millis(settleMillis));
        settle.setOnFinished(event -> startPendingOpening());
        settle.playFromStart();
    }

    /** Deals the opening hand once the table is visible, settled and sized. */
    private void startPendingOpening() {
        if (!openingPending || shown == null || cardW <= 0 || !onScreen
                || settle.getStatus() == javafx.animation.Animation.Status.RUNNING) return;
        openingPending = false;
        stage(PlayAnimator.opening(shown.view(), layout).staging());
        wake();
    }

    /** Whether {@code next} starts dealing: a new game under way. */
    private boolean deals(boolean newGame, MatchUpdate next) {
        return newGame && next.view().phase() == MatchPhase.IN_PROGRESS;
    }

    /** Called when another screen replaces the table. */
    void hidden() {
        onScreen = false;
        while (!held.isEmpty()) present(held.poll());
        settle.stop();
        confirm.setVisible(false);
        cardDetails.close();
    }

    /** Starts performing {@code next} alongside anything still playing. */
    private void stage(Staging next) {
        effects.perform(next, pixelScale());
        staged = true;
    }

    /** Stages the change that produced {@code next}; reduced motion and unrelated jumps get none. */
    private PlayAnimator.Plan plan(boolean newGame, MatchUpdate next) {
        openingPending = false;
        if (state.reducedMotion()) return null;
        boolean deal = deals(newGame, next);
        // The opening deal waits until the table is on screen and sized, so none of it is missed.
        if (deal && (cardW <= 0 || !onScreen || settle.getStatus() == javafx.animation.Animation.Status.RUNNING)) {
            openingPending = true;
            return null;
        }
        if (cardW <= 0 || newGame && !deal) return null;
        if (deal) return PlayAnimator.opening(next.view(), layout);
        if (next.view().revision() != shown.view().revision() + 1) return null;
        // A manual draw or a kept Reconnaissance card has no play record; only the hands changed.
        return next.view().lastPlay() != null ? PlayAnimator.play(shown.view(), next.view(), layout)
                : PlayAnimator.handChanges(shown.view(), next.view(), layout);
    }

    /** Replays a change card by card; reduced motion skips straight to the result, keeping only the final sound. */
    private void startTrace(ScoringTrace change, int delay) {
        if (change == null) return;
        if (state.reducedMotion()) {
            trace = null;
            if (!change.beats.isEmpty()) sounds.play(change.beats.get(change.beats.size() - 1).cue());
            return;
        }
        trace = change;
        traceStart = System.nanoTime() + Math.round(delay * Staging.TEMPO * 1e6);
        beatsPlayed = 0;
    }

    /** Milliseconds into the running trace (negative while it waits for the card to land), or -1 when none runs. */
    private long traceElapsed() {
        return trace == null ? -1 : Math.round((System.nanoTime() - traceStart) / 1e6 / Staging.TEMPO);
    }

    private void announceRounds(MatchUpdate next) {
        List<RoundResult> rounds = next.view().rounds();
        if (rounds.size() > roundsSeen && next.view().phase() == MatchPhase.IN_PROGRESS) {
            RoundResult last = rounds.get(rounds.size() - 1);
            notice("Round " + last.round() + " banked: you " + last.scoreOf(next.view().seat()) + " – "
                    + last.scoreOf(next.view().seat().other()) + " " + next.opponentName());
        }
        roundsSeen = rounds.size();
    }

    private static ScoreRail.Display display(PlayerView view) {
        SideView you = view.you();
        var formation = you.score().formation();
        return new ScoreRail.Display(you.score().total(), view.opponent().score().total(), you.score().units(),
                you.score().combos(), you.matchTotal(), you.playsRemaining(), view.opponent().playsRemaining(), view.round(), formation == null ? null
                : formation.title() + "  +" + formation.bonus() + "  ×" + formation.multiplier());
    }

    // ---------------------------------------------------------------- frames

    private void wake() {
        if (!running) { running = true; lastTick = System.nanoTime(); frames.start(); }
        redraw();
    }

    private void tick(long now) {
        long elapsed = traceElapsed();
        while (trace != null && beatsPlayed < trace.beats.size() && trace.beats.get(beatsPlayed).at() <= elapsed)
            sounds.play(trace.beats.get(beatsPlayed++).cue());
        if (trace != null && elapsed > trace.finishAt + GAIN_MILLIS) trace = null;
        if (staged && !effects.tick(now)) staged = false;
        // The opponent's held move plays once the one before it has finished.
        if (!staged && !held.isEmpty()) present(held.poll());
        // A heavy blow shakes the cards and effects, never the room behind them.
        double[] shake = staged ? effects.shake() : new double[2];
        for (Node shaken : List.of(board, effectsScene)) { shaken.setTranslateX(shake[0]); shaken.setTranslateY(shake[1]); }
        boolean lifting = stepCards(now);
        redraw();
        if (System.currentTimeMillis() > noticeUntil && trace == null && !staged && !lifting) { frames.stop(); running = false; }
    }

    /** Eases hand and row cards toward their display positions; true while anything still moves. */
    private boolean stepCards(long now) {
        if (shown == null) return false;
        // Time-based easing, so the hand feels the same at any frame rate.
        double ms = Math.min(32, (now - lastTick) / 1e6);
        double step = state.reducedMotion() ? 1 : 1 - Math.exp(-ms / 70);
        lastTick = now;
        boolean moving = false;
        List<CardInstance> hand = hand();
        handPose.keySet().retainAll(hand.stream().map(CardInstance::id).toList());
        for (int i = 0; i < hand.size(); i++) {
            double[] target = handTarget(i), pose = handPose(i);
            for (int k = 0; k < 3; k++) {
                pose[k] = Math.abs(target[k] - pose[k]) < (k == 1 ? .002 : .3) ? target[k] : pose[k] + (target[k] - pose[k]) * step;
                moving |= pose[k] != target[k];
            }
        }
        rowPose.keySet().retainAll(shown.view().you().row().cards().stream().map(card -> card.card().id()).toList());
        for (RowCard card : shown.view().you().row().cards()) {
            Rectangle target = slotBounds(TableZone.OWN, card.slot());
            double[] pose = rowPose(card.slot());
            for (int k = 0; k < 2; k++) {
                double value = k == 0 ? target.x : target.y;
                pose[k] = Math.abs(value - pose[k]) < .3 ? value : pose[k] + (value - pose[k]) * step;
                moving |= pose[k] != value;
            }
        }
        dragLean *= Math.exp(-ms / 110);
        if (Math.abs(dragLean) < .002) dragLean = 0;
        return moving || dragLean != 0 || dragging;
    }

    /** {lift, scale, x} a hand card is heading for. */
    private double[] handTarget(int index) {
        CardInstance card = hand().get(index);
        double x = handSlot(index, hand().size()).x;
        if (selected != null && card.id() == selected) return new double[]{26, 1.08, x};
        return hover != null && hover.zone() == TableZone.HAND && hover.index() == index ? new double[]{12, 1.05, x} : new double[]{0, 1, x};
    }

    /** The eased pose of hand card {@code index}; a card new to the hand starts in its own space. */
    private double[] handPose(int index) {
        return handPose.computeIfAbsent(hand().get(index).id(), id -> new double[]{0, 1, handSlot(index, hand().size()).x});
    }

    /** The fan: cards rotate a little away from the middle of the hand and dip along an arc. */
    private static double fanAngle(int index, int count) { return count < 2 ? 0 : fan(index, count) * .05; }
    private static int fanDrop(int index, int count) { return count < 2 ? 0 : (int) Math.round(fan(index, count) * fan(index, count) * 14); }
    private static double fan(int index, int count) { return (index - (count - 1) / 2.0) / ((count - 1) / 2.0); }

    private double pixelScale() {
        double scale = getScene() != null && getScene().getWindow() != null ? getScene().getWindow().getOutputScaleX()
                : javafx.stage.Screen.getPrimary().getOutputScaleX();
        return Math.max(1, Math.round(scale * 4) / 4.0);
    }

    // ---------------------------------------------------------------- actions

    private void play(PlayCard play) {
        selected = null;
        actions.move(play);
        wake();
    }

    /** A manual draw, when the player may make one now. */
    void draw() {
        if (shown != null && !state.reconnecting() && shown.view().canDraw()) { actions.move(new DrawCard()); wake(); }
    }

    /** Skips this turn through the server, using one of the round's ten plays. */
    void skipTurn() {
        if (shown == null || state.reconnecting() || !shown.view().canSkip()) return;
        selected = null;
        actions.move(new SkipTurn());
        wake();
    }

    private void confirmLeave() {
        if (shown != null && shown.view().phase() != MatchPhase.FINISHED) { confirm.setVisible(true); return; }
        actions.leave();
    }

    /** Legal plays of one hand card in the current view. */
    private List<PlayCard> playsFor(Long cardId) {
        if (shown == null || cardId == null || state.reconnecting()) return List.of();
        return shown.view().legalPlays().stream().filter(play -> play.cardId() == cardId).toList();
    }

    /** The legal play that sends a card to a hit area (a space, a Unit or, for Exchange, a hand card), or null. */
    PlayCard playAt(Long cardId, Hit hit) {
        if (hit == null) return null;
        Long target = targetAt(hit);
        for (PlayCard play : playsFor(cardId)) {
            if (hit.zone() == TableZone.CAST) { if (play.slot() == PlayCard.NO_SPACE && play.targetId() == null) return play; continue; }
            if (hit.zone() == TableZone.OWN && play.slot() == hit.index()) return play;
            if (play.targetId() != null && play.targetId().equals(target)) return play;
        }
        return null;
    }

    /** The card instance id a hit points at, or null. */
    private Long targetAt(Hit hit) {
        switch (hit.zone()) {
            case OWN, OPPONENT -> { RowCard card = row(hit.zone()).at(hit.index()); return card == null ? null : card.card().id(); }
            case HAND -> { CardInstance card = item(hand(), hit.index()); return card == null ? null : card.id(); }
            default -> { return null; }
        }
    }

    private Row row(TableZone zone) { return zone == TableZone.OWN ? shown.view().you().row() : shown.view().opponent().row(); }

    private CardInstance handCard(Long id) {
        if (shown == null || id == null) return null;
        return shown.view().hand().stream().filter(card -> card.id() == id).findFirst().orElse(null);
    }

    // ---------------------------------------------------------------- input

    private void installMouse() {
        board.setOnMouseMoved(event -> moveTo(local(event)));
        board.setOnMouseExited(event -> { hover = null; clearPreview(); board.setCursor(javafx.scene.Cursor.DEFAULT); redraw(); });
        previewDelay.setOnFinished(event -> { preview = previewTarget; redraw(); });
        board.setOnMousePressed(event -> { if (event.getButton() == MouseButton.PRIMARY) pressAt(local(event)); });
        board.setOnMouseDragged(event -> dragTo(local(event)));
        board.setOnMouseReleased(event -> { if (event.getButton() == MouseButton.PRIMARY) releaseAt(local(event)); });
    }

    /** Pointer position on the table. */
    private Point2D local(MouseEvent event) { return sceneToLocal(event.getSceneX(), event.getSceneY()); }

    void moveTo(Point2D point) {
        hover = hitAt(point);
        boolean inspectable = hover != null && hover.zone() == TableZone.REVEALED;
        board.setCursor(inspectable ? javafx.scene.Cursor.HAND : javafx.scene.Cursor.DEFAULT);
        Hit target = previewable(hover);
        if (!java.util.Objects.equals(target, previewTarget)) {
            clearPreview();
            previewTarget = target;
            if (target != null) previewDelay.playFromStart();
        }
        wake();
    }

    /** {@code hit} when it is a row card worth previewing now, otherwise null; hand cards are already large. */
    private Hit previewable(Hit hit) {
        if (hit == null || shown == null || dragging || selected != null) return null;
        PlayerView view = shown.view();
        if (view.keeping()) return null;
        if (hit.zone() == TableZone.OWN) return view.you().row().at(hit.index()) != null ? hit : null;
        if (hit.zone() == TableZone.OPPONENT) return view.opponent().row().at(hit.index()) != null ? hit : null;
        return null;
    }

    /** The card a large preview is pending or showing for; for tests. */
    Hit previewTarget() { return previewTarget; }

    private void clearPreview() {
        previewDelay.stop();
        previewTarget = null;
        preview = null;
    }

    void pressAt(Point2D point) {
        requestFocus();
        clearPreview();
        Hit hit = hitAt(point);
        pressed = hit != null && hit.zone() == TableZone.HAND ? hand().get(hit.index()).id() : null;
        // A card already on the player's own row can be picked up to rearrange it, unless a hand
        // card is chosen: then a click on the row is a play.
        pressedSlot = pressed == null && selected == null && hit != null && hit.zone() == TableZone.OWN
                && shown.view().you().row().at(hit.index()) != null ? hit.index() : null;
        pointer = point;
        dragging = false;
    }

    void dragTo(Point2D point) {
        if (pressed == null && pressedSlot == null) return;
        if (pointer.distance(point) > 6) dragging = true;
        leanWithPointer(point);
        pointer = point;
        hover = hitAt(pointer);
        rowPreview = dragging && pressedSlot != null && hover != null && hover.zone() == TableZone.OWN
                ? hover.index() : null;
        wake();
    }

    /** A dragged card leans with the pointer's sideways speed, as if held by its top edge. */
    private void leanWithPointer(Point2D now) {
        long at = System.nanoTime();
        if (lastPointer != null && at > lastPointerAt) {
            double speed = (now.getX() - lastPointer.getX()) / ((at - lastPointerAt) / 1e9);
            double target = Math.max(-.32, Math.min(.32, speed * .00035));
            dragLean += (target - dragLean) * .35;
        }
        lastPointer = now;
        lastPointerAt = at;
    }

    void releaseAt(Point2D point) {
        Hit hit = hitAt(point);
        lastPointer = null;
        if (pressedSlot != null) { dropRowCard(hit, point); return; }
        if (!dragging && hit != null && button(hit)) { pressed = null; return; }
        Long card = dragging ? pressed : selected;
        boolean wasDrag = dragging;
        // Dropped within the hand rather than on a destination: it keeps the place it was shown at.
        List<Long> rearranged = wasDrag && overHand(point) ? hand().stream().map(CardInstance::id).toList() : null;
        pressed = null; dragging = false;
        PlayCard play = rearranged != null ? null : playAt(card, hit);
        if (play != null) { play(play); return; }
        if (rearranged != null) handOrder = rearranged;
        if (!wasDrag && hit != null && hit.zone() == TableZone.HAND && settings.clickToSelectCards()) {
            long id = hand().get(hit.index()).id();
            selected = selected != null && selected == id ? null : id;
        }
        wake();
    }

    /** Handles a click on a revealed card to keep, a Spy-revealed card, or the draw or skip button; false for anything else. */
    private boolean button(Hit hit) {
        switch (hit.zone()) {
            case LOOK -> { actions.move(new KeepCard(shown.view().looking().get(hit.index()).id())); wake(); }
            case DRAW -> draw();
            case SKIP -> skipTurn();
            case REVEALED -> {
                inspectedSpyCard = shown.view().revealed().get(hit.index()).id();
                refreshCardDetails();
            }
            default -> { return false; }
        }
        return true;
    }

    /** Refreshes the Spy inspector, closing it once the card has left the opponent's hand or the round ends. */
    private void refreshCardDetails() {
        if (inspectedSpyCard == null || shown == null) return;
        CardInstance card = shown.view().revealed().stream().filter(c -> c.id() == inspectedSpyCard).findFirst().orElse(null);
        if (card == null || shown.view().phase() != MatchPhase.IN_PROGRESS) cardDetails.close();
        else cardDetails.showRevealed(card);
    }

    /** Moves a picked-up row card to the place it was dropped on, swapping with any card there; display only. */
    private void dropRowCard(Hit hit, Point2D point) {
        int from = pressedSlot;
        boolean moved = dragging && hit != null && hit.zone() == TableZone.OWN && hit.index() != from;
        if (dragging && !state.reducedMotion()) {
            // Continue from the pointer on release, including a cancelled drag back to its old space.
            RowCard card = shown.view().you().row().at(from);
            if (card != null) rowPose.put(card.card().id(), new double[]{point.getX() - cardW / 2.0, point.getY() - cardH / 2.0});
        }
        pressedSlot = null;
        rowPreview = null;
        dragging = false;
        if (moved) {
            int a = place(from), b = place(hit.index());
            rowOrder[a] = hit.index();
            rowOrder[b] = from;
        }
        wake();
    }

    /** Which of the seven places on the player's row shows real space {@code slot}. */
    private int place(int slot) {
        if (dragging && pressedSlot != null && rowPreview != null) {
            if (slot == pressedSlot) return committedPlace(rowPreview);
            if (slot == rowPreview) return committedPlace(pressedSlot);
        }
        return committedPlace(slot);
    }

    private int committedPlace(int slot) {
        for (int i = 0; i < Row.SIZE; i++) if (rowOrder[i] == slot) return i;
        return slot;
    }

    /** Whether a point is over the hand, where a dropped hand card is rearranged rather than played. */
    private boolean overHand(Point2D point) { return point.getY() > handTop - handH * .35; }

    /** {@code cards} in the player's arrangement: kept cards in their chosen order, new ones after them. */
    private List<CardInstance> arranged(List<CardInstance> cards) {
        List<CardInstance> ordered = new ArrayList<>(cards);
        ordered.sort(java.util.Comparator.comparingInt(card -> {
            int at = handOrder.indexOf(card.id());
            return at < 0 ? Integer.MAX_VALUE : at;
        }));
        return ordered;
    }

    private static int indexOf(List<CardInstance> cards, long cardId) {
        for (int i = 0; i < cards.size(); i++) if (cards.get(i).id() == cardId) return i;
        return -1;
    }

    /**
     * The hand as shown, in the player's arrangement. While a hand card is dragged over the hand,
     * it is shown at the place it would drop to, so the others make room.
     */
    private List<CardInstance> hand() {
        List<CardInstance> ordered = arranged(shown.view().hand());
        CardInstance held = dragging && pressed != null && pointer != null && overHand(pointer) ? handCard(pressed) : null;
        if (held != null && ordered.remove(held)) {
            int left = handSlot(0, ordered.size() + 1).x;
            int index = (int) Math.round((pointer.getX() - left - handW / 2.0) / handStep(ordered.size() + 1));
            ordered.add(Math.max(0, Math.min(ordered.size(), index)), held);
        }
        return ordered;
    }

    void keyPressed(KeyEvent event) {
        KeyCode code = event.getCode();
        if (cardDetails.isVisible()) {
            if (code == KeyCode.ESCAPE) cardDetails.close();
            if (code != KeyCode.TAB) event.consume();
            return;
        }
        if (confirm.isVisible()) {
            if (code == KeyCode.ESCAPE) confirm.setVisible(false);
            event.consume();
            return;
        }
        switch (code) {
            case LEFT -> cycle(-1);
            case RIGHT -> cycle(1);
            case ENTER -> confirmKey();
            case D -> draw();
            case S -> skipTurn();
            case ESCAPE -> { if (selected != null) { selected = null; wake(); } else confirmLeave(); }
            default -> {
                int slot = code.isDigitKey() ? code.getCode() - (code.isKeypadKey() ? KeyCode.NUMPAD1.getCode() : KeyCode.DIGIT1.getCode()) : -1;
                if (slot < 0 || slot >= Row.SIZE) return;
                chooseSlot(slot);
            }
        }
        event.consume();
    }

    private void cycle(int step) {
        if (shown == null || shown.view().hand().isEmpty()) return;
        List<CardInstance> hand = hand();
        int index = 0;
        for (int i = 0; i < hand.size(); i++) if (selected != null && hand.get(i).id() == selected) index = i + step;
        selected = hand.get(Math.floorMod(selected == null ? 0 : index, hand.size())).id();
        wake();
    }

    private void chooseSlot(int slot) {
        CardInstance card = handCard(selected);
        if (card == null) return;
        TableZone zone = targetZone(card.def());
        // Number keys count the places as the player sees them.
        PlayCard play = playAt(selected, new Hit(zone, zone == TableZone.OWN ? rowOrder[slot] : slot));
        if (play != null) play(play);
    }

    private void confirmKey() {
        PlayCard cast = playAt(selected, new Hit(TableZone.CAST, 0));
        if (cast != null) play(cast);
        else if (playAt(selected, hover) != null) play(playAt(selected, hover));
    }

    /** The Unit row a card's keyboard numbers refer to. */
    private static TableZone targetZone(CardDef card) {
        TargetSide side = card.effect() instanceof Effect.Destroy destroy ? destroy.side()
                : card.effect() instanceof Effect.Disable disable ? disable.side() : TargetSide.OWN;
        return side == TargetSide.OWN ? TableZone.OWN : TableZone.OPPONENT;
    }

    // ---------------------------------------------------------------- result and confirmation

    private void buildResult() {
        result.getStyleClass().add("leather");
        result.setId("matchResult");
        result.setAlignment(Pos.CENTER);
        // The title rides a swallowtail ribbon whose cloth tells the outcome (see the stylesheet).
        resultTitle.getStyleClass().add("result-title");
        resultTitle.setFont(FxKit.display(30));
        resultDetail.setFont(FxKit.normal(13));
        resultDetail.setTextFill(FxKit.CREAM);
        resultDetail.setWrapText(true);
        resultDetail.setTextAlignment(TextAlignment.CENTER);
        rematch.setOnAction(event -> actions.rematch());
        backToMenu.setOnAction(event -> actions.leave());
        HBox buttons = new HBox(12, rematch, backToMenu);
        buttons.setAlignment(Pos.CENTER);
        result.getChildren().addAll(resultTitle, buildTotals(), resultDetail, buttons);
        result.setVisible(false);
        getChildren().add(result);
    }

    /** The match totals as the panel's headline: your score lit in the outcome colour, theirs dimmed. */
    private HBox buildTotals() {
        yourTotal.getStyleClass().add("result-score");
        theirTotal.getStyleClass().addAll("result-score", "theirs");
        Label dash = new Label("–");
        dash.getStyleClass().addAll("result-score", "theirs");
        dash.setFont(FxKit.bold(34));
        HBox totals = new HBox(22, scoreColumn(yourTotal, new Label("YOU")), dash, scoreColumn(theirTotal, theirName));
        totals.setAlignment(Pos.CENTER);
        return totals;
    }

    private static VBox scoreColumn(Label score, Label caption) {
        score.setFont(FxKit.bold(46));
        caption.getStyleClass().add("result-caption");
        caption.setFont(FxKit.bold(11.5));
        VBox column = new VBox(-4, score, caption);
        column.setAlignment(Pos.CENTER);
        column.setMinWidth(130);
        return column;
    }

    private void buildConfirm() {
        Label title = new Label("Leave match");
        title.setFont(FxKit.display(24));
        title.setTextFill(FxKit.AMBER);
        Label message = new Label("Leave and forfeit this match?");
        message.setFont(FxKit.normal(15));
        message.setTextFill(FxKit.CREAM);
        Button stay = FxKit.pill("No", "cream-outline"), forfeit = FxKit.pill("Forfeit", "danger");
        stay.setOnAction(event -> { confirm.setVisible(false); requestFocus(); });
        forfeit.setOnAction(event -> { confirm.setVisible(false); actions.leave(); });
        HBox buttons = new HBox(12, stay, forfeit);
        buttons.setAlignment(Pos.CENTER);
        VBox dialog = new VBox(14, title, message, buttons);
        dialog.getStyleClass().add("leather");
        dialog.setAlignment(Pos.CENTER);
        dialog.setMaxSize(420, 200);
        dialog.setPadding(new javafx.geometry.Insets(24));
        confirm.setId("leaveConfirm");
        confirm.setStyle("-fx-background-color: rgba(10, 6, 2, 0.55);");
        confirm.getChildren().add(dialog);
        confirm.setVisible(false);
        getChildren().add(confirm);
    }

    private void refreshResult() {
        PlayerView view = shown.view();
        boolean finished = view.phase() == MatchPhase.FINISHED;
        result.setVisible(finished);
        if (!finished) return;
        confirm.setVisible(false);
        var outcome = view.outcome();
        String verdict = outcome.winner() == null ? "Draw" : outcome.winner() == view.seat() ? "Victory" : "Defeat";
        resultTitle.setText(verdict);
        result.getStyleClass().removeAll("victory", "defeat", "draw");
        result.getStyleClass().add(verdict.toLowerCase(Locale.ROOT));
        String how = outcome.forfeit() ? (outcome.winner() == view.seat() ? shown.opponentName() + " forfeited." : "You forfeited.") : "";
        yourTotal.setText(String.valueOf(view.you().matchTotal()));
        theirTotal.setText(String.valueOf(view.opponent().matchTotal()));
        theirName.setText(shown.opponentName().toUpperCase(Locale.ROOT));
        resultDetail.setText(how);
        resultDetail.setVisible(outcome.forfeit());
        resultDetail.setManaged(outcome.forfeit());
        rematch.setDisable(shown.opponentLeft() || shown.rematchRequested());
        rematch.setText((shown.opponentLeft() ? "Opponent left" : shown.rematchRequested() ? "Waiting for opponent…"
                : shown.opponentWantsRematch() ? "Accept rematch" : "Rematch").toUpperCase(Locale.ROOT));
    }

    private static String roundHistory(PlayerView view) {
        StringBuilder text = new StringBuilder();
        for (RoundResult round : view.rounds())
            text.append(text.length() == 0 ? "" : "  ·  ").append("R").append(round.round()).append(" ")
                    .append(round.scoreOf(view.seat())).append("–").append(round.scoreOf(view.seat().other()));
        return text.toString();
    }

    // ---------------------------------------------------------------- layout and hit testing

    private int opponentHandAnchorX() { return (int) Math.max(0, getWidth() - 200); }

    @Override protected void layoutChildren() {
        int w = (int) getWidth(), h = (int) getHeight();
        backdrop.setFitWidth(w);
        backdrop.setFitHeight(h);
        coverViewport();
        headerShade.setWidth(w);
        headerShade.setHeight(150);
        headerShade.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(0, 0, 0, .73)), new Stop(1, Color.rgb(0, 0, 0, 0))));
        board.setWidth(w);
        board.setHeight(h);
        effectsScene.setWidth(w);
        effectsScene.setHeight(h);
        effectsLayer.resize(w, h);
        int railWidth = Math.max(196, Math.min(252, w / 6));
        int railHeight = (int) Math.ceil(railWidth * 550.0 / 260);
        rail.resizeRelocate(20, 76, railWidth, railHeight);
        // The pill's padding, border and offset shadow need its own preferred height, or the label clips.
        leave.resizeRelocate(20, 14, Math.max(120, leave.prefWidth(-1)), leave.prefHeight(-1));
        // The window's floating music toggle occupies the top-right corner.
        tableLeft = 20 + railWidth + 28;
        pilesX = w - 110;
        tableWidth = Math.max(0, pilesX - 16 - tableLeft);
        int across = Row.SIZE;
        cardW = Math.max(40, (tableWidth - (across - 1) * GAP) / across);
        // Cards use the artwork's 2:3 proportions.
        int handReserve = Math.max(180, Math.min(260, (int) (h * .27)));
        cardH = Math.max(60, Math.min(cardW * 3 / 2, (h - 242 - handReserve) / 2));
        cardW = Math.min(cardW, cardH * 2 / 3);
        int width = across * cardW + (across - 1) * GAP;
        rowLeft = tableLeft + (tableWidth - width) / 2;
        opponentTop = 92;
        midY = opponentTop + cardH + 20;
        ownTop = opponentTop + cardH + 80;
        // The hand has its own larger faces; reserve room for the fan and its shadows at the bottom.
        handH = Math.max(cardH, Math.min(cardH * 3 / 2, h - ownTop - cardH - 70));
        handW = handH * 2 / 3;
        handTop = Math.max(ownTop + cardH + 30, h - handH - 40);
        // Centred on the window: the match is over, so the verdict takes the middle of the screen.
        result.resizeRelocate((w - 560) / 2.0, (h - 290) / 2.0, 560, 290);
        confirm.resizeRelocate(0, 0, w, h);
        cardDetails.resizeRelocate(0, 0, w, h);
        startPendingOpening();
        redraw();
        if (staged) wake();
    }

    /** Crops the room picture to cover the table without stretching. */
    private void coverViewport() {
        Image room = backdrop.getImage();
        if (room == null || room.getWidth() <= 0 || getWidth() <= 0 || getHeight() <= 0) return;
        double scale = Math.max(getWidth() / room.getWidth(), getHeight() / room.getHeight());
        double vw = getWidth() / scale, vh = getHeight() / scale;
        backdrop.setViewport(new javafx.geometry.Rectangle2D((room.getWidth() - vw) / 2, (room.getHeight() - vh) / 2, vw, vh));
    }

    /** Where real row space {@code slot} is shown; the player's own row follows their arrangement. */
    Rectangle slotBounds(TableZone zone, int slot) {
        int shownAt = zone == TableZone.OWN ? place(slot) : slot;
        return new Rectangle(rowLeft + shownAt * (cardW + GAP), zone == TableZone.OWN ? ownTop : opponentTop, cardW, cardH);
    }

    /** The visible card position, separate from its destination and authoritative row space. */
    Rectangle rowCardBounds(TableZone zone, int slot) {
        Rectangle bounds = slotBounds(zone, slot);
        if (zone == TableZone.OWN && !state.reducedMotion() && shown.view().you().row().at(slot) != null) {
            double[] pose = rowPose(slot);
            bounds.setLocation((int) Math.round(pose[0]), (int) Math.round(pose[1]));
        }
        return bounds;
    }

    private double[] rowPose(int slot) {
        Rectangle target = slotBounds(TableZone.OWN, slot);
        return rowPose.computeIfAbsent(shown.view().you().row().at(slot).card().id(), id -> new double[]{target.x, target.y});
    }

    /** The manual-draw button under the player's own deck. */
    Rectangle drawBounds() { return new Rectangle(pilesX - 10, ownTop + 128, 76, 26); }

    Rectangle skipBounds() { return new Rectangle(pilesX - 18, ownTop + 162, 92, 26); }

    /** The cards a Reconnaissance revealed, while the player chooses one to keep. */
    Rectangle lookBounds(int index, int count) {
        int w = Math.min(150, cardW * 3 / 2), h = w * 3 / 2;
        int left = tableLeft + (tableWidth - (count * w + (count - 1) * 24)) / 2;
        return new Rectangle(left + index * (w + 24), (int) (getHeight() / 2 - h / 2.0), w, h);
    }

    Rectangle handBounds(int index) {
        double[] pose = state.reducedMotion() ? handTarget(index) : handPose(index);
        int count = hand().size();
        Rectangle slot = handSlot(index, count);
        slot.setLocation((int) Math.round(pose[2]), slot.y + fanDrop(index, count) - (int) Math.round(pose[0]));
        return slot;
    }

    /** Where hand card {@code index} of {@code count} rests; a hand too wide for the table overlaps. */
    Rectangle handSlot(int index, int count) {
        int step = handStep(count);
        int left = tableLeft + (tableWidth - ((count - 1) * step + handW)) / 2;
        return new Rectangle(left + index * step, handTop, handW, handH);
    }

    /** Distance between neighbouring hand cards. */
    private int handStep(int count) {
        return count < 2 ? handW + GAP : Math.min(handW + GAP, (tableWidth - handW) / (count - 1));
    }

    /** The hand's card ids in the order shown; for tests. */
    List<Long> shownHand() { return hand().stream().map(CardInstance::id).toList(); }

    /** Whether a play is still being staged; for tests. */
    boolean animating() { return staged; }

    /** The revision of the match on the table, which may trail a held move; for tests. */
    int shownRevision() { return shown == null ? -1 : shown.view().revision(); }

    ScoreRail rail() { return rail; }
    Button rematchButton() { return rematch; }
    Button backButton() { return backToMenu; }
    boolean resultShowing() { return result.isVisible(); }
    boolean confirmShowing() { return confirm.isVisible(); }

    Rectangle castBounds() { return new Rectangle(tableLeft + tableWidth / 2 - 110, midY + 8, 220, 28); }

    Hit hitAt(Point2D point) {
        if (shown == null || result.isVisible() || cardDetails.isVisible()) return null;
        int x = (int) point.getX(), y = (int) point.getY();
        PlayerView view = shown.view();
        // While keeping a revealed card, only that choice is on offer.
        if (view.keeping()) {
            for (int i = 0; i < view.looking().size(); i++)
                if (lookBounds(i, view.looking().size()).contains(x, y)) return new Hit(TableZone.LOOK, i);
            return null;
        }
        // Keep drop targets fixed while the preview slides cards aside, avoiding a swap-back loop.
        if (dragging && pressedSlot != null) {
            for (int place = 0; place < Row.SIZE; place++)
                if (new Rectangle(rowLeft + place * (cardW + GAP), ownTop, cardW, cardH).contains(x, y))
                    return new Hit(TableZone.OWN, rowOrder[place]);
            return null;
        }
        for (int i = view.revealed().size() - 1; i >= 0; i--)
            if (revealedBounds(i).contains(x, y)) return new Hit(TableZone.REVEALED, i);
        for (int i = hand().size() - 1; i >= 0; i--)
            if (handBounds(i).contains(x, y)) return new Hit(TableZone.HAND, i);
        for (RowCard card : view.you().row().cards())
            if (rowCardBounds(TableZone.OWN, card.slot()).contains(x, y)) return new Hit(TableZone.OWN, card.slot());
        for (TableZone zone : List.of(TableZone.OWN, TableZone.OPPONENT))
            for (int slot = 0; slot < Row.SIZE; slot++)
                if (slotBounds(zone, slot).contains(x, y)) return new Hit(zone, slot);
        if (view.canDraw() && (drawBounds().contains(x, y) || pileBounds().contains(x, y))) return new Hit(TableZone.DRAW, 0);
        if (view.canSkip() && !state.reconnecting() && skipBounds().contains(x, y)) return new Hit(TableZone.SKIP, 0);
        if (playAt(selected, new Hit(TableZone.CAST, 0)) != null && castBounds().contains(x, y)) return new Hit(TableZone.CAST, 0);
        return null;
    }

    private Rectangle pileBounds() { return new Rectangle(pilesX, ownTop + 4, 54, 81); }

    private CardDef cardAt(Hit hit) {
        if (hit == null || shown == null) return null;
        PlayerView view = shown.view();
        CardInstance card = switch (hit.zone()) {
            case HAND -> item(hand(), hit.index());
            case OWN, OPPONENT -> { RowCard placed = row(hit.zone()).at(hit.index()); yield placed == null ? null : placed.card(); }
            case LOOK -> item(view.looking(), hit.index());
            case REVEALED -> item(view.revealed(), hit.index());
            default -> null;
        };
        return card == null ? null : card.def();
    }

    /**
     * The card at {@code index}, or null once a newer view has fewer cards: the remembered hover
     * can point past a hand or Reconnaissance choice that an update just emptied.
     */
    private static CardInstance item(List<CardInstance> cards, int index) {
        return index >= 0 && index < cards.size() ? cards.get(index) : null;
    }

    // ---------------------------------------------------------------- drawing

    private void redraw() {
        GraphicsContext g = board.getGraphicsContext2D();
        g.clearRect(-20, -20, board.getWidth() + 40, board.getHeight() + 40);
        if (shown == null) { centered(g, "Waiting for the match to start…", FxKit.bold(18), FxKit.CREAM, getHeight() / 2); return; }
        // A match can arrive before the window has laid the table out; there is nothing to place yet.
        if (cardW <= 0 || tableWidth <= 0) return;
        drawHeader(g);
        drawRow(g, TableZone.OPPONENT, shown.view().opponent(), shown.opponentName().toUpperCase(Locale.ROOT) + "'S ROW");
        drawRow(g, TableZone.OWN, shown.view().you(), "YOUR ROW");
        drawPiles(g, shown.view().opponent(), opponentTop);
        drawPiles(g, shown.view().you(), ownTop);
        drawDrawButton(g);
        drawSkipButton(g);
        // The keep overlay carries its own heading; the status line would show through under it.
        // A notification toast sits where the status line is; the line waits until it has gone.
        if (!shown.view().keeping() && !effects.noticeShowing()) drawStatus(g);
        drawHand(g);
        if (shown.view().keeping()) drawLooking(g);
        else drawPreview(g);
    }

    /** A large copy of the row card the pointer rests on, beside it, so small printed text never needs squinting at. */
    private void drawPreview(GraphicsContext g) {
        if (preview == null || previewable(preview) == null) return;
        RowCard unit = (preview.zone() == TableZone.OWN ? shown.view().you() : shown.view().opponent()).row().at(preview.index());
        Rectangle at = rowCardBounds(preview.zone(), preview.index());
        int h = (int) Math.min(PREVIEW_H, board.getHeight() * .55), w = h * 2 / 3;
        double x = at.getMaxX() + 14 + w <= board.getWidth() - 8 ? at.getMaxX() + 14 : at.x - 14 - w;
        x = Math.max(8, Math.min(x, board.getWidth() - w - 8));
        double y = Math.max(8, Math.min(at.getCenterY() - h / 2.0, board.getHeight() - h - 8));
        FxKit.drawFace(g, unit.def(), unit.suppressed(), w, h, false, pixelScale(), x, y);
    }

    private void drawHeader(GraphicsContext g) {
        PlayerView view = shown.view();
        text(g, "ROUND " + view.round() + " OF " + Rules.ROUNDS + "  ·  vs " + shown.opponentName()
                + (shown.practice() ? "  ·  PRACTICE" : ""), 160, 36, FxKit.bold(16), FxKit.CREAM, false);
        String history = roundHistory(view);
        text(g, "Match total: you " + view.you().matchTotal() + " – " + view.opponent().matchTotal()
                + (history.isEmpty() ? "" : "   ·   " + history), 160, 56, FxKit.normal(12), FxKit.MUTED, false);
        int count = view.opponent().handCount(), backW = 22, backH = 33, right = opponentHandAnchorX() - 18;
        for (int i = 0; i < count; i++)
            FxKit.drawBack(g, backW, backH, pixelScale(), right - backW - (count - 1 - i) * 12, 20);
        String hand = count + " in hand";
        Font font = FxKit.normal(12);
        double handLeft = right - backW - (count - 1) * 12 - FxKit.width(hand, font) - 10;
        text(g, hand, handLeft, 40, font, FxKit.MUTED, false);
        drawRevealed(g, view.revealed());
    }

    /** The opponent's hand cards a Spy revealed to this player only, small faces left of their hand. */
    private void drawRevealed(GraphicsContext g, List<CardInstance> revealed) {
        if (revealed.isEmpty()) return;
        for (int i = 0; i < revealed.size(); i++) {
            Rectangle r = revealedBounds(i);
            FxKit.drawFace(g, revealed.get(i).def(), List.of(), r.width, r.height, false, pixelScale(), r.x, r.y);
            if (hover != null && hover.zone() == TableZone.REVEALED && hover.index() == i) outline(g, r, FxKit.AMBER);
        }
        Font font = FxKit.bold(10);
        text(g, "SPIED", revealedBounds(0).x - FxKit.width("SPIED", font) - 6, 40, font, FxKit.AMBER, true);
    }

    /** Shared drawing and click target for a card the recipient's Spy actually revealed. */
    Rectangle revealedBounds(int index) {
        int count = shown.view().opponent().handCount(), width = 30;
        double handLeft = opponentHandAnchorX() - 18 - 22 - (count - 1) * 12
                - FxKit.width(count + " in hand", FxKit.normal(12)) - 10;
        int left = (int) (handLeft - 14 - shown.view().revealed().size() * (width + 4));
        return new Rectangle(left + index * (width + 4), 14, width, 45);
    }

    private void drawRow(GraphicsContext g, TableZone zone, SideView side, String title) {
        int top = zone == TableZone.OWN ? ownTop : opponentTop;
        text(g, title, rowLeft, top - 12,
                FxKit.bold(12), FxKit.CREAM, true);
        if (trace != null) drawGain(g, zone == TableZone.OWN ? trace.ownGain : trace.opponentGain, top);
        Long active = dragging ? pressed : selected;
        boolean rearranging = dragging && pressedSlot != null && zone == TableZone.OWN;
        // Paint the vacant spaces first so a sliding card can cross them without being painted over.
        for (int slot = 0; slot < Row.SIZE; slot++) {
            RowCard card = side.row().at(slot);
            boolean destination = rearranging ? slot != pressedSlot : playAt(active, new Hit(zone, slot)) != null;
            boolean hovered = hover != null && hover.zone() == zone && hover.index() == slot;
            if (card == null || staged && effects.hiddenOnRow(card.card().id()) || rearranging && slot == pressedSlot)
                emptySlot(g, slotBounds(zone, slot), destination, hovered);
        }
        for (int slot = 0; slot < Row.SIZE; slot++) {
            RowCard card = side.row().at(slot);
            // A card still travelling here is drawn by its animation instead; one being moved, at the pointer.
            if (card == null || staged && effects.hiddenOnRow(card.card().id()) || rearranging && slot == pressedSlot) continue;
            Rectangle r = rowCardBounds(zone, slot);
            boolean destination = rearranging ? slot != pressedSlot : playAt(active, new Hit(zone, slot)) != null;
            boolean hovered = hover != null && hover.zone() == zone && hover.index() == slot;
            FxKit.drawFace(g, card.def(), card.suppressed(), r.width, r.height, destination && hovered,
                    pixelScale(), r.x, r.y);
            if (destination) outline(g, r, hovered ? FxKit.AMBER : FxKit.AMBER.deriveColor(0, 1, 1, .6));
            else if (!hovered) { g.setFill(QUIET); g.fillRoundRect(r.x, r.y, r.width, r.height, 9, 9); }
            drawTriggers(g, zone, slot, r);
        }
        drawLinks(g, zone, side.score().pairs(), top + cardH);
    }

    /** The trace's warm glow on cards that just changed on a row. */
    private void drawTriggers(GraphicsContext g, TableZone zone, int slot, Rectangle r) {
        long elapsed = traceElapsed();
        if (elapsed < 0) return;
        if (trace.changed.get(zone).contains(slot) && elapsed < ScoringTrace.GLOW_MS)
            glow(g, r, 1 - elapsed / (double) ScoringTrace.GLOW_MS);
    }

    private static void glow(GraphicsContext g, Rectangle r, double strength) {
        g.setFill(Color.rgb(255, 214, 140, .27 * strength));
        g.fillRoundRect(r.x, r.y, r.width, r.height, 9, 9);
        g.setEffect(new DropShadow(BlurType.GAUSSIAN, FxKit.AMBER.deriveColor(0, 1, 1, strength), 18, .3, 0, 0));
        g.setStroke(FxKit.AMBER.deriveColor(0, 1, 1, strength));
        g.setLineWidth(3);
        g.strokeRoundRect(r.x - 3, r.y - 3, r.width + 6, r.height + 6, 12, 12);
        g.setEffect(null);
    }

    private static void emptySlot(GraphicsContext g, Rectangle r, boolean destination, boolean hovered) {
        g.setFill(destination ? Color.rgb(244, 191, 96, hovered ? .27 : .12) : Color.rgb(0, 0, 0, .16));
        g.fillRoundRect(r.x, r.y, r.width, r.height, 7, 7);
        g.setStroke(destination ? FxKit.AMBER.deriveColor(0, 1, 1, hovered ? 1 : .67) : Color.rgb(235, 212, 170, .35));
        g.setLineWidth(destination ? 2 : 1);
        g.setLineDashes(4, 6);
        g.strokeRoundRect(r.x, r.y, r.width, r.height, 7, 7);
        g.setLineDashes((double[]) null);
    }

    private static void outline(GraphicsContext g, Rectangle r, Color color) {
        g.setStroke(color);
        g.setLineWidth(2.5);
        g.strokeRoundRect(r.x - 2, r.y - 2, r.width + 4, r.height + 4, 10, 10);
    }

    /** Thin warm links under matching Units; the hovered card's pairs show their bonus. */
    private void drawLinks(GraphicsContext g, TableZone zone, List<PairBonus> pairs, int bottom) {
        int focus = hover != null && hover.zone() == zone ? hover.index() : -1;
        long elapsed = traceElapsed();
        for (PairBonus pair : pairs) {
            ScoringTrace.Link traced = trace == null ? null : trace.link(zone, pair);
            long since = traced == null ? -1 : elapsed - traced.at();
            // A new pair stays dark until its turn in the trace; while effects run, the drawing link belongs to them.
            if (traced != null && (since < 0 || staged && since < ScoringTrace.LINK_MS)) continue;
            boolean lit = pair.slotA() == focus || pair.slotB() == focus || since >= 0 && since < ScoringTrace.LINK_MS;
            Rectangle a = rowCardBounds(zone, pair.slotA()), b = rowCardBounds(zone, pair.slotB());
            double ax = a.getCenterX(), bx = b.getCenterX(), dip = Math.min(9, 3 + Math.abs(bx - ax) * .02);
            g.setStroke(LINK.deriveColor(0, 1, 1, lit ? .9 : .27));
            g.setLineWidth(lit ? 2 : 1);
            g.beginPath();
            g.moveTo(ax, bottom - 3);
            g.quadraticCurveTo((ax + bx) / 2, bottom - 3 + dip * 2, bx, bottom - 3);
            g.stroke();
            if (lit) text(g, "+" + pair.points(), (ax + bx) / 2 - 8, bottom + dip + 12, FxKit.bold(11), LINK, false);
        }
    }

    private void drawGain(GraphicsContext g, long gain, int top) {
        long age = traceElapsed() - trace.finishAt;
        if (age < 0 || gain == 0 || age > GAIN_MILLIS) return;
        double fade = 1 - age / (double) GAIN_MILLIS;
        String text = (gain > 0 ? "+" : "−") + Math.abs(gain);
        Font font = FxKit.bold(18);
        int right = rowLeft + Row.SIZE * cardW + (Row.SIZE - 1) * GAP;
        text(g, text, right - FxKit.width(text, font), top - 10 - 12 * (1 - fade), font,
                (gain > 0 ? FxKit.CREAM : FxKit.CORAL).deriveColor(0, 1, 1, fade), false);
    }

    /** A deck, face down, and its counts. */
    private void drawPiles(GraphicsContext g, SideView side, int top) {
        if (side.deckCount() > 0)
            FxKit.drawBack(g, 54, 81, pixelScale(), pilesX, top + 4);
        else emptySlot(g, new Rectangle(pilesX, top + 4, 54, 81), false, false);
        text(g, "Deck " + side.deckCount(), pilesX, top + 103, FxKit.normal(11), FxKit.CREAM, true);
        text(g, "Discard " + side.discardCount(), pilesX, top + 119, FxKit.normal(11), FxKit.CREAM, true);
    }

    /** The manual-draw button with the draws left this round; dimmed when no draw is possible now. */
    private void drawDrawButton(GraphicsContext g) {
        if (shown.view().phase() != MatchPhase.IN_PROGRESS) return;
        Rectangle r = drawBounds();
        boolean can = shown.view().canDraw() && !state.reconnecting();
        boolean lit = can && hover != null && hover.zone() == TableZone.DRAW;
        String label = "DRAW " + shown.view().you().drawsRemaining() + "/" + Rules.MANUAL_DRAWS;
        TablePlaque.button(g, r.x, r.y, r.width, r.height, can ? TablePlaque.Look.PRIMARY : TablePlaque.Look.OFF, lit,
                label, FxKit.bold(12));
    }

    /** Skip sits below Draw, with its allowance cost always visible. */
    private void drawSkipButton(GraphicsContext g) {
        if (shown.view().phase() != MatchPhase.IN_PROGRESS) return;
        Rectangle r = skipBounds();
        boolean can = shown.view().canSkip() && !state.reconnecting();
        boolean lit = can && hover != null && hover.zone() == TableZone.SKIP;
        TablePlaque.button(g, r.x, r.y, r.width, r.height, can ? TablePlaque.Look.PLAIN : TablePlaque.Look.OFF, lit,
                "SKIP TURN", FxKit.bold(11));
        text(g, "S · uses 1 play", r.x + 6, r.y + 40, FxKit.normal(11), FxKit.MUTED, true);
    }

    /** The latest notice (such as a rejected move) under a choice overlay, which hides the status line. */
    private void overlayNotice(GraphicsContext g, double baseline) {
        if (notice != null && System.currentTimeMillis() < noticeUntil) centered(g, notice, FxKit.bold(14), FxKit.CORAL, baseline);
    }

    /** The cards a Reconnaissance revealed, privately, over a dimmed table; one is kept. */
    private void drawLooking(GraphicsContext g) {
        dimTable(g);
        List<CardInstance> looking = shown.view().looking();
        Rectangle first = lookBounds(0, looking.size());
        centered(g, "Reconnaissance: keep one card", FxKit.decorative(26), FxKit.AMBER, first.y - 40);
        centered(g, "The others go to the bottom of your deck. Only you see these.", FxKit.normal(14), FxKit.CREAM, first.y - 16);
        for (int i = 0; i < looking.size(); i++) {
            Rectangle r = lookBounds(i, looking.size());
            boolean hovered = hover != null && hover.zone() == TableZone.LOOK && hover.index() == i;
            handCard(g, looking.get(i).def(), List.of(), r.getCenterX(), r.getCenterY() - (hovered ? 8 : 0), 1, 0, hovered,
                    hovered ? 8 : 0, null, r.width, r.height);
        }
        overlayNotice(g, first.y + first.height + 30);
    }

    private void dimTable(GraphicsContext g) {
        g.setFill(Color.rgb(10, 6, 2, .62));
        g.fillRect(tableLeft - 12, 70, tableWidth + 24, getHeight() - 70);
    }

    private void drawStatus(GraphicsContext g) {
        centered(g, statusText(), FxKit.bold(17), FxKit.CREAM, midY);
        boolean castable = playAt(selected, new Hit(TableZone.CAST, 0)) != null;
        String detail = detailText();
        // The cast button takes the detail line's place while it is offered.
        if (detail != null && !castable) centered(g, detail, FxKit.normal(13), FxKit.MUTED, midY + 22);
        if (castable) {
            Rectangle cast = castBounds();
            boolean lit = hover != null && hover.zone() == TableZone.CAST;
            CardInstance spell = handCard(selected);
            String label = Rules.takesTarget(spell.def().effect()) ? "Cast without a target" : "Cast " + spell.def().name();
            TablePlaque.button(g, cast.x, cast.y, cast.width, cast.height, TablePlaque.Look.PRIMARY, lit, label, FxKit.bold(13));
        }
    }

    private String statusText() {
        if (state.reconnecting()) return "Connection lost — reconnecting…";
        if (System.currentTimeMillis() < noticeUntil && notice != null) return notice;
        PlayerView view = shown.view();
        if (view.phase() == MatchPhase.FINISHED) return "Match over";
        if (!shown.opponentConnected()) return shown.opponentName() + " disconnected — their seat is held for now";
        if (view.keeping()) return "Keep one of the cards your Reconnaissance revealed";
        if (!view.yourTurn()) return shown.opponentName() + " is choosing a card…";
        String draws = view.canDraw() ? " or draw (" + view.you().drawsRemaining() + " left)" : "";
        if (view.hand().isEmpty()) return "Your hand is empty — draw a card or skip your turn";
        CardInstance card = handCard(dragging ? pressed : selected);
        if (card == null) return "Your turn — play a card" + draws + ", or skip (S)";
        if (card.def().occupiesSpace())
            return view.you().row().isFull() ? "Your row is full — choose a Unit to replace" : "Choose an empty space on your row";
        Effect effect = card.def().effect();
        if (!Rules.takesTarget(effect)) return "Cast " + card.def().name() + " with the button below";
        if (Rules.spellTargets(card, view.hand(), view.board()).isEmpty()) return "No legal target — this Spell would resolve with no effect";
        if (effect instanceof Effect.Exchange) return "Choose another card in your hand to discard";
        return "Choose a target for " + card.def().name();
    }

    /** Hover details or a labeled preview of the hovered destination. */
    String detailText() {
        CardInstance card = handCard(dragging ? pressed : selected);
        PlayCard preview = card == null ? null : playAt(card.id(), hover);
        if (preview != null) return previewText(card, preview);
        CardDef hovered = cardAt(hover);
        if (hovered != null) return CardFace.details(hovered);
        return null;
    }

    private String previewText(CardInstance card, PlayCard play) {
        PlayerView view = shown.view();
        Rules.Resolution after = Rules.resolve(card, play, view.hand(), view.board());
        long own = after.board().ownScore().total() - view.you().score().total();
        long theirs = after.board().opponentScore().total() - view.opponent().score().total();
        return "PREVIEW · " + after.description() + " · you " + signed(own) + ", " + shown.opponentName() + " " + signed(theirs);
    }

    private static String signed(long value) { return (value >= 0 ? "+" : "−") + Math.abs(value); }

    private void drawHand(GraphicsContext g) {
        // A hand still waiting to be dealt stays in the deck; showing it first would spoil the deal.
        if (shown.view().phase() == MatchPhase.FINISHED || openingPending) return;
        List<CardInstance> hand = hand();
        boolean yourTurn = shown.view().yourTurn();
        for (int i = 0; i < hand.size(); i++) {
            CardInstance card = hand.get(i);
            if (dragging && pressed != null && card.id() == pressed) continue;
            if (staged && effects.hiddenInHand(card.id())) continue;
            Rectangle r = handBounds(i);
            boolean chosen = selected != null && card.id() == selected;
            double[] pose = state.reducedMotion() ? handTarget(i) : handPose(i);
            handCard(g, card.def(), r.getCenterX(), r.getCenterY(), pose[1], fanAngle(i, hand.size()),
                    chosen, pose[0], yourTurn ? null : DIM);
        }
        CardInstance held = dragging ? handCard(pressed) : null;
        if (held != null && pointer != null) handCard(g, held.def(), pointer.getX(), pointer.getY(), 1.1, dragLean, true, 30, null);
        RowCard moving = dragging && pressedSlot != null ? shown.view().you().row().at(pressedSlot) : null;
        if (moving != null && pointer != null)
            handCard(g, moving.def(), moving.suppressed(), pointer.getX(), pointer.getY(), 1.1, dragLean, true, 30, null);
    }

    /**
     * A hand card at a pose. Its shadow deepens as it lifts, which is what makes it read as raised;
     * the chosen card glows. {@code dim} darkens it while it is not the player's turn.
     */
    private void handCard(GraphicsContext g, CardDef card, double cx, double cy, double scale, double angle, boolean chosen,
                          double lift, Color dim) {
        handCard(g, card, List.of(), cx, cy, scale, angle, chosen, lift, dim, handW, handH);
    }

    private void handCard(GraphicsContext g, CardDef card, List<deckduel.core.card.Suppression> marks, double cx, double cy,
                          double scale, double angle, boolean chosen, double lift, Color dim) {
        handCard(g, card, marks, cx, cy, scale, angle, chosen, lift, dim, cardW, cardH);
    }

    private void handCard(GraphicsContext g, CardDef card, List<deckduel.core.card.Suppression> marks, double cx, double cy,
                          double scale, double angle, boolean chosen, double lift, Color dim, int cardW, int cardH) {
        g.save();
        g.translate(cx, cy);
        g.rotate(Math.toDegrees(angle));
        g.scale(scale, scale);
        g.setEffect(chosen ? new DropShadow(BlurType.GAUSSIAN, FxKit.AMBER.deriveColor(0, 1, 1, .75), 26, .25, 0, 4)
                : lift > .5 ? new DropShadow(BlurType.GAUSSIAN, Color.rgb(0, 0, 0, .55), 8 + lift, .1, lift * .2, lift * .45) : null);
        FxKit.drawFace(g, card, marks, cardW, cardH, chosen, pixelScale(), -cardW / 2.0, -cardH / 2.0);
        g.setEffect(null);
        if (dim != null) {
            g.setFill(dim);
            g.fillRoundRect(-cardW / 2.0, -cardH / 2.0, cardW, cardH, 9, 9);
        }
        g.restore();
    }

    /** Text, under the table's hard offset shadow when {@code shadowed}. */
    private static void text(GraphicsContext g, String text, double x, double y, Font font, Color color, boolean shadowed) {
        g.setFont(font);
        if (shadowed) { g.setFill(FxKit.SHADOW); g.fillText(text, x + 1, y + 2); }
        g.setFill(color);
        g.fillText(text, x, y);
    }

    private void centered(GraphicsContext g, String text, Font font, Color color, double baseline) {
        double width = tableWidth > 0 ? tableWidth : getWidth(), left = tableWidth > 0 ? tableLeft : 0;
        String fitted = FxKit.fit(text, font, width - 20);
        double x = left + (width - FxKit.width(fitted, font)) / 2;
        text(g, fitted, x, baseline, font, color, true);
    }
}
