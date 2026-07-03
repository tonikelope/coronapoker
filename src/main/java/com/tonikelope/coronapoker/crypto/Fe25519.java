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
 * Element of the prime field GF(2^255 - 19) — the base field of edwards25519,
 * underlying the Ristretto255 group.
 *
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

    /**
     * sqrt(-1) como Fe25519, cacheado UNA vez. {@link #sqrtRatioM1} lo usaba vía {@code of(SQRT_M1)}
     * dos veces por llamada, y se llama en cada {@code encode}/{@code decode} (~104 veces por
     * bloqueo de baraja): cada {@code of(SQRT_M1)} hacía un {@code new Fe25519} + {@code BigInteger.mod}
     * y forzaba re-descomponer a limbs en el siguiente {@code mul}. Como constante compartida, su
     * {@code limbsCache} se puebla una vez y se reutiliza. Valor idéntico, sin cambiar la reducción.
     */
    private static final Fe25519 SQRT_M1_FE = of(SQRT_M1);

    private static final BigInteger P_MINUS_2 = P.subtract(BigInteger.TWO);
    private static final BigInteger P_MINUS_5_DIV_8 =
            P.subtract(BigInteger.valueOf(5)).divide(BigInteger.valueOf(8));

    /** Canonical representative in [0, P). */
    private final BigInteger v;

    /**
     * Lazily-memoized radix-2^51 limb form of {@code v}. The instance is immutable, so this is a
     * pure cache: any operand reused across many multiplies (the curve constants {@code D2}/{@code D},
     * or a fixed field element) decomposes once instead of on every {@code mul}/{@code sqr}. {@code volatile}
     * for safe publication of the array contents across threads (the background SRA verifiers).
     */
    private volatile long[] limbsCache;

    private Fe25519(BigInteger value) {
        this.v = value.mod(P); // BigInteger.mod is always non-negative
    }

    /** Wraps an already-reduced value in [0, P) without the (expensive) mod. */
    private Fe25519(BigInteger reducedValue, boolean alreadyReduced) {
        this.v = reducedValue;
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

    /**
     * Decodes a field element from 32 little-endian bytes, REJECTING non-canonical
     * encodings: the full 256-bit little-endian value (bit 255 included, NOT masked)
     * must be strictly less than p. Returns null on non-canonical input. Used by
     * Ristretto255 decode, where non-canonical field encodings must be refused.
     */
    public static Fe25519 fromBytesCanonical(byte[] in) {
        if (in == null || in.length != 32) {
            return null;
        }
        byte[] be = new byte[32];
        for (int i = 0; i < 32; i++) {
            be[i] = in[31 - i];
        }
        BigInteger value = new BigInteger(1, be); // full 256-bit unsigned, no masking
        if (value.compareTo(P) >= 0) {
            return null; // non-canonical
        }
        return new Fe25519(value);
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

    // Both operands hold canonical values in [0, P) (the class invariant: every constructor path
    // reduces), so add/sub stay within (-P, 2P) and one conditional subtraction/addition replaces
    // the BigInteger division inside mod() — the dominant cost of the curve formulas' add chains.

    public Fe25519 add(Fe25519 o) {
        BigInteger r = v.add(o.v); // < 2P
        if (r.compareTo(P) >= 0) {
            r = r.subtract(P);
        }
        return new Fe25519(r, true);
    }

    public Fe25519 sub(Fe25519 o) {
        BigInteger r = v.subtract(o.v); // > -P
        if (r.signum() < 0) {
            r = r.add(P);
        }
        return new Fe25519(r, true);
    }

    public Fe25519 mul(Fe25519 o) {
        return fromCanonicalLimbs(mulLimbs(limbs(), o.limbs()));
    }

    public Fe25519 sqr() {
        return fromCanonicalLimbs(sqrLimbs(limbs()));
    }

    /**
     * The memoized limb form of {@code v} (see {@link #limbsCache}). {@code mulLimbs} only reads its
     * operands, never mutates them, so handing out the cached array is safe.
     */
    private long[] limbs() {
        long[] l = limbsCache;
        if (l == null) {
            l = toLimbs(v);
            limbsCache = l;
        }
        return l;
    }

    // ---- Fast field multiply: radix 2^51 limbs (5 × 64-bit), 128-bit accumulation via
    // Math.multiplyHigh, reduction mod p=2^255-19 (2^255 ≡ 19). ~max practical speed in Java.
    // BigInteger stays as canonical storage; mul/sqr convert in/out via 32 LE bytes (cheap). The
    // Fe25519FastReduceTest fuzz (200k mul/sqr vs BigInteger) + RFC 9496 vectors gate correctness.

    private static final long MASK_51 = (1L << 51) - 1;

    /** v in [0, 2^255) → 5 radix-2^51 limbs (each < 2^51). */
    private static long[] toLimbs(BigInteger value) {
        byte[] be = value.toByteArray(); // big-endian, value >= 0
        byte[] le = new byte[32];
        for (int i = 0; i < be.length && i < 32; i++) {
            le[i] = be[be.length - 1 - i];
        }
        long w0 = le64(le, 0), w1 = le64(le, 8), w2 = le64(le, 16), w3 = le64(le, 24);
        return new long[]{
            w0 & MASK_51,
            ((w0 >>> 51) | (w1 << 13)) & MASK_51,
            ((w1 >>> 38) | (w2 << 26)) & MASK_51,
            ((w2 >>> 25) | (w3 << 39)) & MASK_51,
            (w3 >>> 12) & MASK_51
        };
    }

    private static long le64(byte[] b, int off) {
        long r = 0;
        for (int i = 0; i < 8; i++) {
            r |= (b[off + i] & 0xFFL) << (8 * i);
        }
        return r;
    }

    /** Multiply two limb arrays mod p, returning CANONICAL limbs (each < 2^51, value in [0, P)). */
    private static long[] mulLimbs(long[] f, long[] g) {
        long f0 = f[0], f1 = f[1], f2 = f[2], f3 = f[3], f4 = f[4];
        long g0 = g[0], g1 = g[1], g2 = g[2], g3 = g[3], g4 = g[4];
        long g1_19 = 19 * g1, g2_19 = 19 * g2, g3_19 = 19 * g3, g4_19 = 19 * g4;

        // Each h_i accumulated as an unsigned 128-bit value (hi:lo).
        long[] acc = new long[2];
        long h0lo, h0hi, h1lo, h1hi, h2lo, h2hi, h3lo, h3hi, h4lo, h4hi;

        acc[0] = 0; acc[1] = 0;
        a128(acc, f0, g0); a128(acc, f1, g4_19); a128(acc, f2, g3_19); a128(acc, f3, g2_19); a128(acc, f4, g1_19);
        h0lo = acc[0]; h0hi = acc[1];
        acc[0] = 0; acc[1] = 0;
        a128(acc, f0, g1); a128(acc, f1, g0); a128(acc, f2, g4_19); a128(acc, f3, g3_19); a128(acc, f4, g2_19);
        h1lo = acc[0]; h1hi = acc[1];
        acc[0] = 0; acc[1] = 0;
        a128(acc, f0, g2); a128(acc, f1, g1); a128(acc, f2, g0); a128(acc, f3, g4_19); a128(acc, f4, g3_19);
        h2lo = acc[0]; h2hi = acc[1];
        acc[0] = 0; acc[1] = 0;
        a128(acc, f0, g3); a128(acc, f1, g2); a128(acc, f2, g1); a128(acc, f3, g0); a128(acc, f4, g4_19);
        h3lo = acc[0]; h3hi = acc[1];
        acc[0] = 0; acc[1] = 0;
        a128(acc, f0, g4); a128(acc, f1, g3); a128(acc, f2, g2); a128(acc, f3, g1); a128(acc, f4, g0);
        h4lo = acc[0]; h4hi = acc[1];

        return carryNormalize(h0lo, h0hi, h1lo, h1hi, h2lo, h2hi, h3lo, h3hi, h4lo, h4hi);
    }

    /**
     * Dedicated squaring: the symmetric cross terms ({@code f_i·f_j == f_j·f_i}) collapse the 25
     * 64×64 multiplies of the generic product to 15, folding the ×2 into one pre-doubled operand
     * ({@code 38·f_i·f_j = (2f_i)·(19f_j)}). The doubling famously dominates the scalar ladders
     * (4 squarings per point doubling), so this is the hottest field path of all.
     */
    private static long[] sqrLimbs(long[] f) {
        long f0 = f[0], f1 = f[1], f2 = f[2], f3 = f[3], f4 = f[4];
        long f0_2 = 2 * f0, f1_2 = 2 * f1, f2_2 = 2 * f2, f3_2 = 2 * f3;
        long f3_19 = 19 * f3, f4_19 = 19 * f4;

        // Same operand magnitudes as mulLimbs (2f < 2^52, 19f < 2^56), so the unsigned 128-bit
        // accumulation bounds are unchanged.
        long[] acc = new long[2];
        long h0lo, h0hi, h1lo, h1hi, h2lo, h2hi, h3lo, h3hi, h4lo, h4hi;

        acc[0] = 0; acc[1] = 0;
        a128(acc, f0, f0); a128(acc, f1_2, f4_19); a128(acc, f2_2, f3_19);
        h0lo = acc[0]; h0hi = acc[1];
        acc[0] = 0; acc[1] = 0;
        a128(acc, f0_2, f1); a128(acc, f2_2, f4_19); a128(acc, f3, f3_19);
        h1lo = acc[0]; h1hi = acc[1];
        acc[0] = 0; acc[1] = 0;
        a128(acc, f0_2, f2); a128(acc, f1, f1); a128(acc, f3_2, f4_19);
        h2lo = acc[0]; h2hi = acc[1];
        acc[0] = 0; acc[1] = 0;
        a128(acc, f0_2, f3); a128(acc, f1_2, f2); a128(acc, f4, f4_19);
        h3lo = acc[0]; h3hi = acc[1];
        acc[0] = 0; acc[1] = 0;
        a128(acc, f0_2, f4); a128(acc, f1_2, f3); a128(acc, f2, f2);
        h4lo = acc[0]; h4hi = acc[1];

        return carryNormalize(h0lo, h0hi, h1lo, h1hi, h2lo, h2hi, h3lo, h3hi, h4lo, h4hi);
    }

    /**
     * Carry chain + final normalization for the five 128-bit column sums of {@code mulLimbs}/
     * {@code sqrLimbs}: take 51 bits per limb pushing the rest up (h4's carry wraps to l0 ×19,
     * since 2^255 ≡ 19), then two light passes settle every limb under 2^51, and a conditional
     * subtraction of p lands the value in [0, P) — the returned limbs are exactly the canonical
     * decomposition {@code toLimbs} would produce.
     *
     * <p>The second light pass is load-bearing: after the first one, the ×19 wrap can leave
     * {@code l0} just over 2^51 while a ripple of exactly-saturated limbs leaves {@code l1} odd,
     * and reassembling that with bitwise OR would silently drop a carry (an astronomically rare
     * but adversarially relevant corner). After the second pass a further wrap can only add 19 to
     * an {@code l0 ≤ 18}, so every limb is strictly below 2^51 by construction. Package-private
     * for the white-box corner test that pins this exact case.
     */
    static long[] carryNormalize(long h0lo, long h0hi, long h1lo, long h1hi, long h2lo, long h2hi,
            long h3lo, long h3hi, long h4lo, long h4hi) {
        long c;
        long l0 = h0lo & MASK_51; c = sh(h0hi, h0lo, 51);
        long[] s1 = addc(h1lo, h1hi, c); long l1 = s1[0] & MASK_51; c = sh(s1[1], s1[0], 51);
        long[] s2 = addc(h2lo, h2hi, c); long l2 = s2[0] & MASK_51; c = sh(s2[1], s2[0], 51);
        long[] s3 = addc(h3lo, h3hi, c); long l3 = s3[0] & MASK_51; c = sh(s3[1], s3[0], 51);
        long[] s4 = addc(h4lo, h4hi, c); long l4 = s4[0] & MASK_51; c = sh(s4[1], s4[0], 51);
        // wrap top carry into l0 (c < 2^58, so l0 < 2^63: no overflow)
        l0 += 19 * c;
        // first light pass: settle the big wrapped carry
        c = l0 >>> 51; l0 &= MASK_51; l1 += c;
        c = l1 >>> 51; l1 &= MASK_51; l2 += c;
        c = l2 >>> 51; l2 &= MASK_51; l3 += c;
        c = l3 >>> 51; l3 &= MASK_51; l4 += c;
        c = l4 >>> 51; l4 &= MASK_51; l0 += 19 * c; // c <= 1 -> l0 < 2^51 + 19
        // second light pass: settle that last +19 (if l4 wraps again, l0 <= 18 + 19 stays tiny)
        c = l0 >>> 51; l0 &= MASK_51; l1 += c;
        c = l1 >>> 51; l1 &= MASK_51; l2 += c;
        c = l2 >>> 51; l2 &= MASK_51; l3 += c;
        c = l3 >>> 51; l3 &= MASK_51; l4 += c;
        c = l4 >>> 51; l4 &= MASK_51; l0 += 19 * c;
        // conditional subtract p = 2^255 - 19: the value is < 2^255 = p + 19, so "value >= p" is
        // exactly "all high limbs saturated and l0 >= 2^51 - 19", and the difference is l0's excess.
        if (l4 == MASK_51 && l3 == MASK_51 && l2 == MASK_51 && l1 == MASK_51 && l0 >= MASK_51 - 18) {
            l0 -= MASK_51 - 18;
            l1 = 0; l2 = 0; l3 = 0; l4 = 0;
        }
        return new long[]{l0, l1, l2, l3, l4};
    }

    /**
     * Wraps the canonical limbs of a mul/sqr result: rebuilds the BigInteger (the canonical
     * storage) and seeds {@link #limbsCache} with them, so a chained multiply never re-decomposes
     * the value it has just assembled — curve formulas are exactly such chains.
     */
    private static Fe25519 fromCanonicalLimbs(long[] l) {
        long w0 = l[0] | (l[1] << 51);
        long w1 = (l[1] >>> 13) | (l[2] << 38);
        long w2 = (l[2] >>> 26) | (l[3] << 25);
        long w3 = (l[3] >>> 39) | (l[4] << 12);
        byte[] be = new byte[32];
        for (int i = 0; i < 8; i++) {
            be[31 - i] = (byte) (w0 >>> (8 * i));
            be[23 - i] = (byte) (w1 >>> (8 * i));
            be[15 - i] = (byte) (w2 >>> (8 * i));
            be[7 - i] = (byte) (w3 >>> (8 * i));
        }
        Fe25519 r = new Fe25519(new BigInteger(1, be), true);
        r.limbsCache = l;
        return r;
    }

    /** acc(unsigned 128 hi:lo in acc[1]:acc[0]) += a*b (a,b >= 0). */
    private static void a128(long[] acc, long a, long b) {
        long lo = a * b;
        long hi = Math.multiplyHigh(a, b);
        long t = acc[0] + lo;
        if (Long.compareUnsigned(t, lo) < 0) {
            hi++;
        }
        acc[0] = t;
        acc[1] += hi;
    }

    /** Right-shift an unsigned 128-bit value (hi:lo) by s in [1,63], result fits in a long here. */
    private static long sh(long hi, long lo, int s) {
        return (lo >>> s) | (hi << (64 - s));
    }

    /** (hi:lo) + c (c small, >=0) → new unsigned 128-bit as [lo, hi]. */
    private static long[] addc(long lo, long hi, long c) {
        long t = lo + c;
        if (Long.compareUnsigned(t, lo) < 0) {
            hi++;
        }
        return new long[]{t, hi};
    }

    public Fe25519 negate() {
        return v.signum() == 0 ? ZERO : new Fe25519(P.subtract(v), true);
    }

    public Fe25519 pow(BigInteger e) {
        return new Fe25519(v.modPow(e, P), true); // modPow result already in [0, P)
    }

    /** Multiplicative inverse via Fermat's little theorem (a^(p-2)). */
    public Fe25519 inv() {
        return new Fe25519(v.modPow(P_MINUS_2, P), true);
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
        Fe25519 uNegI = uNeg.mul(SQRT_M1_FE);

        boolean correctSignSqrt = check.ctEq(u);
        boolean flippedSignSqrt = check.ctEq(uNeg);
        boolean flippedSignSqrtI = check.ctEq(uNegI);

        Fe25519 rPrime = r.mul(SQRT_M1_FE);
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
