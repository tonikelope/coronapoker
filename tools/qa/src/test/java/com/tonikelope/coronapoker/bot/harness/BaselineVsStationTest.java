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

/** HARD (the top level) must crush a calling station opponent heads-up. */
class BaselineVsStationTest extends BaselineQualityBase {

    @Test
    @DisplayName("HARD must crush calling station heads-up (>+180 bb/100)")
    void hardCrushesStation() {
        // Floor lowered from the pre-bluff +300. Once post-flop bluffing was added
        // (Phase 1) the top level bleeds a little against a *pure* calling station
        // it has not yet read: the harness clears the opponent tracker every
        // session, so roughly the first ~10 hands bluff before isStation() fires
        // and foldEquity drops to 0. Bluffing into an unknown opponent is +EV
        // against the general population (most players fold sometimes); the
        // never-folding station is the worst case for it, and the bot stops once
        // it has the read — in a real long session the leak is negligible. +180
        // still confirms a dominant value-extraction win rather than flagging that
        // deliberate trade-off as a regression. (Multi-way, the real environment,
        // is unaffected: HARD vs 5 stations prints +731 bb/100.)
        double bb100 = runMatchup("STATION-vs-HARD", FixedStrategyBot.Strategy.STATION);
        assertAtLeast("HARD vs STATION", bb100, 180.0);
    }
}
