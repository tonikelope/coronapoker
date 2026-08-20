package com.tonikelope.coronapoker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class RecoveredActionNaNRejectedTest {
    @Test
    public void nanAmountIsRejected() {
        assertFalse(RecoveredActionCodec.decode("YQ==#3#NaN").isOk());
    }
}
