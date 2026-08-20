package com.tonikelope.coronapoker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class RecoveredActionNonNumericDecisionRejectedTest {
    @Test
    public void nonNumericDecisionIsRejected() {
        assertFalse(RecoveredActionCodec.decode("YQ==#fold#0").isOk());
    }
}
