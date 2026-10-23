package deckduel.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Consistent, encrypted SQLite backups. {@code VACUUM INTO} on a separate connection writes a
 * transactionally consistent copy even while the server runs (never a blind file copy); the copy
 * is checked ({@code integrity_check}, {@code foreign_key_check}, required tables), then encrypted
 * with AES-256-GCM under a key file and written owner-only. A restore decrypts, checks again and
 * only then moves the database into place, keeping any existing file aside.
 */
final class Backups {
    private static final Logger LOG = Logger.getLogger(Backups.class.getName());
    private static final byte[] MAGIC = "DDBK\u0001".getBytes(StandardCharsets.US_ASCII);
    private static final int IV_BYTES = 12, TAG_BITS = 128;
    /** Backups are read into memory to decrypt; the game's database is far smaller than this. */
    static final long MAX_BACKUP_BYTES = 512L << 20;
    static final List<String> TABLES = List.of("users", "matches", "match_rounds", "replays", "replay_frames");

    private Backups() { }

    /** Row counts of the tables a restore must preserve. */
    record Summary(long users, long matches, long rounds, long replays, long frames) { }

    /** Creates a key file of 32 random bytes (Base64), owner-only; never overwrites one. */
    static void keygen(Path keyFile) throws IOException {
        if (Files.exists(keyFile)) throw new IOException(keyFile + " already exists; refusing to overwrite a backup key");
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        Files.createDirectories(keyFile.toAbsolutePath().getParent());
        Files.writeString(keyFile, Base64.getEncoder().encodeToString(key) + "\n");
        ownerOnly(keyFile);
        System.out.println("Backup key written to " + keyFile.toAbsolutePath() + ". Keep a copy somewhere safe: backups cannot be restored without it.");
    }

    /** Writes {@code deckduel-<time>.db.enc} into {@code outDir}, keeps the newest {@code keep} backups and returns the new one. */
    static Path backup(Path database, Path outDir, Path keyFile, int keep) throws IOException, SQLException {
        if (!Files.isRegularFile(database)) throw new IOException("No database at " + database);
        byte[] key = readKey(keyFile);
        Files.createDirectories(outDir);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path plain = outDir.resolve(".deckduel-" + stamp + ".db.tmp"), encrypted = outDir.resolve("deckduel-" + stamp + ".db.enc");
        try {
            Files.deleteIfExists(plain);
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
                // The pragma answers with a row; close it, or SQLite refuses to VACUUM.
                try (Statement statement = connection.createStatement(); ResultSet ignored = statement.executeQuery("PRAGMA busy_timeout = 10000")) {
                    ignored.next();
                }
                try (var vacuum = connection.prepareStatement("VACUUM INTO ?")) {
                    vacuum.setString(1, plain.toString());
                    vacuum.execute();
                }
            }
            Summary summary = verify(plain);
            byte[] sealed = encrypt(Files.readAllBytes(plain), key);
            Path partial = outDir.resolve(".deckduel-" + stamp + ".db.enc.tmp");
            Files.write(partial, sealed);
            ownerOnly(partial);
            Files.move(partial, encrypted, StandardCopyOption.ATOMIC_MOVE);
            LOG.info(() -> "backup " + encrypted + " " + summary);
        } finally {
            Files.deleteIfExists(plain);
        }
        prune(outDir, keep);
        return encrypted;
    }

    /**
     * Restores a backup into {@code database}. An existing file is refused unless {@code replace},
     * in which case it is kept as {@code <database>.before-restore-<time>}. Stop the server first.
     */
    static Summary restore(Path backup, Path keyFile, Path database, boolean replace) throws IOException, SQLException {
        if (Files.size(backup) > MAX_BACKUP_BYTES) throw new IOException("The backup is larger than " + MAX_BACKUP_BYTES + " bytes");
        byte[] plain = decrypt(Files.readAllBytes(backup), readKey(keyFile));
        Path folder = database.toAbsolutePath().getParent();
        Files.createDirectories(folder);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path staged = folder.resolve(".restore-" + stamp + ".db.tmp");
        try {
            Files.write(staged, plain);
            ownerOnly(staged);
            Summary summary = verify(staged);
            if (Files.exists(database)) {
                if (!replace) throw new IOException(database + " exists; pass --replace true to keep it aside and restore over it");
                Files.move(database, folder.resolve(database.getFileName() + ".before-restore-" + stamp));
            }
            Files.move(staged, database, StandardCopyOption.ATOMIC_MOVE);
            System.out.println("Restored " + database + ": " + summary);
            return summary;
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    /** Checks integrity, foreign keys and the expected tables, and counts their rows. */
    static Summary verify(Path database) throws SQLException, IOException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            try (ResultSet check = statement.executeQuery("PRAGMA integrity_check")) {
                if (!check.next() || !"ok".equals(check.getString(1))) throw new IOException("The backup failed SQLite's integrity check");
            }
            try (ResultSet broken = statement.executeQuery("PRAGMA foreign_key_check")) {
                if (broken.next()) throw new IOException("The backup has rows whose foreign keys point nowhere (table " + broken.getString(1) + ")");
            }
            List<Long> counts = new ArrayList<>();
            for (String table : TABLES) {
                try (ResultSet count = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    count.next();
                    counts.add(count.getLong(1));
                } catch (SQLException missing) {
                    throw new IOException("The backup has no " + table + " table; it is not a Deck Duel database", missing);
                }
            }
            return new Summary(counts.get(0), counts.get(1), counts.get(2), counts.get(3), counts.get(4));
        }
    }

    static byte[] encrypt(byte[] plain, byte[] key) throws IOException {
        try {
            byte[] iv = new byte[IV_BYTES];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(MAGIC);
            byte[] sealed = cipher.doFinal(plain);
            return ByteBuffer.allocate(MAGIC.length + IV_BYTES + sealed.length).put(MAGIC).put(iv).put(sealed).array();
        } catch (GeneralSecurityException impossible) {
            throw new IOException("AES-GCM is unavailable", impossible);
        }
    }

    static byte[] decrypt(byte[] file, byte[] key) throws IOException {
        if (file.length < MAGIC.length + IV_BYTES + TAG_BITS / 8 || !java.util.Arrays.equals(file, 0, MAGIC.length, MAGIC, 0, MAGIC.length))
            throw new IOException("Not a Deck Duel backup");
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, file, MAGIC.length, IV_BYTES));
            cipher.updateAAD(MAGIC);
            return cipher.doFinal(file, MAGIC.length + IV_BYTES, file.length - MAGIC.length - IV_BYTES);
        } catch (javax.crypto.AEADBadTagException tampered) {
            throw new IOException("The backup was changed or the key is wrong; it cannot be restored", tampered);
        } catch (GeneralSecurityException impossible) {
            throw new IOException("AES-GCM is unavailable", impossible);
        }
    }

    private static byte[] readKey(Path keyFile) throws IOException {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(Files.readString(keyFile).trim());
        } catch (IllegalArgumentException bad) {
            throw new IOException(keyFile + " is not a backup key", bad);
        }
        if (key.length != 32) throw new IOException(keyFile + " is not a 256-bit backup key");
        return key;
    }

    /** Keeps the newest {@code keep} encrypted backups in {@code folder}. */
    private static void prune(Path folder, int keep) throws IOException {
        if (keep < 1) return;
        try (var files = Files.list(folder)) {
            List<Path> backups = files.filter(f -> f.getFileName().toString().matches("deckduel-\\d{8}-\\d{6}\\.db\\.enc"))
                    .sorted(Comparator.comparing((Path f) -> f.getFileName().toString()).reversed()).toList();
            for (Path old : backups.subList(Math.min(keep, backups.size()), backups.size())) Files.delete(old);
        }
    }

    private static void ownerOnly(Path file) throws IOException {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException windows) {
            // Not a POSIX file system.
        }
    }
}
