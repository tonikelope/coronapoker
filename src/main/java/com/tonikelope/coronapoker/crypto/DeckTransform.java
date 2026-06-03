/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.math.BigInteger;
import java.util.Arrays;

/**
 * Deck transform for the verifiable-shuffle engine: a "shuffle step" applies a permutation and a
 * single common scalar to a deck of Ristretto points, {@code out[i] = k · deck[perm[i]]}. Plus the
 * permutation algebra the cut-and-choose proof needs (invert, compose), where the round identity is
 *
 * <pre>  B = apply(A, π, k)  ⇔  B = apply( apply(A, π1, k1), compose(invert(π1), π), k·k1⁻¹ )  </pre>
 *
 * i.e. any shuffle {@code A→B} factors through a fresh intermediate {@code C = apply(A, π1, k1)}.
 * That factorisation is exactly what lets a verifier check one half of the step per challenge round
 * without learning {@code (π, k)}.
 */
public final class DeckTransform {

    private DeckTransform() {
    }

    /** {@code out[i] = k · deck[perm[i]]}. {@code perm} must be a permutation of {@code 0..n-1}. */
    public static EdwardsPoint[] apply(EdwardsPoint[] deck, int[] perm, BigInteger k) {
        BigInteger kk = k.mod(EdwardsPoint.L);
        EdwardsPoint[] out = new EdwardsPoint[deck.length];
        for (int i = 0; i < deck.length; i++) {
            out[i] = deck[perm[i]].scalarMul(kk);
        }
        return out;
    }

    /** Uniform random permutation of {@code 0..n-1} (Fisher–Yates with the project CSPRNG). */
    public static int[] randomPermutation(int n) {
        int[] p = new int[n];
        for (int i = 0; i < n; i++) {
            p[i] = i;
        }
        for (int i = n - 1; i > 0; i--) {
            int j = Helpers.CSPRNG_GENERATOR.nextInt(i + 1); // unbiased
            int t = p[i];
            p[i] = p[j];
            p[j] = t;
        }
        return p;
    }

    /** Inverse permutation: {@code inv[perm[i]] = i}. */
    public static int[] invert(int[] perm) {
        int[] inv = new int[perm.length];
        for (int i = 0; i < perm.length; i++) {
            inv[perm[i]] = i;
        }
        return inv;
    }

    /** Composition with {@code (f∘g)[i] = f[g[i]]}. */
    public static int[] compose(int[] f, int[] g) {
        int[] out = new int[g.length];
        for (int i = 0; i < g.length; i++) {
            out[i] = f[g[i]];
        }
        return out;
    }

    /** True iff {@code perm} is a permutation of {@code 0..n-1}. */
    public static boolean isPermutation(int[] perm, int n) {
        if (perm == null || perm.length != n) {
            return false;
        }
        boolean[] seen = new boolean[n];
        for (int v : perm) {
            if (v < 0 || v >= n || seen[v]) {
                return false;
            }
            seen[v] = true;
        }
        return true;
    }

    /** Decks equal by canonical Ristretto encoding (same length, same points in order). */
    public static boolean decksEqual(EdwardsPoint[] a, EdwardsPoint[] b) {
        if (a == null || b == null || a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (!Arrays.equals(Ristretto255.encode(a[i]), Ristretto255.encode(b[i]))) {
                return false;
            }
        }
        return true;
    }
}
