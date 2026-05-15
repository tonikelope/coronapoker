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

/** EXPERT must out-steal a 6-max table of five nits (tight rocks). */
class MultiwayBaselineVsRockTableTest extends MultiwayBaselineBase {

    @Test
    @DisplayName("6-max: EXPERT vs 5 rocks must steal blinds (>+30 bb/100)")
    void expertStealsRockTable() {
        double bb100 = runMatchup("ROCK-TABLE-vs-EXPERT", FixedStrategyBot.Strategy.ROCK);
        assertAtLeast("EXPERT vs 5 ROCK", bb100, 30.0);
    }
}
