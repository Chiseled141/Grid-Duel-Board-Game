package deckduel.client;

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
 * {@code ~/.deckduel/client.properties}: last host/port, window size and preferences. Deck
 * entries saved by older versions are kept unchanged in the file but no longer used: the server
 * supplies every deck.
 * Missing or unreadable files fall back to defaults silently.
 */
public final class ClientSettings {

    private static final Logger LOG = Logger.getLogger(ClientSettings.class.getName());
    private static final Path FILE = Path.of(System.getProperty("user.home"),
            ".deckduel", "client.properties");

    private String host = "127.0.0.1";
    private int port = 5555;
    private int windowWidth = 1100;
    private int windowHeight = 880;
    private boolean soundEnabled = true;
    private boolean reducedMotion = false;
    private volatile boolean gameSounds = true;
    private volatile boolean clickToSelectCards = true;
    private volatile MenuTheme menuTheme = MenuTheme.GOLDEN_FIELDS;
    // Written by clients that still had a deck builder; preserved, never read for play.
    private volatile String deckRegions = "";
    private volatile String deckCards = "";

    /** Loads the settings file; keeps defaults when it does not exist. */
    public static ClientSettings load() {
        return load(FILE);
    }

    static ClientSettings load(Path file) {
        ClientSettings settings = new ClientSettings();
        if (!Files.isRegularFile(file)) {
            return settings;
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            properties.load(in);
        } catch (IOException e) {
            LOG.log(Level.FINE, "could not read settings", e);
            return settings;
        }
        settings.host = properties.getProperty("host", settings.host);
        settings.port = parseInt(properties.getProperty("port"), settings.port);
        settings.windowWidth = parseInt(properties.getProperty("windowWidth"), settings.windowWidth);
        settings.windowHeight = parseInt(properties.getProperty("windowHeight"), settings.windowHeight);
        settings.soundEnabled = parseBoolean(
                properties.getProperty("soundEnabled"), settings.soundEnabled);
        settings.reducedMotion = parseBoolean(
                properties.getProperty("reducedMotion"), settings.reducedMotion);
        settings.gameSounds = parseBoolean(properties.getProperty("gameSounds"), settings.gameSounds);
        settings.clickToSelectCards = parseBoolean(
                properties.getProperty("clickToSelectCards"), settings.clickToSelectCards);
        settings.menuTheme = MenuTheme.fromPreference(properties.getProperty("menuTheme"));
        settings.deckRegions = properties.getProperty("deckRegions", "");
        settings.deckCards = properties.getProperty("deckCards", "");
        return settings;
    }

    /** Serializes preference writes from menu workers and the frame; failures are logged. */
    public synchronized void save() {
        save(FILE);
    }

    synchronized void save(Path file) {
        Properties properties = new Properties();
        properties.setProperty("host", host);
        properties.setProperty("port", String.valueOf(port));
        properties.setProperty("windowWidth", String.valueOf(windowWidth));
        properties.setProperty("windowHeight", String.valueOf(windowHeight));
        properties.setProperty("soundEnabled", String.valueOf(soundEnabled));
        properties.setProperty("reducedMotion", String.valueOf(reducedMotion));
        properties.setProperty("clickToSelectCards", String.valueOf(clickToSelectCards));
        properties.setProperty("gameSounds", String.valueOf(gameSounds));
        properties.setProperty("menuTheme", menuTheme.name());
        properties.setProperty("deckRegions", deckRegions);
        properties.setProperty("deckCards", deckCards);
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                properties.store(out, "Deck Duel client settings");
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

    private static boolean parseBoolean(String raw, boolean fallback) {
        return raw == null ? fallback : Boolean.parseBoolean(raw);
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

    /** Whether menu music is enabled by the persisted header sound toggle. */
    public boolean soundEnabled() {
        return soundEnabled;
    }

    public void setSoundEnabled(boolean soundEnabled) {
        this.soundEnabled = soundEnabled;
    }

    /** Whether reduced motion is enabled to skip travel, flips, and pulses. */
    public boolean reducedMotion() {
        return reducedMotion;
    }

    public void setReducedMotion(boolean reducedMotion) {
        this.reducedMotion = reducedMotion;
    }

    /** Whether match scoring sounds play; separate from the menu music toggle. */
    public boolean gameSounds() { return gameSounds; }

    /** Turns match scoring sounds on or off. */
    public void setGameSounds(boolean enabled) { gameSounds = enabled; }

    /** Whether a click may draw a card; upward dragging works in either mode. */
    public boolean clickToSelectCards() { return clickToSelectCards; }

    /** Chooses click-or-slide selection, or slide-only selection. */
    public void setClickToSelectCards(boolean enabled) { clickToSelectCards = enabled; }

    /** Preferred scene shared by login and the main menu. */
    public MenuTheme menuTheme() { return menuTheme; }

}
