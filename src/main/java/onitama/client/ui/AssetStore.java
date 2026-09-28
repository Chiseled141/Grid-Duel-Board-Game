package onitama.client.ui;

import java.awt.Image;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

/**
 * Loads optional hand-designed artwork from {@code /assets/} inside the jar
 * (see docs/ASSET_CHECKLIST.md). Every asset is optional: {@link #optional}
 * returns null when the file is absent and the callers keep painting their
 * drawn baseline — so art can be delivered file by file without breaking
 * anything.
 */
public final class AssetStore {

    private static final Logger LOG = Logger.getLogger(AssetStore.class.getName());
    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();

    private AssetStore() {
    }

    /**
     * Returns the named asset image, or null when it does not exist.
     * Results are cached; names are kebab-case file names like
     * {@code board-tile-light.png}.
     */
    public static Image optional(String fileName) {
        return CACHE.computeIfAbsent(fileName, name -> {
            try (InputStream in = AssetStore.class.getResourceAsStream("/assets/" + name)) {
                if (in == null) {
                    return null;
                }
                LOG.info(() -> "using designed asset: " + name);
                return ImageIO.read(in);
            } catch (Exception e) {
                LOG.warning("could not load asset " + name + ": " + e);
                return null;
            }
        });
    }
}
