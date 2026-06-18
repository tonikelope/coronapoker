/*
 * Pot-division money conservation (cent chips). The pot is split in whole cents;
 * every winner gets the floor and the indivisible remainder is carried forward.
 * The hard invariant is conservation: no cent is ever created or lost.
 */
package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class PotMathTest {

    private static long cents(float v) {
        return Math.round((double) v * 100.0);
    }

    @Test
    void singleWinnerGetsEverythingExact() {
        float[] r = PotMath.splitAmongWinners(12.34f, 1);
        assertEquals(12.34f, r[0], 0f);
        assertEquals(0f, r[1], 0f);
    }

    @Test
    void evenSplitHasNoRemainder() {
        float[] r = PotMath.splitAmongWinners(0.30f, 2); // 30c / 2 = 15c
        assertEquals(15L, cents(r[0]));
        assertEquals(0L, cents(r[1]));
    }

    @Test
    void oddSplitCarriesTheRemainder() {
        float[] r = PotMath.splitAmongWinners(0.50f, 3); // 50c / 3 = 16c each, 2c left
        assertEquals(16L, cents(r[0]));
        assertEquals(2L, cents(r[1]));

        float[] r2 = PotMath.splitAmongWinners(1.00f, 3); // 100c / 3 = 33c each, 1c left
        assertEquals(33L, cents(r2[0]));
        assertEquals(1L, cents(r2[1]));
    }

    @Test
    void nickelGranularPotSplitsToCents() {
        // 0.75 (three 0.25 contributions) between 2 winners -> 37c each + 1c carry.
        float[] r = PotMath.splitAmongWinners(0.75f, 2);
        assertEquals(37L, cents(r[0]));
        assertEquals(1L, cents(r[1]));
    }

    @Test
    void splitAmongWinnersConservesMoneyAcrossASweep() {
        for (long amountC = 0; amountC <= 5000; amountC += 7) {
            float amount = (float) (amountC / 100.0);
            for (int n = 1; n <= 9; n++) {
                float[] r = PotMath.splitAmongWinners(amount, n);
                long sum = cents(r[0]) * n + cents(r[1]);
                assertEquals(amountC, sum, "conservation amount=" + amount + " winners=" + n);
                if (n > 1) {
                    assertTrue(cents(r[1]) >= 0 && cents(r[1]) < n,
                            "remainder in [0,n) amount=" + amount + " n=" + n);
                }
            }
        }
    }

    @Test
    void runItTwiceConservesMoneyAcrossASweep() {
        for (long potC = 0; potC <= 30000; potC += 13) {
            float pot = (float) (potC / 100.0);
            float[] s = PotMath.splitForRunItTwice(pot);
            assertEquals(cents(s[0]), cents(s[1]), "equal halves pot=" + pot);
            long remainder = potC - cents(s[0]) - cents(s[1]);
            assertTrue(remainder == 0L || remainder == 1L, "odd cent at most pot=" + pot);
        }
    }
}
