package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Test
    void immediateCanonicalizerIsBoundedForEveryIntegerBoundary() {
        int[] requestedValues = {
            Integer.MIN_VALUE, -1, 0, 1, 2, 7, 75, 76, Integer.MAX_VALUE
        };
        int[] headrooms = {
            Integer.MIN_VALUE, -1, 0, 1, 2, 7, 75, 76, Integer.MAX_VALUE
        };

        for (int requested : requestedValues) {
            for (int headroom : headrooms) {
                int actual = Crupier.canonicalImmediateRebuyAmount(requested, headroom);
                if (requested > 0 && headroom > 0) {
                    assertTrue(actual > 0 && actual <= requested && actual <= headroom,
                            "canonical amount escaped bounds for request=" + requested
                            + ", headroom=" + headroom);
                } else {
                    assertEquals(0, actual,
                            "invalid request/headroom must not create chips: request=" + requested
                            + ", headroom=" + headroom);
                }
            }
        }
    }

    @Test
    void remoteTextNormalizerNeverTurnsMalformedOrOverflowInputIntoPositiveCredit() {
        String[] hostile = {
            null, "", " ", "\t25", "25 ", "-0", "-1",
            String.valueOf(Integer.MIN_VALUE), "2147483648", "not-a-number"
        };
        for (String raw : hostile) {
            assertEquals(0, Crupier.normalizeRequestedRebuy(raw, 75),
                    "hostile raw amount accepted: " + raw);
            assertEquals("0", Crupier.canonicalRemoteRebuyAmount(raw, 75),
                    "hostile raw amount relayed: " + raw);
        }
    }

    @Test
    void optimisticRebuyStateIsIdempotentAcrossAcceptToggleAndDenial() {
        Map<String, Integer> rebuyNow = new HashMap<>();
        String nick = "nick";

        int accepted = Crupier.canonicalImmediateRebuyAmount(120, 75);
        rebuyNow.put(nick, accepted);
        rebuyNow.put(nick, Crupier.canonicalImmediateRebuyAmount(accepted, 75));
        assertEquals(75, rebuyNow.get(nick));

        // The canonical zero used by the host for a toggle-off/no-headroom denial
        // has one meaning: remove the pending request, never insert a zero entry.
        int toggledOff = Crupier.canonicalImmediateRebuyAmount(0, 75);
        if (toggledOff == 0) {
            rebuyNow.remove(nick);
        }
        assertFalse(rebuyNow.containsKey(nick));

        rebuyNow.put(nick, 75);
        Crupier.clearImmediateRebuyOnDenied(rebuyNow, nick);
        Crupier.clearImmediateRebuyOnDenied(rebuyNow, nick);
        Crupier.clearImmediateRebuyOnDenied(rebuyNow, null);
        assertTrue(rebuyNow.isEmpty(), "denial cleanup must be idempotent and null-safe");
    }
}
