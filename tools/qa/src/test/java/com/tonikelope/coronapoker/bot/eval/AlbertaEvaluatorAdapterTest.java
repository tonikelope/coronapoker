/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.bot.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Sanity-checks the Alberta adapter against well-known poker scenarios.
 *
 * Card indices follow the canonical encoding: index = rank + suit * 13, where
 * rank in [0..12] = [2..A] and suit in [0..3] = [c, d, h, s].
 */
class AlbertaEvaluatorAdapterTest {

    private final AlbertaEvaluatorAdapter eval = new AlbertaEvaluatorAdapter();

    // --- Card helpers -------------------------------------------------------

    private static final int CLUBS = 0, DIAMONDS = 1, HEARTS = 2, SPADES = 3;

    private static int card(int rank, int suit) {
        return rank + suit * 13;
    }

    /** rank in 2..14 (2..A). */
    private static int c(int rank2to14, int suit) {
        return (rank2to14 - 2) + suit * 13;
    }

    // --- Hand strength ------------------------------------------------------

    @Test
    @DisplayName("Pocket aces on a dry rag flop is near-certain to win heads-up")
    void aaCrushesRagFlop() {
        int aceClubs = c(14, CLUBS);
        int aceDiamonds = c(14, DIAMONDS);
        int[] board = {c(3, SPADES), c(7, HEARTS), c(2, CLUBS)};

        double strength = eval.handStrength(aceClubs, aceDiamonds, board);
        assertTrue(strength > 0.85, "AA on 372 should dominate; got " + strength);
    }

    @Test
    @DisplayName("Underpair beats high-card holding on the same overcard-heavy board")
    void underpairBeatsHighCardOnly() {
        int[] board = {c(14, SPADES), c(13, HEARTS), c(12, CLUBS)};
        double underpair = eval.handStrength(c(2, CLUBS), c(2, DIAMONDS), board);
        double airHand = eval.handStrength(c(8, CLUBS), c(7, DIAMONDS), board);
        assertTrue(underpair > airHand,
                "22 (pair) must beat 87o (air) on AKQ; got 22=" + underpair + " 87=" + airHand);
    }

    @Test
    @DisplayName("handStrengthVsN against many opponents converges below heads-up value")
    void strengthDecaysVsMoreOpponents() {
        int aceHearts = c(14, HEARTS);
        int kingHearts = c(13, HEARTS);
        int[] board = {c(2, CLUBS), c(7, DIAMONDS), c(9, SPADES)};

        double hu = eval.handStrengthVsN(aceHearts, kingHearts, board, 1);
        double sixHanded = eval.handStrengthVsN(aceHearts, kingHearts, board, 5);
        assertTrue(hu > sixHanded,
                "Equity must drop with more opponents (hu=" + hu + ", 6m=" + sixHanded + ")");
    }

    // --- Hand potential -----------------------------------------------------

    @Test
    @DisplayName("Flush draw on flop shows positive ppot")
    void flushDrawHasPositivePpot() {
        int aceHearts = c(14, HEARTS);
        int kingHearts = c(13, HEARTS);
        int[] board = {c(2, HEARTS), c(7, HEARTS), c(9, CLUBS)};

        Potential p = eval.potential(aceHearts, kingHearts, board, false);
        assertTrue(p.ppot() > 0.10,
                "AKhh on 2h7h9c should have meaningful ppot; got " + p.ppot());
    }

    @Test
    @DisplayName("River board: potential collapses to zero (board complete)")
    void riverHasZeroPotential() {
        int aceClubs = c(14, CLUBS);
        int aceDiamonds = c(14, DIAMONDS);
        int[] board = {c(3, SPADES), c(7, HEARTS), c(2, CLUBS), c(11, DIAMONDS), c(5, HEARTS)};

        Potential p = eval.potential(aceClubs, aceDiamonds, board, false);
        assertEquals(0.0, p.ppot(), 1e-9);
        assertEquals(0.0, p.npot(), 1e-9);
    }

    // --- Hand ranking -------------------------------------------------------

    @Test
    @DisplayName("Aces full beats trip kings at showdown")
    void compareHandsPicksBest() {
        // Board: AcKhKs9c8h
        // Hand A: hole AsAd → AsAdAc trips + KhKs pair = aces full of kings
        int[] handA = {c(14, SPADES), c(14, DIAMONDS),
                       c(14, CLUBS), c(13, HEARTS), c(13, SPADES), c(9, CLUBS), c(8, HEARTS)};
        // Hand B: hole KcQd → KcKhKs trips with A and Q kickers (no full house)
        int[] handB = {c(13, CLUBS), c(12, DIAMONDS),
                       c(14, CLUBS), c(13, HEARTS), c(13, SPADES), c(9, CLUBS), c(8, HEARTS)};

        assertEquals(1, eval.compareHands(handA, handB),
                "Aces full should beat trip kings");
    }

    @Test
    @DisplayName("Identical hands tie")
    void identicalHandsTie() {
        int[] hand = {c(14, CLUBS), c(14, DIAMONDS), c(13, HEARTS), c(13, SPADES), c(9, CLUBS),
                      c(3, DIAMONDS), c(8, HEARTS)};
        assertEquals(0, eval.compareHands(hand, hand));
    }

    // --- Range equity -------------------------------------------------------

    @Test
    @DisplayName("handStrengthVsRange against a uniform range matches handStrength")
    void uniformRangeMatchesUniformOpponent() {
        int aceClubs = c(14, CLUBS);
        int aceDiamonds = c(14, DIAMONDS);
        int[] board = {c(3, SPADES), c(7, HEARTS), c(2, CLUBS)};

        double uniform = eval.handStrength(aceClubs, aceDiamonds, board);

        double[][] flat = new double[52][52];
        for (int i = 0; i < 52; i++) {
            for (int j = 0; j < 52; j++) {
                if (i != j) {
                    flat[i][j] = 1.0;
                }
            }
        }
        double weighted = eval.handStrengthVsRange(aceClubs, aceDiamonds, board, flat);
        // The weighted path accumulates 1220 doubles in a slightly different order than
        // Alberta's handRank, so float rounding leaves a tiny ULP-scale discrepancy.
        assertEquals(uniform, weighted, 1e-3);
    }
}
