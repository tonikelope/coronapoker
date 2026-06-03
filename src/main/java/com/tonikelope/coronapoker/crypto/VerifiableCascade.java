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
}
