package com.tonikelope.coronapoker;

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
}
