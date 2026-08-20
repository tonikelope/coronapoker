package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Base64;
import org.junit.jupiter.api.Test;

public class RecoverDataMalformedNeverSetsSuccessTest {
    @Test
    public void successAlwaysCarriesAValidatedSnapshot() {
        RecoveryReceiveState malformed = new RecoveryReceiveState("session-a");
        malformed.acceptBase64("%%%not-base64%%%");
        assertTrue(malformed.isTerminal());
        assertFalse(malformed.isSuccess());
        assertNull(malformed.snapshot());

        RecoverySnapshotV1.Result built = RecoverySnapshotV1.fromMap(
                RecoverySnapshotFixtures.validMap(), "session-a");
        RecoveryReceiveState valid = new RecoveryReceiveState("session-a");
        valid.acceptBase64(Base64.getEncoder().encodeToString(built.value().encode()));
        assertTrue(valid.isSuccess());
        assertNotNull(valid.snapshot());
    }
}
