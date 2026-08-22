package com.tonikelope.coronapoker;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class RecoverySnapshotCanonicalDomainTest {

    @Test
    public void closedAbortedHandMayHaveNoCryptographicHandId() {
        HashMap<String, Object> map = RecoverySnapshotFixtures.validMap();
        map.put("hand_end", 1234L);
        map.put("hand_id_b64", "");

        RecoverySnapshotV1.Result built = RecoverySnapshotV1.fromMap(map, "session-a");

        assertTrue(built.isOk());
        assertTrue(RecoverySnapshotV1.decode(built.value().encode(), "session-a").isOk());
    }

    @Test
    public void balanceAboveCanonicalTableDomainIsRejected() {
        HashMap<String, Object> map = RecoverySnapshotFixtures.validMap();
        String alice = b64("alice");
        map.put("balance", alice + "|" + (MoneyCents.MAX_CENTS / 100L + 1L) + ".00|100|0");

        RecoverySnapshotV1.Result result = RecoverySnapshotV1.fromMap(map, "session-a");
        assertFalse(result.isOk());
        assertEquals(RecoverySnapshotV1.Error.BAD_MONEY, result.error());
    }

    @Test
    public void blindAboveCanonicalTableDomainIsRejected() {
        HashMap<String, Object> map = RecoverySnapshotFixtures.validMap();
        map.put("bbval", MoneyCents.MAX_CENTS / 100d + 1d);

        RecoverySnapshotV1.Result result = RecoverySnapshotV1.fromMap(map, "session-a");
        assertFalse(result.isOk());
        assertEquals(RecoverySnapshotV1.Error.BAD_MONEY, result.error());
    }

    @Test
    public void rebuyCountAboveCanonicalDomainIsRejected() {
        HashMap<String, Object> map = RecoverySnapshotFixtures.validMap();
        map.put("balance", b64("alice") + "|99.50|100|" + (BuyinCount.MAX_VALUE + 1));

        RecoverySnapshotV1.Result result = RecoverySnapshotV1.fromMap(map, "session-a");
        assertFalse(result.isOk());
        assertEquals(RecoverySnapshotV1.Error.BAD_MONEY, result.error());
    }

    @Test
    public void dealerCanBeSmallBlindHeadsUpButNotMultiway() {
        HashMap<String, Object> headsUp = RecoverySnapshotFixtures.validMap();
        assertTrue(RecoverySnapshotV1.fromMap(headsUp, "session-a").isOk());

        HashMap<String, Object> multiway = RecoverySnapshotFixtures.validMap();
        multiway.put("preflop_players", b64("alice") + "#" + b64("bob") + "#" + b64("charlie"));

        RecoverySnapshotV1.Result result = RecoverySnapshotV1.fromMap(multiway, "session-a");
        assertFalse(result.isOk());
        assertEquals(RecoverySnapshotV1.Error.BAD_VALUE, result.error());
    }

    private static String b64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
