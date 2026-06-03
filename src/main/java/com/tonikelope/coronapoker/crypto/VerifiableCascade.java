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
 * Orchestrates the verifiable-shuffle engine over a whole SRA cascade: proves that the post-cascade
 * deck is an honest permutation+lock chain starting from the <b>public genesis deck</b>, so no card
 * is ever duplicated. This is what closes the rotation smuggle end-to-end — a dishonest host that
 * slips a player's pocket into a community position would have produced a cascade step that is not a
 * real shuffle, and it cannot prove that step (so every honest peer rejects the hand).
 *
 * <p>Layout: {@code decks[0]} is the genesis deck, {@code decks[m+1] = shuffle_m(decks[m])} is peer
 * {@code m}'s output, and {@code proofs[m]} attests that step. The wiring (network, with smoke) will
 * have each peer publish its output deck + a {@link #proveStep} proof during the cascade, and every
 * peer run {@link #verifyChain} before trusting the deck.
 */
public final class VerifiableCascade {

    private VerifiableCascade() {
    }

    /** Prove one cascade step: {@code out = apply(in, perm, k)} (peer's lock {@code k} + shuffle). */
    public static CutChooseShuffleProof.Proof proveStep(EdwardsPoint[] in, EdwardsPoint[] out,
                                                        int[] perm, BigInteger k, int rounds) {
        return CutChooseShuffleProof.prove(in, out, perm, k, rounds);
    }

    /** Production-strength step proof ({@link CutChooseShuffleProof#DEFAULT_ROUNDS}). */
    public static CutChooseShuffleProof.Proof proveStep(EdwardsPoint[] in, EdwardsPoint[] out,
                                                        int[] perm, BigInteger k) {
        return proveStep(in, out, perm, k, CutChooseShuffleProof.DEFAULT_ROUNDS);
    }

    /**
     * Verify the whole cascade is an honest shuffle chain anchored to {@code genesis}:
     * {@code decks[0]} must equal the public genesis deck byte-for-byte, and every step
     * {@code decks[m] → decks[m+1]} must carry a valid shuffle proof. A single failure ⇒ reject the
     * deck (the host cannot have smuggled anything — the final deck is a genuine permutation).
     *
     * @param genesis the public genesis deck every peer derives independently (the anchor)
     * @param decks   {@code decks[0..N]}; {@code decks[0]} is the cascade input, {@code decks[N]} the output
     * @param proofs  {@code proofs[0..N-1]}; {@code proofs[m]} attests {@code decks[m+1] = shuffle(decks[m])}
     */
    public static boolean verifyChain(EdwardsPoint[] genesis, EdwardsPoint[][] decks,
                                      CutChooseShuffleProof.Proof[] proofs) {
        if (genesis == null || decks == null || proofs == null
                || decks.length < 1 || proofs.length != decks.length - 1) {
            return false;
        }
        if (!DeckTransform.decksEqual(genesis, decks[0])) {
            return false; // not anchored to the committed public genesis deck
        }
        for (int m = 0; m < proofs.length; m++) {
            if (decks[m] == null || decks[m + 1] == null
                    || !CutChooseShuffleProof.verify(decks[m], decks[m + 1], proofs[m])) {
                return false;
            }
        }
        return true;
    }

    // ---- Byte-oriented wiring helpers (decks as flat 32-byte-per-point arrays, proof as bytes) ----
    // Keep the byte↔point conversion in this tested layer so the network handlers stay tiny.

    /** Decode a flat deck (n×32 bytes) to points, or null if any point is non-canonical. */
    public static EdwardsPoint[] decodeDeck(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length % 32 != 0) {
            return null;
        }
        int n = bytes.length / 32;
        EdwardsPoint[] d = new EdwardsPoint[n];
        for (int i = 0; i < n; i++) {
            d[i] = Ristretto255.decode(Arrays.copyOfRange(bytes, i * 32, (i + 1) * 32));
            if (d[i] == null) {
                return null;
            }
        }
        return d;
    }

    /**
     * Prove a cascade step from flat byte decks: {@code deckOut = shuffle(k·deckIn)} with the given
     * permutation and lock scalar. Returns the serialized proof, or null on any failure (so a caller
     * in a network handler never throws).
     */
    public static byte[] proveStepWire(byte[] deckInBytes, byte[] deckOutBytes, int[] perm, byte[] kScalar, int rounds) {
        try {
            EdwardsPoint[] in = decodeDeck(deckInBytes);
            EdwardsPoint[] out = decodeDeck(deckOutBytes);
            if (in == null || out == null || in.length != out.length) {
                return null;
            }
            return CutChooseShuffleProof.prove(in, out, perm, RistrettoSRA.bytesToScalar(kScalar), rounds).toBytes();
        } catch (Exception e) {
            return null;
        }
    }

    /** Production-strength wire proof. */
    public static byte[] proveStepWire(byte[] deckInBytes, byte[] deckOutBytes, int[] perm, byte[] kScalar) {
        return proveStepWire(deckInBytes, deckOutBytes, perm, kScalar, CutChooseShuffleProof.DEFAULT_ROUNDS);
    }

    /** Verify a serialized cascade-step proof against flat byte decks. False on any malformed input. */
    public static boolean verifyStepWire(byte[] deckInBytes, byte[] deckOutBytes, byte[] proofBytes) {
        EdwardsPoint[] in = decodeDeck(deckInBytes);
        EdwardsPoint[] out = decodeDeck(deckOutBytes);
        if (in == null || out == null) {
            return false;
        }
        CutChooseShuffleProof.Proof proof = CutChooseShuffleProof.Proof.fromBytes(proofBytes);
        if (proof == null) {
            return false;
        }
        return CutChooseShuffleProof.verify(in, out, proof);
    }
}
