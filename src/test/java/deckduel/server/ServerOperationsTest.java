package deckduel.server;

import static org.junit.jupiter.api.Assertions.*;

import deckduel.cards.CatalogLoader;
import deckduel.core.EndReason;
import deckduel.core.PlayerColor;
import deckduel.core.card.DeckRules;
import deckduel.core.card.GreedyBot;
import deckduel.core.card.MatchPhase;
import deckduel.core.card.MatchState;
import deckduel.core.card.Seat;
import deckduel.db.Database;
import deckduel.db.SqliteMatchDao;
import deckduel.db.SqliteReplayDao;
import deckduel.db.SqliteUserDao;
import deckduel.net.HistoryEntry;
import deckduel.net.ReplayResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The production configuration file, and consistent encrypted backups restored into temporary databases. */
class ServerOperationsTest {
    @TempDir Path temporary;

    private ServerSettings settings(String... pairs) throws IOException {
        Properties properties = new Properties();
        for (int i = 0; i < pairs.length; i += 2) properties.setProperty(pairs[i], pairs[i + 1]);
        return ServerSettings.from(properties, temporary);
    }

    @Test void theConfigurationFileRefusesUnsafeOrMistypedSettings() throws Exception {
        ServerSettings local = settings("database", "data/game.db");
        assertTrue(local.config().loopbackOnly());
        assertNull(local.config().tls());
        assertEquals(temporary.resolve("data/game.db"), local.database());
        assertEquals(5555, local.config().port());
        assertTrue(assertThrows(IOException.class, () -> settings("database", "x.db", "bind", "0.0.0.0"))
                .getMessage().contains("TLS is required"));
        assertTrue(assertThrows(IOException.class, () -> settings("database", "x.db", "tls.certificateChain", "a.pem"))
                .getMessage().contains("both"));
        assertTrue(assertThrows(IOException.class, () -> settings("database", "x.db", "prot", "5555"))
                .getMessage().contains("Unknown server setting 'prot'"));
        assertThrows(IOException.class, () -> settings("bind", "127.0.0.1"), "a database is required");
        assertThrows(IOException.class, () -> settings("database", "x.db", "port", "five"));
        assertThrows(IllegalArgumentException.class, () -> settings("database", "x.db", "minimumClientVersion", "latest"));

        var certificates = new TestCertificates(temporary.resolve("certs"), "Settings CA");
        var issued = certificates.issue("localhost", "dns:localhost", "", 30);
        ServerSettings production = settings("database", "/var/lib/deckduel/deck-duel.db", "bind", "0.0.0.0", "port", "5556",
                "tls.certificateChain", issued.chain().toString(), "tls.privateKey", issued.key().toString(),
                "minimumClientVersion", "1.2.0", "control.socket", "/run/deckduel/control.sock", "handshakeTimeoutSeconds", "5");
        assertFalse(production.config().loopbackOnly());
        assertNotNull(production.config().tls());
        assertEquals("1.2.0", production.config().minimumClientVersion());
        assertEquals(5_000, production.config().handshakeTimeoutMillis());
        assertEquals(Path.of("/run/deckduel/control.sock").toAbsolutePath().normalize(), production.controlSocket());
        assertNull(Compatibility.check("1.2.0", "1.2.0"));
        assertNotNull(Compatibility.check("1.1.9", "1.2.0"));
        assertNull(Compatibility.check("development build", null), "no minimum: anything is accepted");

        Path template = Path.of("deploy/config/server.properties.template");
        Properties shipped = new Properties();
        try (var in = Files.newBufferedReader(template)) { shipped.load(in); }
        assertTrue(ServerSettings.KEYS.containsAll(shipped.stringPropertyNames()), "the template only uses known keys");
        assertTrue(shipped.stringPropertyNames().containsAll(List.of("bind", "port", "database", "tls.certificateChain", "tls.privateKey",
                "minimumClientVersion", "control.socket")), "and shows every setting an operator must decide");
    }

    /** A database with two accounts, a recorded match with rounds and W/L, and one replay with frames. */
    private Path populated() throws Exception {
        Path file = temporary.resolve("live/deck-duel.db");
        try (Database database = new Database(file)) {
            var users = new SqliteUserDao(database);
            users.register("alice", "alicepw");
            users.register("bob", "bobpw1");
            var persistence = new MatchPersistence(database, new SqliteMatchDao(database, users), new SqliteReplayDao(database));
            var deck = DeckRules.deck(CatalogLoader.loadBundled());
            MatchState state = MatchState.start(deck, deck, 3);
            long replay = persistence.begin("alice", "M1-1", "bob", false, "cards-neutral-5");
            persistence.frame(replay, state.view(Seat.FIRST));
            while (state.phase() != MatchPhase.FINISHED) {
                Seat mover = state.awaits(Seat.FIRST) ? Seat.FIRST : Seat.SECOND;
                state = state.apply(mover, GreedyBot.choose(state.view(mover)));
                persistence.frame(replay, state.view(Seat.FIRST));
            }
            persistence.finish(replay, state.view(Seat.FIRST), true);
            persistence.record(new MatchResult("M1-1", "alice", "bob", PlayerColor.BLUE, EndReason.SCORE, "cards-neutral-5", 3, 3),
                    state.rounds());
        }
        return file;
    }

    @Test void aBackupOfALiveDatabaseRestoresAccountsHistoryRoundsAndReplays() throws Exception {
        Path live = populated();
        Path key = temporary.resolve("keys/backup.key"), out = temporary.resolve("backups");
        Backups.keygen(key);
        assertThrows(IOException.class, () -> Backups.keygen(key), "a key is never overwritten");
        Backups.Summary original;
        Path backup;
        // The server's own connection stays open, as it would during a real backup.
        try (Database running = new Database(live)) {
            original = Backups.verify(live);
            backup = Backups.backup(live, out, key, 14);
            new SqliteUserDao(running).register("carol", "carolpw");
        }
        if (Files.getFileStore(backup).supportsFileAttributeView("posix"))
            assertEquals("rw-------", java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(backup)));
        byte[] sealed = Files.readAllBytes(backup);
        assertFalse(new String(sealed, java.nio.charset.StandardCharsets.ISO_8859_1).contains("SQLite format"), "the file is encrypted");
        try (var left = Files.list(out)) { assertEquals(1, left.count(), "no plaintext copy is left behind"); }

        Path restored = temporary.resolve("restored/deck-duel.db");
        Backups.Summary summary = Backups.restore(backup, key, restored, false);
        assertEquals(original, summary);
        assertEquals(2, summary.users(), "the snapshot predates carol");
        assertTrue(summary.frames() > 10);
        try (Database database = new Database(restored)) {
            var users = new SqliteUserDao(database);
            assertEquals(1, users.login("alice", "alicepw").wins());
            assertEquals(1, users.profileOf("bob").losses());
            var persistence = new MatchPersistence(database, new SqliteMatchDao(database, users), new SqliteReplayDao(database));
            HistoryEntry entry = persistence.historyFor("alice", 10).get(0);
            assertEquals(3, entry.yourRounds().size());
            assertEquals(HistoryEntry.Replay.AVAILABLE, entry.replay());
            assertTrue(persistence.replay("alice", entry.replayId()) instanceof ReplayResponse);
        }

        assertThrows(IOException.class, () -> Backups.restore(backup, key, restored, false), "never over an existing database by default");
        Backups.restore(backup, key, restored, true);
        try (var kept = Files.list(restored.getParent())) {
            assertTrue(kept.anyMatch(f -> f.getFileName().toString().startsWith("deck-duel.db.before-restore-")), "the replaced file is kept aside");
        }
    }

    @Test void tamperedBackupsAndWrongKeysAreRefused() throws Exception {
        Path live = populated();
        Path key = temporary.resolve("backup.key"), other = temporary.resolve("other.key");
        Backups.keygen(key);
        Backups.keygen(other);
        Path backup = Backups.backup(live, temporary.resolve("backups"), key, 14);
        assertTrue(assertThrows(IOException.class, () -> Backups.restore(backup, other, temporary.resolve("a.db"), false))
                .getMessage().contains("key is wrong"));
        byte[] bytes = Files.readAllBytes(backup);
        bytes[bytes.length / 2] ^= 1;
        Path tampered = temporary.resolve("tampered.db.enc");
        Files.write(tampered, bytes);
        assertThrows(IOException.class, () -> Backups.restore(tampered, key, temporary.resolve("b.db"), false));
        Files.writeString(temporary.resolve("plain.db.enc"), "SQLite format 3");
        assertTrue(assertThrows(IOException.class, () -> Backups.restore(temporary.resolve("plain.db.enc"), key, temporary.resolve("c.db"), false))
                .getMessage().contains("Not a Deck Duel backup"));
        assertFalse(Files.exists(temporary.resolve("a.db")));
        assertFalse(Files.exists(temporary.resolve("b.db")));
    }

    @Test void oldBackupsBeyondTheRetentionCountAreRemoved() throws Exception {
        Path live = populated();
        Path key = temporary.resolve("backup.key"), out = temporary.resolve("backups");
        Backups.keygen(key);
        Files.createDirectories(out);
        for (String old : List.of("20260101-000000", "20260102-000000", "20260103-000000"))
            Files.writeString(out.resolve("deckduel-" + old + ".db.enc"), "old");
        Files.writeString(out.resolve("notes.txt"), "kept");
        Path newest = Backups.backup(live, out, key, 2);
        try (var left = Files.list(out)) {
            List<String> names = left.map(f -> f.getFileName().toString()).sorted().toList();
            assertEquals(List.of("deckduel-20260103-000000.db.enc", newest.getFileName().toString(), "notes.txt"), names);
        }
    }

    @Test void restoringSomethingThatIsNotADeckDuelDatabaseIsRefused() throws Exception {
        Path key = temporary.resolve("backup.key");
        Backups.keygen(key);
        Path stranger = temporary.resolve("stranger.db");
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + stranger); var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE notes (text TEXT)");
        }
        byte[] keyBytes = java.util.Base64.getDecoder().decode(Files.readString(key).trim());
        Path sealed = temporary.resolve("stranger.db.enc");
        Files.write(sealed, Backups.encrypt(Files.readAllBytes(stranger), keyBytes));
        assertTrue(assertThrows(IOException.class, () -> Backups.restore(sealed, key, temporary.resolve("d.db"), false))
                .getMessage().contains("not a Deck Duel database"));
        assertFalse(Files.exists(temporary.resolve("d.db")));
    }
}
