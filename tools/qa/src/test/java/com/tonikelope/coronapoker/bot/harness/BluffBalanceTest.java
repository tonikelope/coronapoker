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
import com.tonikelope.coronapoker.bot.eval.MemoizedAlbertaEvaluator;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioural quality gate for the "feel human, not a robot" axis. The bb/100
 * gradient proves the levels are <em>distinguishable</em>; these tests prove the
 * top level is not a <em>readable</em> value-only bot — it bluffs the river a
 * credible fraction of the time against a foldable opponent, yet stays
 * disciplined and (almost) never bluffs a calling station that can't fold.
 *
 * <p>A river that is bet ~0% as a bluff means a human can fold every river to the
 * bot and never be wrong; a river bluffed 50% is spew. The accepted band is the
 * polarised-but-sane range a thinking player shows.</p>
 */
class BluffBalanceTest {

    private static final int SESSIONS = 120;
    private static final int HANDS_PER_SESSION = 30;
    private static final long BASE_SEED = 0xB10FFBA1A0CEL;
    private static final float STARTING_STACK = 200f;
    private static final float BIG_BLIND = 2f;

    private final BotEvaluator evaluator = new MemoizedAlbertaEvaluator();

    @BeforeAll
    static void silence() {
        Logger.getLogger(Bot.class.getName()).setLevel(Level.WARNING);
    }

    /** Run HARD vs one fixed-strategy archetype and return HARD's aggregate river-bluff %. */
    private double hardRiverBluffPctVs(FixedStrategyBot.Strategy opp) {
        BotStats hard = new BotStats("HARD");
        for (int session = 0; session < SESSIONS; session++) {
            Bot.TRACKER_MEMORY.clear();
            long seed = BASE_SEED ^ (opp.ordinal() * 0x12345L) ^ (session * 0x10001L);
            HeadsUpSimulator sim = new HeadsUpSimulator(seed, STARTING_STACK, BIG_BLIND, evaluator);
            boolean hardIsA = (session % 2 == 0);
            Bot hardBot = new Bot(hardIsA ? sim.playerA() : sim.playerB());
            hardBot.setDifficulty(Bot.Difficulty.HARD);
            FixedStrategyBot oppBot = new FixedStrategyBot(
                    hardIsA ? sim.playerB() : sim.playerA(), opp, seed ^ 0xF1ED000DEAL);
            if (hardIsA) {
                sim.setBotA(hardBot);
                sim.setBotB(oppBot);
            } else {
                sim.setBotA(oppBot);
                sim.setBotB(hardBot);
            }
            for (int i = 0; i < HANDS_PER_SESSION; i++) {
                sim.resetStacks();
                sim.playOneHand(i % 2 == 0);
            }
            hard.add(hardIsA ? sim.statsA() : sim.statsB());
        }
        System.out.printf("[bluff balance] HARD vs %-7s : river bluff = %.1f%% (%d/%d river bets), turn bluff = %.1f%%%n",
                opp.name(), hard.riverBluffPct(), hard.riverBluffBets, hard.riverBets, hard.turnBluffPct());
        return hard.riverBluffPct();
    }

    @Test
    @DisplayName("HARD bluffs the river a credible amount against a foldable opponent (not a value-only robot)")
    void hardBluffsCredibleAmountVsFoldable() {
        // TAG folds a meaningful share of rivers, so there is fold equity to attack.
        double rvBluff = hardRiverBluffPctVs(FixedStrategyBot.Strategy.TAG);
        assertTrue(rvBluff >= 3.0,
                "HARD should mix bluffs into its river range vs a foldable opponent; got " + rvBluff + "%");
        assertTrue(rvBluff <= 35.0,
                "HARD river range should be polarised, not spewy; got " + rvBluff + "%");
    }

    @Test
    @DisplayName("HARD stays disciplined and (almost) never bluffs a calling station")
    void hardDisciplinedVsStation() {
        // A station never folds: there is no fold equity, so bluffing is pure -EV.
        double rvBluff = hardRiverBluffPctVs(FixedStrategyBot.Strategy.STATION);
        assertTrue(rvBluff <= 5.0,
                "HARD must not keep bluffing a never-folding station; got " + rvBluff + "%");
    }

    @Test
    @DisplayName("The memoized evaluator the harness runs on is numerically identical to Alberta")
    void evaluatorParityHoldsOnSpotCheck() {
        // Cheap belt-and-braces parity check so a regression in MemoizedHandPotential
        // (which every matchup test relies on) shows up here too, not only in its own test.
        AlbertaEvaluatorAdapter alberta = new AlbertaEvaluatorAdapter();
        int aceHearts = 12 + 2 * 13, kingHearts = 11 + 2 * 13;
        int[] flop = {0 + 2 * 13, 5 + 2 * 13, 7}; // 2h 7h 9c
        var a = alberta.potential(aceHearts, kingHearts, flop, true);
        var f = evaluator.potential(aceHearts, kingHearts, flop, true);
        assertTrue(Math.abs(a.ppot() - f.ppot()) < 1e-9 && Math.abs(a.npot() - f.npot()) < 1e-9,
                "Memoized evaluator diverged from Alberta: alberta=" + a + " fast=" + f);
    }
}
