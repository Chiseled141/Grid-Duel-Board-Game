package deckduel.client.startup;

/**
 * What an {@link UpdateService} reported: whether a newer release exists, whether it is required,
 * and the package for this computer ({@code release}, null when the release has none for this
 * platform or the answer came from a fixture).
 */
public record UpdateCheck(Status status, String latestVersion, ReleasePackage release) {

    /** The outcome of one check. */
    public enum Status {
        /** The installed version is the latest release. */
        UP_TO_DATE,
        /** A newer release exists; the player may keep playing without it. */
        OPTIONAL,
        /** A newer release must be installed before the player can enter. */
        REQUIRED,
        /** No update service is configured, so nothing was checked. */
        NOT_CONFIGURED
    }

    /** The installed version is current. */
    public static UpdateCheck upToDate(String version) { return new UpdateCheck(Status.UP_TO_DATE, version, null); }
    /** {@code version} is available and may be skipped. */
    public static UpdateCheck optional(String version) { return new UpdateCheck(Status.OPTIONAL, version, null); }
    /** {@code version} must be installed to play. */
    public static UpdateCheck required(String version) { return new UpdateCheck(Status.REQUIRED, version, null); }
    /** No service answered because none exists; carries no version. */
    public static UpdateCheck notConfigured() { return new UpdateCheck(Status.NOT_CONFIGURED, null, null); }

    /** Whether a newer release can be installed. */
    public boolean available() { return status == Status.OPTIONAL || status == Status.REQUIRED; }
    /** Whether entry is blocked until the update is installed. */
    public boolean required() { return status == Status.REQUIRED; }
}
