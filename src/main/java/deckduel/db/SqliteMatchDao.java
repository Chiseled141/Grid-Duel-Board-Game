package deckduel.db;

import deckduel.core.PlayerColor;
import deckduel.core.EndReason;
import deckduel.core.card.RoundResult;
import deckduel.server.MatchResult;

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
 * between the match insert and a stat update would corrupt the record, so
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
                        + "move_count, played_at, "
                        + "rules_version, started_turns, completed_turns, match_key) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            insert.setLong(1, blueId);
            insert.setLong(2, redId);
            if (winnerId == null) {
                insert.setNull(3, java.sql.Types.INTEGER);
            } else {
                insert.setLong(3, winnerId);
            }
            insert.setString(4, result.way().name());
            insert.setInt(5, 0); // legacy not-applicable sentinel
            insert.setString(6, java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            insert.setString(7, result.rulesVersion());
            insert.setInt(8, result.startedTurns());
            insert.setInt(9, result.completedTurns());
            insert.setString(10, result.roomCode());
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                keys.next();
                matchId = keys.getLong(1);
            }
        }

        // Win/loss updates belong to the
        // same transaction as the match row above.
        updateUserStats(connection, result, PlayerColor.BLUE);
        updateUserStats(connection, result, PlayerColor.RED);
        return matchId;
    }

    private void updateUserStats(Connection connection, MatchResult result, PlayerColor color)
            throws SQLException {
        String username = color == PlayerColor.BLUE ? result.blueUsername() : result.redUsername();
        deckduel.net.UserProfile profile = userDao.profileOf(username);
        if (result.way() == EndReason.DRAW) {
            // Draws leave both win/loss records unchanged.
            return;
        }
        boolean won = result.winnerColor() == color;
        userDao.updateStatsInside(connection, username,
                profile.wins() + (won ? 1 : 0), profile.losses() + (won ? 0 : 1));
    }

    @Override
    public void recordRounds(Connection connection, long matchId, List<RoundResult> rounds) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO match_rounds (match_id, round_number, blue_score, red_score) VALUES (?, ?, ?, ?)")) {
            for (RoundResult round : rounds) {
                insert.setLong(1, matchId);
                insert.setInt(2, round.round());
                insert.setLong(3, round.firstScore());
                insert.setLong(4, round.secondScore());
                insert.executeUpdate();
            }
        }
    }

    @Override
    public List<RoundResult> roundsOf(long matchId) throws SQLException {
        return database.query(connection -> {
            List<RoundResult> rounds = new ArrayList<>();
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT round_number, blue_score, red_score FROM match_rounds WHERE match_id = ? ORDER BY round_number")) {
                select.setLong(1, matchId);
                try (ResultSet row = select.executeQuery()) {
                    // The starting seat is not stored; history only needs the banked scores.
                    while (row.next()) rounds.add(new RoundResult(row.getInt("round_number"), null,
                            row.getLong("blue_score"), row.getLong("red_score")));
                }
            }
            return rounds;
        });
    }

    @Override
    public List<MatchRecord> recentMatches(int limit) throws SQLException {
        return select("", null, limit);
    }

    @Override
    public List<MatchRecord> recentMatchesFor(String username, int limit) throws SQLException {
        return select("WHERE ub.username = ? OR ur.username = ? ", username, limit);
    }

    /** Runs the shared history query, optionally filtered to one player. */
    private List<MatchRecord> select(String filter, String username, int limit) throws SQLException {
        return database.query(connection -> {
            List<MatchRecord> records = new ArrayList<>();
            try (PreparedStatement select = connection.prepareStatement("""
                    SELECT m.id, ub.username AS blue, ur.username AS red, uw.username AS winner,
                           m.end_reason, m.move_count,
                           m.played_at, m.rules_version, m.started_turns, m.completed_turns, m.match_key
                    FROM matches m
                    JOIN users ub ON ub.id = m.blue_user_id
                    JOIN users ur ON ur.id = m.red_user_id
                    LEFT JOIN users uw ON uw.id = m.winner_user_id
                    """ + filter + "ORDER BY m.played_at DESC, m.id DESC LIMIT ?")) {
                int index = 1;
                if (username != null) {
                    select.setString(index++, username);
                    select.setString(index++, username);
                }
                select.setInt(index, limit);
                try (ResultSet row = select.executeQuery()) {
                    while (row.next()) {
                        Integer startedTurns = row.getObject("started_turns") != null
                                ? row.getInt("started_turns") : null;
                        Integer completedTurns = row.getObject("completed_turns") != null
                                ? row.getInt("completed_turns") : null;
                        records.add(new MatchRecord(row.getLong("id"), row.getString("blue"),
                                row.getString("red"), row.getString("winner"),
                                row.getString("end_reason"), row.getInt("move_count"),
                                row.getString("played_at"), row.getString("rules_version"),
                                startedTurns, completedTurns, row.getString("match_key")));
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
