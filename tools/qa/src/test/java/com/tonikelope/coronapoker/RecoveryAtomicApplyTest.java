package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;

public class RecoveryAtomicApplyTest {
    @Test
    public void invalidSnapshotLeavesPreviousStateUntouched() {
        java.util.HashMap<String, Object> state = new java.util.HashMap<>();
        state.put("sentinel", "unchanged");
        byte[] truncated = new byte[]{'C', 'P', 'R', 'S', 0, 0, 0, 1};
        RecoverySnapshotV1.Result result = RecoverySnapshotV1.decodeAndApply(truncated, "session-a", state);
        assertFalse(result.isOk());
        assertEquals(java.util.Map.of("sentinel", "unchanged"), state);
    }
}
