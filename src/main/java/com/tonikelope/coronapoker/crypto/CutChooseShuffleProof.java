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
 * Non-interactive cut-and-choose proof that a deck {@code B} is an honest shuffle of a deck
 * {@code A}: there exist a secret permutation {@code π} and a single common scalar {@code k} with
 * {@code B[i] = k · A[π[i]]}. This is the verifiable-shuffle engine that closes the SRA rotation
 * smuggle (a dishonest host can no longer pass off a player's pocket as a community card, because it
 * could not prove the cascade step was a real shuffle). See {@code docs/sra-shuffle-argument-engine.md}.
 *
 * <p><b>How it works (per round).</b> The prover publishes a fresh intermediate deck
 * {@code C = apply(A, π1, k1)} (random {@code π1, k1}). A Fiat–Shamir bit, bound to {@code A}, {@code B}
 * and <i>every</i> {@code C}, then asks for one half:
 * <ul>
 *   <li>bit 0 → reveal {@code (π1, k1)}; verifier checks {@code C = apply(A, π1, k1)}.</li>
 *   <li>bit 1 → reveal {@code (π2, k2) = (compose(invert(π1), π), k·k1⁻¹)}; verifier checks
 *       {@code B = apply(C, π2, k2)}.</li>
 * </ul>
 * If {@code B} is <i>not</i> a shuffle of {@code A}, then for any {@code C} at least one half fails,
 * so each round catches the cheat with probability ≥ 1/2. The intermediates are fixed before the
 * (Fiat–Shamir) challenge, so a cheating prover cannot adapt them: over {@code rounds} rounds the
 * forgery probability is ≤ {@code 2^-rounds}. Revealing one fresh half per round leaks nothing about
 * {@code (π, k)} (zero-knowledge).
 *
 * <p><b>Soundness parameter.</b> {@link #DEFAULT_ROUNDS} = 128 ⇒ 2^-128. This trades compute (K×)
 * for an implementation whose soundness is a one-line argument rather than subtle algebra — the
 * deliberate choice for money-grade, externally-reviewable crypto.
 */
public final class CutChooseShuffleProof {

    /** Soundness parameter for production: forgery probability ≤ 2^-128. */
    public static final int DEFAULT_ROUNDS = 128;

    private static final String FS_DOMAIN_PREFIX = "SRA/CutChooseShuffle/v1/";

    private CutChooseShuffleProof() {
    }

    /** A proof: per round, the published intermediate deck and the revealed half (perm + scalar). */
    public static final class Proof {
        final int rounds;
        final int deckSize;
        final byte[][][] intermediates; // [round][i] = canonical encoding of C_round[i]
        final int[][] revealedPerm;     // [round] revealed permutation
        final BigInteger[] revealedScalar; // [round] revealed scalar

        Proof(int rounds, int deckSize, byte[][][] intermediates, int[][] revealedPerm, BigInteger[] revealedScalar) {
            this.rounds = rounds;
            this.deckSize = deckSize;
            this.intermediates = intermediates;
            this.revealedPerm = revealedPerm;
            this.revealedScalar = revealedScalar;
        }

        public int getRounds() {
            return rounds;
        }
    }

    private static void absorbDeck(Transcript t, String label, EdwardsPoint[] deck) {
        t.absorb(label + ":n", new byte[]{(byte) (deck.length >>> 8), (byte) deck.length});
        for (int i = 0; i < deck.length; i++) {
            t.absorbPoint(label + ":" + i, deck[i]);
        }
    }

    private static void absorbEncodedDeck(Transcript t, String label, byte[][] deck) {
        t.absorb(label + ":n", new byte[]{(byte) (deck.length >>> 8), (byte) deck.length});
        for (int i = 0; i < deck.length; i++) {
            t.absorb(label + ":" + i, deck[i]);
        }
    }

    private static byte[][] encode(EdwardsPoint[] deck) {
        byte[][] out = new byte[deck.length][];
        for (int i = 0; i < deck.length; i++) {
            out[i] = Ristretto255.encode(deck[i]);
        }
        return out;
    }

    /** The K challenge bits, bound to A, B and every intermediate C (Fiat–Shamir). Package-private
     *  so the adversarial test can simulate a cheating prover. */
    static boolean[] challengeBits(String domain, EdwardsPoint[] a, EdwardsPoint[] b,
                                           byte[][][] intermediates, int rounds) {
        Transcript t = new Transcript(FS_DOMAIN_PREFIX + (domain == null ? "" : domain));
        t.absorb("rounds", new byte[]{(byte) (rounds >>> 24), (byte) (rounds >>> 16), (byte) (rounds >>> 8), (byte) rounds});
        absorbDeck(t, "A", a);
        absorbDeck(t, "B", b);
        for (int j = 0; j < rounds; j++) {
            absorbEncodedDeck(t, "C" + j, intermediates[j]);
        }
        byte[] raw = t.challengeBytes("bits", (rounds + 7) / 8);
        boolean[] bits = new boolean[rounds];
        for (int j = 0; j < rounds; j++) {
            bits[j] = ((raw[j >>> 3] >>> (j & 7)) & 1) == 1;
        }
        return bits;
    }

    /**
     * Prove that {@code b = apply(a, pi, k)}. Caller must pass the true witness; throws if
     * {@code (pi, k)} is not a valid witness (defensive — never produce a "proof" of a false claim).
     */
    public static Proof prove(EdwardsPoint[] a, EdwardsPoint[] b, int[] pi, BigInteger k, int rounds) {
        int n = a.length;
        if (b.length != n || !DeckTransform.isPermutation(pi, n)) {
            throw new IllegalArgumentException("bad deck sizes or permutation");
        }
        BigInteger kk = k.mod(EdwardsPoint.L);
        if (kk.signum() == 0) {
            throw new IllegalArgumentException("k must be invertible");
        }
        if (!DeckTransform.decksEqual(b, DeckTransform.apply(a, pi, kk))) {
            throw new IllegalArgumentException("(pi,k) is not a witness for b = apply(a,pi,k)");
        }

        int[][] pi1 = new int[rounds][];
        BigInteger[] k1 = new BigInteger[rounds];
        byte[][][] intermediates = new byte[rounds][][];
        for (int j = 0; j < rounds; j++) {
            pi1[j] = DeckTransform.randomPermutation(n);
            k1[j] = RistrettoSRA.bytesToScalar(RistrettoSRA.generateLockScalar());
            intermediates[j] = encode(DeckTransform.apply(a, pi1[j], k1[j]));
        }

        boolean[] bits = challengeBits(null, a, b, intermediates, rounds);

        int[][] revealedPerm = new int[rounds][];
        BigInteger[] revealedScalar = new BigInteger[rounds];
        for (int j = 0; j < rounds; j++) {
            if (!bits[j]) {
                // reveal A -> C
                revealedPerm[j] = pi1[j];
                revealedScalar[j] = k1[j];
            } else {
                // reveal C -> B
                revealedPerm[j] = DeckTransform.compose(DeckTransform.invert(pi1[j]), pi);
                revealedScalar[j] = kk.multiply(k1[j].modInverse(EdwardsPoint.L)).mod(EdwardsPoint.L);
            }
        }
        return new Proof(rounds, n, intermediates, revealedPerm, revealedScalar);
    }

    /** Production-strength proof ({@link #DEFAULT_ROUNDS}). */
    public static Proof prove(EdwardsPoint[] a, EdwardsPoint[] b, int[] pi, BigInteger k) {
        return prove(a, b, pi, k, DEFAULT_ROUNDS);
    }

    /** Verify that {@code proof} attests {@code b} is an honest shuffle of {@code a}. */
    public static boolean verify(EdwardsPoint[] a, EdwardsPoint[] b, Proof proof) {
        if (proof == null || a == null || b == null) {
            return false;
        }
        int n = a.length;
        if (b.length != n || proof.deckSize != n || proof.rounds <= 0) {
            return false;
        }
        if (proof.intermediates.length != proof.rounds
                || proof.revealedPerm.length != proof.rounds
                || proof.revealedScalar.length != proof.rounds) {
            return false;
        }

        // Decode the intermediates (reject non-canonical points) and re-derive the challenge bits.
        EdwardsPoint[][] cDecks = new EdwardsPoint[proof.rounds][];
        for (int j = 0; j < proof.rounds; j++) {
            byte[][] enc = proof.intermediates[j];
            if (enc == null || enc.length != n) {
                return false;
            }
            EdwardsPoint[] c = new EdwardsPoint[n];
            for (int i = 0; i < n; i++) {
                if (enc[i] == null || enc[i].length != 32) {
                    return false;
                }
                c[i] = Ristretto255.decode(enc[i]);
                if (c[i] == null) {
                    return false; // non-canonical / off-group intermediate point
                }
            }
            cDecks[j] = c;
        }

        boolean[] bits = challengeBits(null, a, b, proof.intermediates, proof.rounds);

        for (int j = 0; j < proof.rounds; j++) {
            int[] perm = proof.revealedPerm[j];
            BigInteger s = proof.revealedScalar[j];
            if (!DeckTransform.isPermutation(perm, n) || s == null
                    || s.mod(EdwardsPoint.L).signum() == 0) {
                return false;
            }
            if (!bits[j]) {
                // check C = apply(A, perm, s)
                if (!DeckTransform.decksEqual(cDecks[j], DeckTransform.apply(a, perm, s))) {
                    return false;
                }
            } else {
                // check B = apply(C, perm, s)
                if (!DeckTransform.decksEqual(b, DeckTransform.apply(cDecks[j], perm, s))) {
                    return false;
                }
            }
        }
        return true;
    }
}
