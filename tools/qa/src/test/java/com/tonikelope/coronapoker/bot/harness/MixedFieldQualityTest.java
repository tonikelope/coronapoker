/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.bot.harness;

import com.tonikelope.coronapoker.Bot;
import com.tonikelope.coronapoker.bot.eval.MemoizedAlbertaEvaluator;
import com.tonikelope.coronapoker.bot.harness.FixedStrategyBot.Strategy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("slow")
@Tag("bot-campaign")
class MixedFieldQualityTest {

    private static final float BIG_BLIND = 2;
    private static final int SEATS = 6;
    private static final int SESSIONS = QaConfig.sessions(144);
    private static final int HANDS = QaConfig.hands(50);
    private static final int[] DEPTHS = {30, 45, 60, 90, 120, 160};

    @BeforeAll
    static void quietBots() {
        Logger.getLogger(Bot.class.getName()).setLevel(Level.WARNING);
    }

    @Test
    void hardWinsAgainstAMixedFieldWithUnevenStacks() {
        SessionEstimate estimate = play(0x68D1219FF3340AD6L,
                List.of(Strategy.TAG, Strategy.TAG, Strategy.ROCK, Strategy.STATION, Strategy.MANIAC));
        assertTrue(estimate.clears(30, 2), () -> "Mixed-field HARD result: " + estimate);
    }

    @Test
    void hardWinsWhenSelectiveOpponentsOutnumberTheAggressor() {
        SessionEstimate estimate = play(0x670119E6ADE5024BL,
                List.of(Strategy.TAG, Strategy.TAG, Strategy.TAG, Strategy.ROCK, Strategy.MANIAC));
        assertTrue(estimate.clears(10, 2), () -> "Selective-field HARD result: " + estimate);
    }

    private SessionEstimate play(long baseSeed, List<Strategy> lineup) {
        double[] samples = new double[SESSIONS];
        var evaluator = new MemoizedAlbertaEvaluator();
        for (int session = 0; session < samples.length; session++) {
            long seed = QaConfig.seed(baseSeed) + 0x9E3779B97F4A7C15L * session;
            var sim = new MultiwaySimulator(SEATS, seed, 200, BIG_BLIND, evaluator);
            int hero = session % SEATS;
            List<Strategy> opponents = new ArrayList<>(lineup);
            Collections.shuffle(opponents, new Random(seed ^ 0xE3914A2L));
            for (int seat = 0, villain = 0; seat < SEATS; seat++) {
                if (seat == hero) {
                    Bot bot = new Bot(sim.player(seat), new Random(seed ^ 0x5EED5EEDL));
                    bot.setDifficulty(Bot.Difficulty.HARD);
                    sim.setBot(seat, bot);
                } else {
                    sim.setBot(seat, new FixedStrategyBot(sim.player(seat),
                            opponents.get(villain++), seed ^ (0xF1ED000DEAL + seat)));
                }
            }
            for (int hand = 0; hand < HANDS; hand++) {
                for (int seat = 0; seat < SEATS; seat++) {
                    int depth = DEPTHS[(seat - hero + SEATS + session / SEATS) % SEATS];
                    sim.player(seat).setStack(depth * BIG_BLIND);
                }
                sim.playOneHand();
            }
            samples[session] = sim.stats(hero).bbPer100(BIG_BLIND);
        }
        SessionEstimate result = SessionEstimate.of(samples);
        System.out.printf("HARD vs %s, %d hands: %+.1f bb/100 (session SE %.1f, t=%.2f)%n",
                lineup, SESSIONS * HANDS, result.mean(), result.standardError(), result.tStatistic());
        return result;
    }
}
