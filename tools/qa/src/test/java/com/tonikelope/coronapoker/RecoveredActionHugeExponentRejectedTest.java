package com.tonikelope.coronapoker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class RecoveredActionHugeExponentRejectedTest {
    @Test
    public void exponentNotationIsRejectedBeforeNumericConversion() {
        assertFalse(RecoveredActionCodec.decode("YQ==#3#1e100000").isOk());
    }
}
