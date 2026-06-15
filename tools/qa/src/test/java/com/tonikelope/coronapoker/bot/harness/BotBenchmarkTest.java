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
import com.tonikelope.coronapoker.bot.eval.MemoizedAlbertaEvaluator;
import com.tonikelope.coronapoker.bot.eval.BotEvaluator;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Long-running benchmark that drives N hands per difficulty and dumps the
 * resulting bot stats (VPIP / PFR / AF / WTSD / W$SD / c-bet% / win rate /
 * bb/100). Used to spot calibration leaks against the expected ranges noted
 * in the side comments.
 *
 * <p>Run with: {@code mvn test -Dtest=BotBenchmarkTest}</p>
 *
 * <p>Industry HU-NLHE reference ranges (mid-stakes-ish):</p>
 * <pre>
 *   PROFILE           VPIP    PFR    AF
 *   Calling station   60-80%   <15%  <1.0
 *   Loose passive     45-65%  10-20% 0.8-1.4
 *   Tight reg (TAG)   38-50%  28-38% 1.8-2.6
 *   LAG               48-58%  35-45% 2.5-3.5
 *   Shark             40-52%  32-42% 2.4-3.4
 *   Nit              <25%     <18%  ~1.5
 * </pre>
 */
class BotBenchmarkTest {

    // 40 sessions of 25 hands per difficulty: 1000 hands total, 80 personality
    // draws (one per bot per session) so aggregate stats reflect the entire
    // personality distribution of the difficulty level, not whichever two
    // personalities the single-simulator approach happened to roll.
    private static final int SESSIONS_PER_DIFFICULTY = 30;
    private static final int HANDS_PER_SESSION = 8;
    private static final long BASE_SEED = 0xC0C0AB07DEAD1234L;
    private static final float STARTING_STACK = 200f;
    private static final float BIG_BLIND = 2f;

    private final BotEvaluator evaluator = new MemoizedAlbertaEvaluator();

    @BeforeAll
    static void silenceBotChatter() {
        Logger.getLogger(Bot.class.getName()).setLevel(Level.WARNING);
    }

    @Test
    @DisplayName("Benchmark: full stats sweep across the four difficulty levels")
    void benchmarkAllDifficulties() {
        int totalHands = SESSIONS_PER_DIFFICULTY * HANDS_PER_SESSION;
        System.out.println();
        System.out.println("================================================================================");
        System.out.printf("  CoronaPoker bot benchmark — %d hands / difficulty (%d sessions × %d hands)%n",
                totalHands, SESSIONS_PER_DIFFICULTY, HANDS_PER_SESSION);
        System.out.println("================================================================================");

        for (Bot.Difficulty d : Bot.Difficulty.values()) {
            runDifficulty(d);
        }

        System.out.println("================================================================================");
    }

    private void runDifficulty(Bot.Difficulty difficulty) {
        Bot.DIFFICULTY = difficulty;
        BotStats aggA = new BotStats("BOT_A");
        BotStats aggB = new BotStats("BOT_B");

        for (int session = 0; session < SESSIONS_PER_DIFFICULTY; session++) {
            Bot.TRACKER_MEMORY.clear();
            long seed = BASE_SEED ^ (difficulty.ordinal() * 0xA5A5A5L) ^ (session * 0x10001L);
            HeadsUpSimulator sim = new HeadsUpSimulator(seed, STARTING_STACK, BIG_BLIND, evaluator);
            for (int i = 0; i < HANDS_PER_SESSION; i++) {
                sim.resetStacks();
                sim.playOneHand(i % 2 == 0);
            }
            aggA.add(sim.statsA());
            aggB.add(sim.statsB());
        }

        System.out.println();
        System.out.printf("--- Difficulty: %s ---%n", difficulty.name());
        System.out.println(aggA.summary(BIG_BLIND));
        System.out.println(aggB.summary(BIG_BLIND));
    }
}
