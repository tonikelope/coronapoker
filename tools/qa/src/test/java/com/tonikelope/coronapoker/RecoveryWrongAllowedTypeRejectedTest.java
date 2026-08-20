package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;

public class RecoveryWrongAllowedTypeRejectedTest {
    @Test
    public void numericWrapperSubstitutionIsRejectedBeforeStateExists() {
        java.util.HashMap<String, Object> map = RecoverySnapshotFixtures.validMap();
        map.put("buyin", 100L);
        assertFalse(RecoverySnapshotV1.fromMap(map, "session-a").isOk());
    }
}
