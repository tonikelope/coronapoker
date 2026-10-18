/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GdxRecoveryAnimationTest {

    @Test
    void recoveryClockUsesRealMonotonicTimeAfterALongRenderFrame() {
        long started = 40_000_000_000L;

        assertEquals(0f, CoronaPokerGdxTable
                .recoveryAnimationElapsedSeconds(started, started));
        assertEquals(0.016f, CoronaPokerGdxTable
                .recoveryAnimationElapsedSeconds(started,
                        started + 16_000_000L), 0.0001f);
        assertEquals(1.75f, CoronaPokerGdxTable
                .recoveryAnimationElapsedSeconds(started,
                        started + 1_750_000_000L), 0.0001f,
                "a blocked render must catch up instead of advancing by the "
                        + "table clock's former 50 ms clamp");
    }
}
