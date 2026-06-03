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
 * {@code docs/sra-bayer-groth-shuffle.md}): it lets the prover commit to a whole vector (e.g. a
 * permutation, or a row of the deck) in a single group element, and the homomorphism
 * ({@link #add}, {@link #scale}) is what the product / multi-exponentiation arguments exploit.
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
    public static synchronized EdwardsPoint generator(int i) {
        if (i < 0) {
            throw new IllegalArgumentException("negative generator index");
        }
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
        EdwardsPoint c = H.scalarMul(r.mod(EdwardsPoint.L));
        for (int i = 0; i < a.length; i++) {
            c = c.add(generator(i).scalarMul(a[i].mod(EdwardsPoint.L)));
        }
        return Ristretto255.encode(c);
    }

    /** True iff {@code commitment} opens to {@code (a, r)}. */
    public static boolean verify(byte[] commitment, BigInteger[] a, BigInteger r) {
        return commitment != null && Arrays.equals(commitment, commit(a, r));
    }

    /** Homomorphic add: {@code Comm(a,r) ⊕ Comm(b,s) = Comm(a+b, r+s)} (vectors of equal length). */
    public static byte[] add(byte[] c1, byte[] c2) {
        EdwardsPoint p1 = Ristretto255.decode(c1);
        EdwardsPoint p2 = Ristretto255.decode(c2);
        if (p1 == null || p2 == null) {
            return null;
        }
        return Ristretto255.encode(p1.add(p2));
    }

    /** Homomorphic scalar scaling: {@code e·Comm(a,r) = Comm(e·a, e·r)}. */
    public static byte[] scale(byte[] c, BigInteger e) {
        EdwardsPoint p = Ristretto255.decode(c);
        if (p == null) {
            return null;
        }
        return Ristretto255.encode(p.scalarMul(e.mod(EdwardsPoint.L)));
    }
}
