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
 * Weighted-sum (multi-exponentiation over individually-committed weights) argument: given
 * single-value Pedersen commitments {@code C_f[i] = Comm([f_i], s_i)} and PUBLIC bases {@code B_i},
 * proves a claimed point {@code Q == Σ f_i·B_i} in zero knowledge of the weights {@code f}. Shares the
 * individual-commitment substrate with {@link PermutationArgument}, so the same committed vector can be
 * proven (a) a permutation of a public vector AND (b) the weights of a multi-exponentiation of the
 * encrypted deck — which is exactly how the shuffle ties its hidden permutation to the deck points
 * (see {@code docs/SECURITY.md}).
 *
 * <p>Protocol. Masks {@code (β_i, σ_i)}; sends per-element {@code T_i = β_i·G_0 + σ_i·H} and the
 * aggregate {@code T_Q = Σ β_i·B_i}. Challenge {@code e = H(C_f, B, Q, T, T_Q)}. Responses
 * {@code z_i = β_i + e·f_i}, {@code zs_i = σ_i + e·s_i}. Verifier accepts iff, for every {@code i},
 * {@code z_i·G_0 + zs_i·H == T_i ⊕ e·C_f[i]} (knowledge of the committed weight) AND
 * {@code Σ z_i·B_i == T_Q ⊕ e·Q} (the multi-exponentiation linkage). The shared {@code z_i} forces the
 * weights used in both checks to coincide. Complete, special-sound, honest-verifier ZK.
 */
public final class WeightedSumArgument {

    private static final String FS_DOMAIN = "SRA/WeightedSumArg/v1";
    private static final BigInteger L = EdwardsPoint.L;

    private WeightedSumArgument() {
    }

    public static final class Proof {
        final byte[][] t;        // per-element mask commitments T_i
        final byte[] tq;         // encoded Σ β_i·B_i
        final BigInteger[] z;    // z_i = β_i + e·f_i
        final BigInteger[] zs;   // zs_i = σ_i + e·s_i

        Proof(byte[][] t, byte[] tq, BigInteger[] z, BigInteger[] zs) {
            this.t = t;
            this.tq = tq;
            this.z = z;
            this.zs = zs;
        }
    }

    private static BigInteger scalar() {
        return RistrettoSRA.bytesToScalar(RistrettoSRA.generateLockScalar());
    }

    /** Canonical scalar response: present and reduced into {@code [0, L)}. */
    private static boolean inRange(BigInteger s) {
        return s != null && s.signum() >= 0 && s.compareTo(L) < 0;
    }

    static EdwardsPoint msm(BigInteger[] s, EdwardsPoint[] b) {
        return EdwardsPoint.multiscalarMul(s, b); // Straus: shared doubling ladder
    }

    private static BigInteger challenge(byte[][] cf, EdwardsPoint[] b, EdwardsPoint q, byte[][] t, byte[] tq) {
        Transcript tr = new Transcript(FS_DOMAIN);
        tr.absorb("n", new byte[]{(byte) (cf.length >>> 8), (byte) cf.length});
        for (int i = 0; i < cf.length; i++) {
            tr.absorb("Cf" + i, cf[i]);
            tr.absorbPoint("B" + i, b[i]);
            tr.absorb("T" + i, t[i]);
        }
        tr.absorbPoint("Q", q);
        tr.absorb("Tq", tq);
        return tr.challengeScalar("e");
    }

    /**
     * Prove {@code q == Σ f_i·b_i} for individually-committed weights {@code cf[i] = Comm([f_i], s[i])}
     * and public bases {@code b}.
     */
    public static Proof prove(BigInteger[] f, BigInteger[] s, byte[][] cf, EdwardsPoint[] b, EdwardsPoint q) {
        int n = f.length;
        BigInteger[] beta = new BigInteger[n];
        BigInteger[] sigma = new BigInteger[n];
        byte[][] t = new byte[n][];
        for (int i = 0; i < n; i++) {
            beta[i] = scalar();
            sigma[i] = scalar();
            t[i] = MultiplicationProof.commitScalar(beta[i], sigma[i]);
        }
        byte[] tq = Ristretto255.encode(msm(beta, b));
        BigInteger e = challenge(cf, b, q, t, tq);
        BigInteger[] z = new BigInteger[n];
        BigInteger[] zs = new BigInteger[n];
        for (int i = 0; i < n; i++) {
            z[i] = beta[i].add(e.multiply(f[i])).mod(L);
            zs[i] = sigma[i].add(e.multiply(s[i])).mod(L);
        }
        return new Proof(t, tq, z, zs);
    }

    /** Verify the weighted-sum claim {@code q == Σ f_i·b_i}. */
    public static boolean verify(byte[][] cf, EdwardsPoint[] b, EdwardsPoint q, Proof proof) {
        if (proof == null || cf == null || b == null || q == null || cf.length != b.length
                || proof.t == null || proof.t.length != cf.length || proof.tq == null
                || proof.z == null || proof.z.length != cf.length || proof.zs == null || proof.zs.length != cf.length) {
            return false;
        }
        // Canonical responses: present and reduced into [0, L) (rejects nulls and z+L malleability).
        for (int i = 0; i < cf.length; i++) {
            if (!inRange(proof.z[i]) || !inRange(proof.zs[i]) || proof.t[i] == null) {
                return false;
            }
        }
        EdwardsPoint tqPoint = Ristretto255.decode(proof.tq);
        if (tqPoint == null) {
            return false;
        }
        BigInteger e = challenge(cf, b, q, proof.t, proof.tq);
        // Per-element: z_i·G_0 + zs_i·H == T_i ⊕ e·C_f[i], folded to a single shared-ladder
        // multi-scalar per element ("− e·C_f[i] == T_i", free negation) with the native Ristretto
        // equality — same coset relation as the byte comparison, no per-element encode round trips.
        EdwardsPoint g0 = PedersenVectorCommit.generator(0);
        EdwardsPoint h = PedersenVectorCommit.H;
        for (int i = 0; i < cf.length; i++) {
            EdwardsPoint ti = Ristretto255.decode(proof.t[i]);
            EdwardsPoint cfi = Ristretto255.decode(cf[i]);
            if (ti == null || cfi == null) {
                return false;
            }
            EdwardsPoint lhs = EdwardsPoint.multiscalarMul(
                    new BigInteger[]{proof.z[i], proof.zs[i], e}, new EdwardsPoint[]{g0, h, cfi.negate()});
            if (!Ristretto255.equalPoints(lhs, ti)) {
                return false;
            }
        }
        // Aggregate: Σ z_i·B_i == T_Q ⊕ e·Q, folded the same way (Σ z_i·B_i − e·Q == T_Q).
        int n = b.length;
        BigInteger[] zAgg = Arrays.copyOf(proof.z, n + 1);
        zAgg[n] = e;
        EdwardsPoint[] bAgg = Arrays.copyOf(b, n + 1);
        bAgg[n] = q.negate();
        return Ristretto255.equalPoints(EdwardsPoint.multiscalarMul(zAgg, bAgg), tqPoint);
    }
}
