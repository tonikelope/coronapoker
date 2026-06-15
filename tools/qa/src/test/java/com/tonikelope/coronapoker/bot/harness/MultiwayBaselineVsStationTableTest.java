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

/** HARD must crush a 6-max table of five calling stations. */
class MultiwayBaselineVsStationTableTest extends MultiwayBaselineBase {

    @Test
    @DisplayName("6-max: HARD vs 5 stations must print (>+150 bb/100)")
    void expertCrushesStationTable() {
        double bb100 = runMatchup("STATION-TABLE-vs-HARD", FixedStrategyBot.Strategy.STATION);
        assertAtLeast("HARD vs 5 STATION", bb100, 150.0);
    }
}
