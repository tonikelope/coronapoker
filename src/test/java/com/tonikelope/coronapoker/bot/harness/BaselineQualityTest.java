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
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Absolute-quality baseline tests for the production EXPERT bot. Each test
 * pits EXPERT against a deterministic {@link FixedStrategyBot} archetype
 * and asserts that bb/100 lies above a known industry floor.
 *
 * <p>Together with the mixed-matchup gradient test, these tests ensure that
 * a passing build means EXPERT is both <em>better than</em> the lower
 * difficulties (gradient) and <em>good in absolute terms</em> (baseline).
 * No human judgement is required.</p>
 *
 * <p>Run with: {@code mvn test -Dtest=BaselineQualityTest}</p>
 */
class BaselineQualityTest {

    private static final int SESSIONS = 100;
    private static final int HANDS_PER_SESSION = 15;
    private static final long BASE_SEED = 0xBA5E11AAACADE000L;
    private static final float STARTING_STACK = 200f;
    private static final float BIG_BLIND = 2f;

    private final BotEvaluator evaluator = new AlbertaEvaluatorAdapter();

    @BeforeAll
    static void silence() {
        Logger.getLogger(Bot.class.getName()).setLevel(Level.WARNING);
    }

    @Test
    @DisplayName("EXPERT must crush calling station (>+300 bb/100)")
    void expertCrushesStation() {
        double bb100 = runMatchup("STATION-vs-EXPERT", FixedStrategyBot.Strategy.STATION);
        assertAtLeast("EXPERT vs STATION", bb100, 300.0);
    }

    @Test
    @DisplayName("EXPERT must steal from rock (>+50 bb/100)")
    void expertStealsRock() {
        double bb100 = runMatchup("ROCK-vs-EXPERT", FixedStrategyBot.Strategy.ROCK);
        assertAtLeast("EXPERT vs ROCK", bb100, 50.0);
    }

    @Test
    @DisplayName("EXPERT must trap maniac (>+100 bb/100)")
    void expertTrapsManiac() {
        double bb100 = runMatchup("MANIAC-vs-EXPERT", FixedStrategyBot.Strategy.MANIAC);
        assertAtLeast("EXPERT vs MANIAC", bb100, 100.0);
    }

    @Test
    @DisplayName("EXPERT vs fixed TAG (report-only, expect -20 to +50 bb/100)")
    void expertVsTag() {
        double bb100 = runMatchup("TAG-vs-EXPERT", FixedStrategyBot.Strategy.TAG);
        System.out.printf("    INFO: EXPERT vs fixed-TAG bb/100 = %+.1f (no hard assert; report only)%n", bb100);
    }

    private double runMatchup(String label, FixedStrategyBot.Strategy oppStrategy) {
        BotStats aggExpert = new BotStats("EXPERT");
        BotStats aggOpp = new BotStats(oppStrategy.name());
        int totalHands = SESSIONS * HANDS_PER_SESSION;
        System.out.println();
        System.out.printf("--- Baseline matchup: EXPERT vs %s (%d hands) ---%n", oppStrategy.name(), totalHands);

        for (int session = 0; session < SESSIONS; session++) {
            Bot.TRACKER_MEMORY.clear();
            long seed = BASE_SEED ^ (oppStrategy.ordinal() * 0x12345L) ^ (session * 0x10001L);
            HeadsUpSimulator sim = new HeadsUpSimulator(seed, STARTING_STACK, BIG_BLIND, evaluator);

            boolean expertIsA = (session % 2 == 0);
            Bot expertBot = new Bot(expertIsA ? sim.playerA() : sim.playerB());
            expertBot.setDifficulty(Bot.Difficulty.EXPERT);
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

    private static void assertAtLeast(String label, double bb100, double floor) {
        if (bb100 < floor) {
            fail(String.format("%s baseline quality FAIL: bb/100 = %+.1f, expected >= %+.1f",
                    label, bb100, floor));
        }
    }
}
