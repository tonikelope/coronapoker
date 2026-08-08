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

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * Pedersen <b>vector</b> commitment over Ristretto255: {@code C = r·H + Σ a_i·G_i}, where the
 * {@code G_i} and {@code H} are nothing-up-my-sleeve generators with unknown mutual discrete logs
 * (derived by hash-to-group). Perfectly hiding, computationally binding under discrete log.
 *
 * <p>This is the foundational commitment of the Bayer–Groth verifiable shuffle (see
 * {@code docs/SECURITY.md}): it lets the prover commit to a whole vector (e.g. a
 * permutation, or a row of the deck) in a single group element, and its homomorphic
 * structure (point addition / scalar scaling of commitments, exercised directly on
 * {@link EdwardsPoint}s by the argument verifiers) is what the product /
 * multi-exponentiation arguments exploit.
 *
 * <p>Generators are cached and grown on demand up to the requested length. All scalars are reduced
 * mod the group order L; commitments are canonical Ristretto encodings (byte equality = point
 * equality).
 */
public final class PedersenVectorCommit {

    private static final String DOMAIN = "CoronaPoker/PedersenVec/v1/";

    /** Blinding generator H. */
    public static final EdwardsPoint H = deriveGen("H");

    private static volatile EdwardsPoint[] gens = new EdwardsPoint[0];

    // Fixed-base comb for the single-value case r·H + v·G_0 (the single-value commit, ~360 per
    // prove): H and G_0 are constants, so we precompute a Lim-Lee-style comb table per generator
    // once and reuse it across every hand. Processing COMB_COLS(=32) columns takes ~32 doublings
    // instead of the ~256 a Straus ladder needs, with the SAME result (same group point). Tables
    // hold 2^h points per generator (h=COMB_TEETH=8), a one-time sub-ms cost. Curve addition is
    // abelian, so a different summation order can't change the final point or its canonical
    // encoding (see PedersenCombTest).
    private static final int COMB_TEETH = 8;   // h: index bits per column
    private static final int COMB_COLS = 32;   // b = 256 / h (covers reduced scalars < 2^253 < 2^256)
    private static final EdwardsPoint[] COMB_H = buildCombTable(H);
    private static final EdwardsPoint[] COMB_G0 = buildCombTable(generator(0));

    private PedersenVectorCommit() {
    }

    private static EdwardsPoint deriveGen(String label) {
        try {
            byte[] seed = MessageDigest.getInstance("SHA-512")
                    .digest((DOMAIN + label).getBytes(StandardCharsets.UTF_8));
            return Ristretto255.hashToGroup(seed);
        } catch (Exception e) {
            throw new IllegalStateException("PedersenVec generator init failed", e);
        }
    }

    /** The i-th vector generator G_i (cached, grown on demand). */
    public static EdwardsPoint generator(int i) {
        if (i < 0) {
            throw new IllegalArgumentException("negative generator index");
        }
        // Lock-free fast path on the volatile copy-on-write array: every proof calls this per
        // commitment (G_0 above all), and the background verifier threads would otherwise contend
        // on a lock that is only needed to grow the cache.
        EdwardsPoint[] g = gens;
        if (i < g.length) {
            return g[i];
        }
        return growGenerators(i);
    }

    private static synchronized EdwardsPoint growGenerators(int i) {
        if (i >= gens.length) {
            EdwardsPoint[] grown = Arrays.copyOf(gens, i + 1);
            for (int j = gens.length; j <= i; j++) {
                grown[j] = deriveGen("G/" + j);
            }
            gens = grown;
        }
        return gens[i];
    }

    /** Commit to the vector {@code a} with blinding {@code r}: canonical encoding of {@code r·H + Σ a_i·G_i}. */
    public static byte[] commit(BigInteger[] a, BigInteger r) {
        // Single-value case (r·H + v·G_0), by far the most frequent in proofs (~360 per prove):
        // fixed-base combs over H and G_0 (constants) instead of the ~256-doubling Straus ladder.
        // Same group point => byte-identical encoding (see PedersenCombTest).
        if (a.length == 1) {
            return Ristretto255.encode(combRHplusVG0(r, a[0]));
        }
        // Single Straus multi-scalar multiplication over [H, G_0, …, G_{n-1}] (shared doubling ladder).
        int n = a.length;
        BigInteger[] scalars = new BigInteger[n + 1];
        EdwardsPoint[] points = new EdwardsPoint[n + 1];
        scalars[0] = r;
        points[0] = H;
        for (int i = 0; i < n; i++) {
            scalars[i + 1] = a[i];
            points[i + 1] = generator(i);
        }
        return Ristretto255.encode(EdwardsPoint.multiscalarMul(scalars, points));
    }

    // Fixed-base comb table for p: base[row] = 2^(row·COMB_COLS)·p; t[j] = Σ_{row set in j} base[row].
    private static EdwardsPoint[] buildCombTable(EdwardsPoint p) {
        EdwardsPoint[] base = new EdwardsPoint[COMB_TEETH];
        base[0] = p;
        EdwardsPoint cur = p;
        for (int row = 1; row < COMB_TEETH; row++) {
            for (int k = 0; k < COMB_COLS; k++) {
                cur = cur.dbl(); // cur = 2^(row·COMB_COLS)·p
            }
            base[row] = cur;
        }
        int size = 1 << COMB_TEETH;
        EdwardsPoint[] t = new EdwardsPoint[size];
        t[0] = EdwardsPoint.IDENTITY;
        for (int j = 1; j < size; j++) {
            t[j] = t[j & (j - 1)].add(base[Integer.numberOfTrailingZeros(j)]);
        }
        return t;
    }

    // Comb index for column `col`: bit `row` is set iff bit (row·COMB_COLS + col) of s is set.
    private static int combIndex(BigInteger s, int col) {
        int idx = 0;
        for (int row = 0; row < COMB_TEETH; row++) {
            if (s.testBit(row * COMB_COLS + col)) {
                idx |= (1 << row);
            }
        }
        return idx;
    }

    // r·H + v·G_0 using the fixed-base combs (shared doubling ladder). Byte-identical to
    // EdwardsPoint.multiscalarMul([r, v], [H, G_0]); reduces mod L the same way.
    private static EdwardsPoint combRHplusVG0(BigInteger r, BigInteger v) {
        BigInteger rr = r.mod(EdwardsPoint.L);
        BigInteger vv = v.mod(EdwardsPoint.L);
        EdwardsPoint result = EdwardsPoint.IDENTITY;
        for (int col = COMB_COLS - 1; col >= 0; col--) {
            result = result.dbl();
            int ih = combIndex(rr, col);
            if (ih != 0) {
                result = result.add(COMB_H[ih]);
            }
            int ig = combIndex(vv, col);
            if (ig != 0) {
                result = result.add(COMB_G0[ig]);
            }
        }
        return result;
    }
}
