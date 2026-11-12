/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.bot.eval;

/** Exact mass of three ordered, mutually disjoint two-card holdings. */
public final class DisjointHoldings {

    private DisjointHoldings() {}

    /** The weight matrices are symmetric and indexed by {@code first * cards + second}. */
    public static double weightedTriples(int cards, int[] pairs, double[] first, double[] second, double[] third) {
        double[] secondDegree = new double[cards];
        double[] thirdDegree = new double[cards];
        double[] jointEdgeByCard = new double[cards];
        double secondTotal = 0;
        double thirdTotal = 0;
        double jointEdges = 0;
        for (int pair : pairs) {
            int a = pair / cards;
            int b = pair % cards;
            secondDegree[a] += second[pair];
            secondDegree[b] += second[pair];
            thirdDegree[a] += third[pair];
            thirdDegree[b] += third[pair];
            secondTotal += second[pair];
            thirdTotal += third[pair];
            double overlap = second[pair] * third[pair];
            jointEdges += overlap;
            jointEdgeByCard[a] += overlap;
            jointEdgeByCard[b] += overlap;
        }

        double total = 0;
        for (int pair : pairs) {
            if (first[pair] == 0) {
                continue;
            }
            int a = pair / cards;
            int b = pair % cards;
            double left = secondTotal - secondDegree[a] - secondDegree[b] + second[pair];
            double right = thirdTotal - thirdDegree[a] - thirdDegree[b] + third[pair];
            double sameCard = 0;
            for (int card = 0; card < cards; card++) {
                if (card != a && card != b) {
                    int row = card * cards;
                    double leftCard = secondDegree[card] - second[row + a] - second[row + b];
                    double rightCard = thirdDegree[card] - third[row + a] - third[row + b];
                    sameCard += leftCard * rightCard;
                }
            }
            double samePair = jointEdges - jointEdgeByCard[a] - jointEdgeByCard[b] + second[pair] * third[pair];
            // After removing the first seat's cards, subtract holdings that
            // overlap. Identical holdings are counted for both of their cards.
            total += first[pair] * (left * right - sameCard + samePair);
        }
        return total;
    }
}
