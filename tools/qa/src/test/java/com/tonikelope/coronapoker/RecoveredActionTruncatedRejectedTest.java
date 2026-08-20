package com.tonikelope.coronapoker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class RecoveredActionTruncatedRejectedTest {
    @Test
    public void truncatedActionIsAValueErrorNotAnException() {
        assertFalse(RecoveredActionCodec.decode("YQ==#3").isOk());
    }
}
