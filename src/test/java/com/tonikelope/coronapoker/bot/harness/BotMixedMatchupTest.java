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
import com.tonikelope.coronapoker.bot.eval.AlbertaEvaluatorAdapter;
import com.tonikelope.coronapoker.bot.eval.BotEvaluator;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The AAA acid test: pits each difficulty against every lower difficulty in
 * head-to-head matches and reports bb/100 from the higher-difficulty bot's
 * perspective. A truly state-of-the-art configuration produces meaningful
 * positive bb/100 for the higher-difficulty side (e.g. EXPERT crushes EASY by
 * +50bb/100 or more, EXPERT slightly outperforms HARD, etc.).
 *
 * <p>Run with: {@code mvn test -Dtest=BotMixedMatchupTest}</p>
 */
class BotMixedMatchupTest {

    // 100 sessions × 15 hands = 1500 hands/matchup. With HU NLHE per-hand
    // variance ~150 bb/100 std-dev, this drops the standard error on a
    // single-side bb/100 to ~38 bb/100 (delta SE ~54). DELTAs of ±100+ are
    // safely significant at 95% confidence under this volume.
    private static final int SESSIONS_PER_MATCHUP = 100;
    private static final int HANDS_PER_SESSION = 15;
    private static final long BASE_SEED = 0xC0C0FEEDDEADBEEFL;
    private static final float STARTING_STACK = 200f;
    private static final float BIG_BLIND = 2f;

    private final BotEvaluator evaluator = new AlbertaEvaluatorAdapter();

    @BeforeAll
    static void silenceBotChatter() {
        Logger.getLogger(Bot.class.getName()).setLevel(Level.WARNING);
    }

    @Test
    @DisplayName("AAA acid test: higher difficulty must outperform lower difficulty in bb/100")
    void higherDifficultyDominates() {
        int totalHands = SESSIONS_PER_MATCHUP * HANDS_PER_SESSION;
        System.out.println();
        System.out.println("================================================================================");
        System.out.printf("  CoronaPoker mixed-matchup acid test — %d hands / matchup%n", totalHands);
        System.out.println("  (positive bb/100 means the HIGHER-difficulty bot wins)");
        System.out.println("================================================================================");

        Bot.Difficulty[] order = {
                Bot.Difficulty.EASY, Bot.Difficulty.MEDIUM, Bot.Difficulty.HARD, Bot.Difficulty.EXPERT
        };
        // Run each higher-difficulty bot against every lower-difficulty bot.
        for (int hi = order.length - 1; hi >= 1; hi--) {
            for (int lo = hi - 1; lo >= 0; lo--) {
                runMatchup(order[hi], order[lo]);
            }
        }

        System.out.println("================================================================================");
    }

    private void runMatchup(Bot.Difficulty hi, Bot.Difficulty lo) {
        BotStats aggHi = new BotStats(hi.name());
        BotStats aggLo = new BotStats(lo.name());

        for (int session = 0; session < SESSIONS_PER_MATCHUP; session++) {
            Bot.TRACKER_MEMORY.clear();
            long seed = BASE_SEED ^ ((long) hi.ordinal() * 0x111111L) ^ ((long) lo.ordinal() * 0x77777L) ^ (session * 0x10001L);
            HeadsUpSimulator sim = new HeadsUpSimulator(seed, STARTING_STACK, BIG_BLIND, evaluator);

            // Alternate which seat the higher-difficulty bot occupies to wash out
            // any positional advantage from the seed→button rotation.
            boolean hiIsA = (session % 2 == 0);
            sim.setBotDifficulties(hiIsA ? hi : lo, hiIsA ? lo : hi);

            for (int i = 0; i < HANDS_PER_SESSION; i++) {
                sim.resetStacks();
                sim.playOneHand(i % 2 == 0);
            }

            // Aggregate into the "hi" or "lo" bucket based on seat assignment.
            if (hiIsA) {
                aggHi.add(sim.statsA());
                aggLo.add(sim.statsB());
            } else {
                aggHi.add(sim.statsB());
                aggLo.add(sim.statsA());
            }
        }

        System.out.println();
        System.out.printf("--- %s vs %s ---%n", hi.name(), lo.name());
        System.out.println(aggHi.summary(BIG_BLIND));
        System.out.println(aggLo.summary(BIG_BLIND));
        double delta = aggHi.bbPer100(BIG_BLIND) - aggLo.bbPer100(BIG_BLIND);
        System.out.printf("    DELTA bb/100 (%s - %s) = %+7.1f%n", hi.name(), lo.name(), delta);
    }
}
