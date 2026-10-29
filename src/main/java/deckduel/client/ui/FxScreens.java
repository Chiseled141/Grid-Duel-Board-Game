package deckduel.client.ui;

import deckduel.cards.CatalogLoader;
import deckduel.client.ScoringSounds;
import deckduel.client.state.ClientModel;
import deckduel.client.state.ClientModelListener;
import deckduel.client.state.Screen;
import deckduel.core.card.Move;
import java.awt.Rectangle;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * Shows the JavaFX match table in its own window at the Swing frame's place and size,
 * and hands back to the Swing menus when it closes. The model stays
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
    private Stage stage;
    private StackPane root;
    private Button music;
    private CardTableView table;
    private LoadingScreen loading;
    // Set on the EDT; read by the JavaFX thread to drop a hand-over that a newer screen change replaced.
    private volatile Screen showing;

    /**
     * Starts JavaFX and builds the match table. Called on the EDT. {@code close} receives the window
     * bounds when the player closes the JavaFX window, on the EDT.
     */
    FxScreens(ClientModel model, ScoringSounds sounds, JFrame frame, Runnable toggleMusic,
              BooleanSupplier musicOn, Consumer<Rectangle> close) {
        this.model = model;
        this.frame = frame;
        this.toggleMusic = toggleMusic;
        this.musicOn = musicOn;
        this.close = close;
        FxKit.start();
        PaintedCardArt.preload(CatalogLoader.loadBundled().cards());
        boolean music = musicOn.getAsBoolean();
        Platform.runLater(() -> build(sounds, music));
        model.addListener(new ClientModelListener() {
            @Override public void onScreenChanged(Screen screen) { switchTo(screen); }
            @Override public void onMatchChanged() { sendMatch(); }
            @Override public void onActionRejected(String reason) { Platform.runLater(() -> table.notice(reason)); }
        });
    }

    private void build(ScoringSounds sounds, boolean musicOn) {
        VfxSprites.preload();
        table = new CardTableView(new CardTableView.Actions() {
            @Override public void move(Move move) { SwingUtilities.invokeLater(() -> model.move(move)); }
            @Override public void rematch() { SwingUtilities.invokeLater(model::requestRematch); }
            @Override public void leave() { SwingUtilities.invokeLater(model::leaveMatch); }
        }, sounds);
        loading = new LoadingScreen();
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

    /** The brass music tile in the top-right corner, matching the Swing frame's toggle. */
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
        StackPane.setMargin(button, new javafx.geometry.Insets(16, 16, 0, 0));
        this.music = button;
        showMusic(on);
        return button;
    }

    private void showMusic(boolean on) {
        SVGPath notes = new SVGPath();
        notes.setContent("M7 17 V5 L19 2 V14 M7 8 L19 5 M7 17 C7 21 0 21 0 18 C0 15 7 14 7 17 Z "
                + "M19 14 C19 18 12 18 12 15 C12 12 19 11 19 14 Z" + (on ? "" : " M0 1 L22 23"));
        notes.setFill(Color.TRANSPARENT);
        notes.setStroke(Color.web(on ? "#D9B56A" : "#B4A890"));
        notes.setStrokeWidth(1.8);
        music.setGraphic(notes);
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

    /** Brings the JavaFX window forward for the table, and the Swing frame back for anything else. */
    private void switchTo(Screen screen) {
        boolean fx = screen == Screen.TABLE;
        if (screen == Screen.TABLE) sendMatch();
        if (fx) {
            Rectangle bounds = frame.getBounds();
            boolean musicNow = musicOn.getAsBoolean();
            Screen previous = showing;
            showing = screen;
            Platform.runLater(() -> {
                if (previous != null) table.hidden();
                // Coming from the menus, the loading screen covers the table while it gets ready;
                // the opening deal starts once it closes.
                boolean entering = previous == null;
                root.getChildren().setAll(table, music);
                if (entering) root.getChildren().add(1, loading);
                showMusic(musicNow);
                if (!stage.isShowing()) place(bounds);
                stage.show();
                stage.toFront();
                if (entering) {
                    table.shown(LoadingScreen.HOLD_MILLIS);
                    loading.play(() -> root.getChildren().remove(loading));
                } else table.shown();
                // The menu window goes only once the JavaFX one covers it, so nothing flashes between them.
                SwingUtilities.invokeLater(() -> { if (showing != null) frame.setVisible(false); });
            });
        } else if (showing != null) {
            showing = null;
            Platform.runLater(() -> {
                table.hidden();
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
