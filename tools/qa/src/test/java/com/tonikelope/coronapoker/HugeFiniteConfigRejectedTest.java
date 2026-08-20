package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;

public class HugeFiniteConfigRejectedTest {
    @Test
    public void hugeFiniteConfigMoneyNeverReachesCanonicalState() {
        assertFalse(GameConfigWireV1.builder().smallBlind(Double.MAX_VALUE).build().isOk());
        assertFalse(GameConfigWireV1.builder().bigBlind(1e100).build().isOk());
        assertFalse(GameConfigWireV1.builder().blindCap(1e100).build().isOk());
    }
}
