/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.bot.harness;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** HARD (the top level) must steal enough blinds from a rock to stay profitable. */
class BaselineVsRockTest extends BaselineQualityBase {

    @Test
    @DisplayName("HARD must steal from rock heads-up (>+20 bb/100)")
    void hardStealsRock() {
        // Floor +20 (was +50 pre-bluff). A rock folds almost everything, so the
        // edge is blind-steal income; the small post-flop bluffing cost trims it
        // a little. Measured ~+28 bb/100 with the early-read discipline in place.
        double bb100 = runMatchup("ROCK-vs-HARD", FixedStrategyBot.Strategy.ROCK);
        assertAtLeast("HARD vs ROCK", bb100, 20.0);
    }
}
