/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("bot-regression")
class ThreeSeatShowdownTest {

    @Test
    void exactWeightedHoldingsMatchBruteForceForEveryEligiblePot() {
        int[] pairs = {pair(0, 1), pair(0, 4), pair(1, 2), pair(2, 3), pair(4, 5), pair(6, 7),
            pair(2, 8), pair(3, 9)};
        int[] relativeRanks = {2, 0, 1, 0, 2, 1, 2, 0};
        double[][] edgeWeights = {
            {1, 0.4, 0.8, 0.7, 0, 0.1, 0.45, 1},
            {0.2, 0.1, 0, 1, 0.9, 0.7, 0.4, 0.3},
            {0, 0.8, 0.4, 0, 0.5, 1, 0.2, 0.15}
        };
        int[] ranks = new int[52 * 52];
        double[][] weights = new double[3][ranks.length];
        for (int i = 0; i < pairs.length; i++) {
            ranks[pairs[i]] = relativeRanks[i];
            for (int seat = 0; seat < 3; seat++) {
                weights[seat][pairs[i]] = edgeWeights[seat][i];
            }
        }
        ThreeSeatShowdown showdown = new ThreeSeatShowdown(pairs, ranks, 1, weights);
        double total = 0;
        double[] payout = new double[8];
        for (int first = 0; first < pairs.length; first++) {
            for (int second = 0; second < pairs.length; second++) {
                for (int third = 0; third < pairs.length; third++) {
                    long a = cards(pairs[first]), b = cards(pairs[second]), c = cards(pairs[third]);
                    if (((a & b) | (a & c) | (b & c)) != 0) {
                        continue;
                    }
                    double mass = edgeWeights[0][first] * edgeWeights[1][second] * edgeWeights[2][third];
                    total += mass;
                    int[] outcomes = {relativeRanks[first], relativeRanks[second], relativeRanks[third]};
                    for (int eligible = 0; eligible < payout.length; eligible++) {
                        int shares = 1;
                        boolean beatsHero = false;
                        for (int seat = 0; seat < outcomes.length; seat++) {
                            if ((eligible & (1 << seat)) != 0) {
                                beatsHero |= outcomes[seat] > 1;
                                shares += outcomes[seat] == 1 ? 1 : 0;
                            }
                        }
                        if (!beatsHero) {
                            payout[eligible] += mass / shares;
                        }
                    }
                }
            }
        }
        for (int eligible = 0; eligible < payout.length; eligible++) {
            assertEquals(payout[eligible] / total, showdown.share(eligible), 1e-12, "pot mask " + eligible);
        }

        // A rival who cannot win the side pot still removes two cards.
        ThreeSeatShowdown unrestricted = new ThreeSeatShowdown(pairs, ranks, 1,
                new double[][]{weights[0], weights[1], null});
        assertNotEquals(unrestricted.share(3), showdown.share(3), 1e-3);
    }

    private static int pair(int first, int second) {
        return first * 52 + second;
    }

    private static long cards(int pair) {
        return (1L << (pair / 52)) | (1L << (pair % 52));
    }
}
