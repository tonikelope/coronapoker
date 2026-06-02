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

import com.tonikelope.coronapoker.Helpers;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * Commutative-SRA primitives over the prime-order Ristretto255 group — the
 * intended replacement for the Montgomery x-only core in {@code CryptoSRA}.
 *
 * Phase 1 of the verifiable-dealing rework (docs/sra-verifiable-dealing-design.md).
 * Everything is a 32-byte canonical Ristretto encoding on the wire (same size as
 * today, so the command protocol is unchanged). Because Ristretto has prime order,
 * scalars need no clamping and there is no cofactor / small-subgroup caveat: a
 * lock is multiplication by a uniform scalar in [1, L), an unlock is its inverse
 * mod L, and decode() rejects any malformed point (replacing arePointsOnCurve).
 *
 * This class is NOT yet wired into the game; it is the validated engine that the
 * cascade migration (Phase 3) will build on.
 */
public final class RistrettoSRA {

    /** Prime subgroup order L (scalar field modulus). */
    public static final BigInteger L = EdwardsPoint.L;

    private static final int POINT_BYTES = 32;
    private static final int DECK_CARDS = 52;
    private static final String CARD_LABEL_PREFIX = "CORONAPOKER_RISTRETTO_CARD_";

    private static volatile byte[][] genesisDeckCache = null;

    private RistrettoSRA() {
    }

    /** Generates a uniform lock scalar in [1, L), encoded as 32 little-endian bytes. */
    public static byte[] generateLockScalar() {
        while (true) {
            byte[] raw = new byte[32];
            Helpers.CSPRNG_GENERATOR.nextBytes(raw);
            raw[31] &= (byte) 0x1f; // keep <= 253 bits to make rejection efficient
            BigInteger s = bytesToScalar(raw);
            if (s.signum() != 0 && s.compareTo(L) < 0) {
                return raw;
            }
        }
    }

    /** Multiplicative inverse mod L of a lock scalar — the matching unlock. */
    public static byte[] getUnlockScalar(byte[] lockScalar) {
        BigInteger s = bytesToScalar(lockScalar);
        return scalarToBytes(s.modInverse(L));
    }

    /**
     * Public commitment K = k*B (32-byte encoding) for a lock scalar k. Published
     * in H_0 so peers can verify, via DLEQ, that an unlock used this committed key.
     */
    public static byte[] commitment(byte[] lockScalar) {
        return Ristretto255.encode(EdwardsPoint.BASE.scalarMul(bytesToScalar(lockScalar)));
    }

    /**
     * Applies a commutative lock (scalar multiplication) to every 32-byte point in
     * a flat deck. Returns null if the deck is malformed or any point fails to
     * decode (off-group) — the caller treats null as a zero-trust rejection.
     */
    public static byte[] applyCommutativeLock(byte[] deck, byte[] scalar) {
        if (deck == null || deck.length == 0 || deck.length % POINT_BYTES != 0) {
            return null;
        }
        BigInteger s = bytesToScalar(scalar);
        int n = deck.length / POINT_BYTES;
        byte[] out = new byte[deck.length];
        for (int i = 0; i < n; i++) {
            EdwardsPoint p = Ristretto255.decode(Arrays.copyOfRange(deck, i * POINT_BYTES, (i + 1) * POINT_BYTES));
            if (p == null) {
                return null; // off-group / non-canonical point
            }
            byte[] enc = Ristretto255.encode(p.scalarMul(s));
            System.arraycopy(enc, 0, out, i * POINT_BYTES, POINT_BYTES);
        }
        return out;
    }

    /** The 52-card genesis deck as a flat 52*32-byte array of canonical encodings. */
    public static byte[] getGenesisDeck() {
        byte[][] cache = genesisDeckCache;
        if (cache == null) {
            synchronized (RistrettoSRA.class) {
                if (genesisDeckCache == null) {
                    byte[][] tmp = new byte[DECK_CARDS][];
                    try {
                        MessageDigest sha512 = MessageDigest.getInstance("SHA-512");
                        for (int i = 0; i < DECK_CARDS; i++) {
                            byte[] seed = sha512.digest((CARD_LABEL_PREFIX + i).getBytes("UTF-8"));
                            tmp[i] = Ristretto255.hashToGroupEncoded(seed);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to build Ristretto genesis deck", e);
                    }
                    genesisDeckCache = tmp;
                }
                cache = genesisDeckCache;
            }
        }
        byte[] flat = new byte[DECK_CARDS * POINT_BYTES];
        for (int i = 0; i < DECK_CARDS; i++) {
            System.arraycopy(cache[i], 0, flat, i * POINT_BYTES, POINT_BYTES);
        }
        return flat;
    }

    /**
     * Validates that a flat array is a whole number of 32-byte points and that every
     * one decodes as a canonical Ristretto255 element. Replaces the old
     * arePointsOnCurve gate: in a prime-order group a valid decode IS the membership
     * proof, and it additionally rejects non-canonical encodings.
     */
    public static boolean arePointsValid(byte[] data) {
        if (data == null || data.length == 0 || data.length % POINT_BYTES != 0) {
            return false;
        }
        int n = data.length / POINT_BYTES;
        for (int i = 0; i < n; i++) {
            if (Ristretto255.decode(Arrays.copyOfRange(data, i * POINT_BYTES, (i + 1) * POINT_BYTES)) == null) {
                return false;
            }
        }
        return true;
    }

    /** Resolves a fully-unlocked 32-byte point to its card index 0-51, or -1. */
    public static int resolveCardIndex(byte[] unlockedCard) {
        if (unlockedCard == null || unlockedCard.length != POINT_BYTES) {
            return -1;
        }
        getGenesisDeck(); // ensure cache populated
        byte[][] cache = genesisDeckCache;
        for (int i = 0; i < DECK_CARDS; i++) {
            if (Arrays.equals(unlockedCard, cache[i])) {
                return i;
            }
        }
        return -1;
    }

    // --- scalar <-> 32-byte little-endian helpers ---

    static BigInteger bytesToScalar(byte[] le32) {
        byte[] be = new byte[le32.length];
        for (int i = 0; i < le32.length; i++) {
            be[i] = le32[le32.length - 1 - i];
        }
        return new BigInteger(1, be);
    }

    static byte[] scalarToBytes(BigInteger s) {
        byte[] be = s.toByteArray();
        byte[] le = new byte[32];
        for (int i = 0; i < be.length && i < 32; i++) {
            le[i] = be[be.length - 1 - i];
        }
        return le;
    }
}
