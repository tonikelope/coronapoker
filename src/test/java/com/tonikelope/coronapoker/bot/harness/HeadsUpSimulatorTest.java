/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.bot.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.Bot;
import com.tonikelope.coronapoker.bot.eval.AlbertaEvaluatorAdapter;
import com.tonikelope.coronapoker.bot.eval.BotEvaluator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HeadsUpSimulatorTest {

    private final BotEvaluator evaluator = new AlbertaEvaluatorAdapter();

    @Test
    @DisplayName("Plays a single hand to completion without throwing")
    void singleHandSmoke() {
        Bot.DIFFICULTY = Bot.Difficulty.MEDIUM;
        HeadsUpSimulator sim = new HeadsUpSimulator(42L, 200f, 2f, evaluator);

        HeadsUpSimulator.HandResult result = sim.playOneHand(true);
        assertTrue(result.pot > 0f, "Pot must be positive after a complete hand");
        assertTrue(result.winnerIndex == 0 || result.winnerIndex == 1 || result.winnerIndex == -1,
                "Winner index must be valid; got " + result.winnerIndex);

        // Chip conservation (within float epsilon): total chips equal 2 * startingStack
        float total = sim.playerA().getStack() + sim.playerB().getStack();
        assertEquals(400f, total, 0.5f, "Chips must be conserved across a hand");
    }

    @Test
    @DisplayName("Runs 200 hands without exceptions and produces conservation-preserving outcomes")
    void manyHandsConserveChips() {
        Bot.DIFFICULTY = Bot.Difficulty.MEDIUM;
        HeadsUpSimulator sim = new HeadsUpSimulator(1234L, 200f, 2f, evaluator);

        int aWins = 0, bWins = 0, ties = 0;
        for (int i = 0; i < 200; i++) {
            sim.resetStacks();
            HeadsUpSimulator.HandResult r = sim.playOneHand(i % 2 == 0);
            float total = sim.playerA().getStack() + sim.playerB().getStack();
            assertEquals(400f, total, 0.5f, "Chip conservation violated on hand " + i);
            if (r.winnerIndex == 0) {
                aWins++;
            } else if (r.winnerIndex == 1) {
                bWins++;
            } else {
                ties++;
            }
        }
        // Sanity: both bots must win at least some hands and lose at least some.
        assertTrue(aWins > 0 && bWins > 0,
                "Both bots should win some hands (A=" + aWins + " B=" + bWins + " ties=" + ties + ")");
    }
}
