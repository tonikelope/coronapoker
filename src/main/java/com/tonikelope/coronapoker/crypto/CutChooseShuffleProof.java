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
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * Non-interactive cut-and-choose proof that a deck {@code B} is an honest shuffle of a deck
 * {@code A}: there exist a secret permutation {@code π} and a single common scalar {@code k} with
 * {@code B[i] = k · A[π[i]]}. This is the verifiable-shuffle engine that closes the SRA rotation
 * smuggle (a dishonest host can no longer pass off a player's pocket as a community card, because it
 * could not prove the cascade step was a real shuffle). See {@code docs/sra-shuffle-argument-engine.md}.
 *
 * <p><b>How it works (per round).</b> The prover commits the SHA-256 <i>hash</i> of a fresh
 * intermediate deck {@code C = apply(A, π1, k1)} (random {@code π1, k1}). A Fiat–Shamir bit, bound to
 * {@code A}, {@code B} and <i>every</i> committed hash, then asks for one half:
 * <ul>
 *   <li>bit 0 → reveal {@code (π1, k1)}; the verifier recomputes {@code C = apply(A, π1, k1)} and
 *       checks {@code hash(C)} equals the commitment (so {@code C} is a shuffle of {@code A}).</li>
 *   <li>bit 1 → reveal {@code (π2, k2) = (compose(invert(π1), π), k·k1⁻¹)}; the verifier recomputes
 *       {@code C = apply(B, invert(π2), k2⁻¹)} — which makes {@code B = apply(C, π2, k2)} hold by
 *       construction — and checks {@code hash(C)} equals the commitment.</li>
 * </ul>
 * Only the 32-byte hash travels per round (not the deck), so the proof is ~12× smaller. Soundness is
 * unchanged: the hash binds {@code C} before the challenge (collision resistance), and if {@code B} is
 * not a shuffle of {@code A} then for the committed {@code C} at least one half is unanswerable, so
 * each round catches the cheat with probability ≥ 1/2 ⇒ forgery ≤ {@code 2^-rounds}. Revealing one
 * fresh half per round leaks nothing about {@code (π, k)} (zero-knowledge).
 *
 * <p><b>Soundness parameter.</b> {@link #DEFAULT_ROUNDS} = 128 ⇒ 2^-128 — the deliberate choice for
 * money-grade crypto whose soundness is a one-line argument rather than subtle algebra.
 */
public final class CutChooseShuffleProof {

    /** Soundness parameter for production: forgery probability ≤ 2^-128. */
    public static final int DEFAULT_ROUNDS = 128;

    private static final String FS_DOMAIN_PREFIX = "SRA/CutChooseShuffle/v1/";

    /**
     * Dedicated worker pool for the (heavy, embarrassingly parallel) per-round work. Uses every
     * available core, but the threads run at {@link Thread#MIN_PRIORITY} so the UI / game threads
     * always preempt them — full throughput when the machine is idle, zero perceived UI lag. The
     * crypto is run in the background (during betting), so spending all idle cores here is free.
     */
    private static final java.util.concurrent.ForkJoinPool POOL = makePool();

    private static java.util.concurrent.ForkJoinPool makePool() {
        // TODOS los cores (a MIN_PRIORITY). El calculo corre en background CON RETRASO -> ya no pisa
        // la animacion de barajado; cuando arranca (durante las apuestas) la maquina esta ociosa, asi
        // que usar el CPU a tope es gratis.
        int n = Math.max(1, Runtime.getRuntime().availableProcessors());
        java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory factory = p -> {
            java.util.concurrent.ForkJoinWorkerThread w =
                    java.util.concurrent.ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(p);
            w.setPriority(Thread.MIN_PRIORITY);
            w.setName("c1-shuffle-" + w.getPoolIndex());
            return w;
        };
        return new java.util.concurrent.ForkJoinPool(n, factory, null, false);
    }

    /** Run {@code body(j)} for {@code j in [0,rounds)} across all cores in the low-priority pool. */
    private static void runInPool(int rounds, java.util.function.IntConsumer body) {
        try {
            POOL.submit(() -> java.util.stream.IntStream.range(0, rounds).parallel().forEach(body)).get();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ie);
        } catch (java.util.concurrent.ExecutionException ee) {
            throw new RuntimeException(ee.getCause() != null ? ee.getCause() : ee);
        }
    }

    private CutChooseShuffleProof() {
    }

    /** A proof: per round, the committed intermediate hash and the revealed half (perm + scalar). */
    public static final class Proof {
        final int rounds;
        final int deckSize;
        final byte[][] intermediateHash; // [round] = SHA-256 of the round's intermediate deck C
        final int[][] revealedPerm;      // [round] revealed permutation
        final BigInteger[] revealedScalar; // [round] revealed scalar

        Proof(int rounds, int deckSize, byte[][] intermediateHash, int[][] revealedPerm, BigInteger[] revealedScalar) {
            this.rounds = rounds;
            this.deckSize = deckSize;
            this.intermediateHash = intermediateHash;
            this.revealedPerm = revealedPerm;
            this.revealedScalar = revealedScalar;
        }

        public int getRounds() {
            return rounds;
        }

        /** Defensive bounds for {@link #fromBytes} (reject absurd headers before allocating). */
        private static final int MAX_ROUNDS = 1024;
        private static final int MAX_DECK = 256;

        /**
         * Serialize: {@code rounds(4) | deckSize(4) | per round[ 32 hash | deckSize×2 perm | 32 scalar ]}.
         * Perm indices are 2-byte big-endian (deck ≤ 256); scalars 32-byte big-endian.
         */
        public byte[] toBytes() {
            int n = deckSize;
            int perRound = 32 + n * 2 + 32;
            byte[] out = new byte[8 + rounds * perRound];
            putInt(out, 0, rounds);
            putInt(out, 4, deckSize);
            int p = 8;
            for (int j = 0; j < rounds; j++) {
                System.arraycopy(intermediateHash[j], 0, out, p, 32);
                p += 32;
                for (int i = 0; i < n; i++) {
                    int v = revealedPerm[j][i];
                    out[p++] = (byte) (v >>> 8);
                    out[p++] = (byte) v;
                }
                byte[] s = scalar32(revealedScalar[j]);
                System.arraycopy(s, 0, out, p, 32);
                p += 32;
            }
            return out;
        }

        /** Parse {@link #toBytes}. Returns null on any malformed/over-large input (never throws). */
        public static Proof fromBytes(byte[] in) {
            try {
                if (in == null || in.length < 8) {
                    return null;
                }
                int rounds = getInt(in, 0);
                int n = getInt(in, 4);
                if (rounds <= 0 || rounds > MAX_ROUNDS || n <= 0 || n > MAX_DECK) {
                    return null;
                }
                int perRound = 32 + n * 2 + 32;
                if (in.length != 8 + (long) rounds * perRound) {
                    return null;
                }
                byte[][] intermediateHash = new byte[rounds][];
                int[][] revealedPerm = new int[rounds][n];
                BigInteger[] revealedScalar = new BigInteger[rounds];
                int p = 8;
                for (int j = 0; j < rounds; j++) {
                    byte[] h = new byte[32];
                    System.arraycopy(in, p, h, 0, 32);
                    p += 32;
                    intermediateHash[j] = h;
                    for (int i = 0; i < n; i++) {
                        int v = ((in[p] & 0xFF) << 8) | (in[p + 1] & 0xFF);
                        p += 2;
                        revealedPerm[j][i] = v;
                    }
                    byte[] s = new byte[32];
                    System.arraycopy(in, p, s, 0, 32);
                    p += 32;
                    revealedScalar[j] = new BigInteger(1, s);
                }
                return new Proof(rounds, n, intermediateHash, revealedPerm, revealedScalar);
            } catch (Exception e) {
                return null;
            }
        }

        private static void putInt(byte[] b, int off, int v) {
            b[off] = (byte) (v >>> 24);
            b[off + 1] = (byte) (v >>> 16);
            b[off + 2] = (byte) (v >>> 8);
            b[off + 3] = (byte) v;
        }

        private static int getInt(byte[] b, int off) {
            return ((b[off] & 0xFF) << 24) | ((b[off + 1] & 0xFF) << 16)
                    | ((b[off + 2] & 0xFF) << 8) | (b[off + 3] & 0xFF);
        }

        private static byte[] scalar32(BigInteger s) {
            byte[] mod = s.mod(EdwardsPoint.L).toByteArray();
            byte[] fixed = new byte[32];
            int copy = Math.min(mod.length, 32);
            System.arraycopy(mod, mod.length - copy, fixed, 32 - copy, copy);
            return fixed;
        }
    }

    private static void absorbDeck(Transcript t, String label, EdwardsPoint[] deck) {
        t.absorb(label + ":n", new byte[]{(byte) (deck.length >>> 8), (byte) deck.length});
        for (int i = 0; i < deck.length; i++) {
            t.absorbPoint(label + ":" + i, deck[i]);
        }
    }

    /** SHA-256 of a deck's concatenated canonical encodings — the per-round commitment. */
    static byte[] hashDeck(EdwardsPoint[] deck) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (EdwardsPoint p : deck) {
                md.update(Ristretto255.encode(p));
            }
            return md.digest();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** The K challenge bits, bound to A, B and every committed intermediate hash (Fiat–Shamir).
     *  Package-private so the adversarial test can simulate a cheating prover. */
    static boolean[] challengeBits(String domain, EdwardsPoint[] a, EdwardsPoint[] b,
                                   byte[][] intermediateHash, int rounds) {
        Transcript t = new Transcript(FS_DOMAIN_PREFIX + (domain == null ? "" : domain));
        t.absorb("rounds", new byte[]{(byte) (rounds >>> 24), (byte) (rounds >>> 16), (byte) (rounds >>> 8), (byte) rounds});
        absorbDeck(t, "A", a);
        absorbDeck(t, "B", b);
        for (int j = 0; j < rounds; j++) {
            t.absorb("H" + j, intermediateHash[j]);
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

        // Rounds are independent → build the intermediates + their hashes in parallel (scalar-mul heavy).
        final int[][] pi1 = new int[rounds][];
        final BigInteger[] k1 = new BigInteger[rounds];
        final byte[][] hashes = new byte[rounds][];
        runInPool(rounds, j -> {
            pi1[j] = DeckTransform.randomPermutation(n);
            k1[j] = RistrettoSRA.bytesToScalar(RistrettoSRA.generateLockScalar());
            hashes[j] = hashDeck(DeckTransform.apply(a, pi1[j], k1[j]));
        });

        boolean[] bits = challengeBits(null, a, b, hashes, rounds);

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
        return new Proof(rounds, n, hashes, revealedPerm, revealedScalar);
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
        if (proof.intermediateHash.length != proof.rounds
                || proof.revealedPerm.length != proof.rounds
                || proof.revealedScalar.length != proof.rounds) {
            return false;
        }

        final boolean[] bits = challengeBits(null, a, b, proof.intermediateHash, proof.rounds);
        final java.util.concurrent.atomic.AtomicBoolean bad = new java.util.concurrent.atomic.AtomicBoolean(false);

        runInPool(proof.rounds, j -> {
            if (bad.get()) {
                return;
            }
            int[] perm = proof.revealedPerm[j];
            BigInteger s = proof.revealedScalar[j];
            byte[] committed = proof.intermediateHash[j];
            if (!DeckTransform.isPermutation(perm, n) || s == null
                    || s.mod(EdwardsPoint.L).signum() == 0
                    || committed == null || committed.length != 32) {
                bad.set(true);
                return;
            }
            // Recompute the round's intermediate C from the revealed half, then check its hash
            // matches the commitment. For bit 1, C := apply(B, invert(perm), s^-1) makes
            // B = apply(C, perm, s) hold by construction, so the hash check is the whole proof.
            EdwardsPoint[] c = !bits[j]
                    ? DeckTransform.apply(a, perm, s)
                    : DeckTransform.apply(b, DeckTransform.invert(perm), s.modInverse(EdwardsPoint.L));
            if (!Arrays.equals(hashDeck(c), committed)) {
                bad.set(true);
            }
        });
        return !bad.get();
    }
}
