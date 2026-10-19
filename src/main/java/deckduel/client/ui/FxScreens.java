package deckduel.client.ui;

import deckduel.client.ClientSettings;
import deckduel.client.ScoringSounds;
import deckduel.client.state.ClientModel;
import deckduel.client.state.ClientModelListener;
import deckduel.client.state.Screen;
import deckduel.core.card.Move;
import java.awt.Rectangle;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * Shows the JavaFX screens, the match table and the Atlas, in their own window at the Swing
 * frame's place and size, and hands back to the Swing menus when they close. The model stays
 * owned by the EDT: its events arrive here on the EDT and reach the JavaFX views as immutable
 * snapshots through {@link Platform#runLater}; actions from the views go back through
 * {@link SwingUtilities#invokeLater}.
 */
final class FxScreens {
    private final ClientModel model;
    private final JFrame frame;
    private final Runnable toggleMusic;
    private final BooleanSupplier musicOn;
    private final Consumer<Rectangle> close;
    private final AtomicBoolean reducedMotion = new AtomicBoolean();
    private Stage stage;
    private StackPane root;
    private Button music;
    private CardTableView table;
    private AtlasView atlas;
    // Set on the EDT; read by the JavaFX thread to drop a hand-over that a newer screen change replaced.
    private volatile Screen showing;

    /**
     * Starts JavaFX and builds both screens. Called on the EDT. {@code close} receives the window
     * bounds when the player closes the JavaFX window, on the EDT.
     */
    FxScreens(ClientModel model, ClientSettings settings, ScoringSounds sounds, JFrame frame, Runnable toggleMusic,
              BooleanSupplier musicOn, Consumer<Rectangle> close) {
        this.model = model;
        this.frame = frame;
        this.toggleMusic = toggleMusic;
        this.musicOn = musicOn;
        this.close = close;
        reducedMotion.set(model.reducedMotion());
        FxKit.start();
        boolean music = musicOn.getAsBoolean();
        Platform.runLater(() -> build(settings, sounds, music));
        model.addListener(new ClientModelListener() {
            @Override public void onScreenChanged(Screen screen) { switchTo(screen); }
            @Override public void onLobbyChanged() { reducedMotion.set(model.reducedMotion()); }
            @Override public void onMatchChanged() { sendMatch(); }
            @Override public void onActionRejected(String reason) { Platform.runLater(() -> table.notice(reason)); }
        });
    }

    private void build(ClientSettings settings, ScoringSounds sounds, boolean musicOn) {
        table = new CardTableView(new CardTableView.Actions() {
            @Override public void move(Move move) { SwingUtilities.invokeLater(() -> model.move(move)); }
            @Override public void rematch() { SwingUtilities.invokeLater(model::requestRematch); }
            @Override public void leave() { SwingUtilities.invokeLater(model::leaveMatch); }
        }, settings, sounds);
        atlas = new AtlasView(() -> SwingUtilities.invokeLater(model::leaveToLobby), reducedMotion::get);
        music = musicToggle(musicOn);
        root = new StackPane();
        root.setStyle("-fx-background-color: #2C2925;");
        stage = new Stage();
        stage.setTitle("Deck Duel");
        Scene scene = new Scene(root, 1280, 720, Color.web("#2C2925"));
        scene.getStylesheets().add(FxKit.STYLESHEET);
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> {
            event.consume();
            Rectangle bounds = bounds();
            SwingUtilities.invokeLater(() -> close.accept(bounds));
        });
    }

    /** The speaker in the top-right corner, matching the Swing frame's floating toggle. */
    private Button musicToggle(boolean on) {
        Button button = new Button();
        button.setId("menuMusicToggle");
        button.getStyleClass().add("sound-toggle");
        button.setFocusTraversable(false);
        button.setAccessibleText("Toggle menu music");
        button.setOnAction(event -> SwingUtilities.invokeLater(() -> {
            toggleMusic.run();
            boolean now = musicOn.getAsBoolean();
            Platform.runLater(() -> showMusic(now));
        }));
        StackPane.setAlignment(button, Pos.TOP_RIGHT);
        StackPane.setMargin(button, new javafx.geometry.Insets(12, 12, 0, 0));
        this.music = button;
        showMusic(on);
        return button;
    }

    private void showMusic(boolean on) {
        SVGPath speaker = new SVGPath();
        speaker.setContent(on ? "M2 7 H6 L11 3 V17 L6 13 H2 Z M14 6 Q17 10 14 14 M16.5 3.5 Q22 10 16.5 16.5"
                : "M2 7 H6 L11 3 V17 L6 13 H2 Z M15 5 L23 15");
        speaker.setFill(Color.web("#FFEEC8"));
        speaker.setStroke(Color.web("#FFEEC8"));
        speaker.setStrokeWidth(1.5);
        music.setGraphic(speaker);
        music.setTooltip(new javafx.scene.control.Tooltip("Menu music: " + (on ? "On" : "Off")));
    }

    /** The match table and whether the JavaFX window is up; for tests, on the JavaFX thread. */
    CardTableView table() { return table; }
    boolean stageShowing() { return stage != null && stage.isShowing(); }

    /** Whether a JavaFX screen is in front; called on the EDT. */
    boolean showing() { return showing != null; }

    /** A short notice on the match table, for server errors while it is showing; called on the EDT. */
    void notice(String text) { Platform.runLater(() -> table.notice(text)); }

    private void sendMatch() {
        CardTableView.State state = new CardTableView.State(model.match(), model.isReconnecting(), model.reducedMotion());
        Platform.runLater(() -> table.show(state));
    }

    /** Brings the JavaFX window forward for the table and Atlas, and the Swing frame back for anything else. */
    private void switchTo(Screen screen) {
        boolean fx = screen == Screen.TABLE || screen == Screen.ATLAS;
        if (screen == Screen.TABLE) sendMatch();
        if (fx) {
            Rectangle bounds = frame.getBounds();
            boolean musicNow = musicOn.getAsBoolean();
            Screen previous = showing;
            showing = screen;
            Platform.runLater(() -> {
                Parent view = screen == Screen.TABLE ? table : atlas;
                if (previous != null) leave(previous);
                root.getChildren().setAll(view, music);
                showMusic(musicNow);
                if (!stage.isShowing()) place(bounds);
                stage.show();
                stage.toFront();
                if (view == table) table.shown(); else atlas.shown();
                // The menu window goes only once the JavaFX one covers it, so nothing flashes between them.
                SwingUtilities.invokeLater(() -> { if (showing != null) frame.setVisible(false); });
            });
        } else if (showing != null) {
            Screen previous = showing;
            showing = null;
            Platform.runLater(() -> {
                leave(previous);
                Rectangle bounds = bounds();
                SwingUtilities.invokeLater(() -> {
                    frame.setBounds(bounds);
                    frame.setVisible(true);
                    frame.toFront();
                    Platform.runLater(() -> { if (showing == null) stage.hide(); });
                });
            });
        }
    }

    private void leave(Screen screen) {
        if (screen == Screen.TABLE) table.hidden(); else atlas.hidden();
    }

    private void place(Rectangle bounds) {
        stage.setX(bounds.x);
        stage.setY(bounds.y);
        stage.setWidth(bounds.width);
        stage.setHeight(bounds.height);
    }

    private Rectangle bounds() {
        return new Rectangle((int) stage.getX(), (int) stage.getY(), (int) stage.getWidth(), (int) stage.getHeight());
    }

    /** Closes the JavaFX window; the toolkit itself keeps running until {@link #quit}. */
    void dispose() { Platform.runLater(() -> { if (stage != null) stage.close(); }); }

    /** Ends the JavaFX toolkit, whose thread would otherwise keep the quitting client alive. */
    void quit() { Platform.runLater(Platform::exit); }
}
