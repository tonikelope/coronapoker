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
 * Multi-exponentiation argument (linked Σ-protocol): proves that a claimed point
 * {@code Q = Σ x_i·A_i} for PUBLIC bases {@code A_i} and a secret vector {@code x} that is bound by
 * a Pedersen vector commitment {@code C_x = Comm(x, r)} — without revealing {@code x}. This is the
 * piece of the Bayer–Groth shuffle that ties the (hidden) permutation to the encrypted deck points
 * (see {@code docs/sra-bayer-groth-shuffle.md}).
 *
 * <p>Protocol (one move + Fiat–Shamir): prover sends {@code T_c = Comm(b, s)} and {@code T_Q = Σ b_i·A_i}
 * for a random mask {@code (b, s)}; challenge {@code e = H(C_x, A, Q, T_c, T_Q)}; responses
 * {@code z_i = b_i + e·x_i}, {@code z_r = s + e·r}. The verifier accepts iff
 * {@code Comm(z, z_r) == T_c ⊕ e·C_x} AND {@code Σ z_i·A_i == T_Q ⊕ e·Q}. Complete, special-sound
 * (two accepting transcripts extract {@code x} consistent with both checks), honest-verifier ZK.
 *
 * <p>This is the clean O(n) linked-Σ form (not Bayer–Groth's batched logarithmic optimisation); it
 * is correct and self-evidently sound, which is the priority. The batched optimisation can replace it
 * later if proof size matters.
 */
public final class MultiExpArgument {

    private static final String FS_DOMAIN = "SRA/MultiExpArg/v1";

    private MultiExpArgument() {
    }

    public static final class Proof {
        final byte[] tc;      // Comm(b, s)
        final byte[] tq;      // encoded Σ b_i·A_i
        final BigInteger[] z; // z_i = b_i + e·x_i
        final BigInteger zr;  // z_r = s + e·r

        Proof(byte[] tc, byte[] tq, BigInteger[] z, BigInteger zr) {
            this.tc = tc;
            this.tq = tq;
            this.z = z;
            this.zr = zr;
        }
    }

    /** Σ s_i · A_i. */
    static EdwardsPoint msm(BigInteger[] s, EdwardsPoint[] a) {
        EdwardsPoint acc = EdwardsPoint.IDENTITY;
        for (int i = 0; i < a.length; i++) {
            acc = acc.add(a[i].scalarMul(s[i].mod(EdwardsPoint.L)));
        }
        return acc;
    }

    private static BigInteger challenge(byte[] cx, EdwardsPoint[] a, EdwardsPoint q, byte[] tc, byte[] tq) {
        Transcript tr = new Transcript(FS_DOMAIN);
        tr.absorb("n", new byte[]{(byte) (a.length >>> 8), (byte) a.length});
        tr.absorb("Cx", cx);
        for (int i = 0; i < a.length; i++) {
            tr.absorbPoint("A" + i, a[i]);
        }
        tr.absorbPoint("Q", q);
        tr.absorb("Tc", tc);
        tr.absorb("Tq", tq);
        return tr.challengeScalar("e");
    }

    /**
     * Prove {@code q == Σ x_i·a_i} and {@code cx == Comm(x, r)}.
     *
     * @param x   the secret vector (also committed in {@code cx})
     * @param r   the blinding of {@code cx}
     * @param cx  the Pedersen vector commitment to {@code x}
     * @param a   the public bases
     * @param q   the claimed multi-exponentiation point
     */
    public static Proof prove(BigInteger[] x, BigInteger r, byte[] cx, EdwardsPoint[] a, EdwardsPoint q) {
        int n = x.length;
        BigInteger[] b = new BigInteger[n];
        for (int i = 0; i < n; i++) {
            b[i] = RistrettoSRA.bytesToScalar(RistrettoSRA.generateLockScalar());
        }
        BigInteger s = RistrettoSRA.bytesToScalar(RistrettoSRA.generateLockScalar());
        byte[] tc = PedersenVectorCommit.commit(b, s);
        byte[] tq = Ristretto255.encode(msm(b, a));

        BigInteger e = challenge(cx, a, q, tc, tq);
        BigInteger[] z = new BigInteger[n];
        for (int i = 0; i < n; i++) {
            z[i] = b[i].add(e.multiply(x[i])).mod(EdwardsPoint.L);
        }
        BigInteger zr = s.add(e.multiply(r)).mod(EdwardsPoint.L);
        return new Proof(tc, tq, z, zr);
    }

    /** Verify the argument for claimed point {@code q}, public bases {@code a}, commitment {@code cx}. */
    public static boolean verify(byte[] cx, EdwardsPoint[] a, EdwardsPoint q, Proof proof) {
        if (proof == null || cx == null || a == null || q == null
                || proof.z == null || proof.z.length != a.length || proof.tc == null || proof.tq == null || proof.zr == null) {
            return false;
        }
        EdwardsPoint tqPoint = Ristretto255.decode(proof.tq);
        if (tqPoint == null) {
            return false;
        }
        BigInteger e = challenge(cx, a, q, proof.tc, proof.tq);

        // Check 1: Comm(z, zr) == T_c ⊕ e·C_x
        byte[] lhsC = PedersenVectorCommit.commit(proof.z, proof.zr);
        byte[] rhsC = PedersenVectorCommit.add(proof.tc, PedersenVectorCommit.scale(cx, e));
        if (rhsC == null || !Arrays.equals(lhsC, rhsC)) {
            return false;
        }
        // Check 2: Σ z_i·A_i == T_Q ⊕ e·Q
        EdwardsPoint lhsQ = msm(proof.z, a);
        EdwardsPoint rhsQ = tqPoint.add(q.scalarMul(e));
        return Arrays.equals(Ristretto255.encode(lhsQ), Ristretto255.encode(rhsQ));
    }
}
