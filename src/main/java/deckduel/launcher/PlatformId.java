package deckduel.launcher;

import java.util.Locale;

/**
 * Names the operating system and processor an update package is built for, for example
 * {@code mac-aarch64} or {@code windows-x64}. The game jar carries JavaFX native libraries for
 * exactly one of these.
 */
public final class PlatformId {
    private PlatformId() { }

    /** The platform this JVM runs on. */
    public static String current() {
        return of(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
    }

    /** Normalizes {@code os.name} and {@code os.arch} values, for example "Mac OS X" and "arm64". */
    public static String of(String osName, String osArch) {
        String os = osName.toLowerCase(Locale.ROOT);
        String system = os.startsWith("windows") ? "windows" : os.startsWith("mac") || os.contains("darwin") ? "mac"
                : os.contains("linux") ? "linux" : os.replaceAll("[^a-z0-9]", "");
        String arch = osArch.toLowerCase(Locale.ROOT);
        String processor = switch (arch) {
            case "amd64", "x86_64", "x64" -> "x64";
            case "aarch64", "arm64" -> "aarch64";
            default -> arch.replaceAll("[^a-z0-9]", "");
        };
        return system + "-" + processor;
    }

    /** Whether {@code id} is a well-formed platform name. */
    public static boolean valid(String id) { return id != null && id.matches("[a-z0-9]+-[a-z0-9]+"); }
}
