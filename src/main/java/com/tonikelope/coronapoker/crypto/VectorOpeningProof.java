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
 * Non-interactive (Fiat–Shamir) proof of knowledge of the opening of a Pedersen vector commitment
 * {@code C = r·H + Σ a_i·G_i}: the prover proves it knows {@code (a, r)} without revealing them.
 * Standard Schnorr/Σ-protocol — a building block of the Bayer–Groth shuffle (see
 * {@code docs/sra-bayer-groth-shuffle.md}).
 *
 * <p>Protocol: prover sends {@code T = s·H + Σ b_i·G_i} (random mask {@code (b, s)}); challenge
 * {@code e = H(C, T)}; responses {@code z_i = b_i + e·a_i}, {@code z_r = s + e·r}. The verifier
 * accepts iff {@code Comm(z, z_r) == T ⊕ e·C}. Complete, special-sound (two accepting transcripts
 * with the same {@code T} and distinct {@code e} extract {@code (a, r)}), and honest-verifier ZK
 * (the masks hide {@code (a, r)}).
 */
public final class VectorOpeningProof {

    private static final String FS_DOMAIN = "SRA/VectorOpeningPoK/v1";

    private VectorOpeningProof() {
    }

    /** Proof: the mask commitment {@code T} and the responses {@code z}, {@code zr}. */
    public static final class Proof {
        final byte[] t;            // commitment to the mask
        final BigInteger[] z;      // z_i = b_i + e·a_i
        final BigInteger zr;       // z_r = s + e·r

        Proof(byte[] t, BigInteger[] z, BigInteger zr) {
            this.t = t;
            this.z = z;
            this.zr = zr;
        }
    }

    private static BigInteger challenge(byte[] commitment, byte[] t, int n) {
        Transcript tr = new Transcript(FS_DOMAIN);
        tr.absorb("n", new byte[]{(byte) (n >>> 8), (byte) n});
        tr.absorb("C", commitment);
        tr.absorb("T", t);
        return tr.challengeScalar("e");
    }

    /** Prove knowledge of {@code (a, r)} for {@code commitment = Comm(a, r)}. */
    public static Proof prove(BigInteger[] a, BigInteger r, byte[] commitment) {
        int n = a.length;
        BigInteger[] b = new BigInteger[n];
        for (int i = 0; i < n; i++) {
            b[i] = RistrettoSRA.bytesToScalar(RistrettoSRA.generateLockScalar());
        }
        BigInteger s = RistrettoSRA.bytesToScalar(RistrettoSRA.generateLockScalar());
        byte[] t = PedersenVectorCommit.commit(b, s);

        BigInteger e = challenge(commitment, t, n);
        BigInteger[] z = new BigInteger[n];
        for (int i = 0; i < n; i++) {
            z[i] = b[i].add(e.multiply(a[i])).mod(EdwardsPoint.L);
        }
        BigInteger zr = s.add(e.multiply(r)).mod(EdwardsPoint.L);
        return new Proof(t, z, zr);
    }

    /** Verify a proof of knowledge of the opening of {@code commitment} (vector length {@code n}). */
    public static boolean verify(byte[] commitment, int n, Proof proof) {
        if (proof == null || commitment == null || proof.z == null || proof.z.length != n
                || proof.t == null || proof.zr == null) {
            return false;
        }
        BigInteger e = challenge(commitment, proof.t, n);
        // Check Comm(z, zr) == T ⊕ e·C.
        byte[] lhs = PedersenVectorCommit.commit(proof.z, proof.zr);
        byte[] rhs = PedersenVectorCommit.add(proof.t, PedersenVectorCommit.scale(commitment, e));
        return rhs != null && java.util.Arrays.equals(lhs, rhs);
    }
}
