package onitama.db;

import onitama.core.PlayerColor;
import onitama.core.WinCondition;
import onitama.server.MatchResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite implementation of {@link MatchDao}. {@link #recordMatch} runs inside
 * a caller transaction together with the two users' stat updates — a crash
 * between the match insert and an Elo update would corrupt the ratings, so
 * both happen atomically.
 */
public final class SqliteMatchDao implements MatchDao {

    private final Database database;
    private final SqliteUserDao userDao;

    /** Creates the DAO; needs the user DAO for id lookups and stat updates. */
    public SqliteMatchDao(Database database, SqliteUserDao userDao) {
        this.database = database;
        this.userDao = userDao;
    }

    @Override
    public long recordMatch(Connection connection, MatchResult result)
            throws SQLException {
        Long blueId = requireUser(connection, result.blueUsername());
        Long redId = requireUser(connection, result.redUsername());
        Long winnerId = result.winnerUsername() == null
                ? null : requireUser(connection, result.winnerUsername());

        long matchId;
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO matches (blue_user_id, red_user_id, winner_user_id, end_reason, "
                        + "move_count, elo_blue_after, elo_red_after, played_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            insert.setLong(1, blueId);
            insert.setLong(2, redId);
            if (winnerId == null) {
                insert.setNull(3, java.sql.Types.INTEGER);
            } else {
                insert.setLong(3, winnerId);
            }
            insert.setString(4, result.way().name());
            insert.setInt(5, result.moveCount());
            insert.setInt(6, result.blueEloAfter());
            insert.setInt(7, result.redEloAfter());
            insert.setString(8, java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                keys.next();
                matchId = keys.getLong(1);
            }
        }

        // The users table is the rating authority; these updates belong to the
        // same transaction as the match row above.
        updateUserStats(connection, result, PlayerColor.BLUE);
        updateUserStats(connection, result, PlayerColor.RED);
        return matchId;
    }

    private void updateUserStats(Connection connection, MatchResult result, PlayerColor color)
            throws SQLException {
        String username = color == PlayerColor.BLUE ? result.blueUsername() : result.redUsername();
        onitama.net.UserProfile profile = userDao.profileOf(username);
        int newElo = color == PlayerColor.BLUE ? result.blueEloAfter() : result.redEloAfter();
        if (result.way() == WinCondition.DRAW) {
            // A draw moves Elo only: with no winner, counting !won would add
            // a loss to BOTH players.
            userDao.updateStatsInside(connection, username, newElo,
                    profile.wins(), profile.losses());
            return;
        }
        boolean won = result.winnerColor() == color;
        userDao.updateStatsInside(connection, username, newElo,
                profile.wins() + (won ? 1 : 0), profile.losses() + (won ? 0 : 1));
    }

    @Override
    public List<MatchRecord> recentMatches(int limit) throws SQLException {
        return database.query(connection -> {
            List<MatchRecord> records = new ArrayList<>();
            try (PreparedStatement select = connection.prepareStatement("""
                    SELECT m.id, ub.username AS blue, ur.username AS red, uw.username AS winner,
                           m.end_reason, m.move_count, m.elo_blue_after, m.elo_red_after,
                           m.played_at
                    FROM matches m
                    JOIN users ub ON ub.id = m.blue_user_id
                    JOIN users ur ON ur.id = m.red_user_id
                    LEFT JOIN users uw ON uw.id = m.winner_user_id
                    ORDER BY m.played_at DESC, m.id DESC
                    LIMIT ?""")) {
                select.setInt(1, limit);
                try (ResultSet row = select.executeQuery()) {
                    while (row.next()) {
                        records.add(new MatchRecord(row.getLong("id"), row.getString("blue"),
                                row.getString("red"), row.getString("winner"),
                                row.getString("end_reason"), row.getInt("move_count"),
                                row.getInt("elo_blue_after"), row.getInt("elo_red_after"),
                                row.getString("played_at")));
                    }
                }
            }
            return records;
        });
    }

    private long requireUser(Connection connection, String username) throws SQLException {
        Long id = userDao.userIdOf(username, connection);
        if (id == null) {
            // Fails the whole transaction (foreign keys would also reject it).
            throw new SQLException("no such user: " + username);
        }
        return id;
    }
}
