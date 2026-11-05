/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.Random;
import org.junit.jupiter.api.Test;

final class Scalar25519Test {

    @Test
    void fixedWordsAndWnafMatchBigIntegerReference() {
        Random random = new Random(0x5ca1a25519L);
        for (int sample = 0; sample < 100_000; sample++) {
            BigInteger input = new BigInteger(512, random);
            BigInteger expected = input.mod(EdwardsPoint.L);
            Scalar25519 scalar = Scalar25519.fromReduced(input);
            assertEquals(expected.bitLength(), scalar.bitLength());

            BigInteger windows = BigInteger.ZERO;
            for (int bit = 0; bit < 256; bit += 4) {
                windows = windows.add(BigInteger.valueOf(
                        scalar.window4(bit)).shiftLeft(bit));
            }
            assertEquals(expected, windows);

            Scalar25519.Wnaf wnaf = scalar.wnaf5();
            BigInteger reconstructed = BigInteger.ZERO;
            for (int bit = 0; bit < wnaf.length(); bit++) {
                int digit = wnaf.digits()[bit];
                if (digit != 0) {
                    reconstructed = reconstructed.add(
                            BigInteger.valueOf(digit).shiftLeft(bit));
                }
            }
            assertEquals(expected, reconstructed,
                    "wNAF mismatch at sample " + sample);
        }
    }

    @Test
    void unreducedWordsPreserveFullUnsigned256BitMagnitude() {
        BigInteger[] values = {
            BigInteger.ZERO,
            BigInteger.ONE,
            BigInteger.ONE.shiftLeft(255),
            BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE)
        };
        for (BigInteger expected : values) {
            Scalar25519 scalar = Scalar25519.fromUnreduced(expected);
            Scalar25519.Wnaf wnaf = scalar.wnaf5();
            BigInteger reconstructed = BigInteger.ZERO;
            for (int bit = 0; bit < wnaf.length(); bit++) {
                reconstructed = reconstructed.add(BigInteger.valueOf(
                        wnaf.digits()[bit]).shiftLeft(bit));
            }
            assertEquals(expected, reconstructed);
        }
    }
}
