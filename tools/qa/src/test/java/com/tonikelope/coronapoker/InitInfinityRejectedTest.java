package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;

public class InitInfinityRejectedTest {
    @Test
    public void infinityIsRejectedForEveryBlindBoundary() {
        assertFalse(GameConfigWireV1.builder().smallBlind(Double.POSITIVE_INFINITY).build().isOk());
        assertFalse(GameConfigWireV1.builder().bigBlind(Double.NEGATIVE_INFINITY).build().isOk());
        assertFalse(GameConfigWireV1.builder().blindCap(Double.POSITIVE_INFINITY).build().isOk());
    }
}
