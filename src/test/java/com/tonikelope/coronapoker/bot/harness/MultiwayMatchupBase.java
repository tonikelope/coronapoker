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
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Per-matchup base for the 6-handed gradient tests. Each concrete subclass
 * sits one hero bot at seat 0 and fills the remaining five seats with bots
 * of the villain difficulty. The hero must outperform the villains by a
 * meaningful bb/100 margin to PASS — this is the multi-way equivalent of
 * the heads-up acid test and reflects the actual scenario where CoronaPoker
 * is played.
 *
 * <p>Per-session DELTA = hero bb/100 − average villain bb/100. Session
 * deltas are averaged and a standard error is computed; PASS requires both
 * DELTA &gt; {@code MIN_DELTA_BB100} and DELTA &gt; 2 × SE.</p>
 *
 * <p>Hero rotates through all six seats across sessions so any positional
 * advantage from the seed→button rotation washes out.</p>
 */
abstract class MultiwayMatchupBase {

    protected static final int NUM_SEATS = 6;
    // 200 × 50 = 10000 hands/matchup. SE ~20 bb/100 — comfortable for
    // resolving the +30 gradient floor with t > 2 on most matchups.
    // 25 000-hand runs were attempted and timed out beyond reasonable
    // wall-clock on this hardware (six-bot 6-max evaluation cost
    // dominates with 25k hands); 10 000 hands is the established
    // working volume that produces the AAA verdicts shown in the
    // README results section.
    protected static final int SESSIONS_PER_MATCHUP = 200;
    protected static final int HANDS_PER_SESSION = 50;
    protected static final long BASE_SEED = 0xC0C0FEEDBA5E4321L;
    protected static final float STARTING_STACK = 200f;
    protected static final float BIG_BLIND = 2f;

    // 6-max gradient PASS thresholds. With 5 villains contributing to the
    // pot, the hero's edge per matchup gets divided across them so the
    // observable bb/100 swing per matchup is naturally smaller than HU.
    protected static final double MIN_DELTA_BB100 = 30.0;
    protected static final double MIN_SIGNIFICANCE_SIGMAS = 2.0;

    protected final BotEvaluator evaluator = new AlbertaEvaluatorAdapter();

    @BeforeAll
    static void silenceBotChatter() {
        Logger.getLogger(Bot.class.getName()).setLevel(Level.WARNING);
    }

    protected void evaluate(Bot.Difficulty hero, Bot.Difficulty villain) {
        MatchupResult result = runMatchup(hero, villain);
        System.out.println(result.report());
        if (!result.passed()) {
            fail(String.format("%s vs 5x %s FAIL: delta=%+.1f bb/100, SE=%.1f, t=%.2f (need delta > %.0f AND t > %.1f)",
                    hero.name(), villain.name(), result.meanDelta, result.seDelta,
                    result.tStatistic(), MIN_DELTA_BB100, MIN_SIGNIFICANCE_SIGMAS));
        }
    }

    private MatchupResult runMatchup(Bot.Difficulty hero, Bot.Difficulty villain) {
        BotStats aggHero = new BotStats(hero.name());
        BotStats aggVillain = new BotStats(villain.name() + "_AVG");
        double[] sessionDeltas = new double[SESSIONS_PER_MATCHUP];

        long startMs = System.currentTimeMillis();
        int totalHands = SESSIONS_PER_MATCHUP * HANDS_PER_SESSION;
        System.out.printf("%n[START] %s vs 5x %s (%d sessions × %d hands = %d hands total)%n",
                hero.name(), villain.name(), SESSIONS_PER_MATCHUP, HANDS_PER_SESSION, totalHands);
        System.out.flush();

        // Progress is reported every PROGRESS_STEP sessions so a hung run is
        // visible from outside (~10 progress lines per matchup).
        int progressStep = Math.max(1, SESSIONS_PER_MATCHUP / 10);

        for (int session = 0; session < SESSIONS_PER_MATCHUP; session++) {
            Bot.TRACKER_MEMORY.clear();
            long seed = BASE_SEED
                    ^ ((long) hero.ordinal() * 0x111111L)
                    ^ ((long) villain.ordinal() * 0x77777L)
                    ^ ((long) session * 0x10001L);
            MultiwaySimulator sim = new MultiwaySimulator(NUM_SEATS, seed,
                    STARTING_STACK, BIG_BLIND, evaluator);

            // Rotate hero across all six seats over the session loop so
            // positional bias from the button-rotation start point cancels.
            int heroSeat = session % NUM_SEATS;
            Bot.Difficulty[] diffs = new Bot.Difficulty[NUM_SEATS];
            for (int i = 0; i < NUM_SEATS; i++) {
                diffs[i] = (i == heroSeat) ? hero : villain;
            }
            sim.setSeatDifficulties(diffs);

            for (int i = 0; i < HANDS_PER_SESSION; i++) {
                sim.resetStacks();
                sim.playOneHand();
            }

            BotStats heroSessionStats = sim.stats(heroSeat);
            BotStats villainAggSession = aggregateVillainStats(sim, heroSeat);
            sessionDeltas[session] = heroSessionStats.bbPer100(BIG_BLIND)
                    - villainAggSession.bbPer100(BIG_BLIND);

            aggHero.add(heroSessionStats);
            aggVillain.add(villainAggSession);

            if (((session + 1) % progressStep == 0) || session == SESSIONS_PER_MATCHUP - 1) {
                long elapsed = (System.currentTimeMillis() - startMs) / 1000L;
                int sessionsDone = session + 1;
                int handsDone = sessionsDone * HANDS_PER_SESSION;
                int pct = (sessionsDone * 100) / SESSIONS_PER_MATCHUP;
                System.out.printf("  [%s vs 5x %s] %3d%% — %d/%d sessions (%d hands) — %ds elapsed%n",
                        hero.name(), villain.name(), pct,
                        sessionsDone, SESSIONS_PER_MATCHUP, handsDone, elapsed);
                System.out.flush();
            }
        }

        double mean = mean(sessionDeltas);
        double se = standardError(sessionDeltas, mean);
        return new MatchupResult(hero, villain, aggHero, aggVillain, mean, se);
    }

    private static BotStats aggregateVillainStats(MultiwaySimulator sim, int heroSeat) {
        BotStats agg = new BotStats("VILLAIN_AGG");
        for (int i = 0; i < sim.numSeats(); i++) {
            if (i == heroSeat) {
                continue;
            }
            agg.add(sim.stats(i));
        }
        return agg;
    }

    private static double mean(double[] xs) {
        double s = 0;
        for (double x : xs) {
            s += x;
        }
        return s / xs.length;
    }

    private static double standardError(double[] xs, double mean) {
        if (xs.length <= 1) {
            return 0;
        }
        double sse = 0;
        for (double x : xs) {
            double d = x - mean;
            sse += d * d;
        }
        double variance = sse / (xs.length - 1);
        return Math.sqrt(variance / xs.length);
    }

    private static final class MatchupResult {
        final Bot.Difficulty hero;
        final Bot.Difficulty villain;
        final BotStats heroStats;
        final BotStats villainStats;
        final double meanDelta;
        final double seDelta;

        MatchupResult(Bot.Difficulty hero, Bot.Difficulty villain, BotStats heroStats,
                      BotStats villainStats, double meanDelta, double seDelta) {
            this.hero = hero;
            this.villain = villain;
            this.heroStats = heroStats;
            this.villainStats = villainStats;
            this.meanDelta = meanDelta;
            this.seDelta = seDelta;
        }

        double tStatistic() {
            return seDelta == 0 ? Double.POSITIVE_INFINITY : meanDelta / seDelta;
        }

        boolean passed() {
            return meanDelta > MIN_DELTA_BB100
                    && Math.abs(tStatistic()) > MIN_SIGNIFICANCE_SIGMAS;
        }

        String report() {
            StringBuilder sb = new StringBuilder();
            sb.append('\n');
            sb.append(String.format("--- %s vs 5x %s (6-max, %d hands) ---%n",
                    hero.name(), villain.name(), SESSIONS_PER_MATCHUP * HANDS_PER_SESSION));
            sb.append(heroStats.summary(BIG_BLIND)).append('\n');
            sb.append(villainStats.summary(BIG_BLIND)).append('\n');
            sb.append(String.format("    DELTA bb/100 = %+.1f ± %.1f (t=%.2f) — %s",
                    meanDelta, seDelta, tStatistic(),
                    passed() ? "PASS" : "FAIL"));
            return sb.toString();
        }
    }
}
