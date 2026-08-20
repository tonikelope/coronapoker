package com.tonikelope.coronapoker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class RecoveredActionInfinityRejectedTest {
    @Test
    public void infinityAmountIsRejected() {
        assertFalse(RecoveredActionCodec.decode("YQ==#3#Infinity").isOk());
    }
}
