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
 * Baseline-quality tests for the production HARD bot in 6-max — the
 * scenario CoronaPoker is actually played in. Each concrete subclass
 * seats HARD at a rotating position and fills the remaining five
 * seats with a deterministic {@link FixedStrategyBot} archetype to
 * measure how the bot prints (or bleeds) value against a known table
 * shape. Together with the gradient acid test these tests gate AAA
 * quality without requiring a human at the table.
 */
abstract class MultiwayBaselineBase {

    protected static final int NUM_SEATS = 6;
    // 200 × 50 = 10000 hands/matchup — the established working volume.
    protected static final int SESSIONS = QaConfig.sessions(200);
    protected static final int HANDS_PER_SESSION = QaConfig.hands(50);
    protected static final long BASE_SEED = 0xBA5E11AAA6A60001L;
    protected static final float STARTING_STACK = 200f;
    protected static final float BIG_BLIND = 2f;

    protected final BotEvaluator evaluator = new MemoizedAlbertaEvaluator();

    @BeforeAll
    static void silence() {
        Logger.getLogger(Bot.class.getName()).setLevel(Level.WARNING);
    }

    protected double runMatchup(String label, FixedStrategyBot.Strategy villainStrategy) {
        BotStats aggHero = new BotStats("HARD");
        BotStats aggVillains = new BotStats(villainStrategy.name() + "_AVG");
        int totalHands = SESSIONS * HANDS_PER_SESSION;
        long startMs = System.currentTimeMillis();
        System.out.printf("%n[START] HARD vs 5x %s (%d sessions × %d hands = %d hands total)%n",
                villainStrategy.name(), SESSIONS, HANDS_PER_SESSION, totalHands);
        System.out.flush();
        int progressStep = Math.max(1, SESSIONS / 10);

        for (int session = 0; session < SESSIONS; session++) {
            Bot.TRACKER_MEMORY.clear();
            long seed = BASE_SEED ^ (villainStrategy.ordinal() * 0x12345L) ^ (session * 0x10001L);
            MultiwaySimulator sim = new MultiwaySimulator(NUM_SEATS, seed,
                    STARTING_STACK, BIG_BLIND, evaluator);

            int heroSeat = session % NUM_SEATS;
            Bot heroBot = new Bot(sim.player(heroSeat));
            heroBot.setDifficulty(Bot.Difficulty.HARD);
            sim.setBot(heroSeat, heroBot);
            for (int i = 0; i < NUM_SEATS; i++) {
                if (i == heroSeat) {
                    continue;
                }
                FixedStrategyBot villain = new FixedStrategyBot(sim.player(i),
                        villainStrategy, seed ^ 0xF1ED000DEAL ^ (i * 0x7L));
                sim.setBot(i, villain);
            }

            for (int i = 0; i < HANDS_PER_SESSION; i++) {
                sim.resetStacks();
                sim.playOneHand();
            }

            aggHero.add(sim.stats(heroSeat));
            for (int i = 0; i < NUM_SEATS; i++) {
                if (i != heroSeat) {
                    aggVillains.add(sim.stats(i));
                }
            }

            if (((session + 1) % progressStep == 0) || session == SESSIONS - 1) {
                long elapsed = (System.currentTimeMillis() - startMs) / 1000L;
                int sessionsDone = session + 1;
                int handsDone = sessionsDone * HANDS_PER_SESSION;
                int pct = (sessionsDone * 100) / SESSIONS;
                System.out.printf("  [HARD vs 5x %s] %3d%% — %d/%d sessions (%d hands) — %ds elapsed%n",
                        villainStrategy.name(), pct, sessionsDone, SESSIONS, handsDone, elapsed);
                System.out.flush();
            }
        }

        System.out.println(aggHero.summary(BIG_BLIND));
        System.out.println(aggVillains.summary(BIG_BLIND));
        double bb100 = aggHero.bbPer100(BIG_BLIND);
        System.out.printf("    %s bb/100 = %+.1f%n", label, bb100);
        System.out.flush();
        return bb100;
    }

    protected static void assertAtLeast(String label, double bb100, double floor) {
        if (bb100 < floor) {
            fail(String.format("%s baseline FAIL: bb/100 = %+.1f, expected >= %+.1f",
                    label, bb100, floor));
        }
    }
}
