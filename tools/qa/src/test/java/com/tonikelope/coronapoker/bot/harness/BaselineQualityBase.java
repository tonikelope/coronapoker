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
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Shared infrastructure for the per-archetype baseline quality tests. Each
 * concrete subclass pits HARD against one deterministic
 * {@link FixedStrategyBot} archetype. Splitting one test per file lets
 * surefire fork each baseline matchup into its own JVM.
 *
 * <p>This base class deliberately does not end in {@code Test} so surefire
 * does not run it on its own.</p>
 */
abstract class BaselineQualityBase {

    // 200 sessions × 50 hands = 10000 hands/matchup. Larger sample gives
    // the bb/100 estimate a much tighter standard error so calibration
    // changes are distinguishable from noise. With paralelización
    // (forkCount=0.6C ≈ 5 forks) the four baseline matchups still
    // complete in roughly half the wall-clock of a serial run.
    protected static final int SESSIONS = QaConfig.sessions(200);
    protected static final int HANDS_PER_SESSION = QaConfig.hands(50);
    protected static final long BASE_SEED = 0xBA5E11AAACADE000L;
    protected static final float STARTING_STACK = 200f;
    protected static final float BIG_BLIND = 2f;

    protected final BotEvaluator evaluator = new MemoizedAlbertaEvaluator();

    @BeforeAll
    static void silence() {
        Logger.getLogger(Bot.class.getName()).setLevel(Level.WARNING);
    }

    protected double runMatchup(String label, FixedStrategyBot.Strategy oppStrategy) {
        BotStats aggExpert = new BotStats("HARD");
        BotStats aggOpp = new BotStats(oppStrategy.name());
        int totalHands = SESSIONS * HANDS_PER_SESSION;
        System.out.println();
        System.out.printf("--- Baseline matchup: HARD vs %s (%d hands) ---%n", oppStrategy.name(), totalHands);

        for (int session = 0; session < SESSIONS; session++) {
            Bot.TRACKER_MEMORY.clear();
            long seed = BASE_SEED ^ (oppStrategy.ordinal() * 0x12345L) ^ (session * 0x10001L);
            HeadsUpSimulator sim = new HeadsUpSimulator(seed, STARTING_STACK, BIG_BLIND, evaluator);

            boolean expertIsA = (session % 2 == 0);
            Bot expertBot = new Bot(expertIsA ? sim.playerA() : sim.playerB());
            expertBot.setDifficulty(Bot.Difficulty.HARD);
            FixedStrategyBot oppBot = new FixedStrategyBot(
                    expertIsA ? sim.playerB() : sim.playerA(),
                    oppStrategy,
                    seed ^ 0xF1ED000DEAL);

            if (expertIsA) {
                sim.setBotA(expertBot);
                sim.setBotB(oppBot);
            } else {
                sim.setBotA(oppBot);
                sim.setBotB(expertBot);
            }

            for (int i = 0; i < HANDS_PER_SESSION; i++) {
                sim.resetStacks();
                sim.playOneHand(i % 2 == 0);
            }

            if (expertIsA) {
                aggExpert.add(sim.statsA());
                aggOpp.add(sim.statsB());
            } else {
                aggExpert.add(sim.statsB());
                aggOpp.add(sim.statsA());
            }
        }

        System.out.println(aggExpert.summary(BIG_BLIND));
        System.out.println(aggOpp.summary(BIG_BLIND));
        double bb100 = aggExpert.bbPer100(BIG_BLIND);
        System.out.printf("    %s bb/100 = %+.1f%n", label, bb100);
        return bb100;
    }

    protected static void assertAtLeast(String label, double bb100, double floor) {
        if (bb100 < floor) {
            fail(String.format("%s baseline quality FAIL: bb/100 = %+.1f, expected >= %+.1f",
                    label, bb100, floor));
        }
    }
}
