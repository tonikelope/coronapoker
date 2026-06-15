/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.smoke;

import com.tonikelope.coronapoker.Bot;
import com.tonikelope.coronapoker.GameFrame;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guards the difficulty persistence / recovery contract after the 4→3 level
 * merge. The three current levels must round-trip through
 * {@link GameFrame#applyRecoverSettings(String)}, and the legacy {@code EXPERT}
 * token that a game saved before the merge may still carry must resolve to the
 * current top level {@code HARD} rather than throwing and leaving the difficulty
 * stuck at its previous value.
 */
class DifficultyRecoverCompatSmoke {

    @Test
    @DisplayName("Legacy DIFFICULTY=EXPERT from an old save maps to HARD")
    void legacyExpertMapsToHard() {
        Bot.DIFFICULTY = Bot.Difficulty.EASY; // ensure the call actually changes it
        GameFrame.applyRecoverSettings("DIFFICULTY=EXPERT");
        assertEquals(Bot.Difficulty.HARD, Bot.DIFFICULTY,
                "legacy EXPERT token must resolve to the current top level HARD");
    }

    @Test
    @DisplayName("The three current difficulty levels round-trip through recovery")
    void currentLevelsRoundTrip() {
        GameFrame.applyRecoverSettings("DIFFICULTY=EASY");
        assertEquals(Bot.Difficulty.EASY, Bot.DIFFICULTY);
        GameFrame.applyRecoverSettings("DIFFICULTY=MEDIUM");
        assertEquals(Bot.Difficulty.MEDIUM, Bot.DIFFICULTY);
        GameFrame.applyRecoverSettings("DIFFICULTY=HARD");
        assertEquals(Bot.Difficulty.HARD, Bot.DIFFICULTY);
    }

    @Test
    @DisplayName("An unparseable difficulty token leaves the current value untouched")
    void garbageTokenIsIgnored() {
        Bot.DIFFICULTY = Bot.Difficulty.MEDIUM;
        GameFrame.applyRecoverSettings("DIFFICULTY=NONSENSE");
        assertEquals(Bot.Difficulty.MEDIUM, Bot.DIFFICULTY,
                "an unknown token must be ignored, not crash or reset the difficulty");
    }
}
