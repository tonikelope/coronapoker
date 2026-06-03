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
import java.util.List;

/**
 * Byte-oriented wiring for the full {@link DualLockCascade} verification, so the network layer only
 * moves {@code byte[]} / {@code List<byte[]>}. The genesis is NOT transmitted — every peer recomputes it
 * locally (the anchor); a peer that receives the cascade-deck chain, the rotation-state chain and the
 * two proof lists runs {@link #verifyFullChainWire} before participating in the unlock, and refuses the
 * deck on any failure. Decoding is total: malformed input yields {@code false}, never an exception.
 */
public final class DualLockWire {

    private DualLockWire() {
    }

    /** Flat encoding of a deck/region: {@code n × 32} bytes (canonical Ristretto points). */
    public static byte[] encodeDeck(EdwardsPoint[] deck) {
        if (deck == null) {
            return null;
        }
        byte[] out = new byte[deck.length * 32];
        for (int i = 0; i < deck.length; i++) {
            System.arraycopy(Ristretto255.encode(deck[i]), 0, out, i * 32, 32);
        }
        return out;
    }

    /** Serialize a {@link RotationProof.Proof} to a fixed 64 bytes: {@code T (32) || z (32 BE)}. */
    public static byte[] encodeRotationProof(RotationProof.Proof p) {
        if (p == null || p.t == null || p.t.length != 32 || p.z == null) {
            return null;
        }
        byte[] out = new byte[64];
        System.arraycopy(p.t, 0, out, 0, 32);
        System.arraycopy(ProofCodec.scalarToBytes(p.z), 0, out, 32, 32);
        return out;
    }

    static RotationProof.Proof decodeRotationProof(byte[] bytes) {
        if (bytes == null || bytes.length != 64) {
            return null;
        }
        byte[] t = java.util.Arrays.copyOfRange(bytes, 0, 32);
        BigInteger z = new BigInteger(1, java.util.Arrays.copyOfRange(bytes, 32, 64));
        return new RotationProof.Proof(t, z);
    }

    /**
     * Verify the full dual-lock chain from flat bytes. {@code genesisBytes} is the locally-recomputed
     * anchor (never trust a sender's genesis). {@code cascadeDeckBytes} are decks {@code [1..N]} (deck 0
     * is the genesis, prepended here); {@code rotationStateBytes} are community states {@code [1..M]}
     * (state 0 is the pre-rotation community region, derived here). Returns false on any malformed/short/
     * non-anchored input.
     */
    public static boolean verifyFullChainWire(byte[] genesisBytes,
                                              List<byte[]> cascadeDeckBytes, List<byte[]> cascadeProofBytes,
                                              int pocketCount, byte[] megapacketBytes,
                                              List<byte[]> rotationStateBytes, List<byte[]> rotationProofBytes) {
        try {
            if (genesisBytes == null || cascadeDeckBytes == null || cascadeProofBytes == null
                    || megapacketBytes == null || rotationStateBytes == null || rotationProofBytes == null) {
                return false;
            }
            EdwardsPoint[] genesis = ShuffleCascade.decodeDeck(genesisBytes);
            EdwardsPoint[] megapacket = ShuffleCascade.decodeDeck(megapacketBytes);
            if (genesis == null || megapacket == null) {
                return false;
            }
            if (cascadeProofBytes.size() != cascadeDeckBytes.size()) {
                return false; // one cascade proof per step (steps == decks[1..N])
            }
            // Cascade decks: [genesis, deck1, ..., deckN]
            EdwardsPoint[][] cascadeDecks = new EdwardsPoint[cascadeDeckBytes.size() + 1][];
            cascadeDecks[0] = genesis;
            for (int i = 0; i < cascadeDeckBytes.size(); i++) {
                cascadeDecks[i + 1] = ShuffleCascade.decodeDeck(cascadeDeckBytes.get(i));
                if (cascadeDecks[i + 1] == null) {
                    return false;
                }
            }
            ShuffleArgument.Proof[] cascadeProofs = new ShuffleArgument.Proof[cascadeProofBytes.size()];
            for (int i = 0; i < cascadeProofBytes.size(); i++) {
                cascadeProofs[i] = ProofCodec.decodeShuffle(cascadeProofBytes.get(i));
                if (cascadeProofs[i] == null) {
                    return false;
                }
            }
            // Rotation states: [preRotationCommunity, state1, ..., stateM]
            EdwardsPoint[] preRotation = cascadeDecks[cascadeDecks.length - 1];
            if (preRotation == null || pocketCount < 0 || pocketCount > preRotation.length) {
                return false;
            }
            EdwardsPoint[] preRotCommunity = java.util.Arrays.copyOfRange(preRotation, pocketCount, preRotation.length);
            EdwardsPoint[][] rotationStates = new EdwardsPoint[rotationStateBytes.size() + 1][];
            rotationStates[0] = preRotCommunity;
            for (int i = 0; i < rotationStateBytes.size(); i++) {
                rotationStates[i + 1] = ShuffleCascade.decodeDeck(rotationStateBytes.get(i));
                if (rotationStates[i + 1] == null) {
                    return false;
                }
            }
            RotationProof.Proof[] rotationProofs = new RotationProof.Proof[rotationProofBytes.size()];
            for (int i = 0; i < rotationProofBytes.size(); i++) {
                rotationProofs[i] = decodeRotationProof(rotationProofBytes.get(i));
                if (rotationProofs[i] == null) {
                    return false;
                }
            }
            return DualLockCascade.verifyFullChain(genesis, cascadeDecks, cascadeProofs,
                    pocketCount, megapacket, rotationStates, rotationProofs);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
