package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

public class HandCloseRejectsNegativeTest {
    @Test
    public void invalidBalanceCannotBeConstructed() {
        assertThrows(IllegalArgumentException.class,
                () -> new HandCloseTransaction.BalanceUpdate("alice",
                        MoneyCents.fromDouble(-0.01d), MoneyCents.fromDouble(200d),
                        BuyinCount.of(0)));
        assertThrows(IllegalArgumentException.class, () -> BuyinCount.of(-1));
    }
}
