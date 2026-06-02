/*
 * Phase 1 (Ristretto255 engine) — field element tests.
 *
 * Correctness-first validation of Fe25519 (GF(2^255-19)) before any curve
 * arithmetic is built on top: self-validates the RFC 9496 constants, checks the
 * algebraic laws (inverse, square, distributivity), exercises sqrt_ratio_i in
 * both the square and non-square branches, and round-trips the byte encoding.
 */
package com.tonikelope.coronapoker.crypto;

import java.math.BigInteger;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Fe25519Test {

    private static final BigInteger P = Fe25519.P;

    @Test
    public void primeIsCorrect() {
        // p = 2^255 - 19
        assertEquals(BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19)), P);
    }

    @Test
    public void sqrtM1ConstantSelfValidates() {
        // SQRT_M1^2 must equal -1 mod p. Catches any transcription error in the constant.
        Fe25519 i = Fe25519.of(Fe25519.SQRT_M1);
        assertEquals(Fe25519.ONE.negate(), i.sqr(),
                "SQRT_M1^2 must be -1 mod p");
    }

    @Test
    public void inverseLaw() {
        for (long a = 2; a < 50; a++) {
            Fe25519 x = Fe25519.of(a);
            assertEquals(Fe25519.ONE, x.mul(x.inv()), "a * a^-1 must be 1 for a=" + a);
        }
        // A few large-ish values.
        BigInteger[] big = {
            P.subtract(BigInteger.ONE),
            Fe25519.SQRT_M1,
            BigInteger.valueOf(2).pow(200).add(BigInteger.valueOf(12345))
        };
        for (BigInteger b : big) {
            Fe25519 x = Fe25519.of(b);
            assertEquals(Fe25519.ONE, x.mul(x.inv()), "a * a^-1 must be 1 for a=" + b);
        }
    }

    @Test
    public void squareEqualsSelfMultiply() {
        Random rnd = new Random(42);
        for (int k = 0; k < 100; k++) {
            BigInteger b = new BigInteger(255, rnd);
            Fe25519 x = Fe25519.of(b);
            assertEquals(x.mul(x), x.sqr());
        }
    }

    @Test
    public void distributivity() {
        Random rnd = new Random(7);
        for (int k = 0; k < 100; k++) {
            Fe25519 a = Fe25519.of(new BigInteger(255, rnd));
            Fe25519 b = Fe25519.of(new BigInteger(255, rnd));
            Fe25519 c = Fe25519.of(new BigInteger(255, rnd));
            // a*(b+c) == a*b + a*c
            assertEquals(a.mul(b.add(c)), a.mul(b).add(a.mul(c)));
        }
    }

    @Test
    public void negateAndSub() {
        Fe25519 a = Fe25519.of(12345);
        assertEquals(Fe25519.ZERO, a.add(a.negate()));
        assertEquals(a, a.sub(Fe25519.ZERO));
        assertEquals(Fe25519.ZERO, a.sub(a));
    }

    @Test
    public void isNegativeIsLsbOfCanonicalEncoding() {
        assertFalse(Fe25519.of(2).isNegative());
        assertTrue(Fe25519.of(3).isNegative());
        assertFalse(Fe25519.ZERO.isNegative());
        // -1 mod p = p-1, which is even -> not negative.
        assertFalse(Fe25519.ONE.negate().isNegative());
        // abs() returns a non-negative representative.
        assertFalse(Fe25519.of(3).abs().isNegative());
        assertEquals(Fe25519.of(3).negate(), Fe25519.of(3).abs());
    }

    @Test
    public void sqrtRatioSquareCases() {
        // 0/1 -> sqrt 0, square.
        Fe25519.SqrtRatioResult z = Fe25519.sqrtRatioM1(Fe25519.ZERO, Fe25519.ONE);
        assertTrue(z.wasSquare);
        assertEquals(Fe25519.ZERO, z.r);

        // For s in a range, u = s^2, v = 1 -> u/v is a perfect square.
        for (long s = 1; s < 40; s++) {
            Fe25519 sv = Fe25519.of(s);
            Fe25519 u = sv.sqr();
            Fe25519.SqrtRatioResult res = Fe25519.sqrtRatioM1(u, Fe25519.ONE);
            assertTrue(res.wasSquare, "s^2/1 must be a square for s=" + s);
            // Invariant for squares: r^2 * v == u.
            assertEquals(u, res.r.sqr().mul(Fe25519.ONE),
                    "r^2 * v must equal u for s=" + s);
            // r is the canonical (non-negative) root.
            assertFalse(res.r.isNegative());
        }
    }

    @Test
    public void sqrtRatioWithRandomDenominators() {
        // u = s^2 * v  =>  u/v = s^2 is a square for any non-zero v.
        Random rnd = new Random(99);
        for (int k = 0; k < 200; k++) {
            Fe25519 s = Fe25519.of(new BigInteger(255, rnd));
            Fe25519 v = Fe25519.of(new BigInteger(255, rnd));
            if (v.isZero()) {
                continue;
            }
            Fe25519 u = s.sqr().mul(v);
            Fe25519.SqrtRatioResult res = Fe25519.sqrtRatioM1(u, v);
            assertTrue(res.wasSquare, "s^2*v / v must be a square");
            assertEquals(u, res.r.sqr().mul(v), "r^2 * v == u for square case");
        }
    }

    @Test
    public void sqrtRatioNonSquareInvariant() {
        // If u/v is a non-square, the spec guarantees r^2 * v == SQRT_M1 * u.
        // We find a non-square ratio by trial and assert the invariant holds.
        Fe25519 i = Fe25519.of(Fe25519.SQRT_M1);
        Random rnd = new Random(123);
        int nonSquaresChecked = 0;
        for (int k = 0; k < 400 && nonSquaresChecked < 20; k++) {
            Fe25519 u = Fe25519.of(new BigInteger(255, rnd));
            Fe25519 v = Fe25519.of(new BigInteger(255, rnd));
            if (u.isZero() || v.isZero()) {
                continue;
            }
            Fe25519.SqrtRatioResult res = Fe25519.sqrtRatioM1(u, v);
            if (res.wasSquare) {
                assertEquals(u, res.r.sqr().mul(v), "square branch: r^2*v == u");
            } else {
                assertEquals(i.mul(u), res.r.sqr().mul(v),
                        "non-square branch: r^2*v == SQRT_M1 * u");
                nonSquaresChecked++;
            }
        }
        assertTrue(nonSquaresChecked > 0, "expected to exercise the non-square branch");
    }

    @Test
    public void byteRoundTrip() {
        Random rnd = new Random(2024);
        for (int k = 0; k < 200; k++) {
            Fe25519 x = Fe25519.of(new BigInteger(255, rnd));
            assertEquals(x, Fe25519.fromBytes(x.toBytes()), "fromBytes(toBytes(x)) == x");
        }
        // Boundary values.
        assertEquals(Fe25519.ZERO, Fe25519.fromBytes(Fe25519.ZERO.toBytes()));
        assertEquals(Fe25519.ONE, Fe25519.fromBytes(Fe25519.ONE.toBytes()));
        Fe25519 pm1 = Fe25519.ONE.negate(); // p-1, the largest canonical value
        assertEquals(pm1, Fe25519.fromBytes(pm1.toBytes()));
    }

    @Test
    public void fromBytesMasksBit255() {
        // Two encodings differing only in bit 255 must decode to the same element.
        byte[] a = Fe25519.of(1234567).toBytes();
        byte[] b = a.clone();
        b[31] |= (byte) 0x80; // set bit 255
        assertEquals(Fe25519.fromBytes(a), Fe25519.fromBytes(b),
                "bit 255 must be ignored on decode");
    }

    @Test
    public void toBytesIsLittleEndian() {
        // The value 1 encodes as 01 00 00 ... 00.
        byte[] one = Fe25519.ONE.toBytes();
        byte[] expected = new byte[32];
        expected[0] = 1;
        assertArrayEquals(expected, one);

        // The value 256 encodes as 00 01 00 ... 00.
        byte[] v256 = Fe25519.of(256).toBytes();
        byte[] exp256 = new byte[32];
        exp256[1] = 1;
        assertArrayEquals(exp256, v256);
    }
}
