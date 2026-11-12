/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.bot.eval.DisjointHoldings;
import java.util.Arrays;

/** Exact weighted river shares with three dealt opponent hands. */
final class ThreeSeatShowdown {

    private static final int CARDS = 52;
    private static final int PAIRS = CARDS * CARDS;
    private static final double TIE_OFFSET = Math.sqrt(1.0 / 12);
    private final int[] legalPairs;
    private final byte[] outcomes;
    private final double[][] ranges;
    private final double[][] current = new double[3][PAIRS];
    private final double[] shares = new double[8];
    private final double total;
    private final boolean tiesPossible;

    ThreeSeatShowdown(int[] legalPairs, int[] ranks, int heroRank, double[][] ranges) {
        this.legalPairs = legalPairs;
        this.ranges = ranges;
        outcomes = new byte[PAIRS];
        boolean tie = false;
        for (int pair : legalPairs) {
            outcomes[pair] = (byte) Integer.compare(heroRank, ranks[pair]);
            tie |= outcomes[pair] == 0;
        }
        tiesPossible = tie;
        total = mass(0, 1);
        Arrays.fill(shares, Double.NaN);
        shares[0] = 1;
    }

    double share(int mask) {
        if (Double.isNaN(shares[mask]) && total > 0) {
            // With up to three ties, the payout numerator is a cubic in x.
            // Integrating x^ties over [0,1] gives each split-pot share.
            double payout = tiesPossible
                    ? (mass(mask, 0.5 - TIE_OFFSET) + mass(mask, 0.5 + TIE_OFFSET)) / 2
                    : mass(mask, 0);
            shares[mask] = Math.max(0, Math.min(1, payout / total));
        }
        return shares[mask];
    }

    private double mass(int mask, double tieWeight) {
        for (int seat = 0; seat < 3; seat++) {
            double[] range = ranges[seat];
            boolean eligible = (mask & (1 << seat)) != 0;
            for (int pair : legalPairs) {
                double weight = range == null ? 1 : range[pair];
                if (eligible && outcomes[pair] <= 0) {
                    weight *= outcomes[pair] == 0 ? tieWeight : 0;
                }
                current[seat][pair] = current[seat][pair % CARDS * CARDS + pair / CARDS] = weight;
            }
        }

        return DisjointHoldings.weightedTriples(CARDS, legalPairs, current[0], current[1], current[2]);
    }
}
