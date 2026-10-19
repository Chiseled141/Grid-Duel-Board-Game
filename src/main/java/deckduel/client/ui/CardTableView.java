package deckduel.client.ui;

import deckduel.client.ClientSettings;
import deckduel.client.ScoringSounds;
import deckduel.core.card.CardDef;
import deckduel.core.card.CardInstance;
import deckduel.core.card.DrawCard;
import deckduel.core.card.Effect;
import deckduel.core.card.KeepCard;
import deckduel.core.card.LandmarkCard;
import deckduel.core.card.MatchEvent;
import deckduel.core.card.MatchPhase;
import deckduel.core.card.Move;
import deckduel.core.card.PairBonus;
import deckduel.core.card.PickLandmarks;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
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
 * authoritative {@link MatchUpdate}; choosing a Landmark, a destination, a draw or a card to keep
 * sends a move through {@link Actions}, and nothing changes on screen until the server's next view
 * arrives. Previews are labeled and computed from public information only. Every play is staged by {@link PlayAnimator} and performed by
 * {@link TableEffects} under a perspective camera. Used on the JavaFX Application Thread only.
 */
final class CardTableView extends Pane {
    private static final int GAP = 10;
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
    private final Button rematch = FxKit.pill("Rematch", "gold"), backToMenu = FxKit.pill("Back to menu", "cream-outline");
    private final StackPane confirm = new StackPane();
    private Hit inspectedLandmark;
    private Long inspectedSpyCard;
    private final CardDetails cardDetails = new CardDetails(() -> {
        inspectedLandmark = null;
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
    private long lastTick = System.nanoTime(), hoverCard = -1, hoverSince;
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
        @Override public Rectangle landmark(TableZone zone, int index) { return landmarkBounds(zone, index); }
        @Override public Rectangle pile(TableZone zone) { return new Rectangle(pilesX, (zone == TableZone.OWN ? ownTop : opponentTop) + 4, 54, 81); }
        @Override public Rectangle stage() {
            int w = cardW * 5 / 4, h = cardH * 5 / 4;
            return new Rectangle(tableLeft + (tableWidth - w) / 2, midY - h / 2 - 6, w, h);
        }
        @Override public Rectangle opponentHand() { return new Rectangle(opponentHandAnchorX() - 40, 20, 22, 33); }
        @Override public Rectangle table() { return new Rectangle(tableLeft, 0, tableWidth, (int) getHeight()); }
    };

    private State state = new State(null, false, false);
    private MatchUpdate shown;
    private Long selected, pressed;
    // The player's own arrangement, for easier reading only: the hand's order by card id, and which
    // real row space each of the seven places on their row shows. Nothing of it reaches the server.
    private List<Long> handOrder = new ArrayList<>();
    private final int[] rowOrder = {0, 1, 2, 3, 4, 5, 6};
    // Landmark offers ticked so far while choosing two.
    private final java.util.Set<Long> picking = new LinkedHashSet<>();
    private Integer pressedSlot;
    private boolean dragging;
    private Point2D pointer;
    private Hit hover;
    private String notice;
    private long noticeUntil, traceStart;
    private ScoringTrace trace;
    private int beatsPlayed, roundsSeen;
    private int rowLeft, cardW, cardH, opponentTop, ownTop, handTop, midY, tableLeft, tableWidth, pilesX;
    private int handW, handH;
    private int landmarkLeft, landmarkTop, landmarkW, landmarkH, landmarkShelfHeight;

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
        getChildren().addAll(backdrop, headerShade, board, effectsScene, rail, leave);
        buildResult();
        buildConfirm();
        getChildren().add(cardDetails);
        installMouse();
        addEventHandler(KeyEvent.KEY_PRESSED, this::keyPressed);
        CardImages.shared().onLoaded(() -> Platform.runLater(this::redraw));
    }

    // ---------------------------------------------------------------- state

    /**
     * Shows the model's latest match. Its play is staged alongside anything still in motion, so a
     * quick reply never cuts an animation short; a new game or reduced motion clears them.
     */
    void show(State next) {
        state = next;
        MatchUpdate update = next.match();
        if (update == null) {
            cardDetails.close(); inspectedLandmark = null;
            shown = null; selected = null; rail.reset(); result.setVisible(false);
            effects.clear(); staged = false; trace = null;
            redraw();
            return;
        }
        boolean newMatch = shown == null || !shown.matchId().equals(update.matchId());
        boolean newGame = newMatch || shown.game() != update.game();
        ScoringTrace change = newGame ? null : ScoringTrace.between(shown.view(), update.view());
        PlayAnimator.Plan plan = plan(newGame, update);
        if (newGame) {
            cardDetails.close(); inspectedLandmark = null;
            rail.reset(); roundsSeen = 0; selected = null; trace = null;
            handOrder = new ArrayList<>();
            picking.clear();
            for (int i = 0; i < Row.SIZE; i++) rowOrder[i] = i;
        }
        if (newGame || next.reducedMotion()) { effects.clear(); staged = false; }
        announceRounds(update);
        if (selected != null && update.view().hand().stream().noneMatch(card -> card.id() == selected)) selected = null;
        shown = update;
        handOrder = arranged(update.view().hand()).stream().map(CardInstance::id).toList();
        int delay = plan == null ? 0 : plan.scoringDelay();
        startTrace(change, delay);
        if (plan != null && trace != null) PlayAnimator.combos(plan.staging(), trace, delay, layout);
        if (plan != null) stage(plan.staging());
        rail.update(display(update.view()), next.reducedMotion(),
                (int) Math.round((trace == null ? delay : delay + trace.finishAt) * Staging.TEMPO));
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

    /** Whether {@code next} starts dealing: a new game already under way, or round one after both chose Landmarks. */
    private boolean deals(boolean newGame, MatchUpdate next) {
        if (next.view().phase() != MatchPhase.IN_PROGRESS) return false;
        return newGame || shown.view().phase() == MatchPhase.CHOOSING_LANDMARKS;
    }

    /** Called when another screen replaces the table. */
    void hidden() {
        onScreen = false;
        settle.stop();
        confirm.setVisible(false);
        cardDetails.close(); inspectedLandmark = null;
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
        return next.view().lastPlay() != null ? PlayAnimator.play(shown.view(), next.view(), next.opponentName(), layout)
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
        return new ScoreRail.Display(you.score().total(), view.opponent().score().total(), you.score().units(),
                you.score().combos(), you.matchTotal(), you.playsRemaining(), view.round());
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
        Point2D jolt = staged ? effects.shake(now) : Point2D.ZERO;
        board.setTranslateX(jolt.getX()); board.setTranslateY(jolt.getY());
        effectsScene.setTranslateX(jolt.getX()); effectsScene.setTranslateY(jolt.getY());
        boolean lifting = stepHand(now);
        redraw();
        if (System.currentTimeMillis() > noticeUntil && trace == null && !staged && !lifting) { frames.stop(); running = false; }
    }

    /** Eases each hand card toward its resting, hovered or selected pose; true while anything still moves. */
    private boolean stepHand(long now) {
        if (shown == null) return false;
        // Time-based easing, so the hand feels the same at any frame rate.
        double ms = Math.min(32, (now - lastTick) / 1e6), step = 1 - Math.exp(-ms / 70);
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
        dragLean *= Math.exp(-ms / 110);
        if (Math.abs(dragLean) < .002) dragLean = 0;
        return moving || dragLean != 0 || dragging || hoverWobbling();
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

    private boolean hoverWobbling() { return hoverCard >= 0 && (System.nanoTime() - hoverSince) / 1e6 < 520; }

    /** A brief rotation wobble when the pointer arrives on a card. */
    private double hoverWobble(long cardId) {
        if (cardId != hoverCard || state.reducedMotion()) return 0;
        return CardMotion.punch((System.nanoTime() - hoverSince) / 1e6, .055, 95, 26);
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

    /** Ticks or unticks a Landmark offer; at most two can be ticked. */
    private void toggleOffer(int index) {
        long id = shown.view().offers().get(index).id();
        if (!picking.remove(id) && picking.size() < Rules.LANDMARK_PICKS) picking.add(id);
        wake();
    }

    /** Sends the two ticked Landmarks. */
    void confirmLandmarks() {
        if (picking.size() != Rules.LANDMARK_PICKS || !shown.view().choosingLandmarks()) return;
        actions.move(new PickLandmarks(List.copyOf(picking)));
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

    /** The legal play that sends a card to a hit area (a space, a Unit, an enemy Landmark or, for Exchange, a hand card), or null. */
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
            case OWN_LANDMARK, OPPONENT_LANDMARK -> {
                List<LandmarkCard> landmarks = landmarks(hit.zone());
                return hit.index() < landmarks.size() ? landmarks.get(hit.index()).card().id() : null;
            }
            case HAND -> { return hand().get(hit.index()).id(); }
            default -> { return null; }
        }
    }

    private Row row(TableZone zone) { return zone == TableZone.OWN ? shown.view().you().row() : shown.view().opponent().row(); }

    private List<LandmarkCard> landmarks(TableZone zone) {
        return zone == TableZone.OWN_LANDMARK ? shown.view().you().landmarks() : shown.view().opponent().landmarks();
    }

    private CardInstance handCard(Long id) {
        if (shown == null || id == null) return null;
        return shown.view().hand().stream().filter(card -> card.id() == id).findFirst().orElse(null);
    }

    // ---------------------------------------------------------------- input

    private void installMouse() {
        board.setOnMouseMoved(event -> moveTo(local(event)));
        board.setOnMouseExited(event -> { hover = null; board.setCursor(javafx.scene.Cursor.DEFAULT); redraw(); });
        board.setOnMousePressed(event -> { if (event.getButton() == MouseButton.PRIMARY) pressAt(local(event)); });
        board.setOnMouseDragged(event -> dragTo(local(event)));
        board.setOnMouseReleased(event -> { if (event.getButton() == MouseButton.PRIMARY) releaseAt(local(event)); });
    }

    /** Pointer position on the table, unaffected by the shake applied to the board. */
    private Point2D local(MouseEvent event) { return sceneToLocal(event.getSceneX(), event.getSceneY()); }

    void moveTo(Point2D point) {
        hover = hitAt(point);
        boolean inspectable = hover != null && (hover.zone() == TableZone.REVEALED
                || hover.zone() == TableZone.OWN_LANDMARK || hover.zone() == TableZone.OPPONENT_LANDMARK);
        board.setCursor(inspectable ? javafx.scene.Cursor.HAND : javafx.scene.Cursor.DEFAULT);
        long card = hover != null && hover.zone() == TableZone.HAND ? hand().get(hover.index()).id() : -1;
        if (card != hoverCard) { hoverCard = card; hoverSince = System.nanoTime(); }
        wake();
    }

    void pressAt(Point2D point) {
        requestFocus();
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
        if (pressedSlot != null) { dropRowCard(hit); return; }
        if (!dragging && hit != null && button(hit, point)) { pressed = null; return; }
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

    /** Handles a click on an offer, the confirm button, a revealed card or the draw button; false for anything else. */
    private boolean button(Hit hit, Point2D point) {
        switch (hit.zone()) {
            case OFFER -> toggleOffer(hit.index());
            case CONFIRM -> confirmLandmarks();
            case LOOK -> { actions.move(new KeepCard(shown.view().looking().get(hit.index()).id())); wake(); }
            case DRAW -> draw();
            case SKIP -> skipTurn();
            case REVEALED -> {
                inspectedLandmark = null;
                inspectedSpyCard = shown.view().revealed().get(hit.index()).id();
                refreshCardDetails();
            }
            case OWN_LANDMARK, OPPONENT_LANDMARK -> {
                Rectangle tile = landmarkBounds(hit.zone(), hit.index());
                // The info end of a row stays inspectable even while Sabotage is selected.
                if (playAt(selected, hit) != null && point.getX() < tile.getMaxX() - 24) return false;
                inspectLandmark(hit);
            }
            default -> { return false; }
        }
        return true;
    }

    private void inspectLandmark(Hit hit) {
        inspectedSpyCard = null;
        inspectedLandmark = hit;
        refreshCardDetails();
    }

    /** Refreshes against public data, including a Sabotage or round change arriving while open. */
    private void refreshCardDetails() {
        if (inspectedSpyCard != null && shown != null) {
            CardInstance card = shown.view().revealed().stream().filter(c -> c.id() == inspectedSpyCard).findFirst().orElse(null);
            if (card == null || shown.view().phase() != MatchPhase.IN_PROGRESS) cardDetails.close();
            else cardDetails.showRevealed(card);
            return;
        }
        if (inspectedLandmark == null || shown == null) return;
        List<LandmarkCard> cards = landmarks(inspectedLandmark.zone());
        if (inspectedLandmark.index() >= cards.size() || shown.view().phase() != MatchPhase.IN_PROGRESS) {
            inspectedLandmark = null;
            cardDetails.close();
            return;
        }
        boolean own = inspectedLandmark.zone() == TableZone.OWN_LANDMARK;
        cardDetails.show(cards.get(inspectedLandmark.index()), own ? shown.view().you() : shown.view().opponent(), own);
    }

    /** Moves a picked-up row card to the place it was dropped on, swapping with any card there; display only. */
    private void dropRowCard(Hit hit) {
        int from = pressedSlot;
        boolean moved = dragging && hit != null && hit.zone() == TableZone.OWN && hit.index() != from;
        pressedSlot = null;
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
            if (code == KeyCode.ESCAPE) { inspectedLandmark = null; cardDetails.close(); }
            if (code != KeyCode.TAB) event.consume();
            return;
        }
        if (confirm.isVisible()) {
            if (code == KeyCode.ESCAPE) confirm.setVisible(false);
            event.consume();
            return;
        }
        if (shown != null && shown.view().choosingLandmarks()) {
            int offer = code.isDigitKey() ? code.getCode() - (code.isKeypadKey() ? KeyCode.NUMPAD1.getCode() : KeyCode.DIGIT1.getCode()) : -1;
            if (code == KeyCode.ENTER) confirmLandmarks();
            else if (offer >= 0 && offer < shown.view().offers().size()) toggleOffer(offer);
            event.consume();
            return;
        }
        switch (code) {
            case LEFT -> cycle(-1);
            case RIGHT -> cycle(1);
            case ENTER -> confirmKey();
            case D -> draw();
            case S -> skipTurn();
            case L -> {
                if (shown != null && shown.view().phase() == MatchPhase.IN_PROGRESS)
                    inspectLandmark(hover != null && (hover.zone() == TableZone.OWN_LANDMARK
                            || hover.zone() == TableZone.OPPONENT_LANDMARK) ? hover : new Hit(TableZone.OWN_LANDMARK, 0));
            }
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
        if (zone == TableZone.OPPONENT_LANDMARK && slot >= Rules.LANDMARK_PICKS) return;
        PlayCard play = playAt(selected, new Hit(zone, zone == TableZone.OWN ? rowOrder[slot] : slot));
        if (play != null) play(play);
    }

    private void confirmKey() {
        PlayCard cast = playAt(selected, new Hit(TableZone.CAST, 0));
        if (cast != null) play(cast);
        else if (playAt(selected, hover) != null) play(playAt(selected, hover));
    }

    /** The area a card's keyboard numbers refer to: a Unit row, or the enemy Landmarks for Sabotage. */
    private static TableZone targetZone(CardDef card) {
        if (card.effect() instanceof Effect.Sabotage) return TableZone.OPPONENT_LANDMARK;
        TargetSide side = card.effect() instanceof Effect.Destroy destroy ? destroy.side()
                : card.effect() instanceof Effect.Disable disable ? disable.side() : TargetSide.OWN;
        return side == TargetSide.OWN ? TableZone.OWN : TableZone.OPPONENT;
    }

    // ---------------------------------------------------------------- result and confirmation

    private void buildResult() {
        result.getStyleClass().add("leather");
        result.setId("matchResult");
        result.setAlignment(Pos.CENTER);
        resultTitle.setFont(FxKit.display(34));
        resultTitle.setTextFill(FxKit.AMBER);
        resultDetail.setFont(FxKit.normal(14));
        resultDetail.setTextFill(FxKit.CREAM);
        resultDetail.setWrapText(true);
        resultDetail.setTextAlignment(TextAlignment.CENTER);
        rematch.setOnAction(event -> actions.rematch());
        backToMenu.setOnAction(event -> actions.leave());
        HBox buttons = new HBox(12, rematch, backToMenu);
        buttons.setAlignment(Pos.CENTER);
        result.getChildren().addAll(resultTitle, resultDetail, buttons);
        result.setVisible(false);
        getChildren().add(result);
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
        resultTitle.setText(outcome.winner() == null ? "Draw" : outcome.winner() == view.seat() ? "Victory" : "Defeat");
        String how = outcome.forfeit() ? (outcome.winner() == view.seat() ? shown.opponentName() + " forfeited." : "You forfeited.") + "\n" : "";
        resultDetail.setText(how + "Match total: you " + view.you().matchTotal() + " – " + view.opponent().matchTotal() + " "
                + shown.opponentName() + "\n" + roundHistory(view));
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
        // Landmarks share the left column with scores, freeing the table for seven larger Units.
        landmarkTop = 76 + railHeight + 10;
        landmarkShelfHeight = Math.max(66, (h - landmarkTop - 12) / 2);
        landmarkW = railWidth;
        landmarkH = Math.max(28, Math.min(54, (landmarkShelfHeight - 24) / Rules.LANDMARK_PICKS));
        landmarkLeft = 20;
        leave.resizeRelocate(20, 18, 120, 40);
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
        // Over the hand, which no longer matters once the match is over, so both final rows stay visible.
        result.resizeRelocate(tableLeft + (tableWidth - 560) / 2.0, Math.max(ownTop, h - 214), 560, 200);
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

    /** A Landmark tile on one of the two shelves beneath the score panel. */
    Rectangle landmarkBounds(TableZone zone, int index) {
        boolean own = zone == TableZone.OWN || zone == TableZone.OWN_LANDMARK;
        return new Rectangle(landmarkLeft,
                landmarkTop + (own ? landmarkShelfHeight : 0) + 18 + index * (landmarkH + 6), landmarkW, landmarkH);
    }

    /** The manual-draw button under the player's own deck. */
    Rectangle drawBounds() { return new Rectangle(pilesX - 10, ownTop + 128, 76, 26); }

    Rectangle skipBounds() { return new Rectangle(pilesX - 18, ownTop + 162, 92, 26); }

    /** The five Landmark offers, large across the middle of the table while choosing. */
    Rectangle offerBounds(int index, int count) {
        int w = Math.min((tableWidth - (count - 1) * 18) / count, 170), h = w * 3 / 2;
        int left = tableLeft + (tableWidth - (count * w + (count - 1) * 18)) / 2;
        return new Rectangle(left + index * (w + 18), (int) Math.max(150, getHeight() / 2 - h / 2.0 - 10), w, h);
    }

    Rectangle confirmBounds() {
        Rectangle first = offerBounds(0, Rules.LANDMARK_OFFERS);
        return new Rectangle(tableLeft + tableWidth / 2 - 110, first.y + first.height + 28, 220, 34);
    }

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
        // While choosing Landmarks or keeping a revealed card, only that choice is on offer.
        if (view.choosingLandmarks()) {
            for (int i = 0; i < view.offers().size(); i++)
                if (offerBounds(i, view.offers().size()).contains(x, y)) return new Hit(TableZone.OFFER, i);
            return confirmBounds().contains(x, y) ? new Hit(TableZone.CONFIRM, 0) : null;
        }
        if (view.keeping()) {
            for (int i = 0; i < view.looking().size(); i++)
                if (lookBounds(i, view.looking().size()).contains(x, y)) return new Hit(TableZone.LOOK, i);
            return null;
        }
        for (int i = view.revealed().size() - 1; i >= 0; i--)
            if (revealedBounds(i).contains(x, y)) return new Hit(TableZone.REVEALED, i);
        for (int i = hand().size() - 1; i >= 0; i--)
            if (handBounds(i).contains(x, y)) return new Hit(TableZone.HAND, i);
        for (TableZone zone : List.of(TableZone.OWN, TableZone.OPPONENT))
            for (int slot = 0; slot < Row.SIZE; slot++)
                if (slotBounds(zone, slot).contains(x, y)) return new Hit(zone, slot);
        for (TableZone zone : List.of(TableZone.OWN_LANDMARK, TableZone.OPPONENT_LANDMARK))
            for (int i = 0; i < landmarks(zone).size(); i++)
                if (landmarkBounds(zone, i).contains(x, y)) return new Hit(zone, i);
        if (view.canDraw() && (drawBounds().contains(x, y) || pileBounds().contains(x, y))) return new Hit(TableZone.DRAW, 0);
        if (view.canSkip() && !state.reconnecting() && skipBounds().contains(x, y)) return new Hit(TableZone.SKIP, 0);
        if (playAt(selected, new Hit(TableZone.CAST, 0)) != null && castBounds().contains(x, y)) return new Hit(TableZone.CAST, 0);
        return null;
    }

    private Rectangle pileBounds() { return new Rectangle(pilesX, ownTop + 4, 54, 81); }

    private CardDef cardAt(Hit hit) {
        if (hit == null || shown == null) return null;
        PlayerView view = shown.view();
        return switch (hit.zone()) {
            case HAND -> hand().get(hit.index()).def();
            case OWN, OPPONENT -> { RowCard card = row(hit.zone()).at(hit.index()); yield card == null ? null : card.def(); }
            case OWN_LANDMARK, OPPONENT_LANDMARK -> landmarks(hit.zone()).get(hit.index()).def();
            case OFFER -> view.offers().get(hit.index()).def();
            case LOOK -> view.looking().get(hit.index()).def();
            case REVEALED -> view.revealed().get(hit.index()).def();
            default -> null;
        };
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
        drawLandmarks(g, TableZone.OPPONENT_LANDMARK, shown.view().opponent());
        drawLandmarks(g, TableZone.OWN_LANDMARK, shown.view().you());
        drawPiles(g, shown.view().opponent(), opponentTop, null);
        drawPiles(g, shown.view().you(), ownTop, shown.view().deckTop());
        drawDrawButton(g);
        drawSkipButton(g);
        // The choice overlays carry their own heading; the status line would show through under them.
        if (!shown.view().choosingLandmarks() && !shown.view().keeping()) drawStatus(g);
        drawHand(g);
        if (shown.view().choosingLandmarks()) drawOffers(g);
        else if (shown.view().keeping()) drawLooking(g);
    }

    private void drawHeader(GraphicsContext g) {
        PlayerView view = shown.view();
        text(g, "ROUND " + view.round() + " OF " + Rules.ROUNDS + "  ·  vs " + shown.opponentName()
                + (shown.practice() ? "  ·  PRACTICE" : ""), 160, 36, FxKit.bold(16), FxKit.CREAM, false);
        String history = roundHistory(view);
        text(g, "Match total: you " + view.you().matchTotal() + " – " + view.opponent().matchTotal()
                + (history.isEmpty() ? "" : "   ·   " + history), 160, 56, FxKit.normal(12), FxKit.MUTED, false);
        int count = view.opponent().handCount(), backW = 22, backH = 33, right = opponentHandAnchorX() - 18;
        Image back = FxKit.back(backW, backH, pixelScale());
        for (int i = 0; i < count; i++)
            g.drawImage(back, right - backW - (count - 1 - i) * 12, 20, backW + CardFace.SHADOW_X, backH + CardFace.SHADOW_Y);
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
            g.drawImage(FxKit.face(revealed.get(i).def(), List.of(), r.width, r.height, false, pixelScale()), r.x, r.y,
                    r.width + CardFace.SHADOW_X, r.height + CardFace.SHADOW_Y);
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
        text(g, title + "  ·  " + side.score().total() + " pts  ·  " + side.playsRemaining() + " plays left", rowLeft, top - 12,
                FxKit.bold(12), FxKit.CREAM, true);
        if (trace != null) drawGain(g, zone == TableZone.OWN ? trace.ownGain : trace.opponentGain, top);
        Long active = dragging ? pressed : selected;
        boolean rearranging = dragging && pressedSlot != null && zone == TableZone.OWN;
        for (int slot = 0; slot < Row.SIZE; slot++) {
            Rectangle r = slotBounds(zone, slot);
            RowCard card = side.row().at(slot);
            // A card still travelling here is drawn by its animation instead; one being moved, at the pointer.
            if (card != null && staged && effects.hiddenOnRow(card.card().id())) card = null;
            if (rearranging && slot == pressedSlot) card = null;
            boolean destination = rearranging ? slot != pressedSlot : playAt(active, new Hit(zone, slot)) != null;
            boolean hovered = hover != null && hover.zone() == zone && hover.index() == slot;
            if (card == null) emptySlot(g, r, destination, hovered);
            else g.drawImage(FxKit.face(card.def(), card.suppressed(), r.width, r.height, destination && hovered, pixelScale()),
                    r.x, r.y, r.width + CardFace.SHADOW_X, r.height + CardFace.SHADOW_Y);
            if (card != null && destination) outline(g, r, hovered ? FxKit.AMBER : FxKit.AMBER.deriveColor(0, 1, 1, .6));
            if (card != null) drawTriggers(g, zone, slot, r);
        }
        drawLinks(g, zone, side.score().pairs(), top + cardH);
    }

    /**
     * A player's two Landmarks in their own area; a sabotaged one is shaded until the round ends.
     * While Landmarks are being chosen the opponent's stay face down.
     */
    private void drawLandmarks(GraphicsContext g, TableZone zone, SideView side) {
        Rectangle first = landmarkBounds(zone, 0);
        text(g, zone == TableZone.OWN_LANDMARK ? "YOUR LANDMARKS" : "OPPONENT LANDMARKS",
                first.x, first.y - 7, FxKit.bold(10), FxKit.CREAM, true);
        Long active = dragging ? pressed : selected;
        for (int i = 0; i < Rules.LANDMARK_PICKS; i++) {
            Rectangle r = landmarkBounds(zone, i);
            if (i >= side.landmarks().size()) {
                landmarkTile(g, r, null, side.ready(), false);
                continue;
            }
            LandmarkCard landmark = side.landmarks().get(i);
            if (staged && effects.hiddenOnRow(landmark.card().id())) continue;
            boolean destination = playAt(active, new Hit(zone, i)) != null;
            boolean hovered = hover != null && hover.zone() == zone && hover.index() == i;
            landmarkTile(g, r, landmark, false, hovered);
            if (destination) outline(g, r, hovered ? FxKit.AMBER : FxKit.AMBER.deriveColor(0, 1, 1, .6));
            if (trace != null) drawLandmarkFlash(g, zone, r);
        }
    }

    /** A compact named thumbnail; the original card proportions and hover details stay intact. */
    private void landmarkTile(GraphicsContext g, Rectangle r, LandmarkCard landmark, boolean concealed, boolean highlighted) {
        CardDef card = landmark == null ? null : landmark.def();
        Color stateColor = landmark == null ? FxKit.MUTED : landmark.active() ? FxKit.AMBER : FxKit.CORAL;
        g.setFill(highlighted ? Color.web("#3C3024") : Color.web("#211D18"));
        g.fillRoundRect(r.x, r.y, r.width, r.height, 6, 6);
        g.setStroke(highlighted ? FxKit.AMBER : Color.rgb(173, 137, 84, .3));
        g.setLineWidth(1);
        g.strokeRoundRect(r.x, r.y, r.width, r.height, 6, 6);
        int artW = Math.min(28, (r.height - 8) * 2 / 3), artH = artW * 3 / 2;
        Image art = card != null ? FxKit.face(card, List.of(), artW, artH, highlighted, pixelScale())
                : concealed ? FxKit.back(artW, artH, pixelScale()) : null;
        if (art != null) g.drawImage(art, r.x + 5, r.y + (r.height - artH) / 2.0,
                artW + CardFace.SHADOW_X, artH + CardFace.SHADOW_Y);
        String name = card != null ? card.name() : concealed ? "Chosen" : "Not chosen";
        double x = r.x + artW + 13, width = r.width - artW - 57;
        Font font = FxKit.bold(12);
        text(g, FxKit.fit(name, font, width), x, r.getCenterY() + (r.height >= 46 ? -3 : 4), font,
                card == null ? FxKit.MUTED : FxKit.CREAM, true);
        if (r.height >= 46 && landmark != null)
            text(g, landmark.active() ? "Active" : "Disabled this round", x, r.getCenterY() + 12,
                    FxKit.normal(10), stateColor, false);
        g.setFill(stateColor);
        g.fillOval(r.getMaxX() - 34, r.getCenterY() - 2, 4, 4);
        g.setStroke(FxKit.MUTED);
        g.strokeOval(r.getMaxX() - 22, r.getCenterY() - 7, 14, 14);
        text(g, "i", r.getMaxX() - 17, r.getCenterY() + 4, FxKit.bold(11), FxKit.CREAM, false);
    }

    /** A flash on a Landmark whose effect on its owner's score just changed. */
    private void drawLandmarkFlash(GraphicsContext g, TableZone zone, Rectangle r) {
        long flash = traceElapsed() - trace.landmarkAt;
        TableZone row = zone == TableZone.OWN_LANDMARK ? TableZone.OWN : TableZone.OPPONENT;
        if (trace.landmarksChanged.contains(row) && flash >= 0 && flash < ScoringTrace.LANDMARK_MS)
            glow(g, r, 1 - flash / (double) ScoringTrace.LANDMARK_MS);
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
            Rectangle a = slotBounds(zone, pair.slotA()), b = slotBounds(zone, pair.slotB());
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

    /** A deck and its counts; {@code top}, when a Watchtower shows it, is the player's own top card, face up. */
    private void drawPiles(GraphicsContext g, SideView side, int top, CardInstance deckTop) {
        if (side.deckCount() > 0) {
            Image image = deckTop == null ? FxKit.back(54, 81, pixelScale()) : FxKit.face(deckTop.def(), List.of(), 54, 81, false, pixelScale());
            g.drawImage(image, pilesX, top + 4, 54 + CardFace.SHADOW_X, 81 + CardFace.SHADOW_Y);
        } else emptySlot(g, new Rectangle(pilesX, top + 4, 54, 81), false, false);
        if (deckTop != null) text(g, "TOP CARD", pilesX, top, FxKit.bold(9), FxKit.AMBER, true);
        text(g, "Deck " + side.deckCount(), pilesX, top + 103, FxKit.normal(11), FxKit.CREAM, true);
        text(g, "Discard " + side.discardCount(), pilesX, top + 119, FxKit.normal(11), FxKit.CREAM, true);
    }

    /** The manual-draw button with the draws left this round; dimmed when no draw is possible now. */
    private void drawDrawButton(GraphicsContext g) {
        if (shown.view().phase() != MatchPhase.IN_PROGRESS) return;
        Rectangle r = drawBounds();
        boolean can = shown.view().canDraw() && !state.reconnecting();
        boolean lit = can && hover != null && hover.zone() == TableZone.DRAW;
        g.setFill(can ? (lit ? FxKit.AMBER : FxKit.AMBER.deriveColor(0, 1, 1, .78)) : Color.rgb(90, 80, 66, .7));
        g.fillRoundRect(r.x, r.y, r.width, r.height, r.height, r.height);
        Font font = FxKit.bold(12);
        String label = "DRAW " + shown.view().you().drawsRemaining() + "/" + Rules.MANUAL_DRAWS;
        g.setFont(font);
        g.setFill(can ? FxKit.INK : FxKit.MUTED);
        g.fillText(label, r.getCenterX() - FxKit.width(label, font) / 2, r.y + 17);
    }

    /** Skip sits below Draw, with its allowance cost always visible. */
    private void drawSkipButton(GraphicsContext g) {
        if (shown.view().phase() != MatchPhase.IN_PROGRESS) return;
        Rectangle r = skipBounds();
        boolean can = shown.view().canSkip() && !state.reconnecting();
        boolean lit = can && hover != null && hover.zone() == TableZone.SKIP;
        g.setFill(lit ? FxKit.AMBER : Color.rgb(90, 80, 66, .85));
        g.fillRoundRect(r.x, r.y, r.width, r.height, r.height, r.height);
        Font font = FxKit.bold(11);
        String label = "SKIP TURN";
        g.setFont(font);
        g.setFill(lit ? FxKit.INK : can ? FxKit.CREAM : FxKit.MUTED);
        g.fillText(label, r.getCenterX() - FxKit.width(label, font) / 2, r.y + 17);
        text(g, "S · uses 1 play", r.x + 6, r.y + 40, FxKit.normal(11), FxKit.MUTED, true);
    }

    /** The five Landmark offers over a dimmed table; two are ticked, then confirmed. */
    private void drawOffers(GraphicsContext g) {
        dimTable(g);
        List<CardInstance> offers = shown.view().offers();
        Rectangle first = offerBounds(0, offers.size());
        centered(g, "Choose two Landmarks", FxKit.decorative(28), FxKit.AMBER, first.y - 44);
        centered(g, "Click two cards (or press 1–5) — " + picking.size() + " of " + Rules.LANDMARK_PICKS
                + " chosen. They stay in your Landmark area for all three rounds.", FxKit.normal(14), FxKit.CREAM, first.y - 18);
        for (int i = 0; i < offers.size(); i++) {
            Rectangle r = offerBounds(i, offers.size());
            boolean ticked = picking.contains(offers.get(i).id());
            boolean hovered = hover != null && hover.zone() == TableZone.OFFER && hover.index() == i;
            double lift = ticked ? 14 : hovered ? 6 : 0;
            handCard(g, offers.get(i).def(), List.of(), r.getCenterX(), r.getCenterY() - lift, 1, 0, ticked, lift, null, r.width, r.height);
            if (ticked) tick(g, r.getMaxX() - 16, r.y - lift + 16);
        }
        Rectangle confirm = confirmBounds();
        boolean ready = picking.size() == Rules.LANDMARK_PICKS;
        g.setFill(ready ? FxKit.AMBER : Color.rgb(90, 80, 66, .8));
        g.fillRoundRect(confirm.x, confirm.y, confirm.width, confirm.height, confirm.height, confirm.height);
        String label = ready ? "KEEP THESE TWO" : "CHOOSE " + (Rules.LANDMARK_PICKS - picking.size()) + " MORE";
        g.setFont(FxKit.bold(14));
        g.setFill(ready ? FxKit.INK : FxKit.MUTED);
        g.fillText(label, confirm.getCenterX() - FxKit.width(label, FxKit.bold(14)) / 2, confirm.y + 22);
        overlayNotice(g, confirm.y + confirm.height + 26);
    }

    /** A gold check badge on a ticked Landmark offer. */
    private static void tick(GraphicsContext g, double cx, double cy) {
        g.setFill(FxKit.AMBER);
        g.fillOval(cx - 13, cy - 13, 26, 26);
        g.setStroke(FxKit.INK);
        g.setLineWidth(3);
        g.strokePolyline(new double[]{cx - 6, cx - 1, cx + 7}, new double[]{cy, cy + 5, cy - 5}, 3);
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
            g.setFill(lit ? FxKit.AMBER : FxKit.AMBER.deriveColor(0, 1, 1, .67));
            g.fillRoundRect(cast.x, cast.y, cast.width, cast.height, cast.height, cast.height);
            g.setFont(FxKit.bold(13));
            g.setFill(FxKit.INK);
            CardInstance spell = handCard(selected);
            String label = Rules.takesTarget(spell.def().effect()) ? "Cast without a target" : "Cast " + spell.def().name();
            g.fillText(label, cast.getCenterX() - FxKit.width(label, FxKit.bold(13)) / 2, cast.y + 19);
        }
    }

    private String statusText() {
        if (state.reconnecting()) return "Connection lost — reconnecting…";
        if (System.currentTimeMillis() < noticeUntil && notice != null) return notice;
        PlayerView view = shown.view();
        if (view.phase() == MatchPhase.FINISHED) return "Match over";
        if (!shown.opponentConnected()) return shown.opponentName() + " disconnected — their seat is held for now";
        if (view.choosingLandmarks()) return "Choose two Landmarks to keep for the whole match";
        if (view.phase() == MatchPhase.CHOOSING_LANDMARKS) return "Waiting for " + shown.opponentName() + " to choose their Landmarks…";
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
        if (effect instanceof Effect.Sabotage) return "Choose an enemy Landmark to sabotage";
        return "Choose a target for " + card.def().name();
    }

    /** Hover details, then a labeled preview of the hovered destination, then the last public event. */
    String detailText() {
        CardInstance card = handCard(dragging ? pressed : selected);
        PlayCard preview = card == null ? null : playAt(card.id(), hover);
        if (preview != null) return previewText(card, preview);
        CardDef hovered = cardAt(hover);
        if (hovered != null) return CardFace.details(hovered);
        List<MatchEvent> events = shown.view().events();
        return events.isEmpty() ? null : describe(events.get(events.size() - 1));
    }

    private String previewText(CardInstance card, PlayCard play) {
        PlayerView view = shown.view();
        Rules.Resolution after = Rules.resolve(card, play, view.hand(), view.board());
        long own = after.board().ownScore().total() - view.you().score().total();
        long theirs = after.board().opponentScore().total() - view.opponent().score().total();
        return "PREVIEW · " + after.description() + " · you " + signed(own) + ", " + shown.opponentName() + " " + signed(theirs);
    }

    private static String signed(long value) { return (value >= 0 ? "+" : "−") + Math.abs(value); }

    private String describe(MatchEvent event) {
        if (event.actor() == null) return event.text();
        return (event.actor() == shown.view().seat() ? "You " : shown.opponentName() + " ") + event.text() + ".";
    }

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
            handCard(g, card.def(), r.getCenterX(), r.getCenterY(), pose[1], fanAngle(i, hand.size()) + hoverWobble(card.id()),
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
        g.drawImage(FxKit.face(card, marks, cardW, cardH, chosen, pixelScale()), -cardW / 2.0, -cardH / 2.0,
                cardW + CardFace.SHADOW_X, cardH + CardFace.SHADOW_Y);
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
