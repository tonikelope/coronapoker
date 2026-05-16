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

/** EXPERT must trap a maniac opponent and extract value from over-aggression. */
class BaselineVsManiacTest extends BaselineQualityBase {

    @Test
    @DisplayName("EXPERT must trap maniac (>+100 bb/100)")
    void expertTrapsManiac() {
        double bb100 = runMatchup("MANIAC-vs-EXPERT", FixedStrategyBot.Strategy.MANIAC);
        assertAtLeast("EXPERT vs MANIAC", bb100, 100.0);
    }
}
