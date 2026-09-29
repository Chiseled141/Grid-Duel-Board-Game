package onitama.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Client settings persisted with {@link Properties} at
 * {@code ~/.onitama/client.properties}: last host/port and window size.
 * Missing or unreadable files fall back to defaults silently.
 */
public final class ClientSettings {

    private static final Logger LOG = Logger.getLogger(ClientSettings.class.getName());
    private static final Path FILE = Path.of(System.getProperty("user.home"),
            ".onitama", "client.properties");

    private String host = "127.0.0.1";
    private int port = 5555;
    private int windowWidth = 1100;
    private int windowHeight = 880;
    private int pieceStyle = 1;

    /** Loads the settings file; keeps defaults when it does not exist. */
    public static ClientSettings load() {
        ClientSettings settings = new ClientSettings();
        if (!Files.isRegularFile(FILE)) {
            return settings;
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) {
            properties.load(in);
        } catch (IOException e) {
            LOG.log(Level.FINE, "could not read settings", e);
            return settings;
        }
        settings.host = properties.getProperty("host", settings.host);
        settings.port = parseInt(properties.getProperty("port"), settings.port);
        settings.windowWidth = parseInt(properties.getProperty("windowWidth"), settings.windowWidth);
        settings.windowHeight = parseInt(properties.getProperty("windowHeight"), settings.windowHeight);
        settings.pieceStyle = parseInt(properties.getProperty("pieceStyle"), settings.pieceStyle);
        return settings;
    }

    /** Persists the current settings; failures are logged, never thrown. */
    public void save() {
        Properties properties = new Properties();
        properties.setProperty("host", host);
        properties.setProperty("port", String.valueOf(port));
        properties.setProperty("windowWidth", String.valueOf(windowWidth));
        properties.setProperty("windowHeight", String.valueOf(windowHeight));
        properties.setProperty("pieceStyle", String.valueOf(pieceStyle));
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) {
                properties.store(out, "Onitama Online client settings");
            }
        } catch (IOException e) {
            LOG.log(Level.FINE, "could not save settings", e);
        }
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public int windowWidth() {
        return windowWidth;
    }

    public int windowHeight() {
        return windowHeight;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setWindowSize(int width, int height) {
        this.windowWidth = width;
        this.windowHeight = height;
    }

    /** The preferred board-piece style (1 ink tokens, 2 seal stones, 3 ink silhouettes). */
    public int pieceStyle() {
        return pieceStyle;
    }

    public void setPieceStyle(int pieceStyle) {
        this.pieceStyle = pieceStyle;
    }
}
