/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.tonikelope.coronapoker;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Atomic persistence boundary for a new hand and its recovery balance snapshot.
 */
public final class HandCreateTransaction {

    private HandCreateTransaction() {
    }

    public static final class HandRow {

        final int gameId;
        final int counter;
        final double smallBlind;
        final int blindsDouble;
        final String dealer;
        final String smallBlindNick;
        final String bigBlindNick;
        final long start;
        final String preflopPlayers;

        public HandRow(int gameId, int counter, double smallBlind, int blindsDouble,
                String dealer, String smallBlindNick, String bigBlindNick, long start,
                String preflopPlayers) {
            if (gameId <= 0 || counter <= 0 || !Double.isFinite(smallBlind)
                    || smallBlind < 0d || start <= 0L || dealer == null
                    || smallBlindNick == null || bigBlindNick == null
                    || preflopPlayers == null) {
                throw new IllegalArgumentException("invalid hand row");
            }
            this.gameId = gameId;
            this.counter = counter;
            this.smallBlind = smallBlind;
            this.blindsDouble = blindsDouble;
            this.dealer = dealer;
            this.smallBlindNick = smallBlindNick;
            this.bigBlindNick = bigBlindNick;
            this.start = start;
            this.preflopPlayers = preflopPlayers;
        }
    }

    public static final class BalanceRow {

        final String nick;
        final double stack;
        final int buyin;
        final int rebuyCount;

        public BalanceRow(String nick, double stack, int buyin, int rebuyCount) {
            if (nick == null || nick.isEmpty() || !Double.isFinite(stack) || stack < 0d) {
                throw new IllegalArgumentException("invalid balance row");
            }
            this.nick = nick;
            this.stack = stack;
            this.buyin = buyin;
            this.rebuyCount = rebuyCount;
        }
    }

    public static int create(Connection con, HandRow hand, List<BalanceRow> balances)
            throws SQLException {
        validate(con, hand, balances);
        final int[] createdId = {-1};
        inTransaction(con, () -> {
            String sql = "INSERT INTO hand(id_game, counter, sbval, blinds_double, dealer, sb, bb, start, preflop_players) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement statement = con.prepareStatement(
                    sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setQueryTimeout(30);
                statement.setInt(1, hand.gameId);
                statement.setInt(2, hand.counter);
                statement.setDouble(3, Helpers.doubleClean(hand.smallBlind));
                statement.setInt(4, hand.blindsDouble);
                statement.setString(5, hand.dealer);
                statement.setString(6, hand.smallBlindNick);
                statement.setString(7, hand.bigBlindNick);
                statement.setLong(8, hand.start);
                statement.setString(9, hand.preflopPlayers);
                requireOne(statement.executeUpdate(), "hand insert", String.valueOf(hand.counter));
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("hand insert returned no generated key");
                    }
                    long key = keys.getLong(1);
                    if (key <= 0L || key > Integer.MAX_VALUE || keys.next()) {
                        throw new SQLException("hand insert returned an invalid generated key: " + key);
                    }
                    createdId[0] = (int) key;
                }
            }

            try (PreparedStatement statement = con.prepareStatement(
                    "INSERT INTO balance(id_hand, player, stack, buyin, rebuy_count) VALUES (?,?,?,?,?)")) {
                statement.setQueryTimeout(30);
                for (BalanceRow row : balances) {
                    statement.setInt(1, createdId[0]);
                    statement.setString(2, row.nick);
                    statement.setDouble(3, Helpers.doubleClean(row.stack));
                    statement.setInt(4, row.buyin);
                    statement.setInt(5, row.rebuyCount);
                    requireOne(statement.executeUpdate(), "balance insert", row.nick);
                }
            }
        });
        return createdId[0];
    }

    /** Enforces one balance per player/hand without rewriting invalid data. */
    public static void ensureUniqueBalanceRows(Connection con) throws SQLException {
        if (con == null) {
            throw new IllegalArgumentException("connection is required");
        }
        inTransaction(con, () -> {
            try (Statement statement = con.createStatement(); ResultSet rs = statement.executeQuery(
                    "SELECT 1 FROM balance WHERE player IS NULL OR id_hand IS NULL LIMIT 1")) {
                if (rs.next()) {
                    throw new SQLException("balance rows require non-null hand/player identity");
                }
            }
            try (Statement statement = con.createStatement()) {
                statement.setQueryTimeout(30);
                statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_balance_hand_player "
                        + "ON balance(id_hand, player)");
            }
        });
    }

    private static void validate(Connection con, HandRow hand, List<BalanceRow> balances) {
        if (con == null || hand == null || balances == null || balances.isEmpty()) {
            throw new IllegalArgumentException("connection, hand and non-empty balances are required");
        }
        Set<String> nicks = new HashSet<>();
        for (BalanceRow row : balances) {
            if (row == null || !nicks.add(row.nick)) {
                throw new IllegalArgumentException("null or duplicate balance row");
            }
        }
    }

    private static void requireOne(int affected, String operation, String target)
            throws SQLException {
        if (affected != 1) {
            throw new SQLException(operation + " for " + target
                    + " affected " + affected + " rows; expected exactly 1");
        }
    }

    private static void inTransaction(Connection con, SqlWork work) throws SQLException {
        boolean originalAutoCommit = con.getAutoCommit();
        Savepoint savepoint = null;
        if (originalAutoCommit) {
            con.setAutoCommit(false);
        } else {
            savepoint = con.setSavepoint();
        }
        try {
            work.run();
            if (originalAutoCommit) {
                con.commit();
            } else {
                con.releaseSavepoint(savepoint);
            }
        } catch (SQLException | RuntimeException ex) {
            try {
                if (originalAutoCommit) {
                    con.rollback();
                } else {
                    con.rollback(savepoint);
                }
            } catch (SQLException rollbackFailure) {
                ex.addSuppressed(rollbackFailure);
            }
            throw ex;
        } finally {
            if (originalAutoCommit) {
                con.setAutoCommit(true);
            }
        }
    }

    @FunctionalInterface
    private interface SqlWork {

        void run() throws SQLException;
    }
}
