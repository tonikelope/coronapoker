/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.bot.eval;

import org.alberta.poker.Card;
import org.alberta.poker.Hand;
import org.alberta.poker.HandEvaluator;

/**
 * Memoized re-implementation of the University of Alberta hand-potential
 * calculation ({@code HandPotential.ppot_raw}, Papp 1998 §5.3) for the offline
 * QA harness.
 *
 * <p><b>Why this exists.</b> The production bot computes PPot/NPot through
 * {@link AlbertaEvaluatorAdapter}, which calls {@code HandPotential.ppot_raw} —
 * the variant with <em>no</em> rank caching. On the flop (full two-card
 * look-ahead) that evaluates a 7-card hand ~2.7 million times, the overwhelming
 * majority of them redundant. With no native {@code libeval} on Windows every
 * one of those goes through {@code rankHand_Java}, which is what made the QA
 * benchmark suite take hours.</p>
 *
 * <p>The obvious fix — switching the adapter to {@code HandPotential.ppot} (the
 * cached variant) — is a trap: its {@code getCachedRank} helpers call
 * {@code HandEvaluator.rankHand7}, a <em>native method with no Java fallback</em>
 * ({@code CRankHandFast7}). On Windows that throws {@code UnsatisfiedLinkError}.
 * That is exactly why the adapter uses {@code ppot_raw} in the first place.</p>
 *
 * <p>This class ports the algorithm into our own package (the {@code org.alberta}
 * library is treated as immutable) using only {@code HandEvaluator.rankHand}
 * (which has the {@code rankHand_Java} fallback) and adds two caches:</p>
 * <ul>
 *   <li><b>our-hand cache</b>: {@code rank(hole + board + future)} does not depend
 *       on the opponent's cards, so it is computed once per future card (1-card)
 *       or future pair (2-card) and reused across every opponent pair.</li>
 *   <li><b>opponent-hand cache</b>: {@code rank(opp + board + future)} keyed by the
 *       bitmask of the varying cards. A primitive open-addressing {@code long->int}
 *       table is used rather than {@code HashMap<Long,Integer>}: profiling showed
 *       the boxing in the latter ate most of the cache's benefit (2.6x), whereas
 *       the primitive table reaches ~7x.</li>
 * </ul>
 *
 * <p>The counting (AHEAD/TIED/BEHIND tallies, the {@code mult} normaliser of
 * 990/45, the den/num formulas) is identical to {@code ppot_raw}; addition is
 * commutative so enumeration order is irrelevant. {@code MemoizedHandPotentialTest}
 * asserts ppot/npot equality against the Alberta original over thousands of
 * random spots. <b>Not thread-safe</b> — one instance per simulator thread.</p>
 */
final class MemoizedHandPotential {

    private static final int AHEAD = 0;
    private static final int TIED = 1;
    private static final int BEHIND = 2;

    private final HandEvaluator he;

    // Reusable primitive open-addressing cache for opponent 7-card ranks,
    // keyed by the bitmask of the varying cards. An "epoch" stamp avoids
    // clearing the arrays between ppot calls.
    private long[] oppKeys;
    private int[] oppVals;
    private int[] oppStamp;
    private int oppMask;
    private int epoch = 0;

    MemoizedHandPotential(HandEvaluator he) {
        this.he = he;
    }

    /**
     * @return {@code [ppot, npot]}. Mirrors {@code HandPotential.ppot_raw}: a
     * two-card look-ahead is performed only when {@code board.length == 3 && full};
     * a 4-card board does a one-card look-ahead; 5 cards (or fewer than 3) yield
     * {@code [0, 0]}.
     */
    double[] ppotNpot(int hole1, int hole2, int[] board, boolean full) {
        int n = board.length;
        if (n < 3 || n >= 5) {
            return new double[]{0.0, 0.0};
        }
        boolean twoCard = (n == 3 && full);

        Hand bd = new Hand();
        for (int b : board) {
            bd.addCard(b);
        }
        Card c1 = new Card(hole1);
        Card c2 = new Card(hole2);
        int ourrank5 = he.rankHand(c1, c2, bd);

        boolean[] used = new boolean[52];
        used[hole1] = true;
        used[hole2] = true;
        for (int b : board) {
            used[b] = true;
        }
        int[] live = new int[52 - 2 - n];
        int t = 0;
        for (int c = 0; c < 52; c++) {
            if (!used[c]) {
                live[t++] = c;
            }
        }

        double[][] HP = new double[3][3];
        double[] HPTotal = new double[3];

        // our-hand 7-card rank caches (independent of opponent cards)
        int[] ourC1 = null;
        int[][] ourC2 = null;
        if (twoCard) {
            ourC2 = new int[52][52];
            for (int[] row : ourC2) {
                java.util.Arrays.fill(row, -1);
            }
        } else {
            ourC1 = new int[52];
            java.util.Arrays.fill(ourC1, -1);
        }
        // opponent 7-card rank cache: size generously to keep the load factor low
        // (flop two-card has at most C(47,4) ~= 178k distinct keys).
        ensureOppCapacity(twoCard ? (1 << 19) : (1 << 15));
        nextEpoch();

        for (int ii = 0; ii < live.length; ii++) {
            int o1 = live[ii];
            Card co1 = new Card(o1);
            for (int jj = ii + 1; jj < live.length; jj++) {
                int o2 = live[jj];
                Card co2 = new Card(o2);

                int opprank5 = he.rankHand(co1, co2, bd);
                int index = ourrank5 > opprank5 ? AHEAD : (ourrank5 == opprank5 ? TIED : BEHIND);
                HPTotal[index]++;

                for (int kk = 0; kk < live.length; kk++) {
                    if (kk == ii || kk == jj) {
                        continue;
                    }
                    int k = live[kk];
                    if (twoCard) {
                        for (int ll = kk + 1; ll < live.length; ll++) {
                            if (ll == ii || ll == jj) {
                                continue;
                            }
                            int l = live[ll];
                            int our7 = ourRank2(ourC2, hole1, hole2, board, k, l);
                            int opp7 = oppRank(o1, o2, board, k, l);
                            if (our7 > opp7) {
                                HP[index][AHEAD]++;
                            } else if (our7 == opp7) {
                                HP[index][TIED]++;
                            } else {
                                HP[index][BEHIND]++;
                            }
                        }
                    } else {
                        int our7 = ourRank1(ourC1, hole1, hole2, board, k);
                        int opp7 = oppRank(o1, o2, board, k, -1);
                        if (our7 > opp7) {
                            HP[index][AHEAD]++;
                        } else if (our7 == opp7) {
                            HP[index][TIED]++;
                        } else {
                            HP[index][BEHIND]++;
                        }
                    }
                }
            }
        }

        int mult = twoCard ? 990 : 45;
        double den1 = mult * (HPTotal[BEHIND] + HPTotal[TIED] / 2.0);
        double den2 = mult * (HPTotal[AHEAD] + HPTotal[TIED] / 2.0);
        double ppot = den1 > 0
                ? (HP[BEHIND][AHEAD] + HP[BEHIND][TIED] / 2.0 + HP[TIED][AHEAD] / 2.0) / den1
                : 0.0;
        double npot = den2 > 0
                ? (HP[AHEAD][BEHIND] + HP[AHEAD][TIED] / 2.0 + HP[TIED][BEHIND] / 2.0) / den2
                : 0.0;
        return new double[]{ppot, npot};
    }

    private int ourRank1(int[] cache, int h1, int h2, int[] board, int k) {
        if (cache[k] >= 0) {
            return cache[k];
        }
        Hand h = new Hand();
        h.addCard(h1);
        h.addCard(h2);
        for (int b : board) {
            h.addCard(b);
        }
        h.addCard(k);
        int r = he.rankHand(h);
        cache[k] = r;
        return r;
    }

    private int ourRank2(int[][] cache, int h1, int h2, int[] board, int k, int l) {
        int a = Math.min(k, l);
        int b = Math.max(k, l);
        if (cache[a][b] >= 0) {
            return cache[a][b];
        }
        Hand h = new Hand();
        h.addCard(h1);
        h.addCard(h2);
        for (int c : board) {
            h.addCard(c);
        }
        h.addCard(k);
        h.addCard(l);
        int r = he.rankHand(h);
        cache[a][b] = r;
        return r;
    }

    private int oppRank(int o1, int o2, int[] board, int k, int l) {
        long key = (1L << o1) | (1L << o2) | (1L << k);
        if (l >= 0) {
            key |= (1L << l);
        }
        int idx = (int) (mix(key) & oppMask);
        while (oppStamp[idx] == epoch) {
            if (oppKeys[idx] == key) {
                return oppVals[idx];
            }
            idx = (idx + 1) & oppMask;
        }
        Hand h = new Hand();
        h.addCard(o1);
        h.addCard(o2);
        for (int b : board) {
            h.addCard(b);
        }
        h.addCard(k);
        if (l >= 0) {
            h.addCard(l);
        }
        int r = he.rankHand(h);
        oppStamp[idx] = epoch;
        oppKeys[idx] = key;
        oppVals[idx] = r;
        return r;
    }

    private void ensureOppCapacity(int size) {
        if (oppKeys == null || oppKeys.length < size) {
            oppKeys = new long[size];
            oppVals = new int[size];
            oppStamp = new int[size];
            oppMask = size - 1;
            epoch = 0; // stamps are freshly zeroed; first nextEpoch() makes them stale
        }
    }

    private void nextEpoch() {
        epoch++;
        if (epoch == 0) { // wrapped after ~2 billion calls: reset stamps
            java.util.Arrays.fill(oppStamp, 0);
            epoch = 1;
        }
    }

    /** SplitMix64 finaliser — spreads the sparse card bitmask across all bits. */
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
