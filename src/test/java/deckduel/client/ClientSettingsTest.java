package deckduel.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ClientSettingsTest {
    @TempDir Path temporary;

    @Test void cardControlPreferenceDefaultsToBothGesturesAndRoundTrips() throws Exception {
        var file = temporary.resolve("controls.properties");
        assertTrue(ClientSettings.load(file).clickToSelectCards());
        Files.writeString(file, "pieceStyle=2\n");
        var settings = ClientSettings.load(file);
        assertTrue(settings.clickToSelectCards(), "older settings gain both gestures");
        for (boolean enabled : new boolean[]{false, true}) {
            settings.setClickToSelectCards(enabled); settings.save(file);
            var loaded = ClientSettings.load(file);
            assertEquals(enabled, loaded.clickToSelectCards());
            assertFalse(Files.readString(file).contains("pieceStyle="), "retired piece preferences are not saved");
        }
    }

    @Test void menuThemeRoundTripsWithoutChangingOtherPreferences() throws Exception {
        var file = temporary.resolve("client.properties");
        var settings = new ClientSettings();
        settings.setHost("example.test");
        settings.setPort(4321);
        settings.setReducedMotion(true);
        settings.save(file);
        var restored = ClientSettings.load(file);
        assertEquals(MenuTheme.GOLDEN_FIELDS, restored.menuTheme());
        assertEquals("example.test", restored.host());
        assertEquals(4321, restored.port());
        assertTrue(restored.reducedMotion());
        Files.writeString(file, "menuTheme=VALLEY\n");
        assertEquals(MenuTheme.GOLDEN_FIELDS, ClientSettings.load(file).menuTheme(),
                "a retired East Asian theme preference migrates to the remaining battlefield");
    }

    @Test void oldMissingOrUnknownThemeFallsBackToGoldenFields() throws Exception {
        var file = temporary.resolve("client.properties");
        assertEquals(MenuTheme.GOLDEN_FIELDS, ClientSettings.load(file).menuTheme());
        Files.writeString(file, "pieceStyle=2\nmenuTheme=removed-theme\n");
        assertEquals(MenuTheme.GOLDEN_FIELDS, ClientSettings.load(file).menuTheme());
        Files.writeString(file, "pieceStyle=2\n");
        assertEquals(MenuTheme.GOLDEN_FIELDS, ClientSettings.load(file).menuTheme());
    }

    @Test void deckEntriesFromOlderClientsArePreservedUnchanged() throws Exception {
        var file = temporary.resolve("deck.properties");
        java.nio.file.Files.writeString(file, "deckRegions=Hungary;Poland;Bohemia\ndeckCards=hu-raid;pl-siege\nport=5555\n");
        ClientSettings.load(file).save(file);
        var saved = new java.util.Properties();
        try (var in = java.nio.file.Files.newInputStream(file)) { saved.load(in); }
        assertEquals("Hungary;Poland;Bohemia", saved.getProperty("deckRegions"), "the server builds decks; old entries stay as written");
        assertEquals("hu-raid;pl-siege", saved.getProperty("deckCards"));
    }
}
