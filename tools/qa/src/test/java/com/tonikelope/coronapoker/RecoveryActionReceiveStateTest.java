package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

public class RecoveryActionReceiveStateTest {

    @Test
    void malformedOrMissingCriticalActionDataNeverBecomesAnEmptySuccessfulRecovery() {
        RecoveryActionReceiveState malformed = new RecoveryActionReceiveState();
        malformed.acceptFrame("GAME#7#ACTIONDATA");
        assertTrue(malformed.isTerminal());
        assertFalse(malformed.isSuccess());
        assertNull(malformed.actions());

        RecoveryActionReceiveState badBase64 = new RecoveryActionReceiveState();
        badBase64.acceptFrame("GAME#7#ACTIONDATA#%%%not-base64%%%");
        assertTrue(badBase64.isTerminal());
        assertFalse(badBase64.isSuccess());
        assertNull(badBase64.actions());

        RecoveryActionReceiveState missing = new RecoveryActionReceiveState();
        missing.rejectTimeout();
        assertTrue(missing.isTerminal());
        assertFalse(missing.isSuccess());
        assertNull(missing.actions());

        RecoveryActionReceiveState invalidUtf8 = new RecoveryActionReceiveState();
        invalidUtf8.acceptFrame("GAME#7#ACTIONDATA#"
                + Base64.getEncoder().encodeToString(new byte[]{(byte) 0xc3, 0x28}));
        assertTrue(invalidUtf8.isTerminal());
        assertFalse(invalidUtf8.isSuccess());
        assertNull(invalidUtf8.actions());
    }

    @Test
    void explicitEmptyAndValidActionDataAreDistinguishedFromFailure() {
        RecoveryActionReceiveState empty = new RecoveryActionReceiveState();
        empty.acceptFrame("GAME#8#ACTIONDATA#*");
        assertTrue(empty.isSuccess());
        assertEquals("", empty.actions());

        String records = "V1#signed-record";
        RecoveryActionReceiveState populated = new RecoveryActionReceiveState();
        populated.acceptFrame("GAME#9#ACTIONDATA#"
                + Base64.getEncoder().encodeToString(records.getBytes(StandardCharsets.UTF_8)));
        assertTrue(populated.isSuccess());
        assertEquals(records, populated.actions());
    }
}
