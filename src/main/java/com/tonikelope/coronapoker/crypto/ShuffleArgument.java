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
 * Verifiable shuffle argument (Bayer–Groth assembly) for one cascade step: given PUBLIC input deck
 * {@code A} and output deck {@code B}, proves there exist a hidden permutation {@code π} and a hidden
 * common scalar {@code k} with {@code B[i] = k·A[π(i)]} for all {@code i} — in zero knowledge of
 * {@code π} and {@code k}. This is the missing half of the SRA mental-poker engine: it forces the deck
 * to be an HONEST re-encryption shuffle (a real permutation of distinct cards, uniformly relocked),
 * which closes the rotation-smuggle vulnerability (a smuggled duplicate is not a permutation, so the
 * proof fails). See {@code docs/sra-bayer-groth-shuffle.md}.
 *
 * <p>Construction (Neff/Bayer–Groth). A challenge vector {@code e = H(A, B)} is drawn (Fiat–Shamir).
 * The prover commits the permuted challenges {@code f_i = e[π(i)]} and shows:
 * <ol>
 *   <li><b>{@code f} is a permutation of {@code e}</b> — {@link PermutationArgument} (so {@code f_i = e_{σ(i)}}
 *       for some committed {@code σ});</li>
 *   <li><b>{@code Q = Σ f_i·B_i}</b> for a revealed point {@code Q} — {@link WeightedSumArgument};</li>
 *   <li><b>{@code Q = k·P_A}</b> where {@code P_A = Σ e_j·A_j} is public — a Schnorr proof of the common scalar.</li>
 * </ol>
 * Combining: {@code Σ_j e_j·(B_{σ⁻¹(j)} − k·A_j) = 0} for the random {@code e}. Forcing each bracket to
 * zero (hence {@code B_i = k·A_{σ(i)}}) is the random-linear-combination step, and it is sound
 * <b>only because the deck points {@code A_j} are discrete-log-independent</b> random group elements:
 * with no prover-known relation {@code Σ_j λ_j·A_j = O}, the formal coefficient of each {@code A_j} must
 * vanish independently. Honest-verifier ZK from the sub-protocols; the single revealed point
 * {@code Q = k·P_A} leaks neither {@code k} (a discrete log) nor {@code σ} ({@code Q} is permutation-invariant).
 *
 * <p><b>SECURITY PRECONDITION — discrete-log independence of {@code A} (load-bearing).</b> The soundness
 * above collapses if a malicious prover knows a linear relation among the input points {@code A_j}: it
 * could then satisfy the combined equation with a non-permutation {@code B} (a smuggled/duplicated card it
 * can read). The genesis deck (NUMS hash-to-group card points) is DL-independent; and an honest re-encryption
 * shuffle of DL-independent points stays DL-independent (scalar-mul + reorder introduces no relation). So the
 * caller MUST verify each cascade step against an {@code A} that is provably anchored to the recomputable
 * genesis: step 0's input equals genesis, and step {@code m}'s input equals step {@code m−1}'s ALREADY-VERIFIED
 * output. Under that induction every {@code A} is DL-independent and the smuggle is impossible. <b>Verifying an
 * isolated step against an unanchored / caller-supplied {@code A} is NOT sound</b> — {@link #verify} cannot
 * detect provenance, so the cascade wiring is responsible for the anchor + chain (as
 * {@code VerifiableCascade.verifyChain} already does, via {@code decksEqual(genesis, decks[0])}).
 *
 * <p>Defensive hygiene: {@link #verify} additionally rejects identity deck points / {@code Q} / {@code P_A}
 * (which would attest a degenerate all-identity deck, i.e. {@code k = 0}) and rejects out-of-range response
 * scalars (canonical proofs only).
 */
public final class ShuffleArgument {

    private static final String FS_CHALLENGE_DOMAIN = "SRA/Shuffle/challenge/v1";
    private static final String FS_SCALAR_DOMAIN = "SRA/Shuffle/scalar/v1";
    private static final BigInteger L = EdwardsPoint.L;

    private ShuffleArgument() {
    }

    public static final class Proof {
        final byte[][] cf;                       // commitments to the permuted challenges f
        final PermutationArgument.Proof perm;
        final byte[] q;                          // encoded Q = Σ f_i·B_i = k·P_A
        final WeightedSumArgument.Proof wsum;
        final byte[] scT;                        // Schnorr: T = r·P_A
        final BigInteger scZ;                    // z = r + e·k

        Proof(byte[][] cf, PermutationArgument.Proof perm, byte[] q,
                WeightedSumArgument.Proof wsum, byte[] scT, BigInteger scZ) {
            this.cf = cf;
            this.perm = perm;
            this.q = q;
            this.wsum = wsum;
            this.scT = scT;
            this.scZ = scZ;
        }
    }

    private static final byte[] IDENTITY_ENC = Ristretto255.encode(EdwardsPoint.IDENTITY);

    private static BigInteger scalar() {
        return RistrettoSRA.bytesToScalar(RistrettoSRA.generateLockScalar());
    }

    /** True iff the point is null or the group identity (a degenerate / k=0 deck position). */
    private static boolean isIdentity(EdwardsPoint p) {
        return p == null || Arrays.equals(Ristretto255.encode(p), IDENTITY_ENC);
    }

    /** Canonical scalar response: present and reduced into {@code [0, L)}. */
    private static boolean inRange(BigInteger s) {
        return s != null && s.signum() >= 0 && s.compareTo(L) < 0;
    }

    /** Challenge vector {@code e_0..e_{n-1} = H(A, B)} (Fiat–Shamir). */
    private static BigInteger[] challengeVector(EdwardsPoint[] a, EdwardsPoint[] b) {
        Transcript tr = new Transcript(FS_CHALLENGE_DOMAIN);
        int n = a.length;
        tr.absorb("n", new byte[]{(byte) (n >>> 8), (byte) n});
        for (int i = 0; i < n; i++) {
            tr.absorbPoint("A" + i, a[i]);
        }
        for (int i = 0; i < n; i++) {
            tr.absorbPoint("B" + i, b[i]);
        }
        BigInteger[] e = new BigInteger[n];
        for (int i = 0; i < n; i++) {
            e[i] = tr.challengeScalar("e" + i);
        }
        return e;
    }

    /** {@code P_A = Σ e_j·A_j} (Straus: shared doubling ladder). */
    private static EdwardsPoint multiExpPublic(BigInteger[] e, EdwardsPoint[] a) {
        return EdwardsPoint.multiscalarMul(e, a);
    }

    private static BigInteger scalarChallenge(EdwardsPoint pa, EdwardsPoint q, EdwardsPoint t) {
        Transcript tr = new Transcript(FS_SCALAR_DOMAIN);
        tr.absorbPoint("PA", pa);
        tr.absorbPoint("Q", q);
        tr.absorbPoint("T", t);
        return tr.challengeScalar("e");
    }

    /**
     * Prove that {@code B[i] = k·A[pi[i]]} for the given hidden permutation {@code pi} and scalar {@code k}.
     *
     * @param a  public input deck points
     * @param b  public output deck points ({@code b[i] = k·a[pi[i]]})
     * @param pi permutation: output position {@code i} carries input {@code pi[i]}
     * @param k  the common relock scalar
     */
    public static Proof prove(EdwardsPoint[] a, EdwardsPoint[] b, int[] pi, BigInteger k) {
        int n = a.length;
        BigInteger[] e = challengeVector(a, b);

        BigInteger[] f = new BigInteger[n];
        BigInteger[] s = new BigInteger[n];
        byte[][] cf = new byte[n][];
        for (int i = 0; i < n; i++) {
            f[i] = e[pi[i]];
            s[i] = scalar();
            cf[i] = MultiplicationProof.commitScalar(f[i], s[i]);
        }

        PermutationArgument.Proof perm = PermutationArgument.prove(f, s, cf, e);

        EdwardsPoint pa = multiExpPublic(e, a);
        EdwardsPoint q = WeightedSumArgument.msm(f, b);
        WeightedSumArgument.Proof wsum = WeightedSumArgument.prove(f, s, cf, b, q);

        // Schnorr: Q = k·P_A.
        BigInteger r = scalar();
        EdwardsPoint t = pa.scalarMul(r.mod(L));
        BigInteger ce = scalarChallenge(pa, q, t);
        BigInteger z = r.add(ce.multiply(k)).mod(L);

        return new Proof(cf, perm, Ristretto255.encode(q), wsum, Ristretto255.encode(t), z);
    }

    /** Verify that {@code B} is an honest re-encryption shuffle of {@code A} (some {@code π}, single {@code k}). */
    public static boolean verify(EdwardsPoint[] a, EdwardsPoint[] b, Proof proof) {
        if (proof == null || a == null || b == null || a.length == 0 || b.length != a.length
                || proof.cf == null || proof.cf.length != a.length || proof.q == null
                || proof.scT == null || !inRange(proof.scZ)) {
            return false;
        }
        // Degeneracy hygiene: no identity deck points (an identity card / k=0 deck is not a valid shuffle).
        for (int i = 0; i < a.length; i++) {
            if (isIdentity(a[i]) || isIdentity(b[i])) {
                return false;
            }
        }
        BigInteger[] e = challengeVector(a, b);

        // (1) f is a permutation of e.
        if (!PermutationArgument.verify(proof.cf, e, proof.perm)) {
            return false;
        }
        // (2) Q = Σ f_i·B_i for the committed f.
        EdwardsPoint q = Ristretto255.decode(proof.q);
        if (isIdentity(q) || !WeightedSumArgument.verify(proof.cf, b, q, proof.wsum)) {
            return false;
        }
        // (3) Q = k·P_A (Schnorr), P_A public.
        EdwardsPoint pa = multiExpPublic(e, a);
        if (isIdentity(pa)) {
            return false;
        }
        EdwardsPoint t = Ristretto255.decode(proof.scT);
        if (t == null) {
            return false;
        }
        BigInteger ce = scalarChallenge(pa, q, t);
        EdwardsPoint lhs = pa.scalarMul(proof.scZ.mod(L));   // z·P_A
        EdwardsPoint rhs = t.add(q.scalarMul(ce));           // T + e·Q
        return Arrays.equals(Ristretto255.encode(lhs), Ristretto255.encode(rhs));
    }
}
