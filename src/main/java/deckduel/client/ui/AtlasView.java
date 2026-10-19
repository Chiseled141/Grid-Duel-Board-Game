package deckduel.client.ui;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * The Atlas in JavaFX: an interactive map of the seven factions with their arms and banners, and
 * a floating chronicle describing the selected faction. It works offline and never sends anything
 * to the server. Used on the JavaFX Application Thread only.
 */
final class AtlasView extends Pane {
    private static final Logger LOG = Logger.getLogger(AtlasView.class.getName());
    private static final Color INK = Color.web("#392E24"), SECTION = Color.web("#8C6E45");

    private final Runnable back;
    private final AtlasMapView map;
    private final HBox header;
    private final Label hint = new Label("Click a country to explore   ·   Drag to pan   ·   Scroll to zoom   ·   Arrow keys browse   ·   Borders reconstructed");
    private final ScrollPane chronicle = new ScrollPane();
    private final Label zoomLabel = new Label("100%");
    private final StackPane bannerArt = new StackPane();
    private final ImageView flag = new ImageView(), coat = new ImageView();
    private final Label name = new Label(), status = new Label(), seat = new Label(), description = new Label();
    private final VBox banner;
    private Map<String, Image> flags = Map.of(), coats = Map.of();
    private AtlasRegion selected = AtlasRegion.ALL.get(0);

    private record Artwork(Map<String, Image> coats, Map<String, Image> flags, AtlasMapData map, Image terrain) { }

    /** Builds the Atlas; {@code back} returns to the menu. The map and arms load in the background. */
    AtlasView(Runnable back, BooleanSupplier reducedMotion) {
        this.back = back;
        setId("atlasBrowser");
        setStyle("-fx-background-color: #2B221A;");
        map = new AtlasMapView(reducedMotion, region -> select(region, true), this::showZoom);
        header = header();
        hint.getStyleClass().add("atlas-hint");
        hint.setFont(FxKit.normal(11));
        banner = banner();
        getChildren().addAll(map, header, inspector(), hint);
        addEventFilter(KeyEvent.KEY_PRESSED, this::keyPressed);
        select(selected, false);
        load();
    }

    /** Decodes the boundaries, terrain and heraldry off the JavaFX thread, then shows them. */
    private void load() {
        Thread loader = new Thread(() -> {
            try {
                Map<String, Heraldry> arms = Heraldry.load();
                Map<String, Image> coats = new HashMap<>(), banners = new HashMap<>();
                arms.forEach((key, value) -> {
                    coats.put(key, SwingFXUtils.toFXImage(value.coat(), null));
                    banners.put(key, SwingFXUtils.toFXImage(value.flag(), null));
                });
                AtlasMapData data = AtlasMapData.load();
                Image terrain = new Image(data.terrain().toExternalForm());
                if (terrain.isError()) throw new java.io.IOException("Unreadable Atlas terrain", terrain.getException());
                Artwork artwork = new Artwork(coats, banners, data, terrain);
                Platform.runLater(() -> show(artwork));
            } catch (java.io.IOException exception) {
                // Keep navigation and country text available if a bundled image is missing.
                LOG.log(Level.WARNING, "Could not load Atlas artwork", exception);
            }
        }, "atlas-artwork");
        loader.setDaemon(true);
        loader.start();
    }

    private void show(Artwork artwork) {
        flags = artwork.flags();
        coats = artwork.coats();
        map.setData(artwork.map(), artwork.terrain());
        map.setCoats(coats);
        select(selected, false);
    }

    /** Shows a faction in the chronicle and on the map. */
    void select(AtlasRegion region, boolean fly) {
        selected = region;
        map.select(region, fly);
        String key = Heraldry.key(region.name());
        flag.setImage(flags.get(key));
        coat.setImage(coats.get(key));
        name.setText(region.name());
        status.setText(region.status());
        banner.setAccessibleText(region.name() + ", " + region.status());
        seat.setText(region.seat());
        description.setText(region.description());
        chronicle.setVvalue(0);
    }

    AtlasMapView map() { return map; }
    AtlasRegion selected() { return selected; }
    String bannerDescription() { return banner.getAccessibleText(); }

    /** Data already loaded elsewhere, so tests need not wait for the background loader. */
    void showForTest(AtlasMapData data) { map.setData(data, null); select(selected, false); }

    /** Called when the Atlas becomes the visible screen. */
    void shown() { map.requestFocus(); }

    /** Called when another screen replaces the Atlas. */
    void hidden() { map.stop(); }

    private void showZoom() { zoomLabel.setText(Math.round(map.zoom() * 100) + "%"); }

    private void cycle(int step) {
        int index = AtlasRegion.ALL.indexOf(selected);
        select(AtlasRegion.ALL.get(Math.floorMod(index + step, AtlasRegion.ALL.size())), true);
    }

    void keyPressed(KeyEvent event) {
        KeyCode code = event.getCode();
        switch (code) {
            case ESCAPE, BACK_SPACE -> back.run();
            case LEFT -> cycle(-1);
            case RIGHT -> cycle(1);
            case PLUS, EQUALS, ADD -> map.zoomBy(1.35);
            case MINUS, SUBTRACT -> map.zoomBy(1 / 1.35);
            case DIGIT0, NUMPAD0 -> map.overview();
            default -> { return; }
        }
        event.consume();
    }

    // ---------------------------------------------------------------- layout

    @Override protected void layoutChildren() {
        double w = getWidth(), h = getHeight(), panelWidth = Math.min(350, Math.max(280, w / 4));
        map.setPanelWidth(panelWidth);
        map.resizeRelocate(0, 0, w, h);
        header.resizeRelocate(20, 14, Math.max(200, w - 40), 66);
        chronicle.resizeRelocate(w - panelWidth - 24, 108, panelWidth, Math.max(180, h - 164));
        hint.resizeRelocate(22, h - 38, Math.min(Math.max(200, w - 44), hint.prefWidth(-1)), 28);
    }

    private HBox header() {
        Button backButton = FxKit.pill("Back", "cream-outline");
        backButton.setId("atlasBack");
        backButton.setOnAction(event -> back.run());
        Label title = new Label("Atlas");
        title.setFont(FxKit.decorative(24));
        title.setTextFill(Color.web("#F1E3BF"));
        Label subtitle = new Label("The Carpathian Basin · 1444 · Seven factions");
        subtitle.setFont(FxKit.normal(12));
        subtitle.setTextFill(Color.web("#C9B892"));
        VBox titles = new VBox(title, subtitle);
        titles.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(titles, Priority.ALWAYS);
        zoomLabel.setTextFill(Color.web("#F1E3BF"));
        zoomLabel.setFont(FxKit.normal(12));
        HBox controls = new HBox(6, mapButton("−", () -> map.zoomBy(1 / 1.35)), zoomLabel,
                mapButton("+", () -> map.zoomBy(1.35)), mapButton("Whole map", map::overview));
        controls.setAlignment(Pos.CENTER_RIGHT);
        HBox bar = new HBox(16, backButton, titles, controls);
        bar.getStyleClass().add("atlas-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private static Button mapButton(String text, Runnable action) {
        Button button = FxKit.miniPill(text, "cream-outline");
        button.setOnAction(event -> action.run());
        return button;
    }

    private ScrollPane inspector() {
        VBox side = new VBox();
        side.setId("atlasInspector");
        side.setPadding(new Insets(20, 22, 18, 22));
        description.setWrapText(true);
        description.setFont(FxKit.normal(13));
        description.setTextFill(INK);
        seat.setId("atlasSeat");
        seat.setFont(FxKit.normal(13));
        seat.setTextFill(INK);
        seat.setWrapText(true);
        side.getChildren().addAll(section("COUNTRY CHRONICLE"), gap(12), banner, gap(8), section("PRINCIPAL CITIES"), gap(6), seat,
                gap(16), section("HISTORICAL OVERVIEW"), gap(6), description);
        chronicle.setContent(side);
        chronicle.setId("atlasChronicleScroll");
        chronicle.getStyleClass().add("atlas-inspector");
        chronicle.setFitToWidth(true);
        chronicle.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return chronicle;
    }

    /** The complete flag and separate arms above the faction name and standing. */
    private VBox banner() {
        Region plate = new Region();
        plate.setStyle("-fx-background-color: #D8C49B;");
        flag.setPreserveRatio(true);
        flag.setSmooth(true);
        flag.setFitHeight(92);
        flag.setEffect(new javafx.scene.effect.DropShadow(4, 0, 1, Color.rgb(60, 40, 20, .5)));
        coat.setPreserveRatio(true);
        coat.setSmooth(true);
        coat.setFitWidth(76);
        coat.setFitHeight(98);
        StackPane.setAlignment(flag, Pos.CENTER_LEFT);
        StackPane.setMargin(flag, new Insets(0, 0, 0, 12));
        StackPane.setAlignment(coat, Pos.CENTER_RIGHT);
        StackPane.setMargin(coat, new Insets(0, 14, 0, 0));
        bannerArt.getChildren().setAll(plate, flag, coat);
        bannerArt.setMinWidth(0);
        bannerArt.setMinHeight(114);
        bannerArt.setPrefHeight(114);
        // Fit the entire flag: cropping would hide heraldic charges at its edges.
        bannerArt.widthProperty().addListener((property, was, width) -> flag.setFitWidth(Math.max(40, width.doubleValue() - 118)));
        name.setFont(FxKit.decorative(29));
        name.setTextFill(INK);
        status.setFont(FxKit.bold(10.5));
        status.setTextFill(Color.web("#806440"));
        status.setWrapText(true);
        Region rule = new Region();
        rule.setStyle("-fx-background-color: #BAA279;");
        rule.setMinHeight(1);
        rule.setMaxHeight(1);
        VBox box = new VBox(4, bannerArt, name, status, rule);
        box.setId("atlasBanner");
        return box;
    }

    private static Label section(String text) {
        Label title = new Label(text);
        title.getStyleClass().add("atlas-section");
        title.setFont(FxKit.bold(10.5));
        title.setTextFill(SECTION);
        return title;
    }

    private static Region gap(double height) {
        Region gap = new Region();
        gap.setMinHeight(height);
        return gap;
    }
}
