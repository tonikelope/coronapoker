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

/** EXPERT must steal enough blinds from a rock to be profitable. */
class BaselineVsRockTest extends BaselineQualityBase {

    @Test
    @DisplayName("EXPERT must steal from rock (>+50 bb/100)")
    void expertStealsRock() {
        double bb100 = runMatchup("ROCK-vs-EXPERT", FixedStrategyBot.Strategy.ROCK);
        assertAtLeast("EXPERT vs ROCK", bb100, 50.0);
    }
}
