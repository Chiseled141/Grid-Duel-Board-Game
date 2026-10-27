package deckduel.client.startup;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/** The client's own version, stamped into {@code /deckduel/version.properties} by the Maven build. */
public final class InstalledVersion {
    private static final Logger LOG = Logger.getLogger(InstalledVersion.class.getName());
    /** Shown when the build did not stamp a version, as in an IDE run without Maven's resource step. */
    static final String UNKNOWN = "development build";

    private InstalledVersion() { }

    /** Returns the installed version, for example {@code 1.0.0}. */
    public static String current() {
        try (InputStream in = InstalledVersion.class.getResourceAsStream("/deckduel/version.properties")) {
            if (in == null) return UNKNOWN;
            Properties properties = new Properties();
            properties.load(in);
            String version = properties.getProperty("version", "").trim();
            // An unfiltered copy still holds the Maven placeholder.
            return version.isEmpty() || version.contains("${") ? UNKNOWN : version;
        } catch (IOException exception) {
            LOG.log(Level.FINE, "could not read the installed version", exception);
            return UNKNOWN;
        }
    }
}
