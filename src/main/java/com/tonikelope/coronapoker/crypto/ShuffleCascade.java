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
 * Orchestrates the {@link ShuffleArgument} (Bayer–Groth) engine over a whole SRA cascade, and is the
 * component that <b>enforces the discrete-log-independence precondition</b> that {@code ShuffleArgument}
 * alone cannot (see its class javadoc): it anchors the chain to the public genesis deck and verifies
 * every step against the previous ALREADY-VERIFIED deck. By induction each step's input is then a
 * genuine shuffle of DL-independent points, so the per-step soundness holds and a rotation smuggle
 * (a duplicated/relocated card) is impossible — the dishonest step would not be a real shuffle and
 * cannot be proven.
 *
 * <p>Layout: {@code decks[0]} is the genesis deck, {@code decks[m+1] = shuffle_m(decks[m])} peer
 * {@code m}'s output, {@code proofs[m]} attests that step. This is the Bayer–Groth replacement for
 * {@link VerifiableCascade} (cut-and-choose); the network wiring swap is smoke-gated and done
 * separately. The anchor + chain logic here is identical to {@code VerifiableCascade.verifyChain},
 * which is exactly the property the audit flagged as load-bearing.
 */
public final class ShuffleCascade {

    private ShuffleCascade() {
    }

    /** Prove one cascade step: {@code out[i] = k·in[perm[i]]} (peer's shuffle {@code perm} + lock {@code k}). */
    public static ShuffleArgument.Proof proveStep(EdwardsPoint[] in, EdwardsPoint[] out, int[] perm, BigInteger k) {
        return ShuffleArgument.prove(in, out, perm, k);
    }

    /**
     * Verify the whole cascade is an honest shuffle chain anchored to {@code genesis}: {@code decks[0]}
     * must equal the public genesis deck byte-for-byte, and every step {@code decks[m] → decks[m+1]} must
     * carry a valid {@link ShuffleArgument}. A single failure ⇒ reject the deck.
     *
     * <p>The anchor is what makes the chain sound: {@code decks[0] == genesis} pins the first input to
     * DL-independent NUMS points, and each verified step preserves DL-independence, so no step is ever
     * verified against attacker-craftable points. Removing the anchor or skipping the chain (verifying a
     * step against a caller-supplied deck) reopens the smuggle — do not.
     *
     * @param genesis the public genesis deck every peer derives independently (the anchor)
     * @param decks   {@code decks[0..N]}; {@code decks[0]} the cascade input, {@code decks[N]} the output
     * @param proofs  {@code proofs[0..N-1]}; {@code proofs[m]} attests {@code decks[m+1] = shuffle(decks[m])}
     */
    public static boolean verifyChain(EdwardsPoint[] genesis, EdwardsPoint[][] decks, ShuffleArgument.Proof[] proofs) {
        if (genesis == null || decks == null || proofs == null
                || decks.length < 1 || proofs.length != decks.length - 1) {
            return false;
        }
        if (!DeckTransform.decksEqual(genesis, decks[0])) {
            return false; // not anchored to the committed public genesis deck
        }
        for (int m = 0; m < proofs.length; m++) {
            if (decks[m] == null || decks[m + 1] == null
                    || !ShuffleArgument.verify(decks[m], decks[m + 1], proofs[m])) {
                return false;
            }
        }
        return true;
    }
}
