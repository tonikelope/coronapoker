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
 * Batch-DLEQ proof that one cascade <b>rotation</b> step is an honest <b>in-place re-key</b>:
 * {@code out[i] = s·in[i]} for ALL positions {@code i} with a single secret scalar {@code s} and NO
 * reordering — without revealing {@code s} (revealing it would leak the community lock). This closes the
 * dual-lock rotation (FASE 1.5) smuggle flank: the {@code ShuffleArgument} cascade proves
 * genesis→pre-rotation is an honest shuffle, and this proves pre-rotation→MEGAPACKET is an honest
 * per-position rekey, so every dealt point is tied back to genesis with no relocation/duplication.
 *
 * <p>Construction. Honest rotation applies one scalar to every community piece in place
 * ({@code RistrettoSRA.applyCommutativeLock}). The verifier draws Fiat–Shamir weights {@code w_i = H(in, out, i)},
 * aggregates {@code G = Σ w_i·in_i}, {@code H = Σ w_i·out_i}, and the prover gives a Schnorr PoK of
 * {@code s} with {@code H = s·G}. If any {@code out_j ≠ s·in_j}, or a permutation {@code out_i = s·in_{π(i)}}
 * with {@code π ≠ id}, then {@code Σ w_i·(out_i − s·in_i) ≠ O} for random weights except with probability
 * {@code ≤ n/L} (Schwartz–Zippel over DL-independent deck points), so the aggregate {@code H = s·G} fails.
 * Complete, sound, honest-verifier ZK (the Schnorr mask hides {@code s}).
 */
public final class RotationProof {

    private static final String FS_WEIGHTS_DOMAIN = "SRA/RotationProof/weights/v1";
    private static final String FS_SCHNORR_DOMAIN = "SRA/RotationProof/schnorr/v1";
    private static final BigInteger L = EdwardsPoint.L;
    private static final byte[] IDENTITY_ENC = Ristretto255.encode(EdwardsPoint.IDENTITY);

    private RotationProof() {
    }

    public static final class Proof {
        final byte[] t;       // Schnorr commitment T = r·G
        final BigInteger z;   // z = r + e·s

        Proof(byte[] t, BigInteger z) {
            this.t = t;
            this.z = z;
        }
    }

    private static boolean isIdentity(EdwardsPoint p) {
        return p == null || Arrays.equals(Ristretto255.encode(p), IDENTITY_ENC);
    }

    private static boolean inRange(BigInteger s) {
        return s != null && s.signum() >= 0 && s.compareTo(L) < 0;
    }

    /** Fiat–Shamir weights bound to the full (in, out) statement. */
    private static BigInteger[] weights(EdwardsPoint[] in, EdwardsPoint[] out) {
        Transcript tr = new Transcript(FS_WEIGHTS_DOMAIN);
        tr.absorb("n", new byte[]{(byte) (in.length >>> 8), (byte) in.length});
        for (int i = 0; i < in.length; i++) {
            tr.absorbPoint("in" + i, in[i]);
        }
        for (int i = 0; i < out.length; i++) {
            tr.absorbPoint("out" + i, out[i]);
        }
        BigInteger[] w = new BigInteger[in.length];
        for (int i = 0; i < in.length; i++) {
            w[i] = tr.challengeScalar("w" + i);
        }
        return w;
    }

    private static EdwardsPoint msm(BigInteger[] w, EdwardsPoint[] p) {
        return EdwardsPoint.multiscalarMul(w, p); // Straus: shared doubling ladder
    }

    private static BigInteger schnorrChallenge(EdwardsPoint g, EdwardsPoint h, byte[] t) {
        Transcript tr = new Transcript(FS_SCHNORR_DOMAIN);
        tr.absorbPoint("G", g);
        tr.absorbPoint("H", h);
        tr.absorb("T", t);
        return tr.challengeScalar("e");
    }

    /**
     * Prove {@code out[i] = s·in[i]} for all {@code i}. The caller supplies the secret rekey scalar
     * {@code s} it actually applied and the input/output community point arrays.
     */
    public static Proof prove(BigInteger s, EdwardsPoint[] in, EdwardsPoint[] out) {
        BigInteger[] w = weights(in, out);
        EdwardsPoint g = msm(w, in);
        EdwardsPoint h = msm(w, out);
        BigInteger r = RistrettoSRA.bytesToScalar(RistrettoSRA.generateLockScalar());
        EdwardsPoint t = g.scalarMul(r.mod(L));
        BigInteger e = schnorrChallenge(g, h, Ristretto255.encode(t));
        BigInteger z = r.add(e.multiply(s)).mod(L);
        return new Proof(Ristretto255.encode(t), z);
    }

    /** Verify that {@code out} is an honest in-place common-scalar rekey of {@code in}. */
    public static boolean verify(EdwardsPoint[] in, EdwardsPoint[] out, Proof proof) {
        if (proof == null || in == null || out == null || in.length == 0 || out.length != in.length
                || proof.t == null || !inRange(proof.z)) {
            return false;
        }
        for (int i = 0; i < in.length; i++) {
            if (isIdentity(in[i]) || isIdentity(out[i])) {
                return false; // degenerate deck position
            }
        }
        EdwardsPoint t = Ristretto255.decode(proof.t);
        if (t == null) {
            return false;
        }
        BigInteger[] w = weights(in, out);
        EdwardsPoint g = msm(w, in);
        EdwardsPoint h = msm(w, out);
        if (isIdentity(g)) {
            return false; // aggregate base degenerate (negligible for honest decks)
        }
        BigInteger e = schnorrChallenge(g, h, proof.t);
        // z·G == T ⊕ e·H  <=>  H = s·G
        EdwardsPoint lhs = g.scalarMul(proof.z.mod(L));
        EdwardsPoint rhs = t.add(h.scalarMul(e));
        return Arrays.equals(Ristretto255.encode(lhs), Ristretto255.encode(rhs));
    }
}
