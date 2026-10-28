package deckduel.client.ui;

import deckduel.core.card.ChooseKing;
import deckduel.core.card.King;
import deckduel.core.card.MatchPhase;
import deckduel.core.card.PlayerView;
import deckduel.core.card.SideView;
import java.util.ArrayList;
import java.util.List;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyValue;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.geometry.Pos;
import javafx.geometry.Bounds;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

/** Snapshot-only King slots, timed selection, hand-preview toggle and original-art inspectors. */
final class KingTableControls extends Pane {
    private final Consumer<ChooseKing> choose;
    private final Pane slots = new Pane();
    private final Pane flights = new Pane();
    private final Timeline deployment = new Timeline();
    private final StackPane choice = new StackPane(), inspector = new StackPane();
    private final Label notice = label(""), countdown = label("");
    private final Button toggle = new Button();
    private final SVGPath toggleIcon = new SVGPath();
    private final Label handCountdown = new Label();
    private final ImageView hoverArt = new ImageView();
    private final List<Button> confirmations = new ArrayList<>();
    private final List<Node> offerCards = new ArrayList<>();
    private final Map<King, ImageView> offerPictures = new EnumMap<>(King.class);
    private final Timeline clock = new Timeline(new KeyFrame(Duration.millis(200), e -> updateCountdown()));
    private final ParallelTransition reveal = new ParallelTransition();
    private PlayerView view, builtView;
    private King candidate, inspected;
    private boolean inspectingOpponent, blocked, collapsed, reducedMotion;
    private double slotsX, ownY, opponentY, cardWidth = 67, cardHeight = 94;
    private long deadlineMillis;

    KingTableControls(Consumer<ChooseKing> choose) {
        this.choose = choose;
        setId("kingControls"); setManaged(false); setPickOnBounds(false);
        slots.setManaged(false); slots.setPickOnBounds(false);
        flights.setManaged(false); flights.setMouseTransparent(true);
        flights.setId("kingFlights");
        deployment.setOnFinished(e -> finishDeployment());
        choice.setManaged(false); inspector.setManaged(false);
        choice.setId("kingSelection"); inspector.setId("kingInspector");
        choice.setStyle("-fx-background-color: rgba(8,6,4,.91);");
        inspector.setStyle("-fx-background-color: rgba(8,6,4,.94);");
        choice.setVisible(false); inspector.setVisible(false);
        hoverArt.setId("kingHoverArt"); hoverArt.setManaged(false); hoverArt.setMouseTransparent(true);
        hoverArt.setPreserveRatio(true); hoverArt.setSmooth(true); hoverArt.setVisible(false);
        toggle.setId("kingHandToggle"); toggle.setManaged(false); toggle.setVisible(false);
        toggleIcon.setFill(Color.TRANSPARENT); toggleIcon.setStroke(FxKit.CREAM); toggleIcon.setStrokeWidth(1.8);
        toggle.setGraphic(toggleIcon);
        updateToggleIcon();
        handCountdown.setId("kingHandCountdown"); handCountdown.setManaged(false);
        handCountdown.setMouseTransparent(true); handCountdown.setAlignment(Pos.CENTER);
        handCountdown.setFont(FxKit.display(22)); handCountdown.setTextFill(FxKit.AMBER);
        handCountdown.setVisible(false);
        toggle.setStyle("-fx-background-color: linear-gradient(to bottom, #285e68, #133d48);"
                + "-fx-border-color: #c9b47c; -fx-border-width: 1.5; -fx-border-radius: 4;"
                + "-fx-background-radius: 4; -fx-text-fill: #fff1cc; -fx-font-size: 14px;"
                + "-fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 7 20;");
        toggle.setOnAction(e -> toggleHand());
        countdown.setId("kingCountdown");
        countdown.setStyle("-fx-text-fill: #e7c47e; -fx-font-size: 16px; -fx-font-weight: bold;");
        clock.setCycleCount(Animation.INDEFINITE);
        getChildren().addAll(slots, choice, flights, hoverArt, handCountdown, toggle, inspector);
    }

    void update(PlayerView next, long deadline, boolean blocked, boolean dealing, boolean reducedMotion) {
        PlayerView previous = builtView;
        boolean deploy = previous != null && previous.round() == next.round()
                && previous.phase() == MatchPhase.KING_SELECTION && next.phase() == MatchPhase.IN_PROGRESS;
        Map<King, Bounds> origins = new EnumMap<>(King.class);
        if (deploy && !reducedMotion && !blocked && !dealing) {
            finishReveal();
            choice.applyCss(); choice.layout();
            offerPictures.forEach((king, picture) -> origins.put(king,
                    sceneToLocal(picture.localToScene(picture.getLayoutBounds()))));
        }
        boolean newOffer = builtView == null || builtView.round() != next.round()
                || !builtView.kingOffer().equals(next.kingOffer());
        boolean rebuild = newOffer || builtView.selectedKing() != next.selectedKing();
        this.view = next; this.blocked = blocked; this.reducedMotion = reducedMotion; deadlineMillis = deadline;
        if (newOffer) { finishDeployment(); candidate = null; collapsed = false; updateToggleIcon(); }
        if (builtView != next) {
            rebuildSlots();
            if (rebuild) rebuildChoice();
            refreshInspector();
            builtView = next;
        }
        boolean selecting = next.phase() == MatchPhase.KING_SELECTION;
        toggle.setVisible(selecting && !dealing);
        handCountdown.setVisible(toggle.isVisible() && collapsed);
        boolean show = selecting && !dealing && !collapsed;
        setChoiceVisible(show);
        if (!origins.isEmpty()) startDeployment(previous, origins);
        updateConfirmations();
        if (selecting && toggle.isVisible()) {
            if (clock.getStatus() != Animation.Status.RUNNING) clock.play();
            updateCountdown();
        } else clock.stop();
        if (reducedMotion || blocked) { finishReveal(); finishDeployment(); }
    }

    void notice(String text) { notice.setText(text == null ? "" : text); }

    void position(double x, double opponentY, double ownY, double w, double h, double faceWidth, double faceHeight) {
        if (getWidth() != w || getHeight() != h || slotsX != x || this.ownY != ownY
                || this.opponentY != opponentY || cardWidth != faceWidth || cardHeight != faceHeight) finishDeployment();
        resize(w, h);
        flights.resize(w, h);
        if (slotsX != x || this.ownY != ownY || this.opponentY != opponentY
                || cardWidth != faceWidth || cardHeight != faceHeight) {
            slotsX = x; this.ownY = ownY; this.opponentY = opponentY;
            cardWidth = faceWidth; cardHeight = faceHeight;
            if (view != null) rebuildSlots();
        }
        choice.resizeRelocate(0, 65, w, Math.max(0, h - 130));
        inspector.resizeRelocate(0, 0, w, h);
        toggle.resizeRelocate((w - 84) / 2, h - 48, 84, 36);
        handCountdown.resizeRelocate((w - 80) / 2, 76, 80, 32);
    }

    private void toggleHand() {
        if (view == null || view.phase() != MatchPhase.KING_SELECTION) return;
        collapsed = !collapsed;
        updateToggleIcon();
        handCountdown.setVisible(collapsed);
        hoverArt.setVisible(false);
        setChoiceVisible(!collapsed);
        updateCountdown();
    }

    private void setChoiceVisible(boolean visible) {
        if (visible && !choice.isVisible()) {
            choice.setVisible(true);
            if (!reducedMotion) animateOffer();
        } else if (!visible) {
            finishReveal(); choice.setVisible(false);
        }
    }

    /** Existing paintings float up and fade in from left to right; no new art or sound. */
    private void animateOffer() {
        finishReveal();
        for (int i = 0; i < offerCards.size(); i++) {
            Node card = offerCards.get(i);
            card.setOpacity(0); card.setTranslateY(32); card.setScaleX(.93); card.setScaleY(.93);
            FadeTransition fade = new FadeTransition(Duration.millis(320), card);
            fade.setToValue(1);
            TranslateTransition rise = new TranslateTransition(Duration.millis(380), card);
            rise.setToY(0);
            ScaleTransition scale = new ScaleTransition(Duration.millis(380), card);
            scale.setToX(1); scale.setToY(1);
            reveal.getChildren().add(new SequentialTransition(new PauseTransition(Duration.millis(i * 85)),
                    new ParallelTransition(fade, rise, scale)));
        }
        reveal.playFromStart();
    }

    private void finishReveal() {
        reveal.stop(); reveal.getChildren().clear();
        for (Node card : offerCards) {
            card.setOpacity(1); card.setTranslateY(0); card.setScaleX(1); card.setScaleY(1);
        }
    }

    /** Only newly public ownership flies; a reconnect snapshot never replays a deployment. */
    private void startDeployment(PlayerView previous, Map<King, Bounds> origins) {
        finishDeployment();
        flyKings(previous.you(), view.you(), false, origins);
        flyKings(previous.opponent(), view.opponent(), true, origins);
        if (!flights.getChildren().isEmpty()) {
            slots.setMouseTransparent(true);
            deployment.playFromStart();
        }
    }

    private void flyKings(SideView previous, SideView next, boolean opponent, Map<King, Bounds> origins) {
        for (int i = previous.kings().kings().size(); i < next.kings().kings().size(); i++) {
            King king = next.kings().kings().get(i);
            Bounds from = origins.get(king);
            if (from == null || from.getWidth() <= 0 || from.getHeight() <= 0) continue;
            ImageView flying = art(king, from.getWidth(), from.getHeight());
            flying.setId((opponent ? "foeKingFlight" : "yourKingFlight") + i);
            flying.setManaged(false); flying.setEffect(KingVisuals.aura(king));
            flying.setX(from.getMinX()); flying.setY(from.getMinY());
            flights.getChildren().add(flying);
            double x = slotsX + i * (cardWidth + 10), y = opponent ? opponentY : ownY;
            deployment.getKeyFrames().add(new KeyFrame(Duration.millis(650),
                    new KeyValue(flying.xProperty(), x, Interpolator.EASE_BOTH),
                    new KeyValue(flying.yProperty(), y, Interpolator.EASE_BOTH),
                    new KeyValue(flying.fitWidthProperty(), cardWidth, Interpolator.EASE_BOTH),
                    new KeyValue(flying.fitHeightProperty(), cardHeight, Interpolator.EASE_BOTH)));
            Node destination = slots.lookup("#" + (opponent ? "foeKing" : "yourKing") + i);
            if (destination != null) destination.getParent().setOpacity(0);
        }
    }

    private void finishDeployment() {
        deployment.stop(); deployment.getKeyFrames().clear(); flights.getChildren().clear();
        slots.setMouseTransparent(false);
        slots.getChildren().forEach(cell -> cell.setOpacity(1));
    }

    boolean deploying() { return deployment.getStatus() == Animation.Status.RUNNING; }

    private void updateCountdown() {
        if (view == null) return;
        long seconds = deadlineMillis == 0 ? 30 : Math.max(0, (deadlineMillis - System.currentTimeMillis() + 999) / 1000);
        String time = seconds + "s";
        countdown.setText(time + " · " + (view.selectedKing() == null ? "First King is chosen when time runs out" : "Waiting for opponent"));
        handCountdown.setText(time);
        toggle.setAccessibleText((collapsed ? "Show King choices" : "Hide King choices to inspect your hand") + ". " + time + " remaining");
    }

    private void updateToggleIcon() {
        toggleIcon.setContent("M2 12 Q12 0 22 12 Q12 24 2 12 Z "
                + "M15 12 A3 3 0 1 1 9 12 A3 3 0 1 1 15 12"
                + (collapsed ? "" : " M2 2 L22 22"));
    }

    private void rebuildSlots() {
        hoverArt.setVisible(false);
        slots.getChildren().clear(); slots.resize(getWidth(), getHeight());
        addSlots(view.opponent(), opponentY, true);
        addSlots(view.you(), ownY, false);
    }

    private void addSlots(SideView side, double y, boolean opponent) {
        for (int i = 0; i < 2; i++) {
            VBox cell = new VBox(3); cell.setAlignment(Pos.TOP_CENTER);
            cell.setPrefSize(cardWidth, cardHeight + 20);
            cell.relocate(slotsX + i * (cardWidth + 10), y);
            if (i < side.kings().kings().size()) {
                King king = side.kings().kings().get(i);
                Button button = new Button();
                button.setId((opponent ? "foeKing" : "yourKing") + i);
                button.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
                ImageView painting = art(king, cardWidth, cardHeight);
                painting.setEffect(KingVisuals.aura(king));
                button.setGraphic(painting);
                button.setAccessibleText(king.title() + ", " + details(king, side));
                button.setOnMouseEntered(e -> preview(king, button));
                button.setOnMouseExited(e -> hoverArt.setVisible(false));
                button.focusedProperty().addListener((o, was, focused) -> {
                    if (focused) preview(king, button); else hoverArt.setVisible(false);
                });
                button.setOnAction(e -> inspect(king, side, opponent));
                Label points = new Label("+" + points(king, side));
                points.setId((opponent ? "foeKingPoints" : "yourKingPoints") + i);
                points.setFont(FxKit.display(22)); points.setTextFill(KingVisuals.color(king));
                points.setAccessibleText(king.title() + " contribution: " + points.getText());
                points.setEffect(new DropShadow(3, Color.rgb(0, 0, 0, .85)));
                cell.getChildren().addAll(button, points);
            } else {
                Label empty = label("");
                empty.setAccessibleText("Empty King position " + (i + 1));
                empty.setAlignment(Pos.CENTER); empty.setPrefSize(cardWidth, cardHeight);
                empty.setStyle("-fx-border-color: #8c7449; -fx-text-fill: #d5c6a1;");
                cell.getChildren().add(empty);
            }
            slots.getChildren().add(cell);
            if (flights.lookup("#" + (opponent ? "foeKingFlight" : "yourKingFlight") + i) != null) cell.setOpacity(0);
        }
    }

    /** Hover shows only the complete painting, without a text box or opaque information panel. */
    private void preview(King king, Node source) {
        var at = sceneToLocal(source.localToScene(source.getBoundsInLocal()));
        double height = Math.min(480, getHeight() * .58), width = height * 1060 / 1482;
        hoverArt.setImage(KingArt.image(king)); hoverArt.setFitWidth(width); hoverArt.setFitHeight(height);
        hoverArt.setEffect(KingVisuals.aura(king));
        hoverArt.relocate(Math.max(12, at.getMinX() - width - 18),
                Math.max(12, Math.min(at.getCenterY() - height / 2, getHeight() - height - 12)));
        hoverArt.setAccessibleText(king.title()); hoverArt.setVisible(true);
    }

    private void rebuildChoice() {
        finishReveal(); confirmations.clear(); offerCards.clear(); offerPictures.clear();
        VBox content = new VBox(10); content.setAlignment(Pos.CENTER);
        content.getChildren().addAll(label(view.selectedKing() == null ? "CHOOSE YOUR KING · ROUND " + view.round()
                : view.selectedKing().title() + " confirmed · waiting for opponent"), countdown);
        HBox offers = new HBox(18); offers.setAlignment(Pos.CENTER);
        for (King king : view.kingOffer()) offers.getChildren().add(offer(king));
        content.getChildren().addAll(offers, label("Your choice stays private until both players confirm."), notice);
        choice.getChildren().setAll(content);
    }

    private VBox offer(King king) {
        VBox card = new VBox(6); card.setAlignment(Pos.CENTER);
        Button select = new Button(); select.setId("offer-" + king.id());
        select.setStyle("-fx-background-color: transparent; -fx-padding: 3;");
        ImageView picture = art(king, 285, 400);
        picture.fitHeightProperty().bind(heightProperty().subtract(265).multiply(.86));
        picture.fitWidthProperty().bind(widthProperty().subtract(110).divide(3));
        select.setGraphic(picture);
        offerPictures.put(king, picture);
        select.setOnMouseEntered(e -> picture.setEffect(KingVisuals.aura(king)));
        select.setOnMouseExited(e -> picture.setEffect(candidate == king ? KingVisuals.aura(king) : null));
        select.focusedProperty().addListener((o, was, focused) -> {
            picture.setEffect(focused || candidate == king ? KingVisuals.aura(king) : null);
        });
        select.setAccessibleText(king.title() + ", King of " + king.nation() + ": " + king.description());
        select.setOnAction(e -> {
            if (!view.choosingKing() || blocked) return;
            candidate = king;
            offerPictures.forEach((offered, image) -> image.setEffect(offered == king ? KingVisuals.aura(offered) : null));
            updateConfirmations();
        });
        Button confirm = FxKit.pill("Confirm", "gold");
        confirm.setUserData(king); confirm.setId("confirm-" + king.id());
        confirm.setAccessibleText("Confirm " + king.title());
        confirm.setOnAction(e -> {
            if (!blocked && view.choosingKing() && candidate == king) choose.accept(new ChooseKing(king));
        });
        confirmations.add(confirm); card.getChildren().addAll(select, confirm); offerCards.add(card);
        return card;
    }

    private void updateConfirmations() {
        confirmations.forEach(button -> button.setDisable(blocked || !view.choosingKing() || candidate != button.getUserData()));
    }

    private void refreshInspector() {
        if (inspected == null) return;
        SideView side = inspectingOpponent ? view.opponent() : view.you();
        if (!side.kings().kings().contains(inspected)) closeInspector();
        else inspect(inspected, side, inspectingOpponent);
    }

    private void inspect(King king, SideView side, boolean opponent) {
        hoverArt.setVisible(false); inspected = king; inspectingOpponent = opponent;
        VBox content = new VBox(12); content.setAlignment(Pos.CENTER);
        ImageView picture = art(king, 343, 480);
        picture.setId("kingInspectorArt");
        picture.fitHeightProperty().bind(Bindings.min(480, heightProperty().subtract(210)));
        picture.fitWidthProperty().bind(picture.fitHeightProperty().multiply(1060.0 / 1482));
        Label description = label(king.title() + ", King of " + king.nation() + " · " + details(king, side));
        description.setWrapText(true); description.setMaxWidth(700);
        Button close = FxKit.pill("Close", "gold"); close.setOnAction(e -> closeInspector());
        content.getChildren().addAll(picture, description, close);
        inspector.getChildren().setAll(content); inspector.setVisible(true); inspector.toFront();
    }

    void closeInspector() { inspected = null; inspector.setVisible(false); inspector.getChildren().clear(); }
    void suspend() { clock.stop(); finishReveal(); finishDeployment(); hoverArt.setVisible(false); closeInspector(); }
    void reset() {
        suspend(); builtView = null; view = null; candidate = null; collapsed = false;
        choice.setVisible(false); toggle.setVisible(false); handCountdown.setVisible(false);
    }
    boolean choosingVisible() { return choice.isVisible(); }
    boolean selectionHidden() { return collapsed && view != null && view.phase() == MatchPhase.KING_SELECTION; }
    boolean revealing() { return reveal.getStatus() == Animation.Status.RUNNING; }
    private static int points(King king, SideView side) {
        return side.score().kings().stream().filter(b -> b.king() == king).mapToInt(b -> b.points()).sum();
    }
    private static String details(King king, SideView side) {
        return king.ability() + ": " + king.description() + " Current contribution: +" + points(king, side)
                + (king == King.NETHRION ? ". Secret Edict: " + (side.kings().edictUsed() ? "used this round" : "ready") : "");
    }
    private static ImageView art(King king, double w, double h) {
        ImageView art = new ImageView(KingArt.image(king));
        art.setPreserveRatio(true); art.setSmooth(true); art.setFitWidth(w); art.setFitHeight(h);
        return art;
    }
    private static Label label(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #f1dfb9; -fx-font-size: 12px;");
        return label;
    }
}
