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

/** Report-only: EXPERT vs deterministic TAG. Expect roughly -20 to +50 bb/100. */
class BaselineVsTagTest extends BaselineQualityBase {

    @Test
    @DisplayName("EXPERT vs fixed TAG (report-only, expect -20 to +50 bb/100)")
    void expertVsTag() {
        double bb100 = runMatchup("TAG-vs-EXPERT", FixedStrategyBot.Strategy.TAG);
        System.out.printf("    INFO: EXPERT vs fixed-TAG bb/100 = %+.1f (no hard assert; report only)%n", bb100);
    }
}
