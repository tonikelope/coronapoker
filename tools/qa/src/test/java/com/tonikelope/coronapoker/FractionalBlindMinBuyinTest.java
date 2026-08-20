package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FractionalBlindMinBuyinTest {

    @Test
    void integerBuyinMinimumRoundsUpNeverBelowConfiguredBigBlinds() {
        assertEquals(3, BuyinRules.min(0.25, 10));
        assertEquals(4, BuyinRules.min(0.35, 10));
        assertTrue(BuyinRules.min(0.25, 10) >= 0.25 * 10);
    }
}
