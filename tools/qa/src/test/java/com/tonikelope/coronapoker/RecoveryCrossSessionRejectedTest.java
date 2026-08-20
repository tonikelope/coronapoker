package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class RecoveryCrossSessionRejectedTest {
    @Test
    public void validSnapshotCannotCrossSessionBoundary() {
        RecoverySnapshotV1.Result built = RecoverySnapshotV1.fromMap(
                RecoverySnapshotFixtures.validMap(), "session-a");
        assertTrue(built.isOk());
        byte[] encoded = built.value().encode();
        assertTrue(RecoverySnapshotV1.decode(encoded, "session-a").isOk());
        assertFalse(RecoverySnapshotV1.decode(encoded, "session-b").isOk());
    }
}
