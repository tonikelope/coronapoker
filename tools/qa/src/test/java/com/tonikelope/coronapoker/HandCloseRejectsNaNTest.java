package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

public class HandCloseRejectsNaNTest {
    @Test
    public void invalidBalanceCannotBeConstructed() {
        assertThrows(NoSuchMethodException.class,
                () -> HandCloseTransaction.BalanceUpdate.class.getConstructor(
                        String.class, double.class, int.class, int.class));
        assertThrows(IllegalArgumentException.class,
                () -> new HandCloseTransaction.BalanceUpdate("alice",
                        MoneyCents.fromDouble(Double.NaN), MoneyCents.fromDouble(200d),
                        BuyinCount.of(0)));
    }
}
