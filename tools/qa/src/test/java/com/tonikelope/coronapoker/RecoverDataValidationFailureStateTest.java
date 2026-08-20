package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import java.util.Base64;
import org.junit.jupiter.api.Test;

public class RecoverDataValidationFailureStateTest {
    @Test
    public void invalidSnapshotEndsInExplicitFailure() {
        RecoverySnapshotV1.Result built = RecoverySnapshotV1.fromMap(
                RecoverySnapshotFixtures.validMap(), "session-a");
        RecoveryReceiveState state = new RecoveryReceiveState("session-b");
        state.acceptBase64(Base64.getEncoder().encodeToString(built.value().encode()));
        assertEquals(RecoveryReceiveState.Status.FAILED, state.status());
        assertFalse(state.isSuccess());
        assertNull(state.snapshot());
    }
}
