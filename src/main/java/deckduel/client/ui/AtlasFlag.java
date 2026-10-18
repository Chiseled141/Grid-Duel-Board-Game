package deckduel.client.ui;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/** Downloaded banner artwork with attribution and a separate historical identification. */
record AtlasFlag(BufferedImage image, String title, String context, String artist,
                 String license, String licenseUrl, String source) {
    static String key(AtlasRegion region) { return region.name().toLowerCase(Locale.ROOT); }

    static Map<String, AtlasFlag> load() throws IOException {
        Properties properties = new Properties();
        try (var stream = AtlasFlag.class.getResourceAsStream("/assets/atlas/flags/catalog.properties")) {
            if (stream == null) throw new IOException("Missing atlas flag catalog");
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        Map<String, BufferedImage> images = new HashMap<>();
        Map<String, AtlasFlag> flags = new HashMap<>();
        for (AtlasRegion region : AtlasRegion.ALL) {
            String key = key(region), filename = required(properties, key + ".image");
            BufferedImage image = images.get(filename);
            if (image == null) {
                var resource = AtlasFlag.class.getResource("/assets/atlas/flags/" + filename);
                if (resource == null) throw new IOException("Missing atlas flag: " + filename);
                image = ImageIO.read(resource);
                if (image == null) throw new IOException("Unreadable atlas flag: " + filename);
                images.put(filename, image);
            }
            flags.put(key, new AtlasFlag(image, required(properties, key + ".title"),
                required(properties, key + ".context"), required(properties, key + ".artist"),
                required(properties, key + ".license"), required(properties, key + ".licenseUrl"),
                required(properties, key + ".source")));
        }
        return Map.copyOf(flags);
    }

    private static String required(Properties properties, String key) throws IOException {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) throw new IOException("Missing atlas flag metadata: " + key);
        return value;
    }

    String attribution() {
        return title + "\n\n" + context + "\n\nArtwork: " + artist + "\nLicense: " + license
            + "\n" + licenseUrl + "\n\nSource: " + source
            + "\n\nDownloaded artwork; no AI generation or repainting. SVG sources use Wikimedia's PNG rendering; "
            + "PNG originals are unchanged. Display is scaled proportionally.";
    }
}
