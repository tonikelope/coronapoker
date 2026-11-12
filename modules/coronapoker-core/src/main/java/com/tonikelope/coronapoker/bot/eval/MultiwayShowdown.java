/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.bot.eval;

/** Showdown shares with card removal between opponent holdings. */
final class MultiwayShowdown {

    // Integrating x^k over [0, 1] gives the payout when k opponents tie.
    // This quadrature integrates every polynomial through degree nine exactly.
    private static final double[] TIE_VALUES = {0.046910077030668, 0.230765344947158, 0.5,
        0.769234655052842, 0.953089922969332};
    private static final double[] QUADRATURE = {0.118463442528095, 0.239314335249683, 0.284444444444444,
        0.239314335249683, 0.118463442528095};

    private MultiwayShowdown() {}

    static void addShares(double[] equity, double win, double tie, double winWin, double winTie,
            double tieTie, byte[] riverOutcomes, int cards) {
        if (Math.abs(winWin - (2 * win - 1)) < 1e-12) {
            // No two opponents can both hold a non-winning combination for
            // the hero. Sum those mutually exclusive losses and half chops.
            double lossOfShare = 1 - win - tie / 2;
            for (int n = 3; n < equity.length; n++) {
                equity[n] += 1 - n * lossOfShare;
            }
            return;
        }
        if (tie == 0) {
            double triple = riverOutcomes == null || win == 0 || win == 1 ? Double.NaN
                    : exactThree(riverOutcomes, cards, 0);
            addPowers(equity, 1, win, winWin, triple);
            return;
        }
        for (int i = 0; i < TIE_VALUES.length; i++) {
            double x = TIE_VALUES[i];
            double triple = riverOutcomes == null || tie == 1 ? Double.NaN
                    : exactThree(riverOutcomes, cards, x);
            addPowers(equity, QUADRATURE[i], win + x * tie,
                    winWin + x * (2 * winTie + x * tieTie), triple);
        }
    }

    private static void addPowers(double[] equity, double weight, double single, double pair, double triple) {
        if (pair <= 0) {
            return;
        }
        boolean hasExactTriple = !Double.isNaN(triple);
        double share = hasExactTriple ? Math.max(0, Math.min(pair, triple)) : pair;
        double secondConditional = pair / single;
        double conditional = hasExactTriple ? share / pair : secondConditional;
        double correlation = conditional / (hasExactTriple ? secondConditional : single);
        if (hasExactTriple) {
            equity[3] += weight * share;
        }
        for (int n = hasExactTriple ? 4 : 3; n < equity.length; n++) {
            // Reuse the measured change in the probability of surviving one
            // more opponent as the deck gets smaller. Shares stay monotone.
            conditional = Math.min(1, conditional * correlation);
            share *= conditional;
            equity[n] += weight * share;
        }
    }

    private static double exactThree(byte[] outcomes, int cards, double tieValue) {
        int[] legalPairs = new int[(int) pairs(cards)];
        double[] weights = new double[outcomes.length];
        int next = 0;
        for (int first = 0; first < cards; first++) {
            for (int second = first + 1; second < cards; second++) {
                int pair = first * cards + second;
                int outcome = outcomes[pair];
                double weight = outcome == 1 ? 1 : outcome == 2 ? tieValue : 0;
                weights[pair] = weights[second * cards + first] = weight;
                legalPairs[next++] = pair;
            }
        }
        double triples = DisjointHoldings.weightedTriples(cards, legalPairs, weights, weights, weights);
        return triples / (pairs(cards) * pairs(cards - 2) * pairs(cards - 4));
    }

    private static double pairs(int cards) {
        return cards * (cards - 1) / 2.0;
    }
}
