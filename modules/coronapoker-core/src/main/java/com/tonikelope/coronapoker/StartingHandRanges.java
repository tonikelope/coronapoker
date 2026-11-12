/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Soft, suit-symmetric opening ranges for modeling a preflop aggressor. */
final class StartingHandRanges {

    private static final int CARDS = 52;
    private static final int WIDTHS = 20;
    private static final double[][][] WEIGHTS = createWeights();

    private StartingHandRanges() {
    }

    static double[][] weights(double fraction) {
        int slot = (int) Math.round(fraction * WIDTHS) - 1;
        return WEIGHTS[Math.max(0, Math.min(WIDTHS - 1, slot))];
    }

    private static double[][][] createWeights() {
        List<Combination> hands = new ArrayList<>();
        for (int first = 0; first < CARDS; first++) {
            for (int second = first + 1; second < CARDS; second++) {
                hands.add(new Combination(first, second, score(first, second)));
            }
        }
        hands.sort(Comparator.comparingInt(Combination::score).reversed());

        double[][][] weights = new double[WIDTHS][CARDS][CARDS];
        for (int from = 0; from < hands.size();) {
            int to = from + 1;
            while (to < hands.size() && hands.get(to).score() == hands.get(from).score()) {
                to++;
            }
            double percentile = (from + to) / (2.0 * hands.size());
            for (int slot = 0; slot < WIDTHS; slot++) {
                double cutoff = (slot + 1.0) / WIDTHS;
                double membership = Math.max(0, Math.min(1, (cutoff + 0.05 - percentile) / 0.10));
                // Leave some weight outside the opening range for mixed play.
                double weight = 0.08 + 0.92 * membership;
                for (int index = from; index < to; index++) {
                    Combination hand = hands.get(index);
                    weights[slot][hand.first()][hand.second()] = weight;
                    weights[slot][hand.second()][hand.first()] = weight;
                }
            }
            from = to;
        }
        return weights;
    }

    private static int score(int first, int second) {
        int high = Math.max(first % 13, second % 13);
        int low = Math.min(first % 13, second % 13);
        boolean pair = high == low;
        boolean suited = first / 13 == second / 13;
        int tier = Bot.evaluateHandTier(high, low, pair, suited, high - low);
        return (6 - tier) * 100 + 4 * high + 3 * low + (pair ? 24 : suited ? 3 : 0);
    }

    private record Combination(int first, int second, int score) {}
}
