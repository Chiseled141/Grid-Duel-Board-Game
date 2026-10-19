package deckduel.client.ui;

import deckduel.core.card.Effect;
import deckduel.core.card.CardInstance;
import deckduel.core.card.LandmarkCard;
import deckduel.core.card.Scoring;
import deckduel.core.card.SideView;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Read-only inspector for public Landmarks and the recipient's Spy-revealed cards. */
final class CardDetails extends StackPane {
    private final ImageView art = new ImageView();
    private final Label owner = label(11), name = label(25), status = label(12);
    private final Label buff = label(17), contribution = label(14), duration = label(12), balance = label(11);
    private final Runnable closed;

    /** Builds a modal inspector; closing returns focus to the table. */
    CardDetails(Runnable closed) {
        this.closed = closed;
        setId("cardDetails");
        setAccessibleRole(AccessibleRole.PARENT);
        setStyle("-fx-background-color: rgba(10, 6, 2, 0.72);");
        setVisible(false);
        name.setId("cardDetailName");
        status.setId("cardDetailStatus");
        buff.setId("cardDetailBuff");
        contribution.setId("cardDetailContribution");
        owner.setTextFill(FxKit.AMBER);
        name.setFont(FxKit.bold(25));
        duration.setTextFill(FxKit.MUTED);
        balance.setTextFill(FxKit.MUTED);
        Button close = FxKit.pill("Close", "gold-outline");
        close.setId("closeCardDetails");
        close.setFocusTraversable(true);
        close.setOnAction(event -> close());
        VBox text = new VBox(12, owner, name, status, buff, contribution, duration, balance, close);
        text.setPrefWidth(290);
        text.setMaxWidth(290);
        art.setPreserveRatio(true);
        art.setFitWidth(170);
        art.setFitHeight(255);
        HBox dialog = new HBox(28, art, text);
        dialog.setAlignment(Pos.CENTER);
        dialog.getStyleClass().add("leather");
        dialog.setPadding(new Insets(28));
        dialog.setMaxSize(580, USE_PREF_SIZE);
        getChildren().add(dialog);
        setOnMouseClicked(event -> {
            if (event.getTarget() == this) close();
            event.consume();
        });
        addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) close();
            if (event.getCode() != KeyCode.TAB) event.consume();
        });
    }

    /** Opens or refreshes the card, exact buff text, current public contribution and suppression. */
    void show(LandmarkCard landmark, SideView side, boolean own) {
        owner.setText(own ? "YOUR LANDMARK" : "OPPONENT'S LANDMARK");
        name.setText(landmark.def().name());
        status.setText(landmark.active() ? "ACTIVE" : "SABOTAGED · DISABLED THIS ROUND");
        status.setTextFill(landmark.active() ? FxKit.AMBER : FxKit.CORAL);
        buff.setText(landmark.def().text());
        Effect effect = landmark.def().effect();
        boolean scoring = effect instanceof Effect.SharedTagBonus || effect instanceof Effect.WeakUnitBonus
                || effect instanceof Effect.EmptySpaceBonus;
        if (scoring) {
            List<LandmarkCard> without = side.landmarks().stream()
                    .filter(card -> card.card().id() != landmark.card().id()).toList();
            long points = side.score().total() - Scoring.score(side.row(), without).total();
            contribution.setText("Current row bonus: +" + points + " points");
        } else if (effect instanceof Effect.FirstSpellDraw) {
            contribution.setText("Triggers on the owner's first Spell each round. Does not use a manual draw.");
        } else {
            contribution.setText("Private to the owner. Does not draw or reorder the deck.");
        }
        duration.setText(landmark.active() ? "Persists for all three rounds. Sabotage can disable it until round end."
                : "This buff is disabled until the round ends. The Landmark stays in play.");
        balance.setText(scoring ? "Scoring values are provisional." : "");
        balance.setVisible(scoring);
        balance.setManaged(scoring);
        art.setImage(FxKit.face(landmark.def(), List.of(), 170, 255, false, 2));
        setAccessibleText(owner.getText() + ". " + name.getText() + ". " + status.getText() + ". "
                + buff.getText() + " " + contribution.getText() + " " + duration.getText());
        setVisible(true);
    }

    /** Dismisses the inspector without sending a move. */
    void close() {
        setVisible(false);
        art.setImage(null);
        setAccessibleText("");
        closed.run();
    }

    /** Enlarges a card already present in the recipient's private Spy knowledge. */
    void showRevealed(CardInstance card) {
        owner.setText("SPY REVEAL");
        name.setText(card.def().name());
        status.setText("IN THE OPPONENT'S HAND");
        status.setTextFill(FxKit.AMBER);
        buff.setText(card.def().text());
        contribution.setText("Your Spy revealed this card. It remains in the opponent's hand.");
        duration.setText("Visible until it leaves that hand or the round ends. Spy does not steal or discard it.");
        balance.setVisible(false);
        balance.setManaged(false);
        art.setImage(FxKit.face(card.def(), List.of(), 170, 255, false, 2));
        setAccessibleText(owner.getText() + ". " + name.getText() + ". " + buff.getText() + " " + contribution.getText());
        setVisible(true);
    }

    private static Label label(double size) {
        Label label = new Label();
        label.setFont(FxKit.normal(size));
        label.setTextFill(FxKit.CREAM);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }
}
