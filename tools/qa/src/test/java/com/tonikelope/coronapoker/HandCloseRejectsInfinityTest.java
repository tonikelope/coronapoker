package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

public class HandCloseRejectsInfinityTest {
    @Test
    public void invalidBalanceCannotBeConstructed() {
        assertThrows(IllegalArgumentException.class,
                () -> new HandCloseTransaction.BalanceUpdate("alice",
                        MoneyCents.fromDouble(Double.POSITIVE_INFINITY),
                        MoneyCents.fromDouble(200d), BuyinCount.of(0)));
    }
}
