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
 * Element of the prime field GF(2^255 - 19) — the base field of edwards25519,
 * underlying the Ristretto255 group.
 *
 * Phase 1 of the verifiable-dealing rework (docs/sra-verifiable-dealing-design.md).
 * This is a correctness-first BigInteger implementation: every operation reduces
 * mod p, so it is correct by construction. It is validated against algebraic
 * properties and the RFC 9496 constants (see Fe25519Test). A radix-16 fast path
 * can replace the internals later, with those tests as the safety net.
 *
 * Instances are immutable. Not constant-time — acceptable for this threat model
 * (the cascade is not real-time and no peer times another's field ops over the
 * wire); a constant-time backend can replace this without changing the API.
 */
public final class Fe25519 {

    /** Field prime p = 2^255 - 19. */
    public static final BigInteger P =
            BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19));

    /**
     * sqrt(-1) mod p, per RFC 9496. Self-validated in Fe25519Test
     * (SQRT_M1^2 == -1 mod p), so a transcription error cannot slip through.
     */
    public static final BigInteger SQRT_M1 = new BigInteger(
            "19681161376707505956807079304988542015446066515923890162744021073123829784752");

    public static final Fe25519 ZERO = new Fe25519(BigInteger.ZERO);
    public static final Fe25519 ONE = new Fe25519(BigInteger.ONE);

    private static final BigInteger P_MINUS_2 = P.subtract(BigInteger.TWO);
    private static final BigInteger P_MINUS_5_DIV_8 =
            P.subtract(BigInteger.valueOf(5)).divide(BigInteger.valueOf(8));

    /** Canonical representative in [0, P). */
    private final BigInteger v;

    private Fe25519(BigInteger value) {
        this.v = value.mod(P); // BigInteger.mod is always non-negative
    }

    public static Fe25519 of(BigInteger value) {
        return new Fe25519(value);
    }

    public static Fe25519 of(long value) {
        return new Fe25519(BigInteger.valueOf(value));
    }

    /**
     * Decodes a field element from 32 little-endian bytes, masking bit 255 and
     * reducing mod p. Does NOT enforce canonicity — the Ristretto point decode
     * performs the canonical-encoding check separately.
     */
    public static Fe25519 fromBytes(byte[] in) {
        if (in == null || in.length != 32) {
            throw new IllegalArgumentException("field element must be 32 bytes");
        }
        byte[] be = new byte[32];
        for (int i = 0; i < 32; i++) {
            be[i] = in[31 - i];
        }
        be[0] &= (byte) 0x7f; // mask bit 255 (now the most-significant byte)
        return new Fe25519(new BigInteger(1, be));
    }

    /** Encodes to 32 canonical little-endian bytes (value in [0, P)). */
    public byte[] toBytes() {
        byte[] be = v.toByteArray(); // big-endian, two's complement (v >= 0)
        byte[] le = new byte[32];
        for (int i = 0; i < be.length; i++) {
            int idx = be.length - 1 - i; // walk from least-significant byte up
            if (i < 32) {
                le[i] = be[idx];
            }
        }
        return le;
    }

    public Fe25519 add(Fe25519 o) {
        return new Fe25519(v.add(o.v));
    }

    public Fe25519 sub(Fe25519 o) {
        return new Fe25519(v.subtract(o.v));
    }

    public Fe25519 mul(Fe25519 o) {
        return new Fe25519(v.multiply(o.v));
    }

    public Fe25519 sqr() {
        return new Fe25519(v.multiply(v));
    }

    public Fe25519 negate() {
        return new Fe25519(v.negate());
    }

    public Fe25519 pow(BigInteger e) {
        return new Fe25519(v.modPow(e, P));
    }

    /** Multiplicative inverse via Fermat's little theorem (a^(p-2)). */
    public Fe25519 inv() {
        return new Fe25519(v.modPow(P_MINUS_2, P));
    }

    public boolean isZero() {
        return v.signum() == 0;
    }

    /** Ristretto IS_NEGATIVE: the least-significant bit of the canonical encoding. */
    public boolean isNegative() {
        return v.testBit(0);
    }

    /** CT_ABS: -x if x is negative, else x. */
    public Fe25519 abs() {
        return isNegative() ? negate() : this;
    }

    /** Constant-value equality (not timing-relevant here). */
    public boolean ctEq(Fe25519 o) {
        return v.equals(o.v);
    }

    public BigInteger toBigInteger() {
        return v;
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof Fe25519) && v.equals(((Fe25519) o).v);
    }

    @Override
    public int hashCode() {
        return v.hashCode();
    }

    @Override
    public String toString() {
        return "Fe25519(" + v.toString(16) + ")";
    }

    /**
     * RFC 9496 §4.3 sqrt_ratio_i(u, v).
     *
     * Computes r = sqrt(u/v) when u/v is a square (wasSquare = true), or
     * sqrt(i * u/v) otherwise (wasSquare = false), where i = sqrt(-1). The
     * returned r is always non-negative (CT_ABS). Invariant:
     *   wasSquare  -> r^2 * v == u
     *   !wasSquare -> r^2 * v == SQRT_M1 * u
     *
     * @return {wasSquare, r}
     */
    public static SqrtRatioResult sqrtRatioM1(Fe25519 u, Fe25519 v) {
        Fe25519 v3 = v.sqr().mul(v);          // v^3
        Fe25519 v7 = v3.sqr().mul(v);         // v^7
        Fe25519 r = u.mul(v3).mul(u.mul(v7).pow(P_MINUS_5_DIV_8));
        Fe25519 check = v.mul(r.sqr());       // v * r^2

        Fe25519 uNeg = u.negate();
        Fe25519 uNegI = uNeg.mul(of(SQRT_M1));

        boolean correctSignSqrt = check.ctEq(u);
        boolean flippedSignSqrt = check.ctEq(uNeg);
        boolean flippedSignSqrtI = check.ctEq(uNegI);

        Fe25519 rPrime = r.mul(of(SQRT_M1));
        if (flippedSignSqrt || flippedSignSqrtI) {
            r = rPrime;
        }
        r = r.abs();

        boolean wasSquare = correctSignSqrt || flippedSignSqrt;
        return new SqrtRatioResult(wasSquare, r);
    }

    /** Result of {@link #sqrtRatioM1}. */
    public static final class SqrtRatioResult {
        public final boolean wasSquare;
        public final Fe25519 r;

        SqrtRatioResult(boolean wasSquare, Fe25519 r) {
            this.wasSquare = wasSquare;
            this.r = r;
        }
    }
}
