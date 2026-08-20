package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

public class HandCloseDomainCeilingTest {
    @Test
    public void invalidBalanceCannotBeConstructed() {
        assertThrows(IllegalArgumentException.class,
                () -> new HandCloseTransaction.BalanceUpdate("alice",
                        MoneyCents.fromDouble(Double.MAX_VALUE), MoneyCents.fromDouble(200d),
                        BuyinCount.of(0)));
        assertThrows(IllegalArgumentException.class,
                () -> BuyinCount.of(BuyinCount.MAX_VALUE + 1));
    }
}
