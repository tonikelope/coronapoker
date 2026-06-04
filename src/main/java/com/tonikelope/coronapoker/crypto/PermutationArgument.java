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

/**
 * Permutation argument: given single-value Pedersen commitments {@code C_d'[i] = Comm([d'_i], s_i)},
 * proves the committed vector {@code d'} is a permutation of a PUBLIC vector {@code d} — in zero
 * knowledge (the permutation stays hidden). The combinatorial heart of the Bayer–Groth shuffle
 * (see {@code docs/sra-bayer-groth-shuffle.md}).
 *
 * <p>Construction (Neff/Bayer–Groth characterisation). A multiset equals another iff the polynomials
 * {@code Π(X − d'_i)} and {@code Π(X − d_i)} are identical. The prover commits {@code d'} first, then a
 * challenge {@code x} is drawn by Fiat–Shamir (so it cannot be anticipated); the prover shows
 * {@code Π(x − d'_i) == Π(x − d_i)} via a {@link ProductArgument} over the homomorphically-derived
 * factors {@code C_a[i] = x·G_0 ⊖ C_d'[i]} (committing {@code a_i = x − d'_i}) against the public target
 * {@code b = Π(x − d_i)}. By Schwartz–Zippel, if the multisets differ the degree-{@code n} polynomials
 * disagree at a random {@code x} except with probability {@code ≤ n/L} (negligible), so the argument is
 * sound.
 */
public final class PermutationArgument {

    private static final String FS_DOMAIN = "SRA/PermutationArg/v1";
    private static final BigInteger L = EdwardsPoint.L;

    private PermutationArgument() {
    }

    public static final class Proof {
        final ProductArgument.Proof product;

        Proof(ProductArgument.Proof product) {
            this.product = product;
        }
    }

    /** Fiat–Shamir challenge {@code x} bound to the public vector {@code d} and the commitments {@code C_d'}. */
    private static BigInteger challenge(BigInteger[] d, byte[][] cdprime) {
        Transcript tr = new Transcript(FS_DOMAIN);
        tr.absorb("n", new byte[]{(byte) (d.length >>> 8), (byte) d.length});
        for (int i = 0; i < d.length; i++) {
            tr.absorbScalar("d" + i, d[i].mod(L));
        }
        for (int i = 0; i < cdprime.length; i++) {
            tr.absorb("Cd" + i, cdprime[i]);
        }
        return tr.challengeScalar("x");
    }

    /**
     * Derived factor commitments {@code C_a[i] = x·G_0 ⊖ C_d'[i]} (committing {@code a_i = x − d'_i}).
     * The subtraction is a free point negation ({@code ⊖C = −C}, same element {@code (L−1)·C} names),
     * not a scalar multiplication; {@code null} for any {@code C_d'[i]} that fails to decode.
     */
    private static byte[][] factorCommitments(BigInteger x, byte[][] cdprime) {
        EdwardsPoint xG0 = PedersenVectorCommit.generator(0).scalarMul(x.mod(L));
        byte[][] ca = new byte[cdprime.length][];
        for (int i = 0; i < cdprime.length; i++) {
            EdwardsPoint cd = Ristretto255.decode(cdprime[i]);
            ca[i] = cd == null ? null : Ristretto255.encode(xG0.add(cd.negate()));
        }
        return ca;
    }

    /** Public target {@code b = Π(x − d_i) mod L}. */
    private static BigInteger publicTarget(BigInteger x, BigInteger[] d) {
        BigInteger b = BigInteger.ONE;
        for (BigInteger di : d) {
            b = b.multiply(x.subtract(di)).mod(L);
        }
        return b;
    }

    /**
     * Prove that {@code d'} (committed in {@code cdprime[i] = Comm([d'_i], s_i)}) is a permutation of the
     * public vector {@code d}. Requires {@code d'.length == d.length} and the openings {@code (d', s)}.
     */
    public static Proof prove(BigInteger[] dprime, BigInteger[] s, byte[][] cdprime, BigInteger[] d) {
        BigInteger x = challenge(d, cdprime);
        byte[][] ca = factorCommitments(x, cdprime);
        BigInteger[] a = new BigInteger[dprime.length];
        BigInteger[] ra = new BigInteger[dprime.length];
        for (int i = 0; i < dprime.length; i++) {
            a[i] = x.subtract(dprime[i]).mod(L);
            ra[i] = s[i].negate().mod(L);
        }
        BigInteger b = publicTarget(x, d);
        return new Proof(ProductArgument.prove(a, ra, ca, b));
    }

    /** Verify that the committed vector {@code cdprime} is a permutation of the public vector {@code d}. */
    public static boolean verify(byte[][] cdprime, BigInteger[] d, Proof proof) {
        if (proof == null || cdprime == null || d == null || cdprime.length != d.length || d.length == 0) {
            return false;
        }
        BigInteger x = challenge(d, cdprime);
        byte[][] ca = factorCommitments(x, cdprime);
        for (byte[] c : ca) {
            if (c == null) {
                return false;
            }
        }
        BigInteger b = publicTarget(x, d);
        return ProductArgument.verify(ca, b, proof.product);
    }
}
