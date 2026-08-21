package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Regression model for a newcomer observing an already-open recovered hand. */
class RecoveredHandRosterIsolationTest {

    @Test
    void immutableHandRosterExcludesRecoveryLobbyNewcomerFromOldLedger() {
        Map<String, Integer> liveTable = new LinkedHashMap<>();
        liveTable.put("server", 1000);
        liveTable.put("client1", 1000);
        liveTable.put("CoronaBot$1", 1000);
        liveTable.put("client2", 1000);

        Set<String> recoveredHandRoster = Set.of("server", "client1", "CoronaBot$1");
        Map<String, Integer> closingLedger = HandBalanceRoster.selectExact(
                recoveredHandRoster, liveTable);

        assertEquals(recoveredHandRoster, closingLedger.keySet());
        assertEquals(3000, closingLedger.values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void missingOriginalPlayerFailsInsteadOfClosingPartialLedger() {
        assertThrows(IllegalArgumentException.class,
                () -> HandBalanceRoster.selectExact(Set.of("server", "client1"),
                        Map.of("server", 1000, "newcomer", 1000)));
    }
}
