/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.bot.eval.MemoizedAlbertaEvaluator;
import java.util.Arrays;
import java.util.SplittableRandom;
import java.util.TreeSet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("bot-regression")
class MultiwayRangeShowdownTest {

    private static final int CARDS = 52;
    private static final int[] RIVER = {45, 46, 47, 48, 49};
    private static final int HERO = pair(50, 51);

    @ParameterizedTest
    @ValueSource(ints = {4, 9})
    void everySidePotMatchesExactEnumerationWithOverlappingRanges(int seats) {
        double[][] weights = new double[seats][CARDS * CARDS];
        int[] ranks = new int[CARDS * CARDS];
        ranks[HERO] = 1;
        for (int seat = 0; seat < seats; seat++) {
            int safe = pair(2 * seat, 2 * seat + 1);
            int overlap = pair(2 * seat + 1, (2 * seat + 2) % (2 * seats));
            int shared = pair(2 * seat, 40);
            weights[seat][safe] = 0.5 + 0.15 * seat;
            weights[seat][overlap] = 0.9;
            weights[seat][shared] = 0.8;
            ranks[safe] = seat % 2;
            ranks[overlap] = seat % 3;
            ranks[shared] = (seat + 1) % 3;
        }
        compare(weights, ranks, 4096, 1e-12);
    }

    @Test
    void sampledDenseRangesMatchAnIndependentEnumeration() {
        var rng = new SplittableRandom(0x4C7014A9L);
        double[][] weights = new double[4][CARDS * CARDS];
        int[] ranks = new int[CARDS * CARDS];
        ranks[HERO] = 1;
        for (double[] seat : weights) {
            int added = 0;
            while (added < 21) {
                int a = rng.nextInt(28);
                int b = rng.nextInt(28);
                int index = pair(a, b);
                if (a != b && seat[index] == 0) {
                    seat[index] = 0.25 + rng.nextDouble();
                    ranks[index] = rng.nextInt(3);
                    added++;
                }
            }
        }
        // 21^4 possible assignments exceed the engine's exact-enumeration budget.
        compare(weights, ranks, 8192, 0.018);
    }

    @Test
    void unobservedSeatBlocksCardsWithoutBeingIncludedInSidePot() {
        int fixed = pair(0, 1);
        int winning = pair(2, 3);
        int losing = pair(2, 4);
        int disjoint = pair(5, 6);
        int blocksLosing = pair(4, 7);
        int[] legal = {fixed, winning, losing, disjoint, blocksLosing};
        int[] ranks = new int[CARDS * CARDS];
        ranks[HERO] = 1;
        ranks[losing] = 2;
        double[][] weights = {new double[CARDS * CARDS], null, new double[CARDS * CARDS]};
        weights[0][fixed] = 1;
        weights[2][winning] = 1;
        weights[2][losing] = 3;
        var result = new MultiwayRangeShowdown(null, HERO, RIVER, ranks, legal, weights, 4096);
        assertEquals(0.4, result.share(0b100), 1e-12);
        assertEquals(1, result.share(0b011), 1e-12);
        assertEquals(0.4, result.share(0b111), 1e-12);
    }

    @Test
    void impossibleHoldingsDoNotFabricateAnEquity() {
        int index = pair(0, 1);
        double[][] weights = new double[4][CARDS * CARDS];
        for (double[] seat : weights) seat[index] = 1;
        var result = new MultiwayRangeShowdown(null, HERO, RIVER, new int[CARDS * CARDS],
                new int[]{index}, weights, 4096);
        assertEquals(1, result.share(0));
        assertTrue(Double.isNaN(result.share(15)));
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4})
    void opponentsShareTheSameRunoutWithoutReusingAWeightedHolding(int boardSize) {
        var evaluator = new MemoizedAlbertaEvaluator();
        int hero = pair(37, 38); // Kh Ah
        int[] board = Arrays.copyOf(new int[]{26, 31, 7, 41}, boardSize);
        int[] opponents = {pair(12, 25), pair(9, 22), pair(16, 29), pair(11, 24)};
        double[][] weights = new double[opponents.length][CARDS * CARDS];
        long used = (1L << 37) | (1L << 38);
        for (int card : board) used |= 1L << card;
        for (int seat = 0; seat < opponents.length; seat++) {
            int holding = opponents[seat];
            weights[seat][holding] = 1;
            used |= (1L << (holding / CARDS)) | (1L << (holding % CARDS));
        }
        var actual = new MultiwayRangeShowdown(evaluator, hero, board, null, opponents, weights, 8192);
        int[] hand = Arrays.copyOf(board, 7);
        double payoff = 0;
        int runouts = 0;
        for (int a = 0; a < CARDS; a++) {
            if ((used & (1L << a)) != 0) continue;
            hand[boardSize] = a;
            int first = boardSize == 3 ? a + 1 : 0;
            int limit = boardSize == 3 ? CARDS : 1;
            for (int b = first; b < limit; b++) {
                if (boardSize == 3) {
                    if ((used & (1L << b)) != 0) continue;
                    hand[4] = b;
                }
                int heroRank = rank(evaluator, hand, hero);
                int ties = 1;
                boolean loses = false;
                for (int opponent : opponents) {
                    int rank = rank(evaluator, hand, opponent);
                    loses |= rank > heroRank;
                    ties += rank == heroRank ? 1 : 0;
                }
                if (!loses) payoff += 1.0 / ties;
                runouts++;
            }
        }
        assertEquals(payoff / runouts, actual.share(15), 0.015);
    }

    private static int rank(MemoizedAlbertaEvaluator evaluator, int[] hand, int pair) {
        hand[5] = pair / CARDS;
        hand[6] = pair % CARDS;
        return evaluator.rankHand(hand);
    }

    private static void compare(double[][] weights, int[] ranks, int draws, double tolerance) {
        TreeSet<Integer> union = new TreeSet<>();
        int[][] holdings = new int[weights.length][];
        for (int i = 0; i < holdings.length; i++) {
            double[] seat = weights[i];
            holdings[i] = java.util.stream.IntStream.range(0, seat.length).filter(p -> seat[p] > 0).toArray();
            Arrays.stream(holdings[i]).forEach(union::add);
        }
        int[] legal = union.stream().mapToInt(Integer::intValue).toArray();
        var estimate = new MultiwayRangeShowdown(null, HERO, RIVER, ranks, legal, weights, draws);
        double[] payouts = new double[1 << weights.length];
        exhaustive(holdings, weights, ranks, 0, 0, 1, 0, 0, payouts);
        assertTrue(payouts[0] > 0);
        for (int mask = 0; mask < payouts.length; mask++) {
            assertEquals(payouts[mask] / payouts[0], estimate.share(mask), tolerance, "mask " + mask);
            if (mask > 0) assertTrue(estimate.share(mask) <= estimate.share(mask & (mask - 1)) + 1e-12);
        }
    }

    private static void exhaustive(int[][] holdings, double[][] weights, int[] ranks, int seat, long used,
            double weight, int lost, int tied, double[] payouts) {
        if (seat == holdings.length) {
            for (int mask = 0; mask < payouts.length; mask++) {
                if ((mask & lost) == 0) payouts[mask] += weight / (1 + Integer.bitCount(mask & tied));
            }
            return;
        }
        for (int pair : holdings[seat]) {
            long cards = (1L << (pair / CARDS)) | (1L << (pair % CARDS));
            if ((used & cards) == 0) {
                exhaustive(holdings, weights, ranks, seat + 1, used | cards, weight * weights[seat][pair],
                        lost | (ranks[pair] > 1 ? 1 << seat : 0), tied | (ranks[pair] == 1 ? 1 << seat : 0), payouts);
            }
        }
    }

    private static int pair(int a, int b) {
        return Math.min(a, b) * CARDS + Math.max(a, b);
    }
}
