package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SettlementAmountGateTest {

    @Test
    void acceptsAndRoundsValidMoneyToCents() {
        assertEquals(123L, Crupier.settlementAmountToCents(1.23d));
    }

    @Test
    void rejectsNegativeAndNonFiniteAccounting() {
        assertThrows(IllegalArgumentException.class,
                () -> Crupier.settlementAmountToCents(-0.01d));
        assertThrows(IllegalArgumentException.class,
                () -> Crupier.settlementAmountToCents(Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> Crupier.settlementAmountToCents(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,
                () -> Crupier.settlementAmountToCents(Double.NEGATIVE_INFINITY));
    }

    @Test
    void rejectsValuesThatOverflowCanonicalCents() {
        assertThrows(IllegalArgumentException.class,
                () -> Crupier.settlementAmountToCents(Double.MAX_VALUE));
    }
}
