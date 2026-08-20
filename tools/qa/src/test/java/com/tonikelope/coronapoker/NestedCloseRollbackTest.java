package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

public class NestedCloseRollbackTest {
    @Test
    public void nestedFailureRollsBackOnlyCloseAndLeavesHandOpen() throws Exception {
        Class.forName("org.sqlite.JDBC");
        try (Connection con = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement st = con.createStatement()) {
            st.execute("CREATE TABLE hand(id INTEGER PRIMARY KEY, end INTEGER, pot REAL)");
            st.execute("CREATE TABLE balance(id_hand INTEGER, player TEXT, stack REAL, buyin INTEGER, rebuy_count INTEGER)");
            st.execute("CREATE TABLE marker(value TEXT)");
            st.execute("INSERT INTO hand VALUES(10, 0, 5.0)");
            st.execute("INSERT INTO balance VALUES(10, 'alice', 100.0, 200, 0)");
            st.execute("INSERT INTO balance VALUES(10, 'bob', 100.0, 200, 0)");
            st.execute("CREATE TRIGGER fail_bob BEFORE UPDATE ON balance WHEN OLD.player='bob' "
                    + "BEGIN SELECT RAISE(FAIL, 'bob failed'); END");

            con.setAutoCommit(false);
            st.execute("INSERT INTO marker VALUES('outer')");
            assertThrows(SQLException.class, () -> HandCloseTransaction.close(con, 10, 999L,
                    HandPotCents.fromDouble(42.5d),
                    Arrays.asList(balance("alice", 90d), balance("bob", 110d))));

            assertFalse(con.getAutoCommit());
            assertEquals(1, scalar(st, "SELECT COUNT(*) FROM marker"));
            assertEquals(0, scalar(st, "SELECT end FROM hand WHERE id=10"));
            assertEquals(100, scalar(st, "SELECT stack FROM balance WHERE player='alice'"));
        }
    }

    private static HandCloseTransaction.BalanceUpdate balance(String nick, double stack) {
        return new HandCloseTransaction.BalanceUpdate(nick, MoneyCents.fromDouble(stack),
                MoneyCents.fromDouble(200d), BuyinCount.of(0));
    }

    private static int scalar(Statement st, String sql) throws Exception {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
