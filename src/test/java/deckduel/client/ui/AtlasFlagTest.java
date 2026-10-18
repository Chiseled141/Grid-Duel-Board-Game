package deckduel.client.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtlasFlagTest {
    @Test void everyFactionHasSharpOfflineArtworkAndRetrievableAttribution() throws Exception {
        var flags = AtlasFlag.load();
        assertEquals(7, flags.size());
        for (AtlasRegion region : AtlasRegion.ALL) {
            var flag = flags.get(AtlasFlag.key(region));
            assertNotNull(flag, region.name());
            assertTrue(flag.image().getWidth() >= 1000, region.name() + " must use sharp source artwork");
            assertTrue(flag.source().startsWith("https://commons.wikimedia.org/wiki/File:"));
            assertFalse(flag.artist().isBlank()); assertFalse(flag.license().isBlank());
            assertTrue(flag.licenseUrl().startsWith("https://creativecommons.org/"));
            assertTrue(flag.attribution().contains(flag.source()));
            assertTrue(flag.attribution().contains(flag.artist()));
        }
    }

    @Test void transylvaniaUsesExplicitCrownReferenceWithoutInventingAnIndependentFlag() throws Exception {
        var flags = AtlasFlag.load();
        var transylvania = flags.get("transylvania");
        assertSame(flags.get("hungary").image(), transylvania.image());
        assertTrue(transylvania.title().contains("crown reference"));
        assertTrue(transylvania.context().contains("not a claim"));
        assertTrue(flags.get("croatia").title().contains("Talovac"));
        assertTrue(flags.get("croatia").context().contains("campaign/family banner"));
    }
}
