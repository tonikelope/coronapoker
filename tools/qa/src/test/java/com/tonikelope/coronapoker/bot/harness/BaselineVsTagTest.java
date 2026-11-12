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
import org.junit.jupiter.api.Tag;

/**
 * Protects the heads-up result against a selective, aggressive reference.
 */
@Tag("slow")
@Tag("bot-campaign")
class BaselineVsTagTest extends BaselineQualityBase {

    @Test
    @DisplayName("HARD stays competitive against the fixed TAG")
    void hardVsTag() {
        double bb100 = runMatchup("HARD vs fixed TAG", FixedStrategyBot.Strategy.TAG);
        assertAtLeast("HARD vs fixed TAG", bb100, -35);
    }
}
