package onitama.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ClientSettingsTest {
    @TempDir Path temporary;

    @Test void menuThemeRoundTripsWithoutChangingOtherPreferences() throws Exception {
        var file = temporary.resolve("client.properties");
        var settings = new ClientSettings();
        settings.setHost("example.test");
        settings.setPort(4321);
        settings.setPieceStyle(3);
        settings.setReducedMotion(true);
        settings.setMenuTheme(MenuTheme.GOLDEN_FIELDS);
        settings.save(file);
        var restored = ClientSettings.load(file);
        assertEquals(MenuTheme.GOLDEN_FIELDS, restored.menuTheme());
        assertEquals("example.test", restored.host());
        assertEquals(4321, restored.port());
        assertEquals(3, restored.pieceStyle());
        assertTrue(restored.reducedMotion());
        settings.setMenuTheme(MenuTheme.VALLEY);
        settings.save(file);
        assertEquals(MenuTheme.VALLEY, ClientSettings.load(file).menuTheme(),
                "an explicit valley choice must survive the default change");
    }

    @Test void oldMissingUnknownOrNullThemeFallsBackToGoldenFields() throws Exception {
        var file = temporary.resolve("client.properties");
        assertEquals(MenuTheme.GOLDEN_FIELDS, ClientSettings.load(file).menuTheme());
        Files.writeString(file, "pieceStyle=2\nmenuTheme=removed-theme\n");
        assertEquals(MenuTheme.GOLDEN_FIELDS, ClientSettings.load(file).menuTheme());
        Files.writeString(file, "pieceStyle=2\n");
        assertEquals(MenuTheme.GOLDEN_FIELDS, ClientSettings.load(file).menuTheme());
        var settings = new ClientSettings();
        settings.setMenuTheme(MenuTheme.GOLDEN_FIELDS);
        settings.setMenuTheme(null);
        assertEquals(MenuTheme.GOLDEN_FIELDS, settings.menuTheme());
    }
}
