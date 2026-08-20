package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

public class MoneyCentsOverflowRejectedTest {
    @Test
    public void hugeFiniteAmountsFailInsteadOfSaturating() {
        assertThrows(IllegalArgumentException.class, () -> MoneyCents.fromDouble(Double.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,
                () -> MoneyCents.parse("92233720368547758.08"));
        assertThrows(IllegalArgumentException.class,
                () -> MoneyCents.ofCents(MoneyCents.MAX_CENTS + 1));
    }
}
