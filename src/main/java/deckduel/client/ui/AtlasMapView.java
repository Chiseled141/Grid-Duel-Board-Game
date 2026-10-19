package deckduel.client.ui;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javafx.animation.AnimationTimer;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.shape.StrokeType;
import javafx.scene.text.Text;
import javafx.scene.transform.Affine;

/**
 * The full-screen Atlas map in JavaFX: the terrain plate, a dimming mask outside the seven
 * factions and their borders sit in one group under the camera's transform, so panning and
 * zooming only change that transform; labels with their arms stay crisp in screen space.
 * Hit testing uses the same exported boundaries. Used on the JavaFX Application Thread only.
 */
final class AtlasMapView extends Region {
    static final double MAX_ZOOM = 6;
    private static final Color SELECTED = Color.web("#F4CB7B");

    private final BooleanSupplier reducedMotion;
    private final Consumer<AtlasRegion> onSelect;
    private final Runnable cameraMoved;
    private final Group world = new Group(), labels = new Group();
    private final Affine camera = new Affine();
    private final Map<AtlasRegion, SVGPath> borders = new LinkedHashMap<>();
    private final Map<AtlasRegion, Group> labelNodes = new LinkedHashMap<>();
    private final Text loading = new Text("Loading the Atlas…");
    private final AnimationTimer motion = new AnimationTimer() {
        @Override public void handle(long now) { stepCamera(now); }
    };
    private AtlasMapData data;
    private Map<String, Image> coats = Map.of();
    private AtlasRegion selected, hovered;
    private Point2D press, previous;
    private boolean dragged, moving;
    private long lastStep;
    private double panelWidth = 350;
    private double zoom = 1, centreX = AtlasMapData.SIZE / 2.0, centreY = centreX;
    private double targetZoom = 1, targetX = centreX, targetY = centreY;

    AtlasMapView(BooleanSupplier reducedMotion, Consumer<AtlasRegion> onSelect, Runnable cameraMoved) {
        this.reducedMotion = reducedMotion;
        this.onSelect = onSelect;
        this.cameraMoved = cameraMoved;
        setId("atlasMap");
        setStyle("-fx-background-color: #293329;");
        setFocusTraversable(true);
        setAccessibleText("Atlas map. Click a country; arrow keys browse all seven countries.");
        world.getTransforms().add(camera);
        loading.setFont(FxKit.normal(15));
        loading.setFill(FxKit.CREAM);
        loading.setX(30);
        loading.setY(125);
        getChildren().addAll(world, labels, loading);
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);
        installMouse();
    }

    /** Shows the parsed boundaries over {@code terrain}. */
    void setData(AtlasMapData next, Image terrain) {
        data = next;
        world.getChildren().clear();
        borders.clear();
        Rectangle2D plate = new Rectangle2D(AtlasMapData.TERRAIN.getX(), AtlasMapData.TERRAIN.getY(),
                AtlasMapData.TERRAIN.getWidth(), AtlasMapData.TERRAIN.getHeight());
        if (terrain != null) {
            ImageView view = new ImageView(terrain);
            view.setSmooth(true);
            view.setX(plate.getMinX());
            view.setY(plate.getMinY());
            view.setFitWidth(plate.getWidth());
            view.setFitHeight(plate.getHeight());
            world.getChildren().add(view);
        }
        // One even-odd path, the plate's rectangle followed by every faction, darkens only the context around them.
        StringBuilder outside = new StringBuilder("M " + plate.getMinX() + " " + plate.getMinY() + " L " + plate.getMaxX() + " "
                + plate.getMinY() + " L " + plate.getMaxX() + " " + plate.getMaxY() + " L " + plate.getMinX() + " " + plate.getMaxY() + " Z");
        for (AtlasRegion region : AtlasRegion.ALL) outside.append(' ').append(data.outline(region));
        SVGPath mask = new SVGPath();
        mask.setContent(outside.toString());
        mask.setFillRule(FillRule.EVEN_ODD);
        mask.setFill(Color.rgb(18, 29, 22, .61));
        world.getChildren().add(mask);
        for (AtlasRegion region : AtlasRegion.ALL) {
            SVGPath border = new SVGPath();
            border.setContent(data.outline(region));
            border.setFillRule(FillRule.EVEN_ODD);
            border.setStrokeLineJoin(StrokeLineJoin.ROUND);
            border.setStrokeType(StrokeType.CENTERED);
            borders.put(region, border);
            world.getChildren().add(border);
        }
        loading.setVisible(false);
        buildLabels();
        refresh();
    }

    void setPanelWidth(double width) { panelWidth = width; }

    /** Shows each faction's arms above its name. */
    void setCoats(Map<String, Image> next) { coats = Map.copyOf(next); buildLabels(); refresh(); }

    /** Keeps overview and fly-to targets clear of the floating detail panel. */
    private Rectangle2D viewport() {
        return new Rectangle2D(22, 94, Math.max(120, getWidth() - panelWidth - 68), Math.max(120, getHeight() - 142));
    }

    void select(AtlasRegion region, boolean fly) {
        selected = region;
        if (fly && data != null && region != null) {
            java.awt.geom.Rectangle2D box = data.shape(region).getBounds2D();
            Rectangle2D view = viewport();
            double want = Math.min(view.getWidth() / (box.getWidth() * 1.4 * fitScale()),
                    view.getHeight() / (box.getHeight() * 1.4 * fitScale()));
            moveTo(clampZoom(Math.max(1.15, want)), box.getCenterX(), box.getCenterY());
        }
        buildLabels();
        refresh();
    }

    void overview() { moveTo(1, AtlasMapData.SIZE / 2.0, AtlasMapData.SIZE / 2.0); }
    void zoomBy(double factor) { zoomAround(factor, centre(viewport()).getX(), centre(viewport()).getY()); }
    double zoom() { return targetZoom; }
    AtlasRegion selected() { return selected; }
    AtlasRegion hovered() { return hovered; }

    // ---------------------------------------------------------------- camera

    private static Point2D centre(Rectangle2D r) { return new Point2D(r.getMinX() + r.getWidth() / 2, r.getMinY() + r.getHeight() / 2); }

    private double fitScale() {
        Rectangle2D view = viewport();
        return Math.max(1e-3, Math.min(view.getWidth() / AtlasMapData.SIZE, view.getHeight() / AtlasMapData.SIZE));
    }

    private static double clampZoom(double value) { return Math.max(1, Math.min(MAX_ZOOM, value)); }

    /** Map units to view pixels for the current camera. */
    Point2D toScreen(Point2D map) {
        double scale = fitScale() * zoom;
        Point2D middle = centre(viewport());
        return new Point2D(middle.getX() + (map.getX() - centreX) * scale, middle.getY() + (map.getY() - centreY) * scale);
    }

    private Point2D toMap(Point2D screen) {
        double scale = fitScale() * zoom;
        Point2D middle = centre(viewport());
        return new Point2D(centreX + (screen.getX() - middle.getX()) / scale, centreY + (screen.getY() - middle.getY()) / scale);
    }

    private void moveTo(double nextZoom, double x, double y) {
        targetZoom = nextZoom;
        targetX = x;
        targetY = y;
        clampTarget();
        cameraMoved.run();
        if (reducedMotion.getAsBoolean()) {
            zoom = targetZoom; centreX = targetX; centreY = targetY;
            refresh();
        } else if (!moving) {
            moving = true;
            lastStep = System.nanoTime();
            motion.start();
        }
    }

    /** Keeps the map sheet covering the view once zoomed in, so panning never loses it. */
    private void clampTarget() {
        double scale = fitScale() * targetZoom;
        Rectangle2D view = viewport();
        double halfW = view.getWidth() / (2 * scale), halfH = view.getHeight() / (2 * scale), size = AtlasMapData.SIZE;
        targetX = halfW >= size / 2 ? size / 2 : Math.max(halfW, Math.min(size - halfW, targetX));
        targetY = halfH >= size / 2 ? size / 2 : Math.max(halfH, Math.min(size - halfH, targetY));
    }

    private void zoomAround(double factor, double px, double py) {
        Point2D anchor = toMap(new Point2D(px, py));
        double next = clampZoom(targetZoom * factor), scale = fitScale() * next;
        Point2D middle = centre(viewport());
        moveTo(next, anchor.getX() - (px - middle.getX()) / scale, anchor.getY() - (py - middle.getY()) / scale);
    }

    private void stepCamera(long now) {
        // Time-based easing, so the glide takes as long at any frame rate; zoom eases in log space
        // so zooming in and out feel equally smooth.
        double ms = Math.min(50, (now - lastStep) / 1e6), k = 1 - Math.exp(-ms / 67);
        lastStep = now;
        zoom = Math.exp(Math.log(zoom) + (Math.log(targetZoom) - Math.log(zoom)) * k);
        centreX += (targetX - centreX) * k;
        centreY += (targetY - centreY) * k;
        if (Math.abs(zoom - targetZoom) < 1e-3 && Math.hypot(targetX - centreX, targetY - centreY) < .05) {
            zoom = targetZoom; centreX = targetX; centreY = targetY;
            motion.stop();
            moving = false;
        }
        refresh();
    }

    @Override protected void layoutChildren() { refresh(); }

    /** Applies the camera to the map group, keeps border widths constant on screen and places the labels. */
    private void refresh() {
        double scale = fitScale() * zoom;
        Point2D middle = centre(viewport());
        camera.setToTransform(scale, 0, middle.getX() - centreX * scale, 0, scale, middle.getY() - centreY * scale);
        for (Map.Entry<AtlasRegion, SVGPath> entry : borders.entrySet()) {
            boolean chosen = entry.getKey().equals(selected), hover = entry.getKey().equals(hovered);
            SVGPath border = entry.getValue();
            border.setFill(chosen || hover ? Color.rgb(255, 220, 138, chosen ? .13 : .08) : Color.TRANSPARENT);
            border.setStroke(chosen ? SELECTED : Color.rgb(234, 225, 200, hover ? 1 : .59));
            border.setStrokeWidth((chosen ? 2.4 : hover ? 1.8 : .8) / scale);
            // The chosen border is drawn last, so neighbours never paint over its highlight.
            if (chosen) border.toFront();
        }
        if (data == null) return;
        for (Map.Entry<AtlasRegion, Group> entry : labelNodes.entrySet()) {
            Point2D at = toScreen(new Point2D(data.label(entry.getKey()).getX(), data.label(entry.getKey()).getY()));
            entry.getValue().setTranslateX(Math.round(at.getX()));
            entry.getValue().setTranslateY(Math.round(at.getY()));
        }
    }

    /** Each faction's arms above its engraved, outlined name; the chosen one is larger. */
    private void buildLabels() {
        labels.getChildren().clear();
        labelNodes.clear();
        if (data == null) return;
        for (AtlasRegion region : AtlasRegion.ALL) {
            boolean chosen = region.equals(selected);
            Text name = new Text(region.name().toUpperCase(Locale.ROOT));
            name.setFont(FxKit.decorative(chosen ? 18 : 14));
            name.setFill(chosen ? Color.web("#FFE3A3") : Color.web("#FFF0CC"));
            name.setStroke(Color.rgb(30, 30, 20, .82));
            name.setStrokeWidth(2);
            name.setStrokeType(StrokeType.OUTSIDE);
            name.setX(-name.getLayoutBounds().getWidth() / 2);
            Group label = new Group(name);
            Image coat = coats.get(Heraldry.key(region.name()));
            if (coat != null) {
                double h = chosen ? 42 : 32;
                ImageView arms = new ImageView(coat);
                arms.setSmooth(true);
                arms.setFitHeight(h);
                arms.setPreserveRatio(true);
                arms.setX(-coat.getWidth() / coat.getHeight() * h / 2);
                arms.setY(-h - 8 - name.getLayoutBounds().getHeight() * .7);
                label.getChildren().add(0, arms);
            }
            label.setMouseTransparent(true);
            labelNodes.put(region, label);
            labels.getChildren().add(label);
        }
    }

    // ---------------------------------------------------------------- input

    private void installMouse() {
        setOnMouseMoved(event -> hover(new Point2D(event.getX(), event.getY())));
        setOnMouseExited(event -> { hovered = null; refresh(); });
        setOnMousePressed(event -> {
            requestFocus();
            press = previous = new Point2D(event.getX(), event.getY());
            dragged = false;
        });
        setOnMouseDragged(this::drag);
        setOnMouseReleased(event -> {
            if (press != null && !dragged && event.getButton() == MouseButton.PRIMARY) click(new Point2D(event.getX(), event.getY()), event.getClickCount());
            press = previous = null;
            hover(new Point2D(event.getX(), event.getY()));
        });
        setOnScroll(this::scroll);
    }

    private void drag(MouseEvent event) {
        if (previous == null) return;
        Point2D now = new Point2D(event.getX(), event.getY());
        if (press.distance(now) > 4) dragged = true;
        if (dragged) {
            double scale = fitScale() * zoom;
            targetX = centreX - (now.getX() - previous.getX()) / scale;
            targetY = centreY - (now.getY() - previous.getY()) / scale;
            targetZoom = zoom;
            clampTarget();
            centreX = targetX; centreY = targetY;
            motion.stop();
            moving = false;
            refresh();
        }
        previous = now;
    }

    private void scroll(ScrollEvent event) {
        if (event.getDeltaY() == 0) return;
        // A wheel notch is about 40 pixels of delta; trackpads send smaller, smoother steps.
        zoomAround(Math.pow(1.18, event.getDeltaY() / 40), event.getX(), event.getY());
        event.consume();
    }

    /** Selects the faction under the pointer; a double click on open ground zooms in there. */
    void click(Point2D point, int clicks) {
        AtlasRegion region = regionAt(point);
        if (region != null) onSelect.accept(region);
        else if (clicks >= 2) zoomAround(1.8, point.getX(), point.getY());
    }

    /** The faction under a view point, or null. */
    AtlasRegion regionAt(Point2D point) {
        if (data == null) return null;
        Point2D at = toMap(point);
        return data.regionAt(new java.awt.geom.Point2D.Double(at.getX(), at.getY()));
    }

    private void hover(Point2D point) {
        AtlasRegion next = regionAt(point);
        setCursor(next == null ? Cursor.MOVE : Cursor.HAND);
        if (next == null ? hovered == null : next.equals(hovered)) return;
        hovered = next;
        refresh();
    }

    /** Stops the camera when the Atlas closes. */
    void stop() { motion.stop(); moving = false; }
}
