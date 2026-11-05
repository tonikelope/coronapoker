/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.crypto;

import java.math.BigInteger;

/**
 * Non-negative scalar stored as four little-endian 64-bit words. Public/wire
 * APIs keep using {@link BigInteger}; this representation removes its
 * allocation-heavy bit operations from the scalar-multiplication ladders.
 */
final class Scalar25519 {

    private static final int WORDS = 4;
    private static final int WNAF_WORDS_COUNT = WORDS + 1;
    private static final int MAX_WNAF_DIGITS = 258;
    private static final ThreadLocal<long[]> WNAF_WORDS
            = ThreadLocal.withInitial(() -> new long[WNAF_WORDS_COUNT]);
    private static final ThreadLocal<int[]> WNAF_DIGITS
            = ThreadLocal.withInitial(() -> new int[MAX_WNAF_DIGITS]);

    private final long w0;
    private final long w1;
    private final long w2;
    private final long w3;

    private Scalar25519(long w0, long w1, long w2, long w3) {
        this.w0 = w0;
        this.w1 = w1;
        this.w2 = w2;
        this.w3 = w3;
    }

    static Scalar25519 fromReduced(BigInteger value) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException("scalar must be non-negative");
        }
        return fromMagnitude(value.mod(EdwardsPoint.L));
    }

    static Scalar25519 fromUnreduced(BigInteger value) {
        if (value == null || value.signum() < 0 || value.bitLength() > 256) {
            throw new IllegalArgumentException(
                    "scalar must be a non-negative 256-bit integer");
        }
        return fromMagnitude(value);
    }

    private static Scalar25519 fromMagnitude(BigInteger value) {
        long w0 = value.longValue();
        value = value.shiftRight(64);
        long w1 = value.longValue();
        value = value.shiftRight(64);
        long w2 = value.longValue();
        long w3 = value.shiftRight(64).longValue();
        return new Scalar25519(w0, w1, w2, w3);
    }

    boolean isZero() {
        return (w0 | w1 | w2 | w3) == 0L;
    }

    int bitLength() {
        if (w3 != 0L) return 256 - Long.numberOfLeadingZeros(w3);
        if (w2 != 0L) return 192 - Long.numberOfLeadingZeros(w2);
        if (w1 != 0L) return 128 - Long.numberOfLeadingZeros(w1);
        return w0 == 0L ? 0 : 64 - Long.numberOfLeadingZeros(w0);
    }

    int window4(int bit) {
        if (bit < 0 || bit >= 256) return 0;
        int word = bit >>> 6;
        int shift = bit & 63;
        long value = word(word) >>> shift;
        if (shift > 60 && word + 1 < WORDS) {
            value |= word(word + 1) << (64 - shift);
        }
        return (int) value & 0x0f;
    }

    /**
     * Returns a thread-owned width-5 wNAF digit array.  The caller must consume
     * it before requesting another wNAF on the same thread.
     */
    Wnaf wnaf5() {
        long[] words = WNAF_WORDS.get();
        words[0] = w0;
        words[1] = w1;
        words[2] = w2;
        words[3] = w3;
        words[4] = 0L;
        int[] digits = WNAF_DIGITS.get();
        int length = 0;
        while (!zero(words)) {
            int digit = 0;
            if ((words[0] & 1L) != 0L) {
                digit = (int) words[0] & 31;
                if (digit >= 16) digit -= 32;
                if (digit > 0) {
                    subtractSmall(words, digit);
                } else {
                    addSmall(words, -digit);
                }
            }
            digits[length++] = digit;
            shiftRightOne(words);
        }
        return new Wnaf(digits, length);
    }

    private long word(int index) {
        return switch (index) {
            case 0 -> w0;
            case 1 -> w1;
            case 2 -> w2;
            case 3 -> w3;
            default -> 0L;
        };
    }

    private static boolean zero(long[] words) {
        return (words[0] | words[1] | words[2] | words[3]
                | words[4]) == 0L;
    }

    private static void shiftRightOne(long[] words) {
        for (int i = 0; i < words.length - 1; i++) {
            words[i] = (words[i] >>> 1) | (words[i + 1] << 63);
        }
        words[words.length - 1] >>>= 1;
    }

    private static void addSmall(long[] words, int value) {
        long previous = words[0];
        words[0] += value;
        if (Long.compareUnsigned(words[0], previous) >= 0) return;
        for (int i = 1; i < words.length; i++) {
            words[i]++;
            if (words[i] != 0L) return;
        }
    }

    private static void subtractSmall(long[] words, int value) {
        long previous = words[0];
        words[0] -= value;
        if (Long.compareUnsigned(previous, value) >= 0) return;
        for (int i = 1; i < words.length; i++) {
            long before = words[i];
            words[i]--;
            if (before != 0L) return;
        }
    }

    record Wnaf(int[] digits, int length) {
    }
}
