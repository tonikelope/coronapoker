package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

/**
 * Recovery must not confuse the row count with the ordinal printed on the table and in the game
 * log.  A hand interrupted before showdown is replayed at its persisted ordinal; a hand already
 * shown at showdown is skipped and the next hand must use that ordinal plus one.
 */
class RecoveryHandCounterTest {

    @Test
    void anOpenRecoveredHandKeepsItsPersistedOrdinal() {
        assertEquals(7, Crupier.handCounterForRecovery(7, true));
    }

    @Test
    void aShownHandStartsTheFollowingOrdinalAfterRecovery() {
        assertEquals(8, Crupier.handCounterForRecovery(7, false));
    }

    @Test
    void aGameWithoutAnyPersistedHandStartsAtOne() {
        assertEquals(1, Crupier.handCounterForRecovery(0, false));
    }

    @Test
    void recoveryQueryReadsTheLatestHandsDurableOrdinalNotItsRowCount() throws Exception {
        Class.forName("org.sqlite.JDBC");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE game (id INTEGER PRIMARY KEY, server TEXT, start INTEGER, buyin INTEGER, rebuy BOOLEAN, play_time INTEGER, blinds_time INTEGER, blinds_time_type INTEGER)");
            statement.execute("CREATE TABLE hand (id INTEGER PRIMARY KEY, id_game INTEGER, counter INTEGER, end INTEGER, preflop_players TEXT, hand_id_b64 TEXT, sbval REAL, blinds_double INTEGER, dealer TEXT, sb TEXT, bb TEXT)");
            statement.execute("INSERT INTO game VALUES (42, 'server', 1, 100, 1, 0, 0, 0)");
            statement.execute("INSERT INTO hand VALUES (100, 42, 6, 1, '', NULL, 0.1, 0, 'd', 's', 'b')");
            statement.execute("INSERT INTO hand VALUES (101, 42, 7, 0, '', NULL, 0.1, 0, 'd', 's', 'b')");

            try (PreparedStatement query = connection.prepareStatement(Crupier.RECOVERY_GAME_KEY_DATA_SQL)) {
                Crupier.bindRecoveryGameKeyDataQuery(query, 42);
                try (ResultSet result = query.executeQuery()) {
                    org.junit.jupiter.api.Assertions.assertTrue(result.next());
                    assertEquals(7, result.getInt("conta_mano"));
                    assertEquals(101, result.getInt("hand_id"));
                }
            }
        }
    }
}
