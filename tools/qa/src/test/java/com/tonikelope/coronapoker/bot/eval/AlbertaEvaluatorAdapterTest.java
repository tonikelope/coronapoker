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

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Sanity-checks the Alberta adapter against well-known poker scenarios.
 *
 * Card indices follow the canonical encoding: index = rank + suit * 13, where
 * rank in [0..12] = [2..A] and suit in [0..3] = [c, d, h, s].
 */
@Tag("bot-regression")
class AlbertaEvaluatorAdapterTest {

    private final AlbertaEvaluatorAdapter eval = new AlbertaEvaluatorAdapter();

    // --- Card helpers -------------------------------------------------------
    private static final int CLUBS = 0, DIAMONDS = 1, HEARTS = 2, SPADES = 3;

    private static int card(int rank, int suit) {
        return rank + suit * 13;
    }

    /**
     * rank in 2..14 (2..A).
     */
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

    @Test
    void weightedFullFlopWorksWithoutNativeEvaluator() {
        int ace = c(14, HEARTS);
        int king = c(13, HEARTS);
        int[] board = {c(2, HEARTS), c(7, HEARTS), c(9, CLUBS)};
        double[][] flat = new double[52][52];
        for (double[] row : flat) {
            Arrays.fill(row, 1.0);
        }
        Potential uniform = eval.potential(ace, king, board, true);
        Potential weighted = new MemoizedAlbertaEvaluator().potentialVsRange(ace, king, board, flat, true);
        assertTrue(uniform.ppot() > 0);
        assertEquals(uniform.ppot(), weighted.ppot(), 1e-12);
        assertEquals(uniform.npot(), weighted.npot(), 1e-12);
    }

    @Test
    void turnPotentialMatchesExhaustiveTerminalEquity() {
        assertTurnEquity(c(14, HEARTS), c(13, HEARTS),
                new int[]{c(2, HEARTS), c(7, HEARTS), c(9, CLUBS), c(4, SPADES)}, 53537);
        assertTurnEquity(c(14, CLUBS), c(14, DIAMONDS),
                new int[]{c(3, SPADES), c(7, HEARTS), c(2, CLUBS), c(11, DIAMONDS)}, 81052);
    }

    private void assertTurnEquity(int h1, int h2, int[] board, int expectedHalfWins) {
        boolean[] used = new boolean[52];
        for (int card : board) {
            used[card] = true;
        }
        used[h1] = used[h2] = true;
        int[] hero = Arrays.copyOf(board, 7);
        int[] opponent = Arrays.copyOf(board, 7);
        hero[5] = h1;
        hero[6] = h2;
        int outcomes = 0;
        int halfWins = 0;
        for (int first = 0; first < 52; first++) {
            if (used[first]) {
                continue;
            }
            for (int second = first + 1; second < 52; second++) {
                if (used[second]) {
                    continue;
                }
                opponent[5] = first;
                opponent[6] = second;
                for (int river = 0; river < 52; river++) {
                    if (used[river] || river == first || river == second) {
                        continue;
                    }
                    hero[4] = opponent[4] = river;
                    int comparison = eval.compareHands(hero, opponent);
                    halfWins += comparison == 1 ? 2 : comparison == 0 ? 1 : 0;
                    outcomes++;
                }
            }
        }
        assertEquals(45540, outcomes);
        assertEquals(expectedHalfWins, halfWins);
        double expected = halfWins / (2.0 * outcomes);
        double[][] flat = new double[52][52];
        for (double[] row : flat) {
            Arrays.fill(row, 1.0);
        }
        BotEvaluator memoized = new MemoizedAlbertaEvaluator();
        double strength = eval.handStrength(h1, h2, board);
        for (Potential p : new Potential[]{
            eval.potential(h1, h2, board, false),
            memoized.potential(h1, h2, board, false),
            memoized.potentialVsRange(h1, h2, board, flat, false)
        }) {
            assertEquals(expected, strength + (1 - strength) * p.ppot() - strength * p.npot(), 1e-10);
        }
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

    @Test
    @DisplayName("Straight flush ranking uses the suited straight high card")
    void straightFlushRankingUsesItsOwnHighCard() {
        int[] board = {c(3, HEARTS), c(4, HEARTS), c(5, HEARTS), c(6, HEARTS), c(7, CLUBS)};
        int[] sixHigh = {c(2, HEARTS), c(8, DIAMONDS),
            board[0], board[1], board[2], board[3], board[4]};
        int[] sevenHigh = {c(7, HEARTS), c(9, CLUBS),
            board[0], board[1], board[2], board[3], board[4]};

        assertTrue(eval.rankHand(sevenHigh) > eval.rankHand(sixHigh));
        assertEquals(2, eval.compareHands(sixHigh, sevenHigh));
        assertEquals(946.0 / 990.0,
                eval.showdownOdds(c(2, HEARTS), c(8, DIAMONDS), board).equity(1), 1e-12);

        int[] wheel = {c(14, HEARTS), c(2, HEARTS), c(3, HEARTS), c(4, HEARTS),
            c(5, HEARTS), c(6, DIAMONDS), c(7, CLUBS)};
        int[] sixHighSpades = {c(2, SPADES), c(3, SPADES), c(4, SPADES), c(5, SPADES),
            c(6, SPADES), c(13, DIAMONDS), c(12, CLUBS)};
        assertTrue(eval.rankHand(sixHighSpades) > eval.rankHand(wheel));
        assertEquals(2, eval.compareHands(wheel, sixHighSpades));
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
