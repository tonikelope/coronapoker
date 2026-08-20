package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;

public class BlindRangeCrossFieldTest {
    @Test
    public void crossFieldInvariantsAreValidatedTogether() {
        assertFalse(GameConfigWireV1.builder().smallBlind(2d).bigBlind(1d).build().isOk());
        assertFalse(GameConfigWireV1.builder().buyinRangeBb(200, 100).build().isOk());
        assertFalse(GameConfigWireV1.builder().rebuyCapPolicy(99).build().isOk());
    }
}
