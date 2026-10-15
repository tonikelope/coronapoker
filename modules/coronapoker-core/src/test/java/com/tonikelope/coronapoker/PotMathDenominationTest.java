package com.tonikelope.coronapoker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PotMathDenominationTest {

    private static long cents(double amount) {
        return Math.round(amount * 100d);
    }

    @Test
    void tenthChipCannotBeSplitIntoTwoFiveCentStacks() {
        double[] split = PotMath.splitAmongWinners(0.10d, 2, 0.10d);

        assertEquals(0L, cents(split[0]));
        assertEquals(10L, cents(split[1]));
    }

    @Test
    void everyWinnerReceivesWholeChipsAndMoneyIsConserved() {
        for (long amountCents = 0; amountCents <= 5_000;
                amountCents += 7) {
            for (int winners = 1; winners <= 10; winners++) {
                double[] split = PotMath.splitAmongWinners(
                        amountCents / 100d, winners, 0.10d);
                long perWinner = cents(split[0]);
                long remainder = cents(split[1]);
                assertEquals(0L, perWinner % 10L);
                assertEquals(amountCents,
                        perWinner * winners + remainder);
                assertTrue(remainder >= 0L && remainder < winners * 10L);
            }
        }
    }

    @Test
    void runItTwiceAlsoPreservesWholeTableChips() {
        double[] split = PotMath.splitForRunItTwice(0.30d, 0.10d);

        assertEquals(10L, cents(split[0]));
        assertEquals(10L, cents(split[1]));
        assertEquals(30L, cents(split[0]) + cents(split[1]) + 10L);
    }

    @Test
    void denominationUsesTheGcdOfEveryBlindLevel() {
        assertEquals(10L, cents(PotMath.smallestChipDenomination(
                0.10d, 0.20d, 0.20d, 0.40d)));
        assertEquals(5L, cents(PotMath.smallestChipDenomination(
                0.10d, 0.20d, 0.15d, 0.30d)));
    }
}
