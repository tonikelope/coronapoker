/*
 * Run-it-twice pot split test.
 *
 * Each (side) pot is divided in half between the two boards. The game economy
 * works in cent chips (every stack write rounds to 2 decimals via doubleClean),
 * so the split works in integer cents: each board gets the floored half and, if
 * the pot is an odd number of cents, the indivisible cent is NOT dealt — it ends
 * up in bote_sobrante (recomputed after both boards) and is carried over to the
 * next hand. The hard invariant is conservation: sideA + sideB + remainder ==
 * pot exactly — no chip is ever created or lost.
 */
package com.tonikelope.coronapoker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RunItTwiceSplitTest {

    private static long cents(double chips) {
        return Math.round((double) chips * 100.0);
    }

    @Test
    public void evenPotSplitsEqually() {
        double[] s = Crupier.splitPotForRunItTwice(200.00f);
        assertEquals(10000L, cents(s[0]));
        assertEquals(10000L, cents(s[1]));
    }

    @Test
    public void exactHalfToTheCent() {
        // 21.30 -> 2130 cents -> 1065 / 1065 exactly. With cent chips this no
        // longer strands a tenth (the old 0.10-chip engine paid 10.6 / 10.6).
        double[] s = Crupier.splitPotForRunItTwice(21.30f);
        assertEquals(1065L, cents(s[0]));
        assertEquals(1065L, cents(s[1]));
        assertEquals(0L, cents(21.30f) - cents(s[0]) - cents(s[1]));
    }

    @Test
    public void oddCentIsNotDealt() {
        // 21.31 -> 2131 cents -> 1065 / 1065, one cent left for bote_sobrante.
        double[] s = Crupier.splitPotForRunItTwice(21.31f);
        assertEquals(1065L, cents(s[0]));
        assertEquals(1065L, cents(s[1]));
        assertEquals(1L, cents(21.31f) - cents(s[0]) - cents(s[1]));
    }

    @Test
    public void smallestOddUnitStaysUndealt() {
        double[] s = Crupier.splitPotForRunItTwice(0.01f);
        assertEquals(0L, cents(s[0]));
        assertEquals(0L, cents(s[1]));
    }

    @Test
    public void zeroPot() {
        double[] s = Crupier.splitPotForRunItTwice(0f);
        assertEquals(0L, cents(s[0]));
        assertEquals(0L, cents(s[1]));
    }

    @Test
    public void halvesAreAlwaysChipRepresentable() {
        // Every half must be an exact multiple of the cent chip: doubleClean
        // (scale 2, HALF_UP) must be a no-op on it, or the payout would mutate.
        double[] pots = {0.01f, 0.05f, 0.30f, 21.31f, 50.55f, 123.45f, 999.99f};
        for (double p : pots) {
            double[] s = Crupier.splitPotForRunItTwice(p);
            for (double half : s) {
                assertEquals(half, new java.math.BigDecimal(half).setScale(2, java.math.RoundingMode.HALF_UP).doubleValue(),
                        0f, "half must survive doubleClean untouched (pot=" + p + ")");
            }
        }
    }

    @Test
    public void conservationWithRemainder() {
        double[] pots = {0f, 0.01f, 0.02f, 0.05f, 0.99f, 1.00f, 1.01f, 21.31f, 50.55f, 123.45f, 999.99f, 10000.00f};
        for (double p : pots) {
            double[] s = Crupier.splitPotForRunItTwice(p);
            assertEquals(cents(s[0]), cents(s[1]), "both boards get the same floored half (pot=" + p + ")");
            long remainder = cents(p) - cents(s[0]) - cents(s[1]);
            assertTrue(remainder == 0L || remainder == 1L,
                    "remainder is zero or exactly one cent (pot=" + p + ")");
        }
    }
}
