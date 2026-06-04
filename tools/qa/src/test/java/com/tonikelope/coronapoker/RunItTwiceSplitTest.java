/*
 * Run-it-twice pot split test.
 *
 * Each (side) pot is divided in half between the two boards. The game economy
 * works in 0.10 chips (every stack write rounds to 1 decimal via floatClean),
 * so the split works in integer tenths: a half with a sub-chip remainder (e.g.
 * 21.30 / 2 = 10.65) is NOT representable and would be silently destroyed or
 * created by the stack rounding. House rule: both sides get the floored half
 * and the indivisible remainder (one 0.10 chip at most) is NOT dealt — it ends
 * up in bote_sobrante (recomputed after both boards) and is carried over to the
 * next hand. The hard invariant is conservation: sideA + sideB + remainder ==
 * pot exactly — no chip is ever created or lost.
 */
package com.tonikelope.coronapoker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RunItTwiceSplitTest {

    private static long tenths(float chips) {
        return Math.round((double) chips * 10.0);
    }

    @Test
    public void evenPotSplitsEqually() {
        float[] s = Crupier.splitPotForRunItTwice(200.00f);
        assertEquals(1000L, tenths(s[0]));
        assertEquals(1000L, tenths(s[1]));
    }

    @Test
    public void oddChipIsNotDealt() {
        // 21.30 (the indivisible all-in pot): 213 tenths -> 106 / 106, one
        // 0.10 chip left over for bote_sobrante. Never 10.65 (sub-chip).
        float[] s = Crupier.splitPotForRunItTwice(21.30f);
        assertEquals(106L, tenths(s[0]));
        assertEquals(106L, tenths(s[1]));
    }

    @Test
    public void smallestOddUnitStaysUndealt() {
        float[] s = Crupier.splitPotForRunItTwice(0.10f);
        assertEquals(0L, tenths(s[0]));
        assertEquals(0L, tenths(s[1]));
    }

    @Test
    public void zeroPot() {
        float[] s = Crupier.splitPotForRunItTwice(0f);
        assertEquals(0L, tenths(s[0]));
        assertEquals(0L, tenths(s[1]));
    }

    @Test
    public void halvesAreAlwaysChipRepresentable() {
        // Every half must be an exact multiple of the 0.10 chip: floatClean
        // (scale 1, HALF_UP) must be a no-op on it, or the payout would mutate.
        float[] pots = {0.10f, 0.30f, 21.30f, 50.50f, 123.40f, 999.90f};
        for (float p : pots) {
            float[] s = Crupier.splitPotForRunItTwice(p);
            for (float half : s) {
                assertEquals(half, new java.math.BigDecimal(half).setScale(1, java.math.RoundingMode.HALF_UP).floatValue(),
                        0f, "half must survive floatClean untouched (pot=" + p + ")");
            }
        }
    }

    @Test
    public void conservationWithRemainder() {
        float[] pots = {0f, 0.10f, 0.20f, 0.90f, 1.00f, 1.10f, 21.30f, 50.50f, 123.40f, 999.90f, 10000.00f};
        for (float p : pots) {
            float[] s = Crupier.splitPotForRunItTwice(p);
            assertEquals(tenths(s[0]), tenths(s[1]), "both boards get the same floored half (pot=" + p + ")");
            long remainder = tenths(p) - tenths(s[0]) - tenths(s[1]);
            assertTrue(remainder == 0L || remainder == 1L,
                    "remainder is zero or exactly one 0.10 chip (pot=" + p + ")");
        }
    }
}
