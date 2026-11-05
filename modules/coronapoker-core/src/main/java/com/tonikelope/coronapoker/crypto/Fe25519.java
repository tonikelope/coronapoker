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
 * Field elements are stored as five canonical radix-2^51 limbs. The hot curve
 * operations therefore stay entirely in primitive arithmetic; BigInteger is
 * used only at API boundaries and for comparatively rare generic
 * exponentiation operations. The limb kernel is differentially
 * tested against BigInteger and the RFC 9496 vectors.
 *
 * Instances are immutable. Not constant-time — acceptable for this threat model
 * (the cascade is not real-time and no peer times another's field ops over the
 * wire); a constant-time backend can replace this without changing the API.
 */
public final class Fe25519 {

    private static final long MASK_51 = (1L << 51) - 1;

    /**
     * Field prime p = 2^255 - 19.
     */
    public static final BigInteger P
            = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19));

    /**
     * sqrt(-1) mod p, per RFC 9496. Self-validated in Fe25519Test (SQRT_M1^2 ==
     * -1 mod p), so a transcription error cannot slip through.
     */
    public static final BigInteger SQRT_M1 = new BigInteger(
            "19681161376707505956807079304988542015446066515923890162744021073123829784752");

    public static final Fe25519 ZERO = new Fe25519(0, 0, 0, 0, 0, BigInteger.ZERO);
    public static final Fe25519 ONE = new Fe25519(1, 0, 0, 0, 0, BigInteger.ONE);

    /**
     * sqrt(-1) as a Fe25519, cached ONCE. {@link #sqrtRatioM1} used to call
     * {@code of(SQRT_M1)} twice per invocation, and it runs on every
     * {@code encode}/{@code decode} (~104 times per deck lock): each
     * {@code of(SQRT_M1)} did a {@code new Fe25519} + {@code BigInteger.mod}
     * and decomposed the value again. The shared constant now remains in the
     * primitive limb representation used directly by every multiply. Same
     * value, no change to the reduction.
     */
    private static final Fe25519 SQRT_M1_FE = of(SQRT_M1);

    /** Canonical radix-2^51 representative in [0, P). */
    private final long l0;
    private final long l1;
    private final long l2;
    private final long l3;
    private final long l4;

    /** Lazily reconstructed only for API boundaries and generic modPow. */
    private volatile BigInteger bigIntegerCache;

    /** Reused per thread by the 128-bit multiply accumulator. */
    private static final ThreadLocal<long[]> ACCUMULATOR
            = ThreadLocal.withInitial(() -> new long[2]);

    private Fe25519(BigInteger value) {
        BigInteger reduced = value.mod(P); // BigInteger.mod is always non-negative
        long[] limbs = toLimbs(reduced);
        this.l0 = limbs[0];
        this.l1 = limbs[1];
        this.l2 = limbs[2];
        this.l3 = limbs[3];
        this.l4 = limbs[4];
        this.bigIntegerCache = reduced;
    }

    private Fe25519(long l0, long l1, long l2, long l3, long l4) {
        this(l0, l1, l2, l3, l4, null);
    }

    private Fe25519(long l0, long l1, long l2, long l3, long l4, BigInteger cachedValue) {
        this.l0 = l0;
        this.l1 = l1;
        this.l2 = l2;
        this.l3 = l3;
        this.l4 = l4;
        this.bigIntegerCache = cachedValue;
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
     * Decodes a field element from 32 little-endian bytes, REJECTING
     * non-canonical encodings: the full 256-bit little-endian value (bit 255
     * included, NOT masked) must be strictly less than p. Returns null on
     * non-canonical input. Used by Ristretto255 decode, where non-canonical
     * field encodings must be refused.
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

    /**
     * Encodes to 32 canonical little-endian bytes (value in [0, P)).
     */
    public byte[] toBytes() {
        byte[] le = new byte[32];
        long w0 = l0 | (l1 << 51);
        long w1 = (l1 >>> 13) | (l2 << 38);
        long w2 = (l2 >>> 26) | (l3 << 25);
        long w3 = (l3 >>> 39) | (l4 << 12);
        putLe64(le, 0, w0);
        putLe64(le, 8, w1);
        putLe64(le, 16, w2);
        putLe64(le, 24, w3);
        return le;
    }

    public Fe25519 add(Fe25519 o) {
        return carryNormalizeFe(l0 + o.l0, 0, l1 + o.l1, 0, l2 + o.l2, 0,
                l3 + o.l3, 0, l4 + o.l4, 0);
    }

    public Fe25519 sub(Fe25519 o) {
        // Add 2p first so every limb remains non-negative before reduction.
        return carryNormalizeFe(l0 + 2 * (MASK_51 - 18) - o.l0, 0,
                l1 + 2 * MASK_51 - o.l1, 0,
                l2 + 2 * MASK_51 - o.l2, 0,
                l3 + 2 * MASK_51 - o.l3, 0,
                l4 + 2 * MASK_51 - o.l4, 0);
    }

    public Fe25519 mul(Fe25519 o) {
        return mulLimbs(this, o);
    }

    public Fe25519 sqr() {
        return sqrLimbs(this);
    }

    // ---- Fast field multiply: radix 2^51 limbs (5 × 64-bit), 128-bit accumulation via
    // Math.multiplyHigh, reduction mod p=2^255-19 (2^255 ≡ 19). The canonical limbs remain the
    // instance storage across chained operations. Fe25519FastReduceTest fuzz (200k operations vs
    // BigInteger) plus the RFC 9496 vectors gate correctness.
    /**
     * v in [0, 2^255) → 5 radix-2^51 limbs (each < 2^51).
     */
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

    private static void putLe64(byte[] b, int off, long value) {
        for (int i = 0; i < 8; i++) {
            b[off + i] = (byte) (value >>> (8 * i));
        }
    }

    /**
     * Multiply two limb arrays mod p, returning CANONICAL limbs (each < 2^51,
     * value in [0, P)).
     */
    private static Fe25519 mulLimbs(Fe25519 f, Fe25519 g) {
        long f0 = f.l0, f1 = f.l1, f2 = f.l2, f3 = f.l3, f4 = f.l4;
        long g0 = g.l0, g1 = g.l1, g2 = g.l2, g3 = g.l3, g4 = g.l4;
        long g1_19 = 19 * g1, g2_19 = 19 * g2, g3_19 = 19 * g3, g4_19 = 19 * g4;

        // Each h_i accumulated as an unsigned 128-bit value (hi:lo).
        long[] acc = ACCUMULATOR.get();
        long h0lo, h0hi, h1lo, h1hi, h2lo, h2hi, h3lo, h3hi, h4lo, h4hi;

        acc[0] = 0;
        acc[1] = 0;
        a128(acc, f0, g0);
        a128(acc, f1, g4_19);
        a128(acc, f2, g3_19);
        a128(acc, f3, g2_19);
        a128(acc, f4, g1_19);
        h0lo = acc[0];
        h0hi = acc[1];
        acc[0] = 0;
        acc[1] = 0;
        a128(acc, f0, g1);
        a128(acc, f1, g0);
        a128(acc, f2, g4_19);
        a128(acc, f3, g3_19);
        a128(acc, f4, g2_19);
        h1lo = acc[0];
        h1hi = acc[1];
        acc[0] = 0;
        acc[1] = 0;
        a128(acc, f0, g2);
        a128(acc, f1, g1);
        a128(acc, f2, g0);
        a128(acc, f3, g4_19);
        a128(acc, f4, g3_19);
        h2lo = acc[0];
        h2hi = acc[1];
        acc[0] = 0;
        acc[1] = 0;
        a128(acc, f0, g3);
        a128(acc, f1, g2);
        a128(acc, f2, g1);
        a128(acc, f3, g0);
        a128(acc, f4, g4_19);
        h3lo = acc[0];
        h3hi = acc[1];
        acc[0] = 0;
        acc[1] = 0;
        a128(acc, f0, g4);
        a128(acc, f1, g3);
        a128(acc, f2, g2);
        a128(acc, f3, g1);
        a128(acc, f4, g0);
        h4lo = acc[0];
        h4hi = acc[1];

        return carryNormalizeFe(h0lo, h0hi, h1lo, h1hi, h2lo, h2hi, h3lo, h3hi, h4lo, h4hi);
    }

    /**
     * Dedicated squaring: the symmetric cross terms
     * ({@code f_i·f_j == f_j·f_i}) collapse the 25 64×64 multiplies of the
     * generic product to 15, folding the ×2 into one pre-doubled operand
     * ({@code 38·f_i·f_j = (2f_i)·(19f_j)}). The doubling famously dominates
     * the scalar ladders (4 squarings per point doubling), so this is the
     * hottest field path of all.
     */
    private static Fe25519 sqrLimbs(Fe25519 f) {
        long f0 = f.l0, f1 = f.l1, f2 = f.l2, f3 = f.l3, f4 = f.l4;
        long f0_2 = 2 * f0, f1_2 = 2 * f1, f2_2 = 2 * f2, f3_2 = 2 * f3;
        long f3_19 = 19 * f3, f4_19 = 19 * f4;

        // Same operand magnitudes as mulLimbs (2f < 2^52, 19f < 2^56), so the unsigned 128-bit
        // accumulation bounds are unchanged.
        long[] acc = ACCUMULATOR.get();
        long h0lo, h0hi, h1lo, h1hi, h2lo, h2hi, h3lo, h3hi, h4lo, h4hi;

        acc[0] = 0;
        acc[1] = 0;
        a128(acc, f0, f0);
        a128(acc, f1_2, f4_19);
        a128(acc, f2_2, f3_19);
        h0lo = acc[0];
        h0hi = acc[1];
        acc[0] = 0;
        acc[1] = 0;
        a128(acc, f0_2, f1);
        a128(acc, f2_2, f4_19);
        a128(acc, f3, f3_19);
        h1lo = acc[0];
        h1hi = acc[1];
        acc[0] = 0;
        acc[1] = 0;
        a128(acc, f0_2, f2);
        a128(acc, f1, f1);
        a128(acc, f3_2, f4_19);
        h2lo = acc[0];
        h2hi = acc[1];
        acc[0] = 0;
        acc[1] = 0;
        a128(acc, f0_2, f3);
        a128(acc, f1_2, f2);
        a128(acc, f4, f4_19);
        h3lo = acc[0];
        h3hi = acc[1];
        acc[0] = 0;
        acc[1] = 0;
        a128(acc, f0_2, f4);
        a128(acc, f1_2, f3);
        a128(acc, f2, f2);
        h4lo = acc[0];
        h4hi = acc[1];

        return carryNormalizeFe(h0lo, h0hi, h1lo, h1hi, h2lo, h2hi, h3lo, h3hi, h4lo, h4hi);
    }

    /**
     * Carry chain + final normalization for the five 128-bit column sums of
     * {@code mulLimbs}/ {@code sqrLimbs}: take 51 bits per limb pushing the
     * rest up (h4's carry wraps to l0 ×19, since 2^255 ≡ 19), then two light
     * passes settle every limb under 2^51, and a conditional subtraction of p
     * lands the value in [0, P) — the returned limbs are exactly the canonical
     * decomposition {@code toLimbs} would produce.
     *
     * <p>
     * The second light pass is load-bearing: after the first one, the ×19 wrap
     * can leave {@code l0} just over 2^51 while a ripple of exactly-saturated
     * limbs leaves {@code l1} odd, and reassembling that with bitwise OR would
     * silently drop a carry (an astronomically rare but adversarially relevant
     * corner). After the second pass a further wrap can only add 19 to an
     * {@code l0 ≤ 18}, so every limb is strictly below 2^51 by construction.
     * Package-private for the white-box corner test that pins this exact case.
     */
    private static Fe25519 carryNormalizeFe(long h0lo, long h0hi, long h1lo, long h1hi,
            long h2lo, long h2hi, long h3lo, long h3hi, long h4lo, long h4hi) {
        long c;
        long l0 = h0lo & MASK_51;
        c = sh(h0hi, h0lo, 51);
        long t = h1lo + c;
        if (Long.compareUnsigned(t, h1lo) < 0) {
            h1hi++;
        }
        long l1 = t & MASK_51;
        c = sh(h1hi, t, 51);
        t = h2lo + c;
        if (Long.compareUnsigned(t, h2lo) < 0) {
            h2hi++;
        }
        long l2 = t & MASK_51;
        c = sh(h2hi, t, 51);
        t = h3lo + c;
        if (Long.compareUnsigned(t, h3lo) < 0) {
            h3hi++;
        }
        long l3 = t & MASK_51;
        c = sh(h3hi, t, 51);
        t = h4lo + c;
        if (Long.compareUnsigned(t, h4lo) < 0) {
            h4hi++;
        }
        long l4 = t & MASK_51;
        c = sh(h4hi, t, 51);
        // wrap top carry into l0 (c < 2^58, so l0 < 2^63: no overflow)
        l0 += 19 * c;
        // first light pass: settle the big wrapped carry
        c = l0 >>> 51;
        l0 &= MASK_51;
        l1 += c;
        c = l1 >>> 51;
        l1 &= MASK_51;
        l2 += c;
        c = l2 >>> 51;
        l2 &= MASK_51;
        l3 += c;
        c = l3 >>> 51;
        l3 &= MASK_51;
        l4 += c;
        c = l4 >>> 51;
        l4 &= MASK_51;
        l0 += 19 * c; // c <= 1 -> l0 < 2^51 + 19
        // second light pass: settle that last +19 (if l4 wraps again, l0 <= 18 + 19 stays tiny)
        c = l0 >>> 51;
        l0 &= MASK_51;
        l1 += c;
        c = l1 >>> 51;
        l1 &= MASK_51;
        l2 += c;
        c = l2 >>> 51;
        l2 &= MASK_51;
        l3 += c;
        c = l3 >>> 51;
        l3 &= MASK_51;
        l4 += c;
        c = l4 >>> 51;
        l4 &= MASK_51;
        l0 += 19 * c;
        // conditional subtract p = 2^255 - 19: the value is < 2^255 = p + 19, so "value >= p" is
        // exactly "all high limbs saturated and l0 >= 2^51 - 19", and the difference is l0's excess.
        if (l4 == MASK_51 && l3 == MASK_51 && l2 == MASK_51 && l1 == MASK_51 && l0 >= MASK_51 - 18) {
            l0 -= MASK_51 - 18;
            l1 = 0;
            l2 = 0;
            l3 = 0;
            l4 = 0;
        }
        return new Fe25519(l0, l1, l2, l3, l4);
    }

    /**
     * White-box hook for the adversarial carry test. Production arithmetic
     * consumes the allocation-free {@link #carryNormalizeFe} result directly.
     */
    static long[] carryNormalize(long h0lo, long h0hi, long h1lo, long h1hi, long h2lo, long h2hi,
            long h3lo, long h3hi, long h4lo, long h4hi) {
        Fe25519 r = carryNormalizeFe(h0lo, h0hi, h1lo, h1hi, h2lo, h2hi,
                h3lo, h3hi, h4lo, h4hi);
        return new long[]{r.l0, r.l1, r.l2, r.l3, r.l4};
    }

    /**
     * acc(unsigned 128 hi:lo in acc[1]:acc[0]) += a*b (a,b >= 0).
     */
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

    /**
     * Right-shift an unsigned 128-bit value (hi:lo) by s in [1,63], result fits
     * in a long here.
     */
    private static long sh(long hi, long lo, int s) {
        return (lo >>> s) | (hi << (64 - s));
    }

    public Fe25519 negate() {
        return isZero() ? ZERO : ZERO.sub(this);
    }

    public Fe25519 pow(BigInteger e) {
        return new Fe25519(toBigInteger().modPow(e, P));
    }

    /**
     * {@code this^((p-5)/8) == this^(2^252 - 3)} via the standard ref10
     * pow22523 addition chain, run entirely in the radix-2^51 kernel
     * ({@code sqr}/{@code mul}) instead of {@link #pow} /
     * {@code BigInteger.modPow}. This is the exponent inside
     * {@link #sqrtRatioM1}, hit on every Ristretto encode and decode.
     * Byte-identical to the generic {@code pow((p-5)/8)} /
     * {@code BigInteger.modPow} — pinned by {@code Fe25519Pow22523DiffTest}
     * (100k differential fuzz vs modPow). Package-private for that test.
     */
    Fe25519 pow22523() {
        final Fe25519 z = this;
        Fe25519 t0 = z.sqr();               // z^2
        Fe25519 t1 = t0.sqr().sqr();         // z^8
        t1 = z.mul(t1);                      // z^9
        t0 = t0.mul(t1);                     // z^11
        t0 = t0.sqr();                       // z^22
        t0 = t1.mul(t0);                     // z^(2^5 - 1)
        t1 = t0.sqr();
        for (int i = 1; i < 5; i++) {
            t1 = t1.sqr();
        }
        t0 = t1.mul(t0);                     // z^(2^10 - 1)
        t1 = t0.sqr();
        for (int i = 1; i < 10; i++) {
            t1 = t1.sqr();
        }
        t1 = t1.mul(t0);                     // z^(2^20 - 1)
        Fe25519 t2 = t1.sqr();
        for (int i = 1; i < 20; i++) {
            t2 = t2.sqr();
        }
        t1 = t2.mul(t1);                     // z^(2^40 - 1)
        t1 = t1.sqr();
        for (int i = 1; i < 10; i++) {
            t1 = t1.sqr();
        }
        t0 = t1.mul(t0);                     // z^(2^50 - 1)
        t1 = t0.sqr();
        for (int i = 1; i < 50; i++) {
            t1 = t1.sqr();
        }
        t1 = t1.mul(t0);                     // z^(2^100 - 1)
        t2 = t1.sqr();
        for (int i = 1; i < 100; i++) {
            t2 = t2.sqr();
        }
        t1 = t2.mul(t1);                     // z^(2^200 - 1)
        t1 = t1.sqr();
        for (int i = 1; i < 50; i++) {
            t1 = t1.sqr();
        }
        t0 = t1.mul(t0);                     // z^(2^250 - 1)
        t0 = t0.sqr().sqr();                 // z^(2^252 - 4)
        return t0.mul(z);                    // z^(2^252 - 3)
    }

    /**
     * Multiplicative inverse via Fermat's little theorem (a^(p-2)).
     */
    public Fe25519 inv() {
        if (isZero()) return ZERO;
        // pow22523() = x^(2^252 - 3). Therefore
        // (pow22523(x))^8 * x^3 = x^(2^255 - 21) = x^(p - 2).
        // This reuses the audited fixed addition chain and keeps the complete
        // inverse in the primitive 5x51 backend.
        Fe25519 t = pow22523().sqr().sqr().sqr();
        return t.mul(sqr().mul(this));
    }

    public boolean isZero() {
        return (l0 | l1 | l2 | l3 | l4) == 0;
    }

    /**
     * Ristretto IS_NEGATIVE: the least-significant bit of the canonical
     * encoding.
     */
    public boolean isNegative() {
        return (l0 & 1) != 0;
    }

    /**
     * CT_ABS: -x if x is negative, else x.
     */
    public Fe25519 abs() {
        return isNegative() ? negate() : this;
    }

    /**
     * Constant-value equality (not timing-relevant here).
     */
    public boolean ctEq(Fe25519 o) {
        return (l0 ^ o.l0) == 0 && (l1 ^ o.l1) == 0 && (l2 ^ o.l2) == 0
                && (l3 ^ o.l3) == 0 && (l4 ^ o.l4) == 0;
    }

    public BigInteger toBigInteger() {
        BigInteger value = bigIntegerCache;
        if (value == null) {
            byte[] le = toBytes();
            byte[] be = new byte[32];
            for (int i = 0; i < 32; i++) {
                be[i] = le[31 - i];
            }
            value = new BigInteger(1, be);
            bigIntegerCache = value;
        }
        return value;
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof Fe25519) && ctEq((Fe25519) o);
    }

    @Override
    public int hashCode() {
        return toBigInteger().hashCode();
    }

    @Override
    public String toString() {
        return "Fe25519(" + toBigInteger().toString(16) + ")";
    }

    /**
     * RFC 9496 §4.3 sqrt_ratio_i(u, v).
     *
     * Computes r = sqrt(u/v) when u/v is a square (wasSquare = true), or sqrt(i
     * * u/v) otherwise (wasSquare = false), where i = sqrt(-1). The returned r
     * is always non-negative (CT_ABS). Invariant: wasSquare -> r^2 * v == u
     * !wasSquare -> r^2 * v == SQRT_M1 * u
     *
     * @return {wasSquare, r}
     */
    public static SqrtRatioResult sqrtRatioM1(Fe25519 u, Fe25519 v) {
        Fe25519 v3 = v.sqr().mul(v);          // v^3
        Fe25519 v7 = v3.sqr().mul(v);         // v^7
        Fe25519 r = u.mul(v3).mul(u.mul(v7).pow22523());
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

    /**
     * Result of {@link #sqrtRatioM1}.
     */
    public static final class SqrtRatioResult {

        public final boolean wasSquare;
        public final Fe25519 r;

        SqrtRatioResult(boolean wasSquare, Fe25519 r) {
            this.wasSquare = wasSquare;
            this.r = r;
        }
    }
}
