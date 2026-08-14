package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class RebuyAmountValidationTest {

    @Test
    void rejectsNonPositiveAndMalformedRemoteAmounts() {
        assertEquals(0, Crupier.normalizeRequestedRebuy("-5", 100));
        assertEquals(0, Crupier.normalizeRequestedRebuy("0", 100));
        assertEquals(0, Crupier.normalizeRequestedRebuy("not-a-number", 100));
    }

    @Test
    void clampsPositiveAmountToAvailableHeadroom() {
        assertEquals(75, Crupier.normalizeRequestedRebuy("120", 75));
        assertEquals(25, Crupier.normalizeRequestedRebuy("25", 75));
        assertEquals(0, Crupier.normalizeRequestedRebuy("25", 0));
    }

    @Test
    void relaysOnlyTheCanonicalValidatedAmount() {
        assertEquals("75", Crupier.canonicalRemoteRebuyAmount("120", 75));
        assertEquals("0", Crupier.canonicalRemoteRebuyAmount("not-a-number", 75));
    }
}
