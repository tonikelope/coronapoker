package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import java.util.HashMap;
import java.util.Map;
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

    @Test
    void rejectsOverflowWhitespaceAndInvalidHeadroomWithoutCreatingChips() {
        assertEquals(75, Crupier.normalizeRequestedRebuy(String.valueOf(Integer.MAX_VALUE), 75));
        assertEquals(0, Crupier.normalizeRequestedRebuy("2147483648", 75));
        assertEquals(0, Crupier.normalizeRequestedRebuy(" 25 ", 75));
        assertEquals(0, Crupier.normalizeRequestedRebuy("25", -1));
        assertEquals("0", Crupier.canonicalRemoteRebuyAmount(null, 75));
    }

    @Test
    void immediateRebuyRelayUsesTheHostCanonicalAmount() {
        assertEquals(75, Crupier.canonicalImmediateRebuyAmount(120, 75));
        assertEquals(25, Crupier.canonicalImmediateRebuyAmount(25, 75));
        assertEquals(0, Crupier.canonicalImmediateRebuyAmount(0, 75));
        assertEquals(0, Crupier.canonicalImmediateRebuyAmount(-5, 75));
        assertEquals(0, Crupier.canonicalImmediateRebuyAmount(Integer.MAX_VALUE, 0));
    }

    @Test
    void deniedImmediateRebuyClearsAnOptimisticClientEntry() {
        Map<String, Integer> rebuyNow = new HashMap<>();
        rebuyNow.put("nick", 75);

        Crupier.clearImmediateRebuyOnDenied(rebuyNow, "nick");

        assertFalse(rebuyNow.containsKey("nick"));
    }
}
