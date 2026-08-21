package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class HandCloseTransactionTest {

    @Test
    void triggerFailureRollsBackHandAndEveryBalance() throws Exception {
        try (Connection con = database()) {
            try (Statement st = con.createStatement()) {
                st.execute("CREATE TRIGGER fail_bob BEFORE UPDATE ON balance "
                        + "WHEN OLD.player='bob' BEGIN SELECT RAISE(FAIL, 'bob failed'); END");
            }

            assertThrows(SQLException.class, () -> HandCloseTransaction.close(con, 10, 999L, 42.5d,
                    Arrays.asList(balance("alice", 90d), balance("bob", 110d))));

            assertHand(con, 0L, 5d);
            assertBalance(con, "alice", 100d);
            assertBalance(con, "bob", 100d);
            assertTrue(con.getAutoCommit());
        }
    }

    @Test
    void missingBalanceRowRollsBackInsteadOfSilentlyClosing() throws Exception {
        try (Connection con = database()) {
            assertThrows(SQLException.class, () -> HandCloseTransaction.close(con, 10, 999L, 42.5d,
                    Arrays.asList(balance("alice", 90d), balance("ghost", 10d))));

            assertHand(con, 0L, 5d);
            assertBalance(con, "alice", 100d);
            assertTrue(con.getAutoCommit());
        }
    }

    @Test
    void omittedExistingPlayerRollsBackEntireClose() throws Exception {
        try (Connection con = database()) {
            assertThrows(SQLException.class, () -> HandCloseTransaction.close(con, 10, 999L, 42.5d,
                    Arrays.asList(balance("alice", 90d))));
            assertHand(con, 0L, 5d);
            assertBalance(con, "alice", 100d);
            assertBalance(con, "bob", 100d);
        }
    }

    @Test
    void successfulCloseCommitsExactlyOneHandAndAllBalances() throws Exception {
        try (Connection con = database()) {
            HandCloseTransaction.close(con, 10, 999L, 42.5d,
                    Arrays.asList(balance("alice", 90d), balance("bob", 110d)));

            assertHand(con, 999L, 42.5d);
            assertBalance(con, "alice", 90d);
            assertBalance(con, "bob", 110d);
            assertTrue(con.getAutoCommit());
        }
    }

    @Test
    void missingHandRowDoesNotTouchBalances() throws Exception {
        try (Connection con = database()) {
            assertThrows(SQLException.class, () -> HandCloseTransaction.close(con, 999, 1L, 0d,
                    Arrays.asList(balance("alice", 90d), balance("bob", 110d))));
            assertBalance(con, "alice", 100d);
            assertBalance(con, "bob", 100d);
            assertTrue(con.getAutoCommit());
        }
    }

    @Test
    void abortedCloseInsertFailureLeavesHandOpenAndNoNewBalances() throws Exception {
        try (Connection con = database()) {
            try (Statement st = con.createStatement()) {
                st.execute("CREATE TRIGGER fail_bob_abort BEFORE UPDATE ON balance "
                        + "WHEN OLD.player='bob' BEGIN SELECT RAISE(ABORT, 'bob failed'); END");
            }

            assertThrows(SQLException.class, () -> HandCloseTransaction.closeAborted(con, 10, 999L,
                    Arrays.asList(balance("alice", 100d), balance("bob", 100d))));

            assertHand(con, 0L, 5d);
            assertBalance(con, "alice", 100d);
            assertBalance(con, "bob", 100d);
            assertTrue(con.getAutoCommit());
        }
    }

    @Test
    void successfulAbortedCloseUpdatesWithoutDuplicateRows() throws Exception {
        try (Connection con = database()) {
            HandCloseTransaction.closeAborted(con, 10, 999L,
                    Arrays.asList(balance("alice", 95d), balance("bob", 105d)));
            assertHand(con, 999L, 0d);
            assertBalance(con, "alice", 95d);
            assertBalance(con, "bob", 105d);
            try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM balance WHERE id_hand=10")) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1));
            }
        }
    }

    private static HandCloseTransaction.BalanceUpdate balance(String nick, double stack) {
        return new HandCloseTransaction.BalanceUpdate(nick, stack, 200, 0);
    }

    private static Connection database() throws Exception {
        Class.forName("org.sqlite.JDBC");
        Connection con = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement st = con.createStatement()) {
            st.execute("CREATE TABLE hand(id INTEGER PRIMARY KEY, end INTEGER, pot REAL)");
            st.execute("CREATE TABLE balance(id_hand INTEGER, player TEXT, stack REAL, buyin INTEGER, rebuy_count INTEGER)");
            st.execute("INSERT INTO hand VALUES(10, 0, 5.0)");
            st.execute("INSERT INTO balance VALUES(10, 'alice', 100.0, 200, 0)");
            st.execute("INSERT INTO balance VALUES(10, 'bob', 100.0, 200, 0)");
        }
        return con;
    }

    private static void assertHand(Connection con, long end, double pot) throws Exception {
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT end, pot FROM hand WHERE id=10")) {
            assertTrue(rs.next());
            assertEquals(end, rs.getLong(1));
            assertEquals(pot, rs.getDouble(2));
        }
    }

    private static void assertBalance(Connection con, String nick, double stack) throws Exception {
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT stack FROM balance WHERE id_hand=10 AND player='" + nick + "'")) {
            assertTrue(rs.next());
            assertEquals(stack, rs.getDouble(1));
        }
    }
}
