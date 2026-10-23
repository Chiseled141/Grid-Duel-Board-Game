package deckduel.client.startup;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/** Which server the client dials: development overrides, then the packaged production server, then saved settings. */
class EndpointsTest {
    private static final Optional<ServerRoute> PRODUCTION = Optional.of(new ServerRoute("play.example.net", 5555, true));

    @Test void developmentOverridesComeFirst() {
        var choice = Endpoints.resolve("127.0.0.1", 6000, null, PRODUCTION, "127.0.0.1", 5555);
        assertEquals(new ServerRoute("127.0.0.1", 6000, false), choice.route());
        assertEquals("development override", choice.source());
        assertTrue(Endpoints.resolve("localhost", 6000, true, PRODUCTION, "127.0.0.1", 5555).route().tls(), "local TLS testing");
        assertTrue(Endpoints.resolve("staging.example.net", null, null, PRODUCTION, "127.0.0.1", 5555).route().tls(),
                "a remote override always uses TLS");
        assertThrows(IllegalArgumentException.class, () -> Endpoints.resolve("staging.example.net", 5555, false, PRODUCTION, "127.0.0.1", 5555),
                "plaintext to another computer is refused, not used");
    }

    @Test void aPackagedProductionServerBeatsObsoleteSavedSettings() {
        var choice = Endpoints.resolve(null, null, null, PRODUCTION, "127.0.0.1", 5555);
        assertEquals(PRODUCTION.get(), choice.route());
        assertEquals("packaged production server", choice.source());
        assertEquals(PRODUCTION.get(), Endpoints.resolve(null, null, null, PRODUCTION, "192.168.1.20", 7000).route());
    }

    @Test void withoutAProductionServerTheSavedSettingOrLocalDefaultIsUsed() {
        var local = Endpoints.resolve(null, null, null, Optional.empty(), "127.0.0.1", 5555);
        assertEquals(ServerRoute.LOCAL_DEVELOPMENT, local.route());
        assertEquals("local development default", local.source());
        var saved = Endpoints.resolve(null, null, null, Optional.empty(), "lan-box.example.net", 5600);
        assertEquals(new ServerRoute("lan-box.example.net", 5600, true), saved.route());
        assertEquals("saved setting", saved.source());
    }

    @Test void packagedSettingsAreProductionOnlyWhenFilledIn() {
        var unfiltered = new Properties();
        unfiltered.setProperty("host", "${deckduel.server.host}");
        unfiltered.setProperty("port", "${deckduel.server.port}");
        assertTrue(Endpoints.from(unfiltered).isEmpty());
        var filled = new Properties();
        filled.setProperty("host", "play.example.net");
        filled.setProperty("port", "5556");
        assertEquals(new ServerRoute("play.example.net", 5556, true), Endpoints.from(filled).orElseThrow());
        assertTrue(Endpoints.packaged().isEmpty(), "this build packages no production server");
    }
}
