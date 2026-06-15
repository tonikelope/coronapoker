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
    @DisplayName("HARD must steal from rock heads-up (>+15 bb/100)")
    void hardStealsRock() {
        // Floor lowered from the pre-bluff +50 for the same reason as the station
        // baseline: the post-flop bluffing added in Phase 1 costs a little against
        // a rock until the bot reads it (tracker reset every session exaggerates
        // the unread window). Still clearly profitable from blind steals.
        double bb100 = runMatchup("ROCK-vs-HARD", FixedStrategyBot.Strategy.ROCK);
        assertAtLeast("HARD vs ROCK", bb100, 15.0);
    }
}
