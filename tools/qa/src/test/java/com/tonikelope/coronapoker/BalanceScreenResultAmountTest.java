package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class BalanceScreenResultAmountTest {

    @Test
    public void lossAmountDoesNotRepeatNegativeMeaning() {
        String amount = BalanceScreen.resultAmountText(-10d);

        assertFalse(amount.startsWith("-"));
        assertTrue(amount.contains("10"));
    }

    @Test
    public void winAmountDoesNotRepeatPositiveMeaning() {
        String amount = BalanceScreen.resultAmountText(10d);

        assertFalse(amount.startsWith("+"));
        assertTrue(amount.contains("10"));
    }

    @Test
    public void winningAnimationUsesLongerRouteFromFinalStack() {
        assertArrayEquals(new double[]{150d, 50d}, BalanceScreen.amountAnimationRange(100d, 150d));
    }

    @Test
    public void winningAnimationUsesLongerRouteFromZero() {
        assertArrayEquals(new double[]{0d, 100d}, BalanceScreen.amountAnimationRange(50d, 150d));
    }

    @Test
    public void losingAnimationUsesLongerRouteFromZero() {
        assertArrayEquals(new double[]{0d, 60d}, BalanceScreen.amountAnimationRange(100d, 40d));
    }

    @Test
    public void losingAnimationUsesLongerRouteFromFinalStack() {
        assertArrayEquals(new double[]{80d, 20d}, BalanceScreen.amountAnimationRange(100d, 80d));
    }

    @Test
    public void bustedPlayerAnimationStartsAtZeroAndEndsAtUnsignedLoss() {
        assertArrayEquals(new double[]{0d, 10d}, BalanceScreen.amountAnimationRange(10d, 0d));
    }
}
