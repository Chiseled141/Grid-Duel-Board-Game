package deckduel.client.ui;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import javax.imageio.ImageIO;

/**
 * A faction's generated coat of arms and banner with its dating note. These are artistic
 * interpretations for the game, not authenticated surviving banners.
 */
record Heraldry(BufferedImage coat, BufferedImage flag, String design, String scope) {
    static final String DISCLAIMER = "Generated artwork based on written heraldic descriptions; an artistic "
            + "interpretation for the game, not an authenticated surviving banner. Faction names do not imply "
            + "seven sovereign states in 1444: Transylvania was then part of the Kingdom of Hungary.";
    private static final String DIRECTORY = "/assets/atlas/heraldry/";

    /** The resource key of a faction. */
    static String key(String region) { return region.toLowerCase(Locale.ROOT); }

    /** Loads every faction's arms, banner and notes; fails loudly when one is missing. */
    static Map<String, Heraldry> load() throws IOException {
        Properties notes = new Properties();
        try (var stream = Heraldry.class.getResourceAsStream(DIRECTORY + "heraldry.properties")) {
            if (stream == null) throw new IOException("Missing heraldry notes");
            notes.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        Map<String, Heraldry> all = new HashMap<>();
        for (AtlasRegion region : AtlasRegion.ALL) {
            String key = key(region.name());
            all.put(key, new Heraldry(image("coats/" + key + ".png"), image("flags/" + key + ".jpg"),
                    required(notes, key + ".design"), required(notes, key + ".scope")));
        }
        return Map.copyOf(all);
    }

    private static BufferedImage image(String path) throws IOException {
        var resource = Heraldry.class.getResource(DIRECTORY + path);
        if (resource == null) throw new IOException("Missing heraldry image " + path);
        BufferedImage image = ImageIO.read(resource);
        if (image == null) throw new IOException("Unreadable heraldry image " + path);
        return image;
    }

    private static String required(Properties properties, String key) throws IOException {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) throw new IOException("Missing heraldry note " + key);
        return value;
    }
}
