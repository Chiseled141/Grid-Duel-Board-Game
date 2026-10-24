package deckduel.client.ui;

import java.net.URL;
import javafx.animation.AnimationTimer;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

/**
 * The loading screen that covers the match table while it gets ready: the castle hall picture,
 * "Loading" and a bar that fills, then it closes and the opening deal begins.
 * JavaFX Application Thread only.
 */
final class LoadingScreen extends StackPane {
    /** How long the loading screen shows. */
    static final int HOLD_MILLIS = 1600;
    private static final String ART = "/assets/loading-castle-hall-animation-ready-v2.png";

    private final Image art;
    private final ImageView picture = new ImageView();
    private final ProgressBar bar = new ProgressBar(0);
    private final AnimationTimer frames = new AnimationTimer() {
        @Override public void handle(long now) { frame(now); }
    };
    private long start;
    private Runnable done;

    /** Starts loading the picture in the background, so it is ready by the first match. */
    LoadingScreen() {
        setId("loadingScreen");
        setStyle("-fx-background-color: #1A1512;");
        URL url = LoadingScreen.class.getResource(ART);
        art = url == null ? null : new Image(url.toExternalForm(), true);
        picture.setImage(art);
        Label label = new Label("Loading");
        label.setFont(FxKit.display(24));
        label.setTextFill(FxKit.CREAM);
        bar.setPrefWidth(320);
        bar.setStyle("-fx-accent: #C9A15E; -fx-control-inner-background: #2A211A;"
                + " -fx-text-box-border: #4A3A2A; -fx-shadow-highlight-color: transparent;");
        VBox footer = new VBox(12, label, bar);
        footer.setAlignment(Pos.CENTER);
        footer.setMaxHeight(USE_PREF_SIZE);
        StackPane.setAlignment(footer, Pos.BOTTOM_CENTER);
        StackPane.setMargin(footer, new javafx.geometry.Insets(0, 0, 60, 0));
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);
        getChildren().addAll(picture, footer);
        setAccessibleText("Loading");
    }

    /** Shows the loading screen for {@link #HOLD_MILLIS}, then runs {@code done}. */
    void play(Runnable done) {
        this.done = done;
        bar.setProgress(0);
        start = System.nanoTime();
        frames.start();
    }

    private void frame(long now) {
        double ms = (now - start) / 1e6;
        bar.setProgress(Math.min(1, ms / HOLD_MILLIS));
        if (ms < HOLD_MILLIS) return;
        frames.stop();
        if (done != null) done.run();
        done = null;
    }

    @Override protected void layoutChildren() {
        // The picture covers the window; the clip trims whatever overflows.
        if (art != null && art.getWidth() > 0) {
            double scale = Math.max(getWidth() / art.getWidth(), getHeight() / art.getHeight());
            picture.setFitWidth(art.getWidth() * scale);
            picture.setFitHeight(art.getHeight() * scale);
        }
        super.layoutChildren();
    }
}
