package deckduel.client.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;

/**
 * The match table's pause menu, opened by its Menu button or Escape: Resume, Settings, How to
 * play, Surrender and Quit game. Settings and How to play open in place; Surrender and Quit ask
 * first. Used on the JavaFX Application Thread only.
 */
final class PauseMenu extends StackPane {
    /** Preferences the pause menu can switch. */
    enum Preference { GAME_SOUNDS, REDUCED_MOTION, MUSIC }

    /** What the menu's choices do; called on the JavaFX thread. */
    interface Choices {
        void resume();
        void toggle(Preference preference);
        void surrender();
        void quit();
    }

    private final Choices choices;
    private final VBox panel = new VBox(10);
    private final Button sounds = FxKit.pill("Game sounds", "gold-outline"), motion = FxKit.pill("Reduced motion", "gold-outline"),
            music = FxKit.pill("Music", "gold-outline");
    private boolean gameSounds, reducedMotion, musicOn, matchOver;
    private int step;
    private Runnable back;

    PauseMenu(Choices choices) {
        this.choices = choices;
        setId("pauseMenu");
        setStyle("-fx-background-color: rgba(10, 6, 2, 0.62);");
        panel.getStyleClass().add("leather");
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(28, 36, 28, 36));
        panel.setMaxSize(StackPane.USE_PREF_SIZE, StackPane.USE_PREF_SIZE);
        sounds.setOnAction(event -> choices.toggle(Preference.GAME_SOUNDS));
        motion.setOnAction(event -> choices.toggle(Preference.REDUCED_MOTION));
        music.setOnAction(event -> choices.toggle(Preference.MUSIC));
        getChildren().add(panel);
        setVisible(false);
    }

    /** Opens the main list; Surrender is offered only while the match is still being played. */
    void open(boolean matchOver) {
        this.matchOver = matchOver;
        showMain();
        setVisible(true);
    }

    /** Shows the current preference values on the Settings buttons. */
    void preferences(boolean gameSounds, boolean reducedMotion, boolean music) {
        this.gameSounds = gameSounds; this.reducedMotion = reducedMotion; this.musicOn = music;
        sounds.setText(onOff("Game sounds", gameSounds));
        motion.setText(onOff("Reduced motion", reducedMotion));
        this.music.setText(onOff("Music", music));
    }

    /** Escape: a sub-page returns to the main list, the main list resumes the match. */
    void back() {
        if (back != null) back.run(); else choices.resume();
    }

    private void showMain() {
        back = null;
        Button resume = wide(FxKit.pill("Resume", "gold"));
        resume.setOnAction(event -> choices.resume());
        Button settings = wide(FxKit.pill("Settings", "gold-outline"));
        settings.setOnAction(event -> showSettings());
        Button guide = wide(FxKit.pill("How to play", "gold-outline"));
        guide.setOnAction(event -> { step = 0; showGuide(); });
        Button surrender = wide(FxKit.pill("Surrender", "danger"));
        surrender.setOnAction(event -> confirm("Surrender", "Give up this match? It counts as a loss.", "Surrender", choices::surrender));
        surrender.setVisible(!matchOver); surrender.setManaged(!matchOver);
        Button quit = wide(FxKit.pill("Quit game", "cream-outline"));
        quit.setOnAction(event -> confirm("Quit game", matchOver ? "Close Deck Duel?"
                : "Close Deck Duel? This match counts as a loss.", "Quit", choices::quit));
        panel.getChildren().setAll(title("Paused"), resume, settings, guide, surrender, quit);
        resume.requestFocus();
    }

    private void showSettings() {
        back = this::showMain;
        preferences(gameSounds, reducedMotion, musicOn);
        panel.getChildren().setAll(title("Settings"), wide(sounds), wide(motion), wide(music), backButton());
    }

    private void showGuide() {
        back = this::showMain;
        GuideSteps.Step shown = GuideSteps.STEPS.get(step);
        ImageView clip = new ImageView();
        var resource = PauseMenu.class.getResource(shown.resource());
        if (resource != null) clip.setImage(new Image(resource.toExternalForm(), true));
        clip.setFitWidth(520); clip.setFitHeight(292); clip.setPreserveRatio(true);
        Label heading = new Label(shown.title());
        heading.setFont(FxKit.display(22)); heading.setTextFill(FxKit.AMBER);
        Label text = new Label(shown.text());
        text.setFont(FxKit.normal(16)); text.setTextFill(FxKit.CREAM);
        text.setWrapText(true); text.setTextAlignment(TextAlignment.CENTER); text.setMaxWidth(520);
        Label count = new Label((step + 1) + " / " + GuideSteps.STEPS.size());
        count.setFont(FxKit.bold(13)); count.setTextFill(FxKit.MUTED);
        Button previous = FxKit.pill("Previous", "cream-outline"), next = FxKit.pill("Next", "gold");
        previous.setDisable(step == 0); next.setDisable(step == GuideSteps.STEPS.size() - 1);
        previous.setOnAction(event -> { step--; showGuide(); });
        next.setOnAction(event -> { step++; showGuide(); });
        HBox nav = new HBox(12, previous, count, next);
        nav.setAlignment(Pos.CENTER);
        panel.getChildren().setAll(heading, clip, text, nav, backButton());
    }

    private void confirm(String title, String message, String yes, Runnable action) {
        back = this::showMain;
        Label text = new Label(message);
        text.setFont(FxKit.normal(15)); text.setTextFill(FxKit.CREAM);
        Button no = FxKit.pill("No", "cream-outline"), confirm = FxKit.pill(yes, "danger");
        no.setOnAction(event -> showMain());
        confirm.setOnAction(event -> action.run());
        HBox buttons = new HBox(12, no, confirm);
        buttons.setAlignment(Pos.CENTER);
        panel.getChildren().setAll(title(title), text, buttons);
    }

    private Button backButton() {
        Button button = FxKit.pill("Back", "cream-outline");
        button.setOnAction(event -> showMain());
        return button;
    }

    private static Label title(String text) {
        Label label = new Label(text);
        label.setFont(FxKit.display(28));
        label.setTextFill(FxKit.AMBER);
        VBox.setMargin(label, new Insets(0, 0, 6, 0));
        return label;
    }

    private static Button wide(Button button) {
        button.setPrefWidth(280);
        button.setFont(FxKit.display(16));
        return button;
    }

    private static String onOff(String name, boolean on) { return (name + ": " + (on ? "On" : "Off")).toUpperCase(java.util.Locale.ROOT); }
}
