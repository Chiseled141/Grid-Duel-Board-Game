package deckduel.server;

import static org.junit.jupiter.api.Assertions.*;

import deckduel.cards.CatalogLoader;
import deckduel.core.EndReason;
import deckduel.core.PlayerColor;
import deckduel.core.card.DeckRules;
import deckduel.core.card.GreedyBot;
import deckduel.core.card.MatchPhase;
import deckduel.core.card.MatchState;
import deckduel.core.card.PlayerView;
import deckduel.core.card.RoundResult;
import deckduel.core.card.Seat;
import deckduel.db.Database;
import deckduel.db.SqliteMatchDao;
import deckduel.db.SqliteReplayDao;
import deckduel.db.SqliteUserDao;
import deckduel.net.ErrorMessage;
import deckduel.net.HistoryEntry;
import deckduel.net.ReplayResponse;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Replay storage against temporary SQLite: additive migration, honest statuses and owner-only reads. */
class ReplayPersistenceTest {
    @TempDir Path temporary;
    private Database database;
    private SqliteUserDao users;
    private SqliteReplayDao replays;
    private MatchPersistence persistence;

    @BeforeEach void open() throws Exception { open(temporary.resolve("replays.db")); }
    @AfterEach void close() throws Exception { database.close(); }

    private void open(Path file) throws Exception {
        database = new Database(file);
        users = new SqliteUserDao(database);
        replays = new SqliteReplayDao(database);
        persistence = new MatchPersistence(database, new SqliteMatchDao(database, users), replays);
    }

    /** The views one seat sees through a whole greedy game, one per revision. */
    private static List<PlayerView> game(Seat seat) {
        var deck = DeckRules.deck(CatalogLoader.loadBundled());
        MatchState state = MatchState.start(deck, deck, 42);
        List<PlayerView> views = new ArrayList<>(List.of(state.view(seat)));
        while (state.phase() != MatchPhase.FINISHED) {
            Seat mover = state.awaits(Seat.FIRST) ? Seat.FIRST : Seat.SECOND;
            state = state.apply(mover, GreedyBot.choose(state.view(mover)));
            views.add(state.view(seat));
        }
        return views;
    }

    private static MatchResult result(String key) {
        return new MatchResult(key, "alice", "bob", PlayerColor.BLUE, EndReason.SCORE, "cards-neutral-5", 3, 3);
    }

    private long record(String owner, String key, boolean practice, List<PlayerView> views, boolean complete) {
        long id = persistence.begin(owner, key, practice ? MatchSession.EASY_BOT : "bob", practice, "cards-neutral-5");
        for (PlayerView view : views) assertTrue(persistence.frame(id, view));
        persistence.finish(id, views.get(views.size() - 1), complete);
        return id;
    }

    @Test void anOnlineResultLinksToEachPlayersOwnReplayWithoutTouchingTheResultTransaction() throws Exception {
        users.register("alice", "alicepw");
        users.register("bob", "bobpw1");
        List<PlayerView> views = game(Seat.FIRST);
        long id = record("alice", "M1-1", false, views, true);
        persistence.record(result("M1-1"), List.of(new RoundResult(1, null, 30, 20)));
        assertEquals(1, users.profileOf("alice").wins());
        assertEquals(1, users.profileOf("bob").losses());

        HistoryEntry alices = persistence.historyFor("alice", 10).get(0);
        assertEquals(HistoryEntry.Replay.AVAILABLE, alices.replay());
        assertEquals(id, alices.replayId());
        HistoryEntry bobs = persistence.historyFor("bob", 10).get(0);
        assertEquals(HistoryEntry.Replay.UNAVAILABLE, bobs.replay(), "Bob has no recording of his own here");
        assertNull(bobs.replayId());

        var replay = (ReplayResponse) persistence.replay("alice", id);
        assertEquals(views, replay.frames());
        assertTrue(replay.complete());
        assertEquals(ReplayStore.UNAVAILABLE, ((ErrorMessage) persistence.replay("bob", id)).text(), "owner only");
        assertEquals(ReplayStore.UNAVAILABLE, ((ErrorMessage) persistence.replay("mallory", id)).text());
        assertNull(replays.find("bob", id));
    }

    @Test void aRepeatedRevisionIsStoredOnce() throws Exception {
        users.register("alice", "alicepw");
        PlayerView opening = game(Seat.FIRST).get(0);
        long id = persistence.begin("alice", "M2-1", MatchSession.EASY_BOT, true, "cards-neutral-5");
        assertTrue(replays.addFrame(id, opening.revision(), new byte[] {1}));
        assertFalse(replays.addFrame(id, opening.revision(), new byte[] {2}), "the primary key ignores a second copy");
        assertEquals(1, replays.frames(id).size());
    }

    @Test void missingFramesAndUnfinishedRecordingsAreReportedAsIncomplete() throws Exception {
        users.register("alice", "alicepw");
        users.register("bob", "bobpw1");
        List<PlayerView> views = game(Seat.FIRST);
        // A practice game whose middle frames could not be stored.
        long gappy = persistence.begin("alice", "P1-1", MatchSession.EASY_BOT, true, "cards-neutral-5");
        for (PlayerView view : List.of(views.get(0), views.get(1), views.get(views.size() - 1))) persistence.frame(gappy, view);
        persistence.finish(gappy, views.get(views.size() - 1), false);
        HistoryEntry practice = persistence.historyFor("alice", 10).get(0);
        assertTrue(practice.practice());
        assertEquals(HistoryEntry.Replay.INCOMPLETE, practice.replay());
        assertFalse(((ReplayResponse) persistence.replay("alice", gappy)).complete());

        // An online match whose recording never got its result (the finish write failed).
        long unfinished = persistence.begin("alice", "M3-1", "bob", false, "cards-neutral-5");
        persistence.frame(unfinished, views.get(0));
        persistence.record(result("M3-1"), List.of());
        HistoryEntry online = persistence.historyFor("alice", 10).stream().filter(e -> !e.practice()).findFirst().orElseThrow();
        assertEquals(HistoryEntry.Replay.INCOMPLETE, online.replay());

        // A practice game cut off by a server stop has no result and is not listed at all.
        persistence.begin("alice", "P2-1", MatchSession.EASY_BOT, true, "cards-neutral-5");
        assertEquals(2, persistence.historyFor("alice", 10).size());
    }

    @Test void framesFromAnIncompatibleBuildAreRefusedNotPartlyShown() throws Exception {
        users.register("alice", "alicepw");
        long id = persistence.begin("alice", "M4-1", "bob", false, "cards-neutral-5");
        replays.addFrame(id, 0, new byte[] {(byte) 0xAC, (byte) 0xED, 0, 5, 1, 2, 3});
        var reply = (ErrorMessage) persistence.replay("alice", id);
        assertTrue(reply.text().contains("another version"));
        long empty = persistence.begin("alice", "M5-1", "bob", false, "cards-neutral-5");
        assertEquals(ReplayStore.UNAVAILABLE, ((ErrorMessage) persistence.replay("alice", empty)).text());
    }

    @Test void realProtocol16FrameIsRefusedWithoutDeletingHistoryOrReplayBytes() throws Exception {
        users.register("alice", "alicepw");
        long id = persistence.begin("alice", "pre-kings", "bob", false, "cards-neutral-5");
        byte[] original;
        try (var in = getClass().getResourceAsStream("/replays/protocol16-frame.ser")) {
            assertNotNull(in); original = in.readAllBytes();
        }
        replays.addFrame(id, 0, original);
        assertTrue(((ErrorMessage) persistence.replay("alice", id)).text().contains("another version"));
        assertEquals(1, replays.frames(id).size(), "the stored legacy frame is retained");
        assertNotNull(users.profileOf("alice"));
    }

    @Test void aDatabaseFromBeforeReplaysMigratesAdditivelyAndKeepsItsHistory() throws Exception {
        database.close();
        Path old = temporary.resolve("before-replays.db");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + old); var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE NOT NULL,"
                    + " password_hash TEXT NOT NULL, wins INTEGER NOT NULL DEFAULT 0, losses INTEGER NOT NULL DEFAULT 0,"
                    + " created_at TEXT NOT NULL)");
            sql.execute("CREATE TABLE matches (id INTEGER PRIMARY KEY AUTOINCREMENT, blue_user_id INTEGER NOT NULL REFERENCES users(id),"
                    + " red_user_id INTEGER NOT NULL REFERENCES users(id), winner_user_id INTEGER REFERENCES users(id),"
                    + " end_reason TEXT NOT NULL, move_count INTEGER NOT NULL, played_at TEXT NOT NULL,"
                    + " rules_version TEXT NOT NULL DEFAULT 'legacy-deckduel-v1', started_turns INTEGER, completed_turns INTEGER)");
            sql.execute("CREATE TABLE match_rounds (match_id INTEGER NOT NULL REFERENCES matches(id), round_number INTEGER NOT NULL,"
                    + " blue_score INTEGER NOT NULL, red_score INTEGER NOT NULL, PRIMARY KEY (match_id, round_number))");
            sql.execute("INSERT INTO users (username, password_hash, wins, losses, created_at) VALUES"
                    + " ('alice', 'x', 4, 1, '2026-10-01'), ('bob', 'y', 1, 4, '2026-10-01')");
            sql.execute("INSERT INTO matches (blue_user_id, red_user_id, winner_user_id, end_reason, move_count, played_at,"
                    + " rules_version, started_turns, completed_turns) VALUES (1, 2, 1, 'SCORE', 0, '2026-10-20T10:00:00',"
                    + " 'cards-neutral-5', 3, 3)");
            sql.execute("INSERT INTO match_rounds VALUES (1, 1, 40, 30), (1, 2, 35, 36), (1, 3, 50, 20)");
        }
        // Opening twice also checks that the migration is idempotent.
        for (int attempt = 0; attempt < 2; attempt++) {
            open(old);
            HistoryEntry legacy = persistence.historyFor("alice", 10).get(0);
            assertEquals(HistoryEntry.Replay.UNAVAILABLE, legacy.replay(), "matches from before replays have none");
            assertEquals(List.of(40L, 35L, 50L), legacy.yourRounds());
            assertEquals(4, users.profileOf("alice").wins());
            assertEquals(4, users.profileOf("bob").losses());
            database.query(connection -> {
                try (var check = connection.prepareStatement("PRAGMA foreign_key_check"); var violations = check.executeQuery()) {
                    assertFalse(violations.next());
                }
                return null;
            });
            if (attempt == 0) database.close();
        }
        persistence.record(result("M9-1"), List.of());
        assertEquals(5, users.profileOf("alice").wins(), "new results still update wins and losses atomically");
    }
}
