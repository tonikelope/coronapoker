/*
 * Copyright (C) 2020 tonikelope
 _              _ _        _
| |_ ___  _ __ (_) | _____| | ___  _ __   ___
| __/ _ \| '_ \| | |/ / _ \ |/ _ \| '_ \ / _ \
| || (_) | | | | |   <  __/ | (_) | |_) |  __/
 \__\___/|_| |_|_|_|\_\___|_|\___/| .__/ \___|
 ____    ___  ____    ___
|___ \  / _ \|___ \  / _ \
  __) || | | | __) || | | |
 / __/ | |_| |/ __/ | |_| |
|_____| \___/|_____| \___/

https://github.com/tonikelope/coronapoker
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
package com.tonikelope.coronapoker;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Deterministic, seed-driven deck shuffle for the SRA cascade (formerly part of the legacy
 * CryptoSRA engine, whose Montgomery point math was retired by the Ristretto255 migration).
 *
 * A peer's cascade step reorders the deck with a Fisher-Yates shuffle whose index draws come
 * from an AES-256-CTR stream expanded from the per-hand seed, using rejection sampling so the
 * draw is unbiased. Determinism is the point: {@link #shufflePermutation} replays the exact
 * same swaps to recover the permutation the peer applied, which the Bayer-Groth shuffle
 * argument needs as a witness (the permutation is never revealed on the wire).
 */
public class DeterministicShuffle {

    /**
     * Shuffles a flat array of 32-byte points deterministically using AES-256-CTR.
     * @param deck The deck to be shuffled
     * @param seed The deterministic seed (32-byte key, optionally + 16-byte IV)
     * @return The shuffled deck
     */
    public static byte[] shuffleDeck(byte[] deck, byte[] seed) {
        if (deck == null || deck.length % 32 != 0) {
            return null;
        }

        byte[] shuffled = new byte[deck.length];
        System.arraycopy(deck, 0, shuffled, 0, deck.length);

        try {
            DeterministicStream stream = new DeterministicStream(seed);
            int numCards = deck.length / 32;
            byte[] temp = new byte[32];

            for (int i = numCards - 1; i > 0; i--) {
                int j = stream.getUnbiasedInt(i + 1);
                System.arraycopy(shuffled, i * 32, temp, 0, 32);
                System.arraycopy(shuffled, j * 32, shuffled, i * 32, 32);
                System.arraycopy(temp, 0, shuffled, j * 32, 32);
            }
        } catch (Exception e) {
            throw new RuntimeException("Deterministic shuffle failed", e);
        }
        return shuffled;
    }

    /**
     * The permutation that {@link #shuffleDeck} applies for a given seed, as an index array:
     * {@code perm[i]} is the original position whose card ends up at position {@code i}, i.e.
     * {@code shuffleDeck(deck, seed)[i] == deck[perm[i]]}. Replays the exact same Fisher–Yates
     * swaps on an identity index array, so it stays in lock-step with {@code shuffleDeck}.
     *
     * <p>Needed by the verifiable-shuffle engine: a peer must know its own permutation to prove its
     * cascade step was an honest shuffle (it is never revealed on the wire).
     */
    public static int[] shufflePermutation(int numCards, byte[] seed) {
        int[] perm = new int[numCards];
        for (int i = 0; i < numCards; i++) {
            perm[i] = i;
        }
        try {
            DeterministicStream stream = new DeterministicStream(seed);
            for (int i = numCards - 1; i > 0; i--) {
                int j = stream.getUnbiasedInt(i + 1);
                int t = perm[i];
                perm[i] = perm[j];
                perm[j] = t;
            }
        } catch (Exception e) {
            throw new RuntimeException("Deterministic shuffle permutation failed", e);
        }
        return perm;
    }

    // =========================================================================
    // DETERMINISTIC AES-256-CTR EXPANSION STREAM (Zero-State Collisions)
    // =========================================================================
    private static final class DeterministicStream {
        private final Cipher cipher;
        private final byte[] zeroBlock = new byte[64];
        private byte[] buffer = new byte[0];
        private int index = 0;

        public DeterministicStream(byte[] seed) throws Exception {
            // First 32 bytes: AES-256 key. Bytes 32..48: IV. Falls back to a zero IV
            // when the caller supplies a 32-byte legacy seed.
            byte[] key = new byte[32];
            byte[] iv = new byte[16];
            System.arraycopy(seed, 0, key, 0, Math.min(seed.length, 32));
            if (seed.length >= 48) {
                System.arraycopy(seed, 32, iv, 0, 16);
            }

            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            cipher = Cipher.getInstance("AES/CTR/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        }

        /**
         * Extracts an integer using Rejection Sampling to prevent Modulo Bias
         * @param range The maximum limit (exclusive)
         * @return An unbiased uniformly distributed integer in [0, range)
         */
        public int getUnbiasedInt(int range) throws Exception {
            int limit = 256 - (256 % range);
            while (true) {
                if (index >= buffer.length) {
                    buffer = cipher.update(zeroBlock);
                    index = 0;
                }
                int val = buffer[index++] & 0xFF; // Unsigned byte representation
                if (val < limit) {
                    return val % range;
                }
            }
        }
    }
}
