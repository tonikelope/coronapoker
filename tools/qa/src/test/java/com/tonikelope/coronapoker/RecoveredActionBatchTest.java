package com.tonikelope.coronapoker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("protocol-sim")
class RecoveredActionBatchTest {

    @Test
    void nullReadFailureIsNotEquivalentToAnEmptyActionHistory() {
        RecoveredActionBatch.Result missing = RecoveredActionBatch.decode(null);
        assertFalse(missing.isOk());
        assertEquals(RecoveredActionBatch.Error.MISSING, missing.error());

        RecoveredActionBatch.Result empty = RecoveredActionBatch.decode("");
        assertTrue(empty.isOk());
        assertTrue(empty.actions().isEmpty());
    }

    @Test
    void malformedTokenRejectsTheWholeBatch() {
        String valid = RecoveredActionCodec.encodeV1("alice", Player.CHECK,
                0d, null, null);
        RecoveredActionBatch.Result result = RecoveredActionBatch.decode(
                valid + "@INVALID_RECOVERY_ACTION@");
        assertFalse(result.isOk());
        assertTrue(result.actions().isEmpty());
    }
}
