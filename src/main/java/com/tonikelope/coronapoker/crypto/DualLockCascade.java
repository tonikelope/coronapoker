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

import java.util.Arrays;

/**
 * Verifies the WHOLE dual-lock deal chain end-to-end, genesis → MEGAPACKET, so that every dealt point
 * is provably tied back to the public genesis with no relocation or duplication anywhere — closing the
 * rotation smuggle that {@link ShuffleCascade} alone leaves open (it covers only genesis→pre-rotation).
 *
 * <p>The chain has two segments:
 * <ol>
 *   <li><b>Cascade</b> (genesis → pre-rotation deck): every step an honest re-encryption shuffle,
 *       verified by {@link ShuffleCascade#verifyChain} (Bayer–Groth, anchored to genesis).</li>
 *   <li><b>Rotation</b> (pre-rotation community region → MEGAPACKET community region): every peer's
 *       step an honest in-place common-scalar re-key, verified by {@link RotationProof} (batch-DLEQ),
 *       chained from the pre-rotation community region.</li>
 * </ol>
 * Plus two structural invariants: the MEGAPACKET <b>pocket region equals the pre-rotation pocket region
 * byte-for-byte</b> (the rotation must not touch pocket pieces), and the MEGAPACKET <b>community region
 * equals the rotation chain's output</b>. Together these force the MEGAPACKET to be a genuine
 * permutation+relock of genesis: a host cannot park one player's pocket point into a community slot,
 * nor duplicate a card, without failing a proof or an invariant.
 *
 * <p>Point-level API (the network layer feeds decoded decks). Like {@link ShuffleCascade}, this only
 * verifies — the wiring (broadcasting the proofs + each peer running this before the unlock window) is
 * what makes it effective; verifying against a caller-supplied genesis is not sound (the anchor must be
 * the independently-recomputed genesis).
 */
public final class DualLockCascade {

    private DualLockCascade() {
    }

    private static boolean regionEqual(EdwardsPoint[] a, EdwardsPoint[] b) {
        if (a == null || b == null || a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            // Native Ristretto group equality: same relation as comparing canonical encodings.
            if (a[i] == null || b[i] == null || !Ristretto255.equalPoints(a[i], b[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Verify the full genesis → MEGAPACKET dual-lock chain.
     *
     * @param genesis        the public genesis deck (the anchor; every peer recomputes it independently)
     * @param cascadeDecks   {@code [genesis, ..., preRotation]} — the Bayer–Groth cascade decks
     * @param cascadeProofs  one {@link ShuffleArgument} proof per cascade step
     * @param pocketCount    number of pocket positions = numPlayers·2 (the community region is the rest)
     * @param megapacket     the dealt deck (pocket region from the cascade, community region rotated)
     * @param rotationStates {@code [preRotationCommunity, ..., megapacketCommunity]} — per-peer rekey states
     * @param rotationProofs one {@link RotationProof} per rotation step
     */
    public static boolean verifyFullChain(EdwardsPoint[] genesis,
                                          EdwardsPoint[][] cascadeDecks, ShuffleArgument.Proof[] cascadeProofs,
                                          int pocketCount, EdwardsPoint[] megapacket,
                                          EdwardsPoint[][] rotationStates, RotationProof.Proof[] rotationProofs) {
        if (genesis == null || cascadeDecks == null || megapacket == null
                || rotationStates == null || rotationProofs == null
                || cascadeDecks.length < 1 || rotationStates.length < 1
                || rotationProofs.length != rotationStates.length - 1) {
            return false;
        }
        int deckLen = genesis.length;
        if (pocketCount < 0 || pocketCount > deckLen || megapacket.length != deckLen) {
            return false;
        }
        int communityLen = deckLen - pocketCount;

        // (1) Cascade: genesis → pre-rotation, every step an honest shuffle anchored to genesis.
        if (!ShuffleCascade.verifyChain(genesis, cascadeDecks, cascadeProofs)) {
            return false;
        }
        EdwardsPoint[] preRotation = cascadeDecks[cascadeDecks.length - 1];
        if (preRotation == null || preRotation.length != deckLen) {
            return false;
        }

        // (2) Pocket invariance: the MEGAPACKET pocket region must equal the pre-rotation pocket region.
        EdwardsPoint[] preRotPocket = Arrays.copyOfRange(preRotation, 0, pocketCount);
        EdwardsPoint[] megaPocket = Arrays.copyOfRange(megapacket, 0, pocketCount);
        if (!regionEqual(preRotPocket, megaPocket)) {
            return false;
        }

        // (3) Rotation chain anchor: rotationStates[0] must equal the pre-rotation community region.
        EdwardsPoint[] preRotCommunity = Arrays.copyOfRange(preRotation, pocketCount, deckLen);
        if (rotationStates[0] == null || rotationStates[0].length != communityLen
                || !regionEqual(rotationStates[0], preRotCommunity)) {
            return false;
        }

        // (4) Rotation steps: every peer's step an honest in-place common-scalar rekey.
        for (int j = 0; j < rotationProofs.length; j++) {
            if (rotationStates[j] == null || rotationStates[j + 1] == null
                    || rotationStates[j + 1].length != communityLen
                    || !RotationProof.verify(rotationStates[j], rotationStates[j + 1], rotationProofs[j])) {
                return false;
            }
        }

        // (5) MEGAPACKET community region must equal the rotation chain's output.
        EdwardsPoint[] megaCommunity = Arrays.copyOfRange(megapacket, pocketCount, deckLen);
        return regionEqual(rotationStates[rotationStates.length - 1], megaCommunity);
    }
}
