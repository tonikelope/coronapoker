/*
 * Money resolution of the current money quantizers and comparisons.
 */
package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.core.game.MoneyMath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class HelpersMoneyTest {

    @Test
    void doubleCleanUsesThePreciseBigDecimalConstructor() {
        // Lock the current half-cent rounding decision.
        assertEquals(2.67, MoneyMath.clean(2.675), 0.0);
        assertEquals(0.14, MoneyMath.clean(0.145), 0.0);
        assertEquals(1.00, MoneyMath.clean(1.005), 0.0);
    }

    @Test
    void doubleCleanIsExactAboveTheFloatCeiling() {
        // Cents stay exact at high stacks where float32 cannot represent every cent.
        assertEquals(200000.07, MoneyMath.clean(200000.07), 0.0);
        assertEquals(1000000.55, MoneyMath.clean(1000000.55), 0.0);
        assertEquals(9999999.99, MoneyMath.clean(9999999.99), 0.0);
    }

    @Test
    void doubleCompareIsAtCentResolution() {
        assertEquals(0, MoneyMath.compare(0.25, 0.25));
        assertEquals(0, MoneyMath.compare(0.250001, 0.25));
        assertTrue(MoneyMath.compare(0.10, 0.20) < 0);
        assertTrue(MoneyMath.compare(200000.25, 200000.20) > 0);
    }
}
