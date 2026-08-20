package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

public class MoneyCentsScaleRejectedTest {
    @Test
    public void moreThanTwoDecimalPlacesAreRejectedNotRounded() {
        assertEquals(1234L, MoneyCents.parse("12.34").cents());
        assertThrows(IllegalArgumentException.class, () -> MoneyCents.parse("12.345"));
        assertThrows(IllegalArgumentException.class, () -> MoneyCents.fromDouble(12.345));
    }
}
