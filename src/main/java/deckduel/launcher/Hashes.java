package deckduel.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 digests as lowercase hex, the form release metadata and package manifests use. */
public final class Hashes {
    private Hashes() { }

    /** A new SHA-256 digest; every Java runtime provides one. */
    public static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** The SHA-256 of a file's contents. */
    public static String sha256(Path file) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            for (int read; (read = in.read(buffer)) > 0; ) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Whether {@code text} looks like a SHA-256 hex digest. */
    public static boolean valid(String text) { return text != null && text.matches("[0-9a-f]{64}"); }
}
