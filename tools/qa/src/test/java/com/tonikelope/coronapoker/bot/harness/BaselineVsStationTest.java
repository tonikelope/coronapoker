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

/** EXPERT must crush a calling station opponent. */
class BaselineVsStationTest extends BaselineQualityBase {

    @Test
    @DisplayName("EXPERT must crush calling station (>+300 bb/100)")
    void expertCrushesStation() {
        double bb100 = runMatchup("STATION-vs-EXPERT", FixedStrategyBot.Strategy.STATION);
        assertAtLeast("EXPERT vs STATION", bb100, 300.0);
    }
}
