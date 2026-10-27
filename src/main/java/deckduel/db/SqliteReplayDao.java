package deckduel.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** SQLite implementation of {@link ReplayDao}; owner checks are part of every query. */
public final class SqliteReplayDao implements ReplayDao {
    private static final String COLUMNS = "id, match_key, opponent, practice, rules_version, played_at, finished, "
            + "complete, result, end_reason, your_rounds, opponent_rounds";
    private static final String OWNER = "owner_user_id = (SELECT id FROM users WHERE username = ?)";

    private final Database database;

    /** Creates the DAO over the shared database. */
    public SqliteReplayDao(Database database) { this.database = database; }

    @Override
    public long create(String owner, String matchKey, String opponent, boolean practice, String rulesVersion)
            throws SQLException {
        return database.query(connection -> {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO replays (owner_user_id, match_key, opponent, practice, rules_version, played_at) "
                            + "VALUES ((SELECT id FROM users WHERE username = ?), ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                insert.setString(1, owner);
                insert.setString(2, matchKey);
                insert.setString(3, opponent);
                insert.setInt(4, practice ? 1 : 0);
                insert.setString(5, rulesVersion);
                insert.setString(6, now());
                insert.executeUpdate();
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    keys.next();
                    return keys.getLong(1);
                }
            }
        });
    }

    @Override
    public boolean addFrame(long replayId, int revision, byte[] view) throws SQLException {
        return database.query(connection -> {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT OR IGNORE INTO replay_frames (replay_id, revision, view) VALUES (?, ?, ?)")) {
                insert.setLong(1, replayId);
                insert.setInt(2, revision);
                insert.setBytes(3, view);
                return insert.executeUpdate() == 1;
            }
        });
    }

    @Override
    public void finish(long replayId, String result, String endReason, List<Long> yourRounds,
                       List<Long> opponentRounds, boolean complete) throws SQLException {
        database.query(connection -> {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE replays SET finished = 1, complete = ?, result = ?, end_reason = ?, your_rounds = ?, "
                            + "opponent_rounds = ?, played_at = ? WHERE id = ?")) {
                update.setInt(1, complete ? 1 : 0);
                update.setString(2, result);
                update.setString(3, endReason);
                update.setString(4, join(yourRounds));
                update.setString(5, join(opponentRounds));
                update.setString(6, now());
                update.setLong(7, replayId);
                update.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public ReplayRecord find(String owner, long replayId) throws SQLException {
        List<ReplayRecord> found = select("WHERE id = ? AND " + OWNER, 1, statement -> {
            statement.setLong(1, replayId);
            statement.setString(2, owner);
        });
        return found.isEmpty() ? null : found.get(0);
    }

    @Override
    public ReplayRecord findByMatch(String owner, String matchKey) throws SQLException {
        List<ReplayRecord> found = select("WHERE match_key = ? AND " + OWNER, 1, statement -> {
            statement.setString(1, matchKey);
            statement.setString(2, owner);
        });
        return found.isEmpty() ? null : found.get(0);
    }

    @Override
    public List<ReplayRecord> finishedPractice(String owner, int limit) throws SQLException {
        return select("WHERE practice = 1 AND finished = 1 AND " + OWNER + " ORDER BY played_at DESC, id DESC",
                limit, statement -> statement.setString(1, owner));
    }

    @Override
    public List<byte[]> frames(long replayId) throws SQLException {
        return database.query(connection -> {
            List<byte[]> frames = new ArrayList<>();
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT view FROM replay_frames WHERE replay_id = ? ORDER BY revision")) {
                select.setLong(1, replayId);
                try (ResultSet row = select.executeQuery()) {
                    while (row.next()) frames.add(row.getBytes("view"));
                }
            }
            return frames;
        });
    }

    private interface Binder { void bind(PreparedStatement statement) throws SQLException; }

    private List<ReplayRecord> select(String where, int limit, Binder binder) throws SQLException {
        return database.query(connection -> {
            List<ReplayRecord> records = new ArrayList<>();
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT " + COLUMNS + " FROM replays " + where + " LIMIT " + Math.max(1, limit))) {
                binder.bind(select);
                try (ResultSet row = select.executeQuery()) {
                    while (row.next()) records.add(record(row));
                }
            }
            return records;
        });
    }

    private static ReplayRecord record(ResultSet row) throws SQLException {
        return new ReplayRecord(row.getLong("id"), row.getString("match_key"), row.getString("opponent"),
                row.getInt("practice") == 1, row.getString("rules_version"), row.getString("played_at"),
                row.getInt("finished") == 1, row.getInt("complete") == 1, row.getString("result"),
                row.getString("end_reason"), split(row.getString("your_rounds")), split(row.getString("opponent_rounds")));
    }

    private static String join(List<Long> scores) {
        return String.join(",", scores.stream().map(String::valueOf).toList());
    }

    private static List<Long> split(String scores) {
        if (scores == null || scores.isBlank()) return List.of();
        return Arrays.stream(scores.split(",")).map(Long::valueOf).toList();
    }

    private static String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
