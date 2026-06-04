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
import java.util.Arrays;

/**
 * Product argument: given single-value Pedersen commitments {@code C_a[0..n-1]} (with
 * {@code C_a[i] = Comm([a_i], r_i)}), proves that {@code Π a_i == b} for a PUBLIC target {@code b},
 * in zero knowledge (the {@code a_i} stay hidden). The combinatorial core of the permutation /
 * shuffle argument (see {@code docs/sra-bayer-groth-shuffle.md}): a permutation of a public vector
 * {@code d} is characterised by {@code Π(x − d'_i) == Π(x − d_i)} for a random challenge {@code x},
 * and the right-hand side is exactly such a public product.
 *
 * <p>Construction — grand-product. Partial products {@code p_0 = a_0}, {@code p_i = p_{i-1}·a_i}, so
 * {@code p_{n-1} = b}. The prover commits {@code C_p[i] = Comm([p_i], ρ_i)} (with {@code C_p[0] = C_a[0]})
 * and gives, per step, a {@link MultiplicationProof} that {@code C_p[i]} commits {@code p_{i-1}·a_i}
 * (product of {@code C_p[i-1]} and {@code C_a[i]}). It closes with a Schnorr-on-{@code H} proof that
 * the final {@code C_p[n-1]} opens to the public {@code b} (i.e. its {@code G_0}-component is exactly
 * {@code b}). Each multiplication gate is special-sound, so the whole chain binds {@code Π a_i = b}.
 */
public final class ProductArgument {

    private static final String FS_OPEN_DOMAIN = "SRA/ProductArg/PublicOpen/v1";
    private static final BigInteger L = EdwardsPoint.L;

    private ProductArgument() {
    }

    public static final class Proof {
        final byte[][] cp;                       // C_p[1..n-1] (C_p[0] = C_a[0], implicit); length n-1
        final MultiplicationProof.Proof[] steps; // one per multiplication step; length n-1
        final byte[] openT;                      // Schnorr-on-H: T = k·H
        final BigInteger openZ;                  // z = k + e·ρ_{n-1}

        Proof(byte[][] cp, MultiplicationProof.Proof[] steps, byte[] openT, BigInteger openZ) {
            this.cp = cp;
            this.steps = steps;
            this.openT = openT;
            this.openZ = openZ;
        }
    }

    private static BigInteger scalar() {
        return RistrettoSRA.bytesToScalar(RistrettoSRA.generateLockScalar());
    }

    /**
     * Encoded point {@code C − b·G_0} (should equal {@code ρ·H} when {@code C} commits {@code b}).
     * The subtraction is a free point negation, not a scalar multiplication by {@code L−1};
     * {@code null} if {@code c} fails to decode.
     */
    private static byte[] minusValue(byte[] c, BigInteger b) {
        EdwardsPoint cp = Ristretto255.decode(c);
        if (cp == null) {
            return null;
        }
        EdwardsPoint bG0 = PedersenVectorCommit.generator(0).scalarMul(b.mod(L));
        return Ristretto255.encode(cp.add(bG0.negate()));
    }

    private static BigInteger openChallenge(byte[] cLast, BigInteger b, byte[] t) {
        Transcript tr = new Transcript(FS_OPEN_DOMAIN);
        tr.absorb("Clast", cLast);
        tr.absorbScalar("b", b.mod(L));
        tr.absorb("T", t);
        return tr.challengeScalar("e");
    }

    /**
     * Prove {@code Π a_i == b}. The caller supplies the openings {@code a}, {@code ra} and the matching
     * published commitments {@code ca[i] = Comm([a_i], ra[i])}.
     */
    public static Proof prove(BigInteger[] a, BigInteger[] ra, byte[][] ca, BigInteger b) {
        int n = a.length;
        BigInteger[] p = new BigInteger[n];
        BigInteger[] rho = new BigInteger[n];
        byte[][] cpFull = new byte[n][];
        p[0] = a[0].mod(L);
        rho[0] = ra[0];
        cpFull[0] = ca[0];

        byte[][] cp = new byte[Math.max(0, n - 1)][];
        MultiplicationProof.Proof[] steps = new MultiplicationProof.Proof[Math.max(0, n - 1)];
        for (int i = 1; i < n; i++) {
            p[i] = p[i - 1].multiply(a[i]).mod(L);
            rho[i] = scalar();
            cpFull[i] = MultiplicationProof.commitScalar(p[i], rho[i]);
            // C_p[i] commits p[i-1]·a[i]: product of C_p[i-1] and C_a[i].
            steps[i - 1] = MultiplicationProof.prove(p[i - 1], rho[i - 1], a[i], ra[i], p[i], rho[i], ca[i]);
            cp[i - 1] = cpFull[i];
        }

        // Final public opening: C_p[n-1] opens to b (Schnorr-on-H of ρ_{n-1}).
        BigInteger k = scalar();
        byte[] t = MultiplicationProof.commitScalar(BigInteger.ZERO, k); // k·H
        BigInteger e = openChallenge(cpFull[n - 1], b, t);
        BigInteger z = k.add(e.multiply(rho[n - 1])).mod(L);
        return new Proof(cp, steps, t, z);
    }

    /** Verify that the committed factors {@code ca} multiply to the public target {@code b}. */
    public static boolean verify(byte[][] ca, BigInteger b, Proof proof) {
        if (proof == null || ca == null || ca.length == 0 || proof.openT == null
                || proof.openZ == null || proof.openZ.signum() < 0 || proof.openZ.compareTo(L) >= 0) {
            return false;
        }
        int n = ca.length;
        if (proof.cp == null || proof.cp.length != n - 1 || proof.steps == null || proof.steps.length != n - 1) {
            return false;
        }
        // Reconstruct the C_p chain: C_p[0] = C_a[0], C_p[i] = proof.cp[i-1].
        byte[][] cpFull = new byte[n][];
        cpFull[0] = ca[0];
        for (int i = 1; i < n; i++) {
            cpFull[i] = proof.cp[i - 1];
            if (cpFull[i] == null) {
                return false;
            }
        }
        // Each multiplication gate: C_p[i] == C_p[i-1] · C_a[i].
        for (int i = 1; i < n; i++) {
            if (!MultiplicationProof.verify(cpFull[i - 1], ca[i], cpFull[i], proof.steps[i - 1])) {
                return false;
            }
        }
        // Final opening: C_p[n-1] − b·G_0 == ρ·H, proven by z·H == T ⊕ e·(C_p[n-1] − b·G_0).
        byte[] pPoint = minusValue(cpFull[n - 1], b);
        if (pPoint == null) {
            return false;
        }
        BigInteger e = openChallenge(cpFull[n - 1], b, proof.openT);
        byte[] lhs = MultiplicationProof.commitScalar(BigInteger.ZERO, proof.openZ); // z·H
        byte[] rhs = PedersenVectorCommit.add(proof.openT, PedersenVectorCommit.scale(pPoint, e));
        return rhs != null && Arrays.equals(lhs, rhs);
    }
}
