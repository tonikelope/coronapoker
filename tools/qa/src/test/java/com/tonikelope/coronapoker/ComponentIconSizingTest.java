package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ComponentIconSizingTest {

    @Test
    void usesLaidOutHeightWhenAvailable() {
        assertEquals(35, Helpers.resolveScaledIconSize(50, 45, 40, 0.7f));
    }

    @Test
    void fallsBackToPreferredThenMinimumHeightDuringRelayout() {
        assertEquals(32, Helpers.resolveScaledIconSize(0, 45, 40, 0.7f));
        assertEquals(28, Helpers.resolveScaledIconSize(0, 0, 40, 0.7f));
    }

    @Test
    void refusesToInventAVisualSizeWithoutLayoutInformation() {
        assertEquals(0, Helpers.resolveScaledIconSize(0, 0, 0, 0.7f));
        assertEquals(0, Helpers.resolveScaledIconSize(45, 45, 45, 0f));
    }
}
