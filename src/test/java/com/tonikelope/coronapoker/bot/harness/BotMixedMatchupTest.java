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
 * AAA acid test: each higher-difficulty bot must outperform each lower
 * difficulty in bb/100 over a heads-up sample large enough to make the
 * result statistically significant.
 *
 * <p>Each matchup is its own {@link Test} so the suite reports per-matchup
 * pass/fail (and surefire-parallel can run them concurrently if enabled in
 * the pom). Verdict is automated: {@code DELTA > MIN_DELTA_BB100} and
 * {@code DELTA > 2 × SE(DELTA)} → PASS, else FAIL. No human in the loop.</p>
 *
 * <p>Run with: {@code mvn test -Dtest=BotMixedMatchupTest}</p>
 */
class BotMixedMatchupTest {

    // 60 sessions × 50 hands = 3000 hands/matchup. Each session models one
    // CoronaPoker game where the bot's OpponentTracker accumulates throughout
    // the session (50 hands is well above the tracker's 10-hand threshold).
    // TRACKER_MEMORY is cleared between sessions because seat composition
    // alternates and the tracker must not carry data between different
    // difficulty assignments at the same seat.
    private static final int SESSIONS_PER_MATCHUP = 60;
    private static final int HANDS_PER_SESSION = 50;
    private static final long BASE_SEED = 0xC0C0FEEDDEADBEEFL;
    private static final float STARTING_STACK = 200f;
    private static final float BIG_BLIND = 2f;

    // AAA quality gate: a matchup PASSES when DELTA > 50 AND DELTA > 2 × SE.
    private static final double MIN_DELTA_BB100 = 50.0;
    private static final double MIN_SIGNIFICANCE_SIGMAS = 2.0;

    private final BotEvaluator evaluator = new AlbertaEvaluatorAdapter();

    @BeforeAll
    static void silenceBotChatter() {
        Logger.getLogger(Bot.class.getName()).setLevel(Level.WARNING);
    }

    @Test
    @DisplayName("EXPERT > HARD")
    void expertBeatsHard() {
        evaluate(Bot.Difficulty.EXPERT, Bot.Difficulty.HARD);
    }

    @Test
    @DisplayName("EXPERT > MEDIUM")
    void expertBeatsMedium() {
        evaluate(Bot.Difficulty.EXPERT, Bot.Difficulty.MEDIUM);
    }

    @Test
    @DisplayName("EXPERT > EASY")
    void expertBeatsEasy() {
        evaluate(Bot.Difficulty.EXPERT, Bot.Difficulty.EASY);
    }

    @Test
    @DisplayName("HARD > MEDIUM")
    void hardBeatsMedium() {
        evaluate(Bot.Difficulty.HARD, Bot.Difficulty.MEDIUM);
    }

    @Test
    @DisplayName("HARD > EASY")
    void hardBeatsEasy() {
        evaluate(Bot.Difficulty.HARD, Bot.Difficulty.EASY);
    }

    @Test
    @DisplayName("MEDIUM > EASY")
    void mediumBeatsEasy() {
        evaluate(Bot.Difficulty.MEDIUM, Bot.Difficulty.EASY);
    }

    private void evaluate(Bot.Difficulty hi, Bot.Difficulty lo) {
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
