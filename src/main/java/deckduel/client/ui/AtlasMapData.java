package deckduel.client.ui;

import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

/** Artwork and fixed projected boundaries exported together from the researched map sources. */
final class AtlasMapData {
    static final int SIZE = 3840;
    private static final String ROOT = "/assets/atlas/researched-map/";
    /** Where the terrain plate sits in map units; it shares the boundaries' projection and pixel origin exactly. */
    static final java.awt.geom.Rectangle2D TERRAIN = new java.awt.geom.Rectangle2D.Double(-1600, -400, 8192, 4608);
    private final URL terrain;
    private final Map<AtlasRegion, Shape> shapes;
    private final Map<AtlasRegion, Point2D> labels;
    private final Map<AtlasRegion, String> outlines;

    private AtlasMapData(URL terrain, Map<AtlasRegion, Shape> shapes, Map<AtlasRegion, Point2D> labels,
                         Map<AtlasRegion, String> outlines) {
        this.terrain = terrain;
        this.outlines = Map.copyOf(outlines);
        this.shapes = Map.copyOf(shapes);
        this.labels = Map.copyOf(labels);
    }

    /** Parses the boundaries off the UI threads; no live network or external SVG resources are used. */
    static AtlasMapData load() throws IOException {
        try (var input = AtlasMapData.class.getResourceAsStream(ROOT + "boundaries.svg")) {
            if (input == null) throw new IOException("Missing Atlas boundaries");
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var paths = factory.newDocumentBuilder().parse(input).getElementsByTagName("path");
            Map<AtlasRegion, String> outlines = new LinkedHashMap<>();
            Map<AtlasRegion, Shape> shapes = new LinkedHashMap<>();
            Map<AtlasRegion, Point2D> labels = new LinkedHashMap<>();
            for (int i = 0; i < paths.getLength(); i++) {
                Element path = (Element) paths.item(i);
                String key = path.getAttribute("id");
                AtlasRegion region = AtlasRegion.ALL.stream()
                        .filter(r -> r.name().toLowerCase(Locale.ROOT).equals(key)).findFirst().orElseThrow();
                shapes.put(region, parsePath(path.getAttribute("d")));
                outlines.put(region, path.getAttribute("d").trim());
                labels.put(region, new Point2D.Double(Double.parseDouble(path.getAttribute("data-label-x")),
                        Double.parseDouble(path.getAttribute("data-label-y"))));
            }
            if (shapes.size() != AtlasRegion.ALL.size()) throw new IOException("Incomplete Atlas boundaries");
            URL terrain = AtlasMapData.class.getResource(ROOT + "terrain.png");
            if (terrain == null) throw new IOException("Missing Atlas artwork: terrain.png");
            return new AtlasMapData(terrain, shapes, labels, outlines);
        } catch (IOException exception) { throw exception; }
        catch (Exception exception) { throw new IOException("Could not decode Atlas map", exception); }
    }

    /** Only the exporter-generated M/L/Z polygon subset is accepted. */
    private static Path2D parsePath(String data) throws IOException {
        Path2D path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        String[] tokens = data.trim().split("\\s+");
        for (int i = 0; i < tokens.length;) {
            String command = tokens[i++];
            if (command.equals("Z")) path.closePath();
            else if (command.equals("M") || command.equals("L")) {
                double x = Double.parseDouble(tokens[i++]), y = Double.parseDouble(tokens[i++]);
                if (command.equals("M")) path.moveTo(x, y); else path.lineTo(x, y);
            } else throw new IOException("Unsupported Atlas path command: " + command);
        }
        return path;
    }

    /** The painted terrain plate, decoded by the view that shows it. */
    URL terrain() { return terrain; }
    Shape shape(AtlasRegion region) { return shapes.get(region); }
    /** The region's boundary as validated SVG path data (M/L/Z only), for drawing. */
    String outline(AtlasRegion region) { return outlines.get(region); }
    Point2D label(AtlasRegion region) { return labels.get(region); }

    AtlasRegion regionAt(Point2D point) {
        for (AtlasRegion region : AtlasRegion.ALL) if (shapes.get(region).contains(point)) return region;
        return null;
    }
}
