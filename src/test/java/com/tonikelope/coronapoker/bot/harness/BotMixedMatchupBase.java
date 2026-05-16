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
 * Shared infrastructure for the per-matchup mixed-difficulty acid tests.
 * The split into one concrete class per matchup lets surefire fork each
 * matchup into its own JVM (forkCount in pom.xml). Each matchup runs in
 * parallel and gets its own clean {@link Bot#TRACKER_MEMORY}.
 *
 * <p>This class deliberately does not end in {@code Test} so surefire
 * does not try to run it on its own.</p>
 */
abstract class BotMixedMatchupBase {

    // 200 sessions × 50 hands = 10000 hands/matchup. The per-session
    // delta SE in HU NLHE is roughly 400/sqrt(50) ~ 56 bb/100 per
    // session, so with 200 sessions the matchup-level SE drops to
    // ~4 bb/100 — comfortably below the +50 PASS threshold so DELTAs
    // of +50 register as solid PASS rather than noise. Doubles
    // wall-clock runtime but the calibration signal becomes legible.
    protected static final int SESSIONS_PER_MATCHUP = 200;
    protected static final int HANDS_PER_SESSION = 50;
    protected static final long BASE_SEED = 0xC0C0FEEDDEADBEEFL;
    protected static final float STARTING_STACK = 200f;
    protected static final float BIG_BLIND = 2f;

    // A matchup PASSES when DELTA > 50 AND DELTA > 2 × SE.
    protected static final double MIN_DELTA_BB100 = 50.0;
    protected static final double MIN_SIGNIFICANCE_SIGMAS = 2.0;

    protected final BotEvaluator evaluator = new AlbertaEvaluatorAdapter();

    @BeforeAll
    static void silenceBotChatter() {
        Logger.getLogger(Bot.class.getName()).setLevel(Level.WARNING);
    }

    protected void evaluate(Bot.Difficulty hi, Bot.Difficulty lo) {
        MatchupResult result = runMatchup(hi, lo);
        System.out.println(result.report());
        if (!result.passed()) {
            fail(String.format("%s vs %s FAIL: delta=%+.1f bb/100, SE=%.1f, t=%.2f (need delta > %.0f AND t > %.1f)",
                    hi.name(), lo.name(), result.meanDelta, result.seDelta,
                    result.tStatistic(), MIN_DELTA_BB100, MIN_SIGNIFICANCE_SIGMAS));
        }
    }

    private MatchupResult runMatchup(Bot.Difficulty hi, Bot.Difficulty lo) {
        BotStats aggHi = new BotStats(hi.name());
        BotStats aggLo = new BotStats(lo.name());
        double[] sessionDeltas = new double[SESSIONS_PER_MATCHUP];

        for (int session = 0; session < SESSIONS_PER_MATCHUP; session++) {
            Bot.TRACKER_MEMORY.clear();
            long seed = BASE_SEED
                    ^ ((long) hi.ordinal() * 0x111111L)
                    ^ ((long) lo.ordinal() * 0x77777L)
                    ^ (session * 0x10001L);
            HeadsUpSimulator sim = new HeadsUpSimulator(seed, STARTING_STACK, BIG_BLIND, evaluator);

            boolean hiIsA = (session % 2 == 0);
            sim.setBotDifficulties(hiIsA ? hi : lo, hiIsA ? lo : hi);

            for (int i = 0; i < HANDS_PER_SESSION; i++) {
                sim.resetStacks();
                sim.playOneHand(i % 2 == 0);
            }

            BotStats hiThisSession = hiIsA ? sim.statsA() : sim.statsB();
            BotStats loThisSession = hiIsA ? sim.statsB() : sim.statsA();
            sessionDeltas[session] = hiThisSession.bbPer100(BIG_BLIND) - loThisSession.bbPer100(BIG_BLIND);

            aggHi.add(hiThisSession);
            aggLo.add(loThisSession);
        }

        double mean = mean(sessionDeltas);
        double se = standardError(sessionDeltas, mean);
        return new MatchupResult(hi, lo, aggHi, aggLo, mean, se);
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
        final Bot.Difficulty hi;
        final Bot.Difficulty lo;
        final BotStats hiStats;
        final BotStats loStats;
        final double meanDelta;
        final double seDelta;

        MatchupResult(Bot.Difficulty hi, Bot.Difficulty lo, BotStats hiStats, BotStats loStats,
                      double meanDelta, double seDelta) {
            this.hi = hi;
            this.lo = lo;
            this.hiStats = hiStats;
            this.loStats = loStats;
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
            sb.append(String.format("--- %s vs %s ---%n", hi.name(), lo.name()));
            sb.append(hiStats.summary(BIG_BLIND)).append('\n');
            sb.append(loStats.summary(BIG_BLIND)).append('\n');
            sb.append(String.format("    DELTA bb/100 = %+.1f ± %.1f (t=%.2f) — %s",
                    meanDelta, seDelta, tStatistic(),
                    passed() ? "PASS" : "FAIL"));
            return sb.toString();
        }
    }
}
