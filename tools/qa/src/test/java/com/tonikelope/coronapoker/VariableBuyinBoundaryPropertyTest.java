package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VariableBuyinBoundaryPropertyTest {

    @Test
    void effectiveIntegerRangeRespectsExactCentBoundaries() {
        for (int blindSteps = 1; blindSteps <= 2_000; blindSteps++) {
            double bb = blindSteps * 0.05;
            for (int minBb = 10; minBb <= 490; minBb += 10) {
                for (int maxBb = minBb + 10; maxBb <= 500; maxBb += 50) {
                    BuyinRules.Range range = BuyinRules.range(bb, minBb, maxBb);
                    long exactMinCents = MoneyCents.fromDouble(bb).cents() * minBb;
                    long exactMaxCents = MoneyCents.fromDouble(bb).cents() * maxBb;
                    assertTrue(range.minEffectiveCents() >= exactMinCents);
                    assertTrue(range.maxEffectiveCents() <= exactMaxCents);
                    assertTrue(range.min() <= range.suggested());
                    assertTrue(range.suggested() <= range.max());
                }
            }
        }
    }
}
