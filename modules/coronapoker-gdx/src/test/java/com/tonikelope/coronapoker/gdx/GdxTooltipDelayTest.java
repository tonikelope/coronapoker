/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class GdxTooltipDelayTest {

    @Test
    void requiresAContinuousDwellOnTheSameTarget() {
        GdxTooltipDelay delay = new GdxTooltipDelay(500L);

        assertFalse(delay.ready("first", 1_000L));
        assertFalse(delay.ready("first", 1_499L));
        assertTrue(delay.ready("first", 1_500L));
        assertFalse(delay.ready("second", 1_600L));
        assertTrue(delay.ready("second", 2_100L));
    }

    @Test
    void leavingTheTargetRestartsTheDwell() {
        GdxTooltipDelay delay = new GdxTooltipDelay(500L);

        assertFalse(delay.ready("latency", 1_000L));
        delay.clear();
        assertFalse(delay.ready("latency", 2_000L));
        assertFalse(delay.ready("latency", 2_499L));
        assertTrue(delay.ready("latency", 2_500L));
    }
}
