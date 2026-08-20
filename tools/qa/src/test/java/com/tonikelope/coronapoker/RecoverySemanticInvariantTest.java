package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;

public class RecoverySemanticInvariantTest {
    @Test
    public void bigBlindBelowSmallBlindIsRejected() {
        java.util.HashMap<String, Object> map = RecoverySnapshotFixtures.validMap();
        map.put("bbval", 0.25d);
        assertFalse(RecoverySnapshotV1.fromMap(map, "session-a").isOk());
    }
}
