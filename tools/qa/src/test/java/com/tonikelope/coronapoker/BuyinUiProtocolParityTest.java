package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BuyinUiProtocolParityTest {

    @Test
    void uiBoundsAndWireAdmissionUseTheSameEffectiveAmounts() {
        BuyinRules.Range range = BuyinRules.range(0.25, 10, 100);

        assertEquals(3, range.min());
        assertEquals(25, range.max());
        assertEquals(12, range.suggested());
        assertEquals(range.min(), range.clampWireAmount(0));
        assertEquals(17, range.clampWireAmount(17));
        assertEquals(range.max(), range.clampWireAmount(999));
        assertEquals(range.min() * 100L, range.minEffectiveCents());
        assertEquals(range.max() * 100L, range.maxEffectiveCents());
    }
}
