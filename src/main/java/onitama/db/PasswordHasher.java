package onitama.db;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Password hashing with PBKDF2-HMAC-SHA256 (JDK built-in). Stored format:
 * {@code iterations:saltBase64:hashBase64} with a random 16-byte salt per
 * user. Plaintext passwords are never stored or logged.
 */
public final class PasswordHasher {

    private static final int ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    private PasswordHasher() {
    }

    /** Hashes a fresh password with a new random salt. */
    public static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] key = derive(password, salt, ITERATIONS);
        return ITERATIONS + ":" + encode(salt) + ":" + encode(key);
    }

    /**
     * Checks a password against a stored hash in constant time.
     * Any malformed stored value simply fails verification.
     */
    public static boolean verify(String password, String stored) {
        try {
            String[] parts = stored.split(":");
            int iterations = Integer.parseInt(parts[0]);
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] expected = Base64.getDecoder().decode(parts[2]);
            byte[] actual = derive(password, salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            // PBKDF2WithHmacSHA256 is mandated by the JDK spec; unreachable in practice.
            throw new IllegalStateException("PBKDF2 unavailable", e);
        }
    }

    private static String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
