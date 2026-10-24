package deckduel.client.ui;

import deckduel.core.card.ChooseKing;
import deckduel.core.card.King;
import deckduel.core.card.MatchPhase;
import deckduel.core.card.PlayerView;
import deckduel.core.card.SideView;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Snapshot-only King slots, simultaneous selection and original-art inspector. JavaFX thread only. */
final class KingTableControls extends Pane {
    private final Consumer<ChooseKing> choose;
    private final Pane slots = new Pane();
    private final StackPane choice = new StackPane(), inspector = new StackPane();
    private final Label notice = label("");
    private final List<Button> confirmations = new ArrayList<>();
    private PlayerView view;
    private King candidate, inspected;
    private boolean inspectingOpponent, blocked;
    private double slotsX, ownY, opponentY;
    private PlayerView builtView;

    KingTableControls(Consumer<ChooseKing> choose) {
        this.choose = choose;
        setId("kingControls");
        setManaged(false);
        setPickOnBounds(false);
        slots.setManaged(false);
        slots.setPickOnBounds(false);
        choice.setManaged(false); inspector.setManaged(false);
        choice.setId("kingSelection"); inspector.setId("kingInspector");
        choice.setStyle("-fx-background-color: rgba(8,6,4,.94);");
        inspector.setStyle("-fx-background-color: rgba(8,6,4,.94);");
        choice.setVisible(false); inspector.setVisible(false);
        getChildren().addAll(slots, choice, inspector);
    }

    void update(PlayerView next, boolean blocked, boolean dealing) {
        this.view = next;
        this.blocked = blocked;
        boolean changed = builtView != next;
        if (changed) {
            if (builtView == null || !builtView.kingOffer().equals(next.kingOffer())) candidate = null;
            builtView = next;
            rebuildSlots();
            rebuildChoice();
            if (inspected != null) {
                SideView side = inspectingOpponent ? next.opponent() : next.you();
                if (!side.kings().kings().contains(inspected)) closeInspector();
                else inspect(inspected, side, inspectingOpponent);
            }
        }
        choice.setVisible(next.phase() == MatchPhase.KING_SELECTION && !dealing);
        confirmations.forEach(button -> button.setDisable(blocked || !next.choosingKing()));
    }

    void notice(String text) { notice.setText(text == null ? "" : text); }

    void position(double x, double opponentY, double ownY, double w, double h) {
        resize(w, h);
        if (slotsX != x || this.ownY != ownY || this.opponentY != opponentY) {
            slotsX = x; this.ownY = ownY; this.opponentY = opponentY;
            if (view != null) rebuildSlots();
        }
        choice.resizeRelocate(0, 65, w, Math.max(0, h - 65));
        inspector.resizeRelocate(0, 0, w, h);
    }

    private void rebuildSlots() {
        slots.getChildren().clear();
        slots.resize(getWidth(), getHeight());
        addSlots(view.opponent(), opponentY, true);
        addSlots(view.you(), ownY, false);
    }

    private void addSlots(SideView side, double y, boolean opponent) {
        for (int i = 0; i < 2; i++) {
            VBox cell = new VBox(3);
            cell.setAlignment(Pos.TOP_CENTER);
            cell.setPrefSize(67, 120);
            cell.relocate(slotsX + i * 72, y);
            if (i < side.kings().kings().size()) {
                King king = side.kings().kings().get(i);
                Button button = new Button();
                button.setId((opponent ? "foeKing" : "yourKing") + i);
                button.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
                button.setGraphic(art(king, 67, 94));
                button.setAccessibleText(king.title() + ", " + details(king, side));
                Tooltip tip = new Tooltip(details(king, side));
                tip.setGraphic(art(king, 280, 392));
                tip.setMaxWidth(410); tip.setWrapText(true);
                button.setTooltip(tip);
                button.setOnAction(e -> inspect(king, side, opponent));
                cell.getChildren().addAll(button, label(king.title() + " +" + points(king, side)));
            } else {
                Label empty = label("KING " + (i + 1));
                empty.setStyle("-fx-border-color: #8c7449; -fx-text-fill: #d5c6a1; -fx-padding: 22 5;");
                cell.getChildren().add(empty);
            }
            slots.getChildren().add(cell);
        }
    }

    private void rebuildChoice() {
        confirmations.clear();
        VBox content = new VBox(12);
        content.setAlignment(Pos.CENTER);
        content.getChildren().add(label(view.selectedKing() == null ? "CHOOSE YOUR KING · ROUND " + view.round()
                : view.selectedKing().title() + " confirmed · waiting for opponent"));
        HBox offers = new HBox(18); offers.setAlignment(Pos.CENTER);
        for (King king : view.kingOffer()) {
            VBox card = new VBox(6); card.setAlignment(Pos.CENTER);
            Button select = new Button();
            select.setId("offer-" + king.id());
            select.setStyle("-fx-background-color: transparent; -fx-padding: 3;");
            ImageView art = art(king, 285, 400);
            art.fitHeightProperty().bind(heightProperty().subtract(205).multiply(.82));
            art.fitWidthProperty().bind(widthProperty().subtract(110).divide(3));
            select.setGraphic(art);
            select.setAccessibleText(king.title() + ", King of " + king.nation() + ": " + king.description());
            select.setDisable(!view.choosingKing() || blocked);
            select.setOnAction(e -> {
                candidate = king;
                for (Button button : confirmations) button.setText(button.getUserData() == king ? "CONFIRM " + king.title() : "Select King");
            });
            Button confirm = FxKit.pill(candidate == king ? "CONFIRM " + king.title() : "Select King", "gold");
            confirm.setUserData(king);
            confirm.setId("confirm-" + king.id());
            confirm.setOnAction(e -> {
                if (candidate != king) { select.fire(); return; }
                if (!blocked && view.choosingKing()) choose.accept(new ChooseKing(king));
            });
            confirmations.add(confirm);
            card.getChildren().addAll(select, confirm);
            offers.getChildren().add(card);
        }
        content.getChildren().addAll(offers, label("Your choice stays private until both players confirm. No play or draw is spent."), notice);
        choice.getChildren().setAll(content);
    }

    private void inspect(King king, SideView side, boolean opponent) {
        inspected = king; inspectingOpponent = opponent;
        VBox content = new VBox(12); content.setAlignment(Pos.CENTER);
        ImageView picture = art(king, 425, 595);
        picture.fitHeightProperty().bind(heightProperty().subtract(150));
        Label description = label(king.title() + ", King of " + king.nation() + " · " + details(king, side));
        description.setWrapText(true); description.setMaxWidth(700);
        Button close = FxKit.pill("Close", "gold"); close.setOnAction(e -> closeInspector());
        content.getChildren().addAll(picture, description, close);
        inspector.getChildren().setAll(content); inspector.setVisible(true); inspector.toFront();
    }

    void closeInspector() { inspected = null; inspector.setVisible(false); inspector.getChildren().clear(); }
    boolean choosingVisible() { return choice.isVisible(); }
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
