/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.bot.harness;

import com.tonikelope.coronapoker.Bot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Acid test: HARD must outperform MEDIUM by a significant bb/100 margin. */
class MixedMatchup_HardVsMediumTest extends BotMixedMatchupBase {

    @Test
    @DisplayName("HARD > MEDIUM")
    void hardBeatsMedium() {
        evaluate(Bot.Difficulty.HARD, Bot.Difficulty.MEDIUM);
    }
}
