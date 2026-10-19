package deckduel.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.Test;

class AtlasViewTest {
    private static final AtlasMapData DATA = loadMap();

    private static AtlasMapData loadMap() {
        try { return AtlasMapData.load(); }
        catch (java.io.IOException exception) { throw new ExceptionInInitializerError(exception); }
    }

    private record Fixture(AtlasView view, AtomicInteger backs) { }

    private static Fixture fixture(double width, double height) {
        AtomicInteger backs = new AtomicInteger();
        var view = new AtlasView(backs::incrementAndGet, () -> true);
        new Scene(view).getStylesheets().add(FxKit.STYLESHEET);
        view.showForTest(DATA);
        size(view, width, height);
        return new Fixture(view, backs);
    }

    private static void size(AtlasView view, double width, double height) {
        view.resize(width, height);
        view.applyCss();
        view.layout();
    }

    private static KeyEvent key(KeyCode code) { return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false); }

    @Test void allSevenLabelsHitTheirOwnExportedBoundary() throws Exception {
        assertNotNull(DATA.terrain());
        var terrain = javax.imageio.ImageIO.read(DATA.terrain());
        assertTrue(terrain.getWidth() >= 1000);
        for (AtlasRegion region : AtlasRegion.ALL) {
            assertNotNull(DATA.shape(region));
            assertTrue(DATA.outline(region).startsWith("M"), region.name() + " keeps its drawable outline");
            assertEquals(region, DATA.regionAt(DATA.label(region)), region.name());
        }
        assertNull(DATA.regionAt(new Point2D.Double(0, 0)), "transparent margin is not a country");
    }

    @Test void exportedCountryBoundariesDoNotOverlap() {
        for (int i = 0; i < AtlasRegion.ALL.size(); i++) for (int j = i + 1; j < AtlasRegion.ALL.size(); j++) {
            Area overlap = new Area(DATA.shape(AtlasRegion.ALL.get(i)));
            overlap.intersect(new Area(DATA.shape(AtlasRegion.ALL.get(j))));
            assertTrue(polygonArea(overlap) < .001,
                    AtlasRegion.ALL.get(i).name() + " overlaps " + AtlasRegion.ALL.get(j).name());
        }
    }

    @Test void visibleBordersSeparateInsideFromOutside() {
        double[] c = new double[6];
        for (AtlasRegion region : AtlasRegion.ALL) {
            var shape = DATA.shape(region);
            double x = 0, y = 0;
            for (var path = shape.getPathIterator(null); !path.isDone(); path.next()) {
                int type = path.currentSegment(c);
                if (type == java.awt.geom.PathIterator.SEG_MOVETO) { x = c[0]; y = c[1]; }
                else if (type == java.awt.geom.PathIterator.SEG_LINETO) {
                    double dx = c[0] - x, dy = c[1] - y, length = Math.hypot(dx, dy);
                    if (length > 0) {
                        double mx = (x + c[0]) / 2, my = (y + c[1]) / 2;
                        double nx = -dy / length * .25, ny = dx / length * .25;
                        assertNotEquals(shape.contains(mx + nx, my + ny), shape.contains(mx - nx, my - ny),
                                region.name() + " must not draw internal seam strokes");
                    }
                    x = c[0]; y = c[1];
                }
            }
        }
    }

    // Vector boolean operations can leave round-off slivers far smaller than one raster pixel.
    private static double polygonArea(Area area) {
        double sum = 0, startX = 0, startY = 0, x = 0, y = 0;
        double[] c = new double[6];
        for (var it = area.getPathIterator(null, .01); !it.isDone(); it.next()) {
            int type = it.currentSegment(c);
            if (type == java.awt.geom.PathIterator.SEG_MOVETO) { startX = x = c[0]; startY = y = c[1]; }
            else if (type == java.awt.geom.PathIterator.SEG_LINETO) { sum += x * c[1] - c[0] * y; x = c[0]; y = c[1]; }
            else if (type == java.awt.geom.PathIterator.SEG_CLOSE) sum += x * startY - startX * y;
        }
        return Math.abs(sum) / 2;
    }

    @Test void everyFactionHasArmsBannerAndDatingNote() throws Exception {
        var heraldry = Heraldry.load();
        assertEquals(7, heraldry.size());
        for (AtlasRegion region : AtlasRegion.ALL) {
            Heraldry arms = heraldry.get(Heraldry.key(region.name()));
            assertNotNull(arms, region.name());
            assertTrue(arms.coat().getColorModel().hasAlpha(), region.name() + " shield keeps its transparent background");
            assertTrue(arms.flag().getWidth() > arms.flag().getHeight(), region.name() + " banner is landscape");
            assertFalse(arms.design().isBlank());
            assertFalse(arms.scope().isBlank());
        }
        assertTrue(heraldry.get("transylvania").scope().contains("not a claim about 1444"));
    }

    @Test void escapeBackspaceAndBackReturnToTheMenu() throws Exception {
        FxTesting.run(() -> {
            var f = fixture(1280, 720);
            f.view.keyPressed(key(KeyCode.ESCAPE));
            f.view.keyPressed(key(KeyCode.BACK_SPACE));
            ((Button) f.view.lookup("#atlasBack")).fire();
            assertEquals(3, f.backs.get());
        });
    }

    @Test void choosingACountryShowsItsFloatingPanelAndFliesToIt() throws Exception {
        FxTesting.run(() -> {
            var f = fixture(1280, 720);
            AtlasMapView map = f.view.map();
            assertEquals(1, map.zoom());
            f.view.select(AtlasRegion.named("Styria"), true);
            assertEquals("Styria", map.selected().name());
            assertTrue(map.zoom() > 1.5, "a small faction is zoomed into");
            assertTrue(f.view.bannerDescription().startsWith("Styria"));
            for (String gone : new String[]{"#atlasHeraldryNote", "#atlasUnits", "#atlasDeckStatus", "#atlasOpenDeck"})
                assertNull(f.view.lookup(gone), "no deck controls or extra notes in the Atlas: " + gone);
            for (AtlasRegion region : AtlasRegion.ALL) assertNull(f.view.lookup("#atlas" + region.name()), "no bottom nation picker");
            f.view.select(AtlasRegion.named("Hungary"), false);
            map.overview();
            assertEquals(1, map.zoom());
            f.view.keyPressed(key(KeyCode.RIGHT));
            assertEquals("Poland", map.selected().name(), "arrow keys browse the factions in order");
        });
    }

    @Test void clickingTheMapSelectsTheFactionUnderThePointer() throws Exception {
        FxTesting.run(() -> {
            var f = fixture(1280, 720);
            AtlasMapView map = f.view.map();
            var label = DATA.label(AtlasRegion.named("Styria"));
            javafx.geometry.Point2D graz = map.toScreen(new javafx.geometry.Point2D(label.getX(), label.getY()));
            assertEquals("Styria", map.regionAt(graz).name());
            map.click(graz, 1);
            assertEquals("Styria", map.selected().name());
            assertEquals("Styria", f.view.selected().name(), "the chronicle follows the map");
            assertNull(map.regionAt(map.toScreen(new javafx.geometry.Point2D(0, 0))), "the empty map margin is not a faction");
        });
    }

    @Test void theMapFillsTheBackgroundAtSeveralSizesAndZoomIsCapped() throws Exception {
        FxTesting.run(() -> {
            var f = fixture(1280, 720);
            AtlasMapView map = f.view.map();
            for (double[] size : new double[][]{{1280, 720}, {1920, 1080}}) {
                size(f.view, size[0], size[1]);
                assertEquals(size[0], map.getWidth(), "the map fills the Atlas background");
                assertEquals(size[1], map.getHeight());
                var details = f.view.lookup("#atlasChronicleScroll");
                assertTrue(details.getLayoutX() > map.getWidth() / 2, "details float on the right of the map");
                for (double zoom : new double[]{1, 2.5, AtlasMapView.MAX_ZOOM}) {
                    map.overview();
                    map.zoomBy(zoom);
                    assertNotNull(f.view.snapshot(null, null));
                }
            }
            map.zoomBy(100);
            assertEquals(AtlasMapView.MAX_ZOOM, map.zoom(), "zoom is capped");
        });
    }
}
