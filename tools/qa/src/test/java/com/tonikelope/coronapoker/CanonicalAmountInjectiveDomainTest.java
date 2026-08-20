package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class CanonicalAmountInjectiveDomainTest {
    @Test
    public void distinctCentValuesHaveDistinctCanonicalValues() {
        Set<Long> seen = new HashSet<>();
        for (long cents = 0; cents <= 1_000_000; cents += 37) {
            String decimal = BigDecimal.valueOf(cents, 2).toPlainString();
            long canonical = MoneyCents.parse(decimal).cents();
            assertEquals(cents, canonical);
            if (!seen.add(canonical)) throw new AssertionError("canonical collision at " + cents);
        }
    }
}
