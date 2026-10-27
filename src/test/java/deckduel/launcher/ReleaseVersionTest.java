package deckduel.launcher;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Version ordering and platform names, which decide what an update offers and installs. */
class ReleaseVersionTest {
    private static ReleaseVersion v(String text) { return ReleaseVersion.parse(text); }

    @Test void numbersCompareAsNumbersAndPreReleasesComeFirst() {
        List<String> ordered = List.of("0.9.9", "1.0.0-alpha", "1.0.0-alpha.2", "1.0.0-alpha.10", "1.0.0-beta",
                "1.0.0-rc.1", "1.0.0", "1.0.1", "1.2.0", "1.10.0", "2.0.0");
        List<ReleaseVersion> shuffled = new ArrayList<>(ordered.stream().map(ReleaseVersionTest::v).toList());
        Collections.shuffle(shuffled, new java.util.Random(4));
        Collections.sort(shuffled);
        assertEquals(ordered, shuffled.stream().map(ReleaseVersion::toString).toList());
        assertTrue(v("1.10.0").newerThan(v("1.9.0")), "not compared as text");
        assertEquals(0, v("1.2.3").compareTo(v("1.2.3")));
        assertFalse(v("1.0.0").newerThan(v("1.0.0")));
    }

    @Test void anythingButAReleaseVersionIsRejected() {
        for (String bad : List.of("development build", "1.0", "1.0.0.0", "v1.0.0", "-1.0.0", "1.0.0-", "1.0.0-bad tag", ""))
            assertFalse(ReleaseVersion.isValid(bad), bad);
        assertThrows(IllegalArgumentException.class, () -> v("1.x.0"));
    }

    @Test void platformNamesNormalizeJavaSystemProperties() {
        assertEquals("mac-aarch64", PlatformId.of("Mac OS X", "aarch64"));
        assertEquals("mac-x64", PlatformId.of("Mac OS X", "x86_64"));
        assertEquals("windows-x64", PlatformId.of("Windows 11", "amd64"));
        assertEquals("windows-aarch64", PlatformId.of("Windows 11", "arm64"));
        assertEquals("linux-x64", PlatformId.of("Linux", "amd64"));
        assertEquals("linux-aarch64", PlatformId.of("Linux", "aarch64"));
        assertTrue(PlatformId.valid(PlatformId.current()));
        assertFalse(PlatformId.valid("../mac"));
    }
}
