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

/** Acid test: EXPERT must outperform EASY by a significant bb/100 margin. */
class MixedMatchup_ExpertVsEasyTest extends BotMixedMatchupBase {

    @Test
    @DisplayName("EXPERT > EASY")
    void expertBeatsEasy() {
        evaluate(Bot.Difficulty.EXPERT, Bot.Difficulty.EASY);
    }
}
