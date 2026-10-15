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

    private static long cents(double v) {
        return Math.round(v * 100.0);
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
            for (int n = 1; n <= 10; n++) {
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

    @Test
    void doubleSplitConservesMoneyAcrossASweep() {
        for (long amountC = 0; amountC <= 5000; amountC += 7) {
            double amount = amountC / 100.0;
            for (int n = 1; n <= 10; n++) {
                double[] r = PotMath.splitAmongWinners(amount, n);
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
    void doubleSplitIsExactAboveTheFloatCeiling() {
        // A deep-stack pot above ~131072 chips still splits to exact cents.
        double pot = 400001.37; // 40000137 cents, 3 winners -> 13333379 each, 0 left
        double[] d = PotMath.splitAmongWinners(pot, 3);
        long per = cents(d[0]);
        long rem = cents(d[1]);
        assertEquals(40000137L, per * 3 + rem, "double split conserves above the ceiling");
        assertEquals(13333379L, per);
        assertEquals(0L, rem);
        assertTrue(rem >= 0 && rem < 3, "remainder in [0,3)");
    }

    @Test
    void tableChipSplitNeverManufacturesHalfOfTheMinimumChip() {
        double[] tenthSplit = PotMath.splitAmongWinners(0.10d, 2, 0.10d);
        assertEquals(0L, cents(tenthSplit[0]));
        assertEquals(10L, cents(tenthSplit[1]));

        double[] threeTenths = PotMath.splitAmongWinners(0.30d, 2, 0.10d);
        assertEquals(10L, cents(threeTenths[0]));
        assertEquals(10L, cents(threeTenths[1]));

        double[] legacyResidue = PotMath.splitAmongWinners(0.35d, 1, 0.10d);
        assertEquals(30L, cents(legacyResidue[0]));
        assertEquals(5L, cents(legacyResidue[1]));
    }

    @Test
    void configuredBlindLadderDefinesTheSmallestPhysicalChip() {
        assertEquals(10L, cents(PotMath.smallestChipDenomination(
                0.10d, 0.20d, 0.20d, 0.40d)));
        assertEquals(5L, cents(PotMath.smallestChipDenomination(
                0.10d, 0.20d, 0.15d, 0.30d)));
    }

    @Test
    void denominationAwareSplitConservesEveryCentAcrossASweep() {
        for (long amountC = 0; amountC <= 5000; amountC += 7) {
            double amount = amountC / 100d;
            for (int winners = 1; winners <= 10; winners++) {
                double[] split = PotMath.splitAmongWinners(
                        amount, winners, 0.10d);
                long paid = cents(split[0]) * winners;
                long remainder = cents(split[1]);
                assertEquals(amountC, paid + remainder);
                assertEquals(0L, cents(split[0]) % 10L);
                assertTrue(remainder >= 0L && remainder < winners * 10L);
            }
        }
    }

    @Test
    void runItTwiceAlsoUsesWholeTableChips() {
        double[] split = PotMath.splitForRunItTwice(0.30d, 0.10d);
        assertEquals(10L, cents(split[0]));
        assertEquals(10L, cents(split[1]));
        assertEquals(10L, 30L - cents(split[0]) - cents(split[1]));

        double[] indivisible = PotMath.splitForRunItTwice(0.10d, 0.10d);
        assertEquals(0L, cents(indivisible[0]));
        assertEquals(0L, cents(indivisible[1]));
    }

    @Test
    void doubleRunItTwiceMatchesFloatBelowCeilingAndIsExactAbove() {
        for (long potC = 0; potC <= 30000; potC += 13) {
            double pot = potC / 100.0;
            float[] f = PotMath.splitForRunItTwice((float) pot);
            double[] d = PotMath.splitForRunItTwice(pot);
            assertEquals(cents(f[0]), cents(d[0]), "half cents must agree pot=" + pot);
            assertEquals(cents(d[0]), cents(d[1]), "equal halves pot=" + pot);
        }
        // Above the ceiling: equal halves, at most one odd cent unpaid.
        double bigPot = 500000.01; // 50000001 cents -> 25000000 each, 1 odd cent carried
        double[] d = PotMath.splitForRunItTwice(bigPot);
        assertEquals(25000000L, cents(d[0]));
        assertEquals(cents(d[0]), cents(d[1]));
        assertEquals(1L, 50000001L - cents(d[0]) - cents(d[1]), "odd cent carried above ceiling");
    }
}
