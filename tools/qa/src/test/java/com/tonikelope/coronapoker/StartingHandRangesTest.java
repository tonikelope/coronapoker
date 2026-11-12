/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("bot-regression")
class StartingHandRangesTest {

    @Test
    void aTighterRangeDownweightsMarginalHandsWithoutEliminatingThem() {
        double[][] tight = StartingHandRanges.weights(0.20);
        double[][] wide = StartingHandRanges.weights(0.75);
        assertEquals(1, tight[12][25]); // AA
        assertTrue(tight[0][42] > 0); // 52o
        assertTrue(tight[23][8] < wide[23][8]); // QTo
        assertEquals(tight[23][8], tight[8][23]);
    }

    @Test
    void suitPermutationsHaveTheSameWeightAtEveryWidth() {
        for (int width = 1; width <= 20; width++) {
            double[][] weights = StartingHandRanges.weights(width / 20.0);
            for (int first = 0; first < 52; first++) {
                for (int second = first + 1; second < 52; second++) {
                    assertEquals(weights[first][second], weights[(first + 13) % 52][(second + 13) % 52]);
                }
            }
        }
    }
}
