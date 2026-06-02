/*
 * Run-it-twice pot split test.
 *
 * Each (side) pot is divided in half between the two boards. To avoid float
 * indivisibility (a pot whose half is not representable in 2-decimal chips, e.g.
 * 0.05 / 2 = 0.025), the split works in integer cents. House rule: when the cent
 * total is odd, SIDE-A keeps the extra cent. The hard invariant is conservation:
 * sideA + sideB == pot exactly — no chip is ever created or lost.
 */
package com.tonikelope.coronapoker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RunItTwiceSplitTest {

    private static long cents(float chips) {
        return Math.round((double) chips * 100.0);
    }

    @Test
    public void evenPotSplitsEqually() {
        float[] s = Crupier.splitPotForRunItTwice(200.00f);
        assertEquals(10000L, cents(s[0]));
        assertEquals(10000L, cents(s[1]));
    }

    @Test
    public void oddCentGoesToSideA() {
        float[] s = Crupier.splitPotForRunItTwice(100.01f);
        assertEquals(5001L, cents(s[0]), "SIDE-A keeps the extra cent");
        assertEquals(5000L, cents(s[1]));
    }

    @Test
    public void smallestOddUnitAllToSideA() {
        float[] s = Crupier.splitPotForRunItTwice(0.01f);
        assertEquals(1L, cents(s[0]));
        assertEquals(0L, cents(s[1]));
    }

    @Test
    public void indivisibleHalfStaysInCents() {
        // 0.05 chips = 5 cents -> 3 / 2, never 0.025.
        float[] s = Crupier.splitPotForRunItTwice(0.05f);
        assertEquals(3L, cents(s[0]));
        assertEquals(2L, cents(s[1]));
    }

    @Test
    public void zeroPot() {
        float[] s = Crupier.splitPotForRunItTwice(0f);
        assertEquals(0L, cents(s[0]));
        assertEquals(0L, cents(s[1]));
    }

    @Test
    public void conservationAndSideABias() {
        float[] pots = {0f, 0.01f, 0.02f, 0.99f, 1.00f, 1.01f, 50.05f, 123.45f, 999.99f, 10000.00f};
        for (float p : pots) {
            float[] s = Crupier.splitPotForRunItTwice(p);
            long total = cents(s[0]) + cents(s[1]);
            assertEquals(cents(p), total, "conservation: sideA + sideB must equal the pot exactly (pot=" + p + ")");
            assertTrue(cents(s[0]) >= cents(s[1]), "SIDE-A >= SIDE-B (pot=" + p + ")");
            assertTrue(cents(s[0]) - cents(s[1]) <= 1, "halves differ by at most one cent (pot=" + p + ")");
        }
    }
}
