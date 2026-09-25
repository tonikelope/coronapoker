package com.tonikelope.coronapoker.core;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Renderer-neutral access to the persisted game history.
 *
 * <p>The classic statistics window historically owns these queries together
 * with Swing widgets.  GDX must read the same authoritative SQLite rows
 * without constructing Swing, duplicating poker rules or opening a second
 * connection.  This repository therefore uses the process-owned
 * {@link DatabaseService} and its shared transaction lock.</p>
 */
public final class StatsRepository {

    private final DatabaseService database;

    public StatsRepository(DatabaseService database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    public List<GameSummary> games() throws SQLException {
        String sql = "SELECT g.*, (SELECT COUNT(*) FROM hand h "
                + "WHERE h.id_game=g.id AND h.end IS NOT NULL) AS tot_hands "
                + "FROM game g ORDER BY g.start DESC";
        synchronized (database.lock()) {
            try (Statement statement = database.connection().createStatement()) {
                statement.setQueryTimeout(30);
                try (ResultSet rows = statement.executeQuery(sql)) {
                    List<GameSummary> result = new ArrayList<>();
                    while (rows.next()) {
                        result.add(new GameSummary(rows.getInt("id"),
                                rows.getLong("start"), nullableLong(rows, "end"),
                                rows.getLong("play_time"), rows.getString("server"),
                                decodePlayers(rows.getString("players")),
                                rows.getInt("buyin"), rows.getDouble("sb"),
                                rows.getInt("blinds_time"),
                                rows.getInt("blinds_time_type"),
                                rows.getBoolean("rebuy"),
                                rows.getInt("tot_hands"),
                                rows.getBoolean("private"),
                                rows.getBoolean("imported"),
                                rows.getString("imported_from")));
                    }
                    return List.copyOf(result);
                }
            }
        }
    }

    public List<HandSummary> hands(int gameId) throws SQLException {
        String sql = "SELECT * FROM hand WHERE id_game=? AND end IS NOT NULL "
                + "ORDER BY counter DESC, id DESC";
        synchronized (database.lock()) {
            try (PreparedStatement statement = database.connection()
                    .prepareStatement(sql)) {
                statement.setQueryTimeout(30);
                statement.setInt(1, gameId);
                try (ResultSet rows = statement.executeQuery()) {
                    List<HandSummary> result = new ArrayList<>();
                    while (rows.next()) {
                        result.add(new HandSummary(rows.getInt("id"), gameId,
                                rows.getInt("counter"), rows.getDouble("sbval"),
                                rows.getInt("blinds_double"),
                                rows.getString("dealer"), rows.getString("sb"),
                                rows.getString("bb"), rows.getLong("start"),
                                rows.getLong("end"),
                                splitCards(rows.getString("com_cards")),
                                decodePlayers(rows.getString("preflop_players")),
                                decodePlayers(rows.getString("flop_players")),
                                decodePlayers(rows.getString("turn_players")),
                                decodePlayers(rows.getString("river_players")),
                                rows.getDouble("pot")));
                    }
                    return List.copyOf(result);
                }
            }
        }
    }

    /** Balance at all-games, one-game or one-hand scope. */
    public List<BalanceRow> balances(Integer gameId, Integer handId)
            throws SQLException {
        String sql;
        if (handId != null) {
            sql = "SELECT player, stack, buyin FROM balance "
                    + "WHERE id_hand=? ORDER BY (stack-buyin) DESC, player";
        } else if (gameId != null) {
            sql = "SELECT b.player, b.stack, b.buyin FROM balance b "
                    + "JOIN hand h ON h.id=b.id_hand WHERE h.id_game=? "
                    + "AND h.id=(SELECT MAX(h2.id) FROM hand h2 "
                    + "JOIN balance b2 ON b2.id_hand=h2.id "
                    + "WHERE h2.id_game=?) ORDER BY (b.stack-b.buyin) DESC, b.player";
        } else {
            sql = "SELECT player, SUM(stack) AS stack, SUM(buyin) AS buyin "
                    + "FROM balance WHERE id_hand IN (SELECT MAX(h.id) FROM hand h "
                    + "JOIN balance b ON b.id_hand=h.id GROUP BY h.id_game) "
                    + "GROUP BY player ORDER BY (SUM(stack)-SUM(buyin)) DESC, player";
        }
        synchronized (database.lock()) {
            try (PreparedStatement statement = database.connection()
                    .prepareStatement(sql)) {
                statement.setQueryTimeout(30);
                if (handId != null) {
                    statement.setInt(1, handId);
                } else if (gameId != null) {
                    statement.setInt(1, gameId);
                    statement.setInt(2, gameId);
                }
                try (ResultSet rows = statement.executeQuery()) {
                    List<BalanceRow> result = new ArrayList<>();
                    while (rows.next()) {
                        double stack = rows.getDouble("stack");
                        double buyin = rows.getDouble("buyin");
                        double profit = stack - buyin;
                        double roi = buyin == 0d ? 0d : profit * 100d / buyin;
                        result.add(new BalanceRow(rows.getString("player"),
                                stack, buyin, profit, roi));
                    }
                    return List.copyOf(result);
                }
            }
        }
    }

    /**
     * Stack evolution for every player in one game, ordered by hand and seat
     * name.  Keeping this query in the renderer-neutral repository lets the
     * GDX statistics screen reproduce Swing's session graph without importing
     * JFreeChart or any Swing component.
     */
    public List<BalancePoint> balanceHistory(int gameId) throws SQLException {
        String sql = "SELECT b.player,h.counter,b.stack FROM balance b "
                + "JOIN hand h ON h.id=b.id_hand WHERE h.id_game=? "
                + "AND h.end IS NOT NULL ORDER BY h.counter,h.id,b.player";
        synchronized (database.lock()) {
            try (PreparedStatement statement = database.connection()
                    .prepareStatement(sql)) {
                statement.setQueryTimeout(30);
                statement.setInt(1, gameId);
                try (ResultSet rows = statement.executeQuery()) {
                    List<BalancePoint> result = new ArrayList<>();
                    while (rows.next()) {
                        result.add(new BalancePoint(rows.getString("player"),
                                rows.getInt("counter"),
                                rows.getDouble("stack")));
                    }
                    return List.copyOf(result);
                }
            }
        }
    }

    public List<ShowdownRow> showdown(int handId) throws SQLException {
        String sql = "SELECT player,winner,hole_cards,hand_cards,hand_val,pay,profit "
                + "FROM showdown WHERE id_hand=? ORDER BY winner DESC,pay DESC";
        synchronized (database.lock()) {
            try (PreparedStatement statement = database.connection()
                    .prepareStatement(sql)) {
                statement.setQueryTimeout(30);
                statement.setInt(1, handId);
                try (ResultSet rows = statement.executeQuery()) {
                    List<ShowdownRow> result = new ArrayList<>();
                    while (rows.next()) {
                        result.add(new ShowdownRow(rows.getString("player"),
                                rows.getBoolean("winner"),
                                splitCards(rows.getString("hole_cards")),
                                splitCards(rows.getString("hand_cards")),
                                rows.getInt("hand_val"), rows.getDouble("pay"),
                                rows.getDouble("profit")));
                    }
                    return List.copyOf(result);
                }
            }
        }
    }

    public List<MetricRow> averageResponse(Integer gameId, Integer handId)
            throws SQLException {
        String sql = "SELECT a.player, ROUND(AVG(a.response_time),1) AS value "
                + "FROM action a "
                + (gameId == null ? "" : "JOIN hand h ON h.id=a.id_hand ")
                + (handId != null ? "WHERE a.id_hand=? "
                        : gameId != null ? "WHERE h.id_game=? " : "")
                + "GROUP BY a.player ORDER BY value DESC,a.player";
        return metricRows(sql, handId != null ? handId : gameId);
    }

    /** Percentage of hands in which a player bet or raised on a street. */
    public List<MetricRow> raiseFrequency(Integer gameId, int round)
            throws SQLException {
        String gameJoin = gameId == null ? "" : " JOIN hand h ON h.id=a.id_hand";
        String gameWhere = gameId == null ? "" : " AND h.id_game=?";
        String sql = "SELECT a.player, ROUND(100.0*COUNT(DISTINCT CASE "
                + "WHEN a.action>=3 THEN a.id_hand END)/"
                + "NULLIF(COUNT(DISTINCT CASE WHEN a.action>=2 THEN a.id_hand END),0),1) "
                + "AS value FROM action a" + gameJoin
                + " WHERE a.round=?" + gameWhere
                + " GROUP BY a.player ORDER BY value DESC,a.player";
        synchronized (database.lock()) {
            try (PreparedStatement statement = database.connection()
                    .prepareStatement(sql)) {
                statement.setQueryTimeout(30);
                statement.setInt(1, round);
                if (gameId != null) statement.setInt(2, gameId);
                return readMetricRows(statement);
            }
        }
    }

    public List<PerformanceRow> performance(Integer gameId)
            throws SQLException {
        String join = gameId == null ? "" : " JOIN hand h ON h.id=a.id_hand";
        String where = gameId == null ? "" : " WHERE h.id_game=?";
        String sql = "SELECT a.player,COUNT(DISTINCT a.id_hand) AS total,"
                + "COUNT(DISTINCT CASE WHEN a.round=1 AND a.action>=2 "
                + "THEN a.id_hand END) AS played FROM action a" + join
                + where + " GROUP BY a.player ORDER BY a.player";
        Map<String, BalanceRow> balanceByPlayer = new HashMap<>();
        for (BalanceRow row : balances(gameId, null)) {
            balanceByPlayer.put(row.player(), row);
        }
        synchronized (database.lock()) {
            try (PreparedStatement statement = database.connection()
                    .prepareStatement(sql)) {
                statement.setQueryTimeout(30);
                if (gameId != null) statement.setInt(1, gameId);
                try (ResultSet rows = statement.executeQuery()) {
                    List<PerformanceRow> result = new ArrayList<>();
                    while (rows.next()) {
                        String player = rows.getString("player");
                        int total = rows.getInt("total");
                        int played = rows.getInt("played");
                        int won = wonHands(player, gameId);
                        double participation = total == 0 ? 0d
                                : played * 100d / total;
                        double wonPercent = total == 0 ? 0d
                                : won * 100d / total;
                        double precision = played == 0 ? 0d
                                : won * 100d / played;
                        BalanceRow balance = balanceByPlayer.get(player);
                        double roi = balance == null ? 0d
                                : balance.roiPercent();
                        double effectiveness = participation <= 0d ? 0d
                                : (roi / 100d) / Math.sqrt(participation / 100d);
                        result.add(new PerformanceRow(player, participation,
                                wonPercent, precision, roi, effectiveness));
                    }
                    result.sort((left, right) -> Double.compare(
                            right.effectiveness(), left.effectiveness()));
                    return List.copyOf(result);
                }
            }
        }
    }

    private int wonHands(String player, Integer gameId) throws SQLException {
        String sql = "SELECT COUNT(DISTINCT s.id_hand) FROM showdown s"
                + (gameId == null ? "" : " JOIN hand h ON h.id=s.id_hand")
                + " WHERE s.player=? AND s.winner=1"
                + (gameId == null ? "" : " AND h.id_game=?");
        try (PreparedStatement statement = database.connection()
                .prepareStatement(sql)) {
            statement.setQueryTimeout(30);
            statement.setString(1, player);
            if (gameId != null) statement.setInt(2, gameId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? row.getInt(1) : 0;
            }
        }
    }

    public List<BestHandRow> bestHands(Integer gameId) throws SQLException {
        String sql = "SELECT s.player,s.hole_cards,s.hand_cards,s.hand_val,"
                + "h.counter,s.profit,g.server,g.start FROM showdown s "
                + "JOIN hand h ON h.id=s.id_hand JOIN game g ON g.id=h.id_game "
                + "WHERE s.winner=1" + (gameId == null ? "" : " AND g.id=?")
                + " ORDER BY s.hand_val DESC,s.profit DESC LIMIT 1000";
        synchronized (database.lock()) {
            try (PreparedStatement statement = database.connection()
                    .prepareStatement(sql)) {
                statement.setQueryTimeout(30);
                if (gameId != null) statement.setInt(1, gameId);
                try (ResultSet rows = statement.executeQuery()) {
                    List<BestHandRow> result = new ArrayList<>();
                    while (rows.next()) {
                        result.add(new BestHandRow(rows.getString("player"),
                                splitCards(rows.getString("hole_cards")),
                                splitCards(rows.getString("hand_cards")),
                                rows.getInt("hand_val"),
                                rows.getInt("counter"),
                                rows.getDouble("profit"),
                                rows.getString("server"), rows.getLong("start")));
                    }
                    return List.copyOf(result);
                }
            }
        }
    }

    private List<MetricRow> metricRows(String sql, Integer scopeId)
            throws SQLException {
        synchronized (database.lock()) {
            try (PreparedStatement statement = database.connection()
                    .prepareStatement(sql)) {
                statement.setQueryTimeout(30);
                if (scopeId != null) statement.setInt(1, scopeId);
                return readMetricRows(statement);
            }
        }
    }

    private static List<MetricRow> readMetricRows(PreparedStatement statement)
            throws SQLException {
        try (ResultSet rows = statement.executeQuery()) {
            List<MetricRow> result = new ArrayList<>();
            while (rows.next()) {
                result.add(new MetricRow(rows.getString("player"),
                        rows.getDouble("value")));
            }
            return List.copyOf(result);
        }
    }

    public void setPrivate(int gameId, boolean value) throws SQLException {
        synchronized (database.lock()) {
            try (PreparedStatement statement = database.connection()
                    .prepareStatement("UPDATE game SET private=? WHERE id=?")) {
                statement.setQueryTimeout(30);
                statement.setBoolean(1, value);
                statement.setInt(2, gameId);
                statement.executeUpdate();
            }
        }
    }

    /** Applies one privacy state to an explicit, renderer-selected game set. */
    public int setPrivate(List<Integer> gameIds, boolean value)
            throws SQLException {
        if (gameIds == null || gameIds.isEmpty()) return 0;
        String sql = "UPDATE game SET private=? WHERE id IN ("
                + placeholders(gameIds.size()) + ")";
        synchronized (database.lock()) {
            try (PreparedStatement statement = database.connection()
                    .prepareStatement(sql)) {
                statement.setQueryTimeout(30);
                statement.setBoolean(1, value);
                for (int index = 0; index < gameIds.size(); index++) {
                    statement.setInt(index + 2, gameIds.get(index));
                }
                return statement.executeUpdate();
            }
        }
    }

    public void deleteGame(int gameId) throws SQLException {
        synchronized (database.lock()) {
            try (PreparedStatement statement = database.connection()
                    .prepareStatement("DELETE FROM game WHERE id=?")) {
                statement.setQueryTimeout(30);
                statement.setInt(1, gameId);
                statement.executeUpdate();
            }
        }
    }

    /** Deletes only the explicit games selected by a frontend filter. */
    public int deleteGames(List<Integer> gameIds) throws SQLException {
        if (gameIds == null || gameIds.isEmpty()) return 0;
        String sql = "DELETE FROM game WHERE id IN ("
                + placeholders(gameIds.size()) + ")";
        synchronized (database.lock()) {
            try (PreparedStatement statement = database.connection()
                    .prepareStatement(sql)) {
                statement.setQueryTimeout(30);
                for (int index = 0; index < gameIds.size(); index++) {
                    statement.setInt(index + 1, gameIds.get(index));
                }
                return statement.executeUpdate();
            }
        }
    }

    public int deleteImportedGames() throws SQLException {
        synchronized (database.lock()) {
            int games;
            try (Statement statement = database.connection().createStatement();
                    ResultSet rows = statement.executeQuery(
                            "SELECT COUNT(*) FROM game WHERE imported=1")) {
                statement.setQueryTimeout(30);
                games = rows.next() ? rows.getInt(1) : 0;
            }
            try (Statement statement = database.connection().createStatement()) {
                statement.setQueryTimeout(30);
                statement.executeUpdate("DELETE FROM game WHERE imported=1");
            }
            return games;
        }
    }

    static List<String> decodePlayers(String encoded) {
        if (encoded == null || encoded.isBlank()) return List.of();
        List<String> players = new ArrayList<>();
        for (String token : encoded.split("#")) {
            if (token.isBlank()) continue;
            try {
                players.add(new String(Base64.getDecoder().decode(token),
                        StandardCharsets.UTF_8));
            } catch (IllegalArgumentException invalidLegacyValue) {
                players.add(token);
            }
        }
        return List.copyOf(players);
    }

    private static String placeholders(int count) {
        StringBuilder sql = new StringBuilder(Math.max(1, count * 2 - 1));
        for (int index = 0; index < count; index++) {
            if (index > 0) sql.append(',');
            sql.append('?');
        }
        return sql.toString();
    }

    static List<String> splitCards(String encoded) {
        if (encoded == null || encoded.isBlank()) return List.of();
        List<String> cards = new ArrayList<>();
        for (String token : encoded.split("#")) {
            if (!token.isBlank()) cards.add(token);
        }
        return List.copyOf(cards);
    }

    private static Long nullableLong(ResultSet rows, String column)
            throws SQLException {
        long value = rows.getLong(column);
        return rows.wasNull() ? null : value;
    }

    public record GameSummary(int id, long startedAtMillis,
            Long endedAtMillis, long playTimeSeconds, String server,
            List<String> players, int buyin, double smallBlind,
            int blindsTime, int blindsTimeType, boolean rebuy, int hands,
            boolean privateGame, boolean imported, String importedFrom) {

        public GameSummary {
            players = List.copyOf(players == null ? List.of() : players);
        }

        public GameSummary withPrivateGame(boolean value) {
            return new GameSummary(id, startedAtMillis, endedAtMillis,
                    playTimeSeconds, server, players, buyin, smallBlind,
                    blindsTime, blindsTimeType, rebuy, hands, value, imported,
                    importedFrom);
        }
    }

    public record HandSummary(int id, int gameId, int counter,
            double smallBlind, int blindsDouble, String dealer,
            String smallBlindPlayer, String bigBlindPlayer,
            long startedAtMillis, long endedAtMillis,
            List<String> communityCards, List<String> preflopPlayers,
            List<String> flopPlayers, List<String> turnPlayers,
            List<String> riverPlayers, double pot) {

        public HandSummary {
            communityCards = List.copyOf(communityCards == null
                    ? List.of() : communityCards);
            preflopPlayers = List.copyOf(preflopPlayers == null
                    ? List.of() : preflopPlayers);
            flopPlayers = List.copyOf(flopPlayers == null
                    ? List.of() : flopPlayers);
            turnPlayers = List.copyOf(turnPlayers == null
                    ? List.of() : turnPlayers);
            riverPlayers = List.copyOf(riverPlayers == null
                    ? List.of() : riverPlayers);
        }
    }

    public record BalanceRow(String player, double stack, double buyin,
            double profit, double roiPercent) { }

    public record BalancePoint(String player, int handCounter, double stack) { }

    public record MetricRow(String player, double value) { }

    public record PerformanceRow(String player, double playedPercent,
            double wonPercent, double precisionPercent, double roiPercent,
            double effectiveness) { }

    public record BestHandRow(String player, List<String> holeCards,
            List<String> handCards, int handValue, int handCounter,
            double profit, String server, long gameStartedAtMillis) {

        public BestHandRow {
            holeCards = List.copyOf(holeCards == null ? List.of() : holeCards);
            handCards = List.copyOf(handCards == null ? List.of() : handCards);
        }
    }

    public record ShowdownRow(String player, boolean winner,
            List<String> holeCards, List<String> handCards, int handValue,
            double pay, double profit) {

        public ShowdownRow {
            holeCards = List.copyOf(holeCards == null ? List.of() : holeCards);
            handCards = List.copyOf(handCards == null ? List.of() : handCards);
        }
    }
}
