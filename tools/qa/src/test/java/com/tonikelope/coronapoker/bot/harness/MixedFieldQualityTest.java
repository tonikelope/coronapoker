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
    private static final int SESSIONS = QaConfig.sessions(144);
    private static final int HANDS = QaConfig.hands(50);
    private static final int[] DEPTHS = {30, 45, 60, 90, 120, 160};
    private static final int[] WIDE_DEPTHS = {15, 25, 40, 65, 100, 160};

    @BeforeAll
    static void quietBots() {
        Logger.getLogger(Bot.class.getName()).setLevel(Level.WARNING);
    }

    @Test
    void hardWinsAgainstAMixedFieldWithUnevenStacks() {
        SessionEstimate estimate = play(0x68D1219FF3340AD6L,
                List.of(Strategy.TAG, Strategy.TAG, Strategy.ROCK, Strategy.STATION, Strategy.MANIAC), DEPTHS);
        assertTrue(estimate.clears(30, 2), () -> "Mixed-field HARD result: " + estimate);
    }

    @Test
    void hardWinsWhenSelectiveOpponentsOutnumberTheAggressor() {
        SessionEstimate estimate = play(0x670119E6ADE5024BL,
                List.of(Strategy.TAG, Strategy.TAG, Strategy.TAG, Strategy.ROCK, Strategy.MANIAC), DEPTHS);
        assertTrue(estimate.clears(10, 2), () -> "Selective-field HARD result: " + estimate);
    }

    @Test
    void hardCompetesAtThreeHandedTablesWithShortAndDeepStacks() {
        SessionEstimate estimate = play(0x42D31399D1L, List.of(Strategy.TAG, Strategy.TAG), WIDE_DEPTHS);
        assertTrue(estimate.mean() >= -20, () -> "Three-handed HARD result: " + estimate);
    }

    @Test
    void hardWinsAtNineHandedTablesWithMixedRanges() {
        SessionEstimate estimate = play(0x59103DA3L, List.of(Strategy.TAG, Strategy.TAG, Strategy.STATION,
                Strategy.ROCK, Strategy.TAG, Strategy.TAG, Strategy.STATION, Strategy.ROCK), WIDE_DEPTHS);
        assertTrue(estimate.clears(40, 2), () -> "Nine-handed HARD result: " + estimate);
    }

    private SessionEstimate play(long baseSeed, List<Strategy> lineup, int[] depths) {
        double[] samples = new double[SESSIONS];
        var evaluator = new MemoizedAlbertaEvaluator();
        int seats = lineup.size() + 1;
        for (int session = 0; session < samples.length; session++) {
            long seed = QaConfig.seed(baseSeed) + 0x9E3779B97F4A7C15L * session;
            var sim = new MultiwaySimulator(seats, seed, 200, BIG_BLIND, evaluator);
            int hero = session % seats;
            List<Strategy> opponents = new ArrayList<>(lineup);
            Collections.shuffle(opponents, new Random(seed ^ 0xE3914A2L));
            for (int seat = 0, villain = 0; seat < seats; seat++) {
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
                for (int seat = 0; seat < seats; seat++) {
                    int clockwise = (seat - hero + seats) % seats;
                    int depth = depths[(clockwise + session / seats) % depths.length];
                    sim.player(seat).setStack(depth * BIG_BLIND);
                }
                sim.playOneHand();
            }
            samples[session] = sim.stats(hero).bbPer100(BIG_BLIND);
        }
        SessionEstimate result = SessionEstimate.of(samples);
        System.out.printf("%d seats, HARD vs %s, %d hands: %+.1f bb/100 (session SE %.1f, t=%.2f)%n",
                seats, lineup, SESSIONS * HANDS, result.mean(), result.standardError(), result.tStatistic());
        return result;
    }
}
