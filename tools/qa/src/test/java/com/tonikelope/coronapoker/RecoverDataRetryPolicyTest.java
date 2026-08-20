package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

public class RecoverDataRetryPolicyTest {
    @Test
    public void invalidCriticalSnapshotFailsClosedAndIsNotRetriedInPlace() {
        RecoveryReceiveState state = new RecoveryReceiveState("session-a");
        state.rejectMalformedFrame();
        assertFalse(state.shouldRetry());
        assertThrows(IllegalStateException.class, () -> state.acceptBase64("anything"));

        RecoveryReceiveState timedOut = new RecoveryReceiveState("session-a");
        timedOut.rejectTimeout();
        assertFalse(timedOut.shouldRetry());
        assertFalse(timedOut.isSuccess());
    }
}
