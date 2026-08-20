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

class HandCreateTransactionTest {

    @Test
    void secondBalanceFailureLeavesPreviousHandAsLatest() throws Exception {
        try (Connection con = database()) {
            try (Statement st = con.createStatement()) {
                st.execute("INSERT INTO hand(id,id_game,counter) VALUES(7,1,7)");
                st.execute("CREATE TRIGGER fail_bob BEFORE INSERT ON balance "
                        + "WHEN NEW.player='bob' BEGIN SELECT RAISE(ABORT, 'bob failed'); END");
            }

            assertThrows(SQLException.class, () -> create(con));
            assertEquals(7, scalarInt(con, "SELECT MAX(id) FROM hand"));
            assertEquals(0, scalarInt(con, "SELECT COUNT(*) FROM balance"));
            assertTrue(con.getAutoCommit());
        }
    }

    @Test
    void ignoredBalanceInsertIsTreatedAsFailureAndRolledBack() throws Exception {
        try (Connection con = database()) {
            try (Statement st = con.createStatement()) {
                st.execute("CREATE TRIGGER ignore_bob BEFORE INSERT ON balance "
                        + "WHEN NEW.player='bob' BEGIN SELECT RAISE(IGNORE); END");
            }
            assertThrows(SQLException.class, () -> create(con));
            assertEquals(0, scalarInt(con, "SELECT COUNT(*) FROM hand"));
            assertEquals(0, scalarInt(con, "SELECT COUNT(*) FROM balance"));
            assertTrue(con.getAutoCommit());
        }
    }

    @Test
    void successReturnsCommittedIdWithCompleteRoster() throws Exception {
        try (Connection con = database()) {
            int id = create(con);
            assertTrue(id > 0);
            assertEquals(id, scalarInt(con, "SELECT MAX(id) FROM hand"));
            assertEquals(2, scalarInt(con, "SELECT COUNT(*) FROM balance WHERE id_hand=" + id));
            assertTrue(con.getAutoCommit());
        }
    }

    @Test
    void duplicateNickIsRejectedBeforeAnyWrite() throws Exception {
        try (Connection con = database()) {
            HandCreateTransaction.HandRow hand = new HandCreateTransaction.HandRow(
                    1, 8, 0.5d, 0, "dealer", "sb", "bb", 123L, "alice");
            assertThrows(IllegalArgumentException.class, () -> HandCreateTransaction.create(con, hand,
                    Arrays.asList(
                            new HandCreateTransaction.BalanceRow("alice", 100d, 100, 0),
                            new HandCreateTransaction.BalanceRow("alice", 100d, 100, 0))));
            assertEquals(0, scalarInt(con, "SELECT COUNT(*) FROM hand"));
            assertEquals(0, scalarInt(con, "SELECT COUNT(*) FROM balance"));
        }
    }

    @Test
    void nestedTransactionUsesSavepointWithoutCommittingCaller() throws Exception {
        try (Connection con = database()) {
            con.setAutoCommit(false);
            int id = create(con);
            assertTrue(id > 0);
            assertTrue(!con.getAutoCommit());
            con.rollback();
            assertEquals(0, scalarInt(con, "SELECT COUNT(*) FROM hand"));
            assertEquals(0, scalarInt(con, "SELECT COUNT(*) FROM balance"));
            con.setAutoCommit(true);
        }
    }

    @Test
    void uniquenessGateRejectsDuplicateBalancesWithoutDeletingRows() throws Exception {
        try (Connection con = database()) {
            try (Statement st = con.createStatement()) {
                st.execute("INSERT INTO hand(id,id_game,counter) VALUES(7,1,7)");
                st.execute("INSERT INTO balance(id,id_hand,player,stack,buyin,rebuy_count) VALUES(1,7,'alice',90,100,0)");
                st.execute("INSERT INTO balance(id,id_hand,player,stack,buyin,rebuy_count) VALUES(2,7,'alice',95,100,0)");
            }
            assertThrows(SQLException.class,
                    () -> HandCreateTransaction.ensureUniqueBalanceRows(con));
            assertEquals(2, scalarInt(con, "SELECT COUNT(*) FROM balance WHERE id_hand=7 AND player='alice'"));
            assertTrue(con.getAutoCommit());
        }
    }

    @Test
    void uniquenessGateRejectsNullIdentityWithoutDeletingRows() throws Exception {
        try (Connection con = database()) {
            try (Statement st = con.createStatement()) {
                st.execute("INSERT INTO hand(id,id_game,counter) VALUES(7,1,7)");
                st.execute("INSERT INTO balance(id,id_hand,player,stack,buyin,rebuy_count) VALUES(1,7,NULL,90,100,0)");
            }
            assertThrows(SQLException.class,
                    () -> HandCreateTransaction.ensureUniqueBalanceRows(con));
            assertEquals(1, scalarInt(con, "SELECT COUNT(*) FROM balance"));
            assertTrue(con.getAutoCommit());
        }
    }

    private static int create(Connection con) throws Exception {
        HandCreateTransaction.HandRow hand = new HandCreateTransaction.HandRow(
                1, 8, 0.5d, 0, "dealer", "sb", "bb", 123L, "alice#bob");
        return HandCreateTransaction.create(con, hand, Arrays.asList(
                new HandCreateTransaction.BalanceRow("alice", 100d, 100, 0),
                new HandCreateTransaction.BalanceRow("bob", 100d, 100, 0)));
    }

    private static Connection database() throws Exception {
        Class.forName("org.sqlite.JDBC");
        Connection con = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement st = con.createStatement()) {
            st.execute("CREATE TABLE hand(id INTEGER PRIMARY KEY, id_game INTEGER, counter INTEGER, sbval REAL, blinds_double INTEGER, dealer TEXT, sb TEXT, bb TEXT, start INTEGER, preflop_players TEXT)");
            st.execute("CREATE TABLE balance(id INTEGER PRIMARY KEY, id_hand INTEGER, player TEXT, stack REAL, buyin INTEGER, rebuy_count INTEGER)");
        }
        return con;
    }

    private static int scalarInt(Connection con, String sql) throws Exception {
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }

    private static double scalarDouble(Connection con, String sql) throws Exception {
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next());
            return rs.getDouble(1);
        }
    }
}
