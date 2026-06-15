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

/** HARD must trap five maniacs and extract value from over-aggression. */
class MultiwayBaselineVsManiacTableTest extends MultiwayBaselineBase {

    @Test
    @DisplayName("6-max: HARD vs 5 maniacs must trap and print (>+100 bb/100)")
    void expertTrapsManiacTable() {
        double bb100 = runMatchup("MANIAC-TABLE-vs-HARD", FixedStrategyBot.Strategy.MANIAC);
        assertAtLeast("HARD vs 5 MANIAC", bb100, 100.0);
    }
}
