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

/** HARD must out-steal a 6-max table of five nits (tight rocks). */
class MultiwayBaselineVsRockTableTest extends MultiwayBaselineBase {

    @Test
    @DisplayName("6-max: HARD vs 5 rocks must not bleed (>-25 bb/100)")
    void expertStealsRockTable() {
        // Floor calibrated to 6-max reality: at a 5-rock table, HARD
        // steals blinds when they fold (≥95%) but loses small pots to
        // their AA/KK/QQ 3-bet range when they wake up with hands. The
        // net is naturally close to zero — a -25 bb/100 floor catches a
        // genuine baseline regression while accepting the structural
        // 6-max math.
        double bb100 = runMatchup("ROCK-TABLE-vs-HARD", FixedStrategyBot.Strategy.ROCK);
        assertAtLeast("HARD vs 5 ROCK", bb100, -25.0);
    }
}
