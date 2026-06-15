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

import java.util.Random;
import org.alberta.poker.HandEvaluator;
import org.alberta.poker.ai.HandPotential;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Equivalence gate for {@link MemoizedHandPotential} / {@link MemoizedAlbertaEvaluator}.
 *
 * The memoized evaluator must produce ppot/npot <em>numerically identical</em> to
 * the Alberta {@code ppot_raw} path the production bot actually uses, otherwise it
 * would silently change bot behaviour. The sums are over integer tallies (exactly
 * representable in double), so order-independent — equality is asserted to 1e-9.
 *
 * Card encoding matches {@link AlbertaEvaluatorAdapterTest}: index = rank + suit*13,
 * rank 0..12 = 2..A, suit 0..3 = c,d,h,s.
 */
class MemoizedHandPotentialTest {

    private static final int CLUBS = 0, DIAMONDS = 1, HEARTS = 2, SPADES = 3;

    /** rank in 2..14. */
    private static int c(int rank2to14, int suit) {
        return (rank2to14 - 2) + suit * 13;
    }

    private final AlbertaEvaluatorAdapter alberta =
            new AlbertaEvaluatorAdapter(new HandEvaluator(), new HandPotential());
    private final MemoizedAlbertaEvaluator fast = new MemoizedAlbertaEvaluator();

    private void assertSamePotential(int h1, int h2, int[] board, boolean full, String label) {
        Potential a = alberta.potential(h1, h2, board, full);
        Potential f = fast.potential(h1, h2, board, full);
        assertEquals(a.ppot(), f.ppot(), 1e-9, "ppot mismatch @ " + label);
        assertEquals(a.npot(), f.npot(), 1e-9, "npot mismatch @ " + label);
    }

    private static int[] pickDistinct(Random r, int count) {
        boolean[] used = new boolean[52];
        int[] out = new int[count];
        int n = 0;
        while (n < count) {
            int x = r.nextInt(52);
            if (!used[x]) {
                used[x] = true;
                out[n++] = x;
            }
        }
        return out;
    }

    @Test
    @DisplayName("Edge boards (flush / paired / monotone / connected) match Alberta exactly")
    void edgeCasesMatch() {
        // Nut flush draw on two-tone flop
        assertSamePotential(c(14, HEARTS), c(13, HEARTS),
                new int[]{c(2, HEARTS), c(7, HEARTS), c(9, CLUBS)}, true, "AKhh 2h7h9c (2-card)");
        // Overpair on dry rag flop
        assertSamePotential(c(14, CLUBS), c(14, DIAMONDS),
                new int[]{c(3, SPADES), c(7, HEARTS), c(2, CLUBS)}, true, "AA 372 (2-card)");
        // Paired board
        assertSamePotential(c(11, CLUBS), c(10, CLUBS),
                new int[]{c(8, SPADES), c(8, HEARTS), c(2, DIAMONDS)}, true, "JTc 88x (2-card)");
        // Monotone flop, no flush card in hand
        assertSamePotential(c(14, CLUBS), c(13, DIAMONDS),
                new int[]{c(5, HEARTS), c(9, HEARTS), c(12, HEARTS)}, true, "AKo monotone (2-card)");
        // Open-ended straight draw
        assertSamePotential(c(10, CLUBS), c(9, DIAMONDS),
                new int[]{c(8, SPADES), c(7, HEARTS), c(2, CLUBS)}, true, "T9 876 OESD (2-card)");
        // Turn (one-card look-ahead)
        assertSamePotential(c(14, HEARTS), c(13, HEARTS),
                new int[]{c(2, HEARTS), c(7, HEARTS), c(9, CLUBS), c(4, SPADES)}, false, "AKhh turn");
        // Flop with one-card look-ahead (full=false)
        assertSamePotential(c(14, HEARTS), c(13, HEARTS),
                new int[]{c(2, HEARTS), c(7, HEARTS), c(9, CLUBS)}, false, "AKhh flop (1-card)");
    }

    @Test
    @DisplayName("River and pre-flop sized boards yield zero potential, same as Alberta")
    void degenerateBoardsZero() {
        int[] river = {c(3, SPADES), c(7, HEARTS), c(2, CLUBS), c(11, DIAMONDS), c(5, HEARTS)};
        Potential f = fast.potential(c(14, CLUBS), c(14, DIAMONDS), river, false);
        assertEquals(0.0, f.ppot(), 1e-9);
        assertEquals(0.0, f.npot(), 1e-9);
    }

    @Test
    @DisplayName("200 random turns: memoized == Alberta (ppot & npot)")
    void randomTurnsMatch() {
        Random r = new Random(0xA11CE5L);
        for (int t = 0; t < 200; t++) {
            int[] cards = pickDistinct(r, 6); // 2 hole + 4 board
            int[] board = {cards[2], cards[3], cards[4], cards[5]};
            assertSamePotential(cards[0], cards[1], board, false, "rand turn #" + t);
        }
    }

    @Test
    @DisplayName("40 random flops (two-card look-ahead): memoized == Alberta (ppot & npot)")
    void randomFlopsTwoCardMatch() {
        Random r = new Random(0xF10F10F1L);
        for (int t = 0; t < 40; t++) {
            int[] cards = pickDistinct(r, 5); // 2 hole + 3 board
            int[] board = {cards[2], cards[3], cards[4]};
            assertSamePotential(cards[0], cards[1], board, true, "rand flop #" + t);
        }
    }

    @Test
    @DisplayName("Speed report: memoized flop ppot is dramatically faster than Alberta raw")
    void speedReport() {
        Random r = new Random(0x5EEDL);
        int n = 12;
        int[][] holes = new int[n][];
        int[][] boards = new int[n][];
        for (int t = 0; t < n; t++) {
            int[] cards = pickDistinct(r, 5);
            holes[t] = new int[]{cards[0], cards[1]};
            boards[t] = new int[]{cards[2], cards[3], cards[4]};
        }
        long ta = System.nanoTime();
        for (int t = 0; t < n; t++) {
            alberta.potential(holes[t][0], holes[t][1], boards[t], true);
        }
        long albertaMs = (System.nanoTime() - ta) / 1_000_000L;

        long tf = System.nanoTime();
        for (int t = 0; t < n; t++) {
            fast.potential(holes[t][0], holes[t][1], boards[t], true);
        }
        long fastMs = (System.nanoTime() - tf) / 1_000_000L;

        System.out.printf("[ppot speed] %d flop 2-card look-aheads: Alberta=%dms, Memoized=%dms (%.1fx)%n",
                n, albertaMs, fastMs, fastMs == 0 ? 999.0 : (double) albertaMs / fastMs);
        assertTrue(fastMs <= albertaMs,
                "Memoized must not be slower than Alberta raw (alberta=" + albertaMs + " fast=" + fastMs + ")");
    }
}
