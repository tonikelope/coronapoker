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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Strict, all-or-nothing persistence for normal and aborted hand closes.
 */
public final class HandCloseTransaction {

    private HandCloseTransaction() {
    }

    public static final class BalanceUpdate {

        final String nick;
        final double stack;
        final int buyin;
        final int rebuyCount;

        public BalanceUpdate(String nick, double stack, int buyin, int rebuyCount) {
            if (nick == null || nick.isEmpty()) {
                throw new IllegalArgumentException("nick is required");
            }
            this.nick = nick;
            this.stack = stack;
            this.buyin = buyin;
            this.rebuyCount = rebuyCount;
        }
    }

    public static void close(Connection con, int handId, long end, double pot,
            List<BalanceUpdate> balances) throws SQLException {
        validate(con, handId, balances);
        inTransaction(con, () -> {
            requireExactRoster(con, handId, balances);
            try (PreparedStatement hand = con.prepareStatement(
                    "UPDATE hand SET end=?, pot=? WHERE id=?")) {
                hand.setQueryTimeout(30);
                hand.setLong(1, end);
                hand.setDouble(2, Helpers.doubleClean(pot));
                hand.setInt(3, handId);
                requireOne(hand.executeUpdate(), "hand close", String.valueOf(handId));
            }
            try (PreparedStatement balance = con.prepareStatement(
                    "UPDATE balance SET stack=?, buyin=?, rebuy_count=? WHERE id_hand=? AND player=?")) {
                balance.setQueryTimeout(30);
                for (BalanceUpdate row : balances) {
                    bindBalance(balance, row, handId);
                    requireOne(balance.executeUpdate(), "balance update", row.nick);
                }
            }
            requireExactRoster(con, handId, balances);
        });
    }

    public static void closeAborted(Connection con, int handId, long end,
            List<BalanceUpdate> balances) throws SQLException {
        validate(con, handId, balances);
        inTransaction(con, () -> {
            try (PreparedStatement hand = con.prepareStatement(
                    "UPDATE hand SET end=?, pot=0 WHERE id=?")) {
                hand.setQueryTimeout(30);
                hand.setLong(1, end);
                hand.setInt(2, handId);
                requireOne(hand.executeUpdate(), "aborted hand close", String.valueOf(handId));
            }
            try (PreparedStatement update = con.prepareStatement(
                    "UPDATE balance SET stack=?, buyin=?, rebuy_count=? WHERE id_hand=? AND player=?"); PreparedStatement insert = con.prepareStatement(
                            "INSERT INTO balance(id_hand, player, stack, buyin, rebuy_count) VALUES (?,?,?,?,?)")) {
                update.setQueryTimeout(30);
                insert.setQueryTimeout(30);
                for (BalanceUpdate row : balances) {
                    bindBalance(update, row, handId);
                    int affected = update.executeUpdate();
                    if (affected == 0) {
                        insert.setInt(1, handId);
                        insert.setString(2, row.nick);
                        insert.setDouble(3, Helpers.doubleClean(row.stack));
                        insert.setInt(4, row.buyin);
                        insert.setInt(5, row.rebuyCount);
                        requireOne(insert.executeUpdate(), "balance insert", row.nick);
                    } else {
                        requireOne(affected, "balance update", row.nick);
                    }
                }
            }
            requireExactRoster(con, handId, balances);
        });
    }

    private static void bindBalance(PreparedStatement statement, BalanceUpdate row,
            int handId) throws SQLException {
        statement.setDouble(1, Helpers.doubleClean(row.stack));
        statement.setInt(2, row.buyin);
        statement.setInt(3, row.rebuyCount);
        statement.setInt(4, handId);
        statement.setString(5, row.nick);
    }

    private static void validate(Connection con, int handId, List<BalanceUpdate> balances) {
        if (con == null || handId <= 0 || balances == null || balances.isEmpty()) {
            throw new IllegalArgumentException("connection, positive hand id and balances are required");
        }
        Set<String> nicks = new HashSet<>();
        for (BalanceUpdate row : balances) {
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

    private static void requireExactRoster(Connection con, int handId,
            List<BalanceUpdate> balances) throws SQLException {
        Set<String> expected = new HashSet<>();
        for (BalanceUpdate row : balances) {
            expected.add(row.nick);
        }
        Set<String> actual = new HashSet<>();
        try (PreparedStatement statement = con.prepareStatement(
                "SELECT player FROM balance WHERE id_hand=?")) {
            statement.setQueryTimeout(30);
            statement.setInt(1, handId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String nick = rs.getString(1);
                    if (nick == null || !actual.add(nick)) {
                        throw new SQLException("duplicate or null balance row for hand " + handId);
                    }
                }
            }
        }
        if (!actual.equals(expected)) {
            throw new SQLException("balance roster mismatch for hand " + handId
                    + ": expected=" + expected + ", actual=" + actual);
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
