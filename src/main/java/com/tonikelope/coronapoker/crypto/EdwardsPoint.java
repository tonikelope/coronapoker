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
 * Point on edwards25519, the twisted Edwards curve -x^2 + y^2 = 1 + d*x^2*y^2
 * (a = -1) birationally equivalent to Curve25519, underlying Ristretto255.
 *
 * Phase 1 of the verifiable-dealing rework (docs/sra-verifiable-dealing-design.md).
 * Extended homogeneous coordinates (X:Y:Z:T) with x = X/Z, y = Y/Z, x*y = T/Z.
 * Group law via the unified Hisil-Wong-Carter-Dawson formulas (add-2008-hwcd-3 /
 * dbl-2008-hwcd), correct for all inputs including the identity.
 *
 * Correctness-first (Fe25519 / BigInteger backend); not constant-time. Validated
 * against algebraic properties — group order L, scalar commutativity — in
 * EdwardsPointTest. Ristretto encode/decode is layered on top separately.
 *
 * Instances are immutable.
 */
public final class EdwardsPoint {

    /** Curve constant d = -121665/121666 mod p. */
    public static final Fe25519 D =
            Fe25519.of(-121665).mul(Fe25519.of(121666).inv());

    /** k = 2*d, used by the addition formula. */
    private static final Fe25519 D2 = D.add(D);

    /** Order of the prime-order subgroup: L = 2^252 + 27742317777372353535851937790883648493. */
    public static final BigInteger L = new BigInteger(
            "7237005577332262213973186563042994240857116359379907606001950938285454250989");

    /** Neutral element (0, 1) -> (0:1:1:0). */
    public static final EdwardsPoint IDENTITY =
            new EdwardsPoint(Fe25519.ZERO, Fe25519.ONE, Fe25519.ONE, Fe25519.ZERO);

    /** Standard ed25519 base point B (y = 4/5, x the positive root). */
    public static final EdwardsPoint BASE = fromAffine(
            Fe25519.of(new BigInteger(
                "15112221349535400772501151409588531511454012693041857206046113283949847762202")),
            Fe25519.of(new BigInteger(
                "46316835694926478169428394003475163141307993866256225615783033603165251855960")));

    private final Fe25519 X;
    private final Fe25519 Y;
    private final Fe25519 Z;
    private final Fe25519 T;

    /**
     * Lazily-memoized 4-bit window table ({@code 0..15 · this}) used by {@link #scalarMul}. The instance
     * is immutable, so for a point scalar-multiplied many times — above all the constant {@link #BASE},
     * the {@code k·B} commitments and DLEQ bases — the 15-addition table is built once and reused.
     * {@code volatile} for safe publication across the background SRA verifier threads.
     */
    private volatile EdwardsPoint[] windowTable;

    /**
     * Lazily-memoized canonical Ristretto255 encoding of this point (see {@link Ristretto255#encode}).
     * Same pure-cache pattern as {@link #windowTable}: the instance is immutable, so its canonical
     * encoding is a constant — computed at most once, and seeded for free by
     * {@link Ristretto255#decode} (RFC 9496 guarantees the round-trip), so wire points re-encode
     * without paying the sqrt-ratio exponentiation. Holds a private copy; {@code encode} hands out
     * clones. {@code volatile} for safe publication across the background SRA verifier threads.
     */
    private volatile byte[] ristrettoCache;

    byte[] ristrettoCache() {
        return ristrettoCache;
    }

    void ristrettoCache(byte[] enc) {
        this.ristrettoCache = enc;
    }

    EdwardsPoint(Fe25519 x, Fe25519 y, Fe25519 z, Fe25519 t) {
        this.X = x;
        this.Y = y;
        this.Z = z;
        this.T = t;
    }

    /** Builds an extended point from affine (x, y): (x : y : 1 : x*y). */
    public static EdwardsPoint fromAffine(Fe25519 x, Fe25519 y) {
        return new EdwardsPoint(x, y, Fe25519.ONE, x.mul(y));
    }

    // Package-private extended-coordinate accessors for Ristretto255 encode/decode.
    Fe25519 extX() { return X; }
    Fe25519 extY() { return Y; }
    Fe25519 extZ() { return Z; }
    Fe25519 extT() { return T; }

    public Fe25519 affineX() {
        return X.mul(Z.inv());
    }

    public Fe25519 affineY() {
        return Y.mul(Z.inv());
    }

    /** add-2008-hwcd-3 (unified, a = -1). */
    public EdwardsPoint add(EdwardsPoint q) {
        Fe25519 a = Y.sub(X).mul(q.Y.sub(q.X));
        Fe25519 b = Y.add(X).mul(q.Y.add(q.X));
        Fe25519 c = T.mul(D2).mul(q.T);
        Fe25519 d = Z.mul(q.Z);
        d = d.add(d); // 2*Z1*Z2
        Fe25519 e = b.sub(a);
        Fe25519 f = d.sub(c);
        Fe25519 g = d.add(c);
        Fe25519 h = b.add(a);
        return new EdwardsPoint(e.mul(f), g.mul(h), f.mul(g), e.mul(h));
    }

    /** dbl-2008-hwcd (a = -1). */
    public EdwardsPoint dbl() {
        Fe25519 a = X.sqr();
        Fe25519 b = Y.sqr();
        Fe25519 c = Z.sqr();
        c = c.add(c); // 2*Z1^2
        Fe25519 d = a.negate(); // a * X^2, a = -1
        Fe25519 xy = X.add(Y);
        Fe25519 e = xy.sqr().sub(a).sub(b);
        Fe25519 g = d.add(b);
        Fe25519 f = g.sub(c);
        Fe25519 h = d.sub(b);
        return new EdwardsPoint(e.mul(f), g.mul(h), f.mul(g), e.mul(h));
    }

    public EdwardsPoint negate() {
        return new EdwardsPoint(X.negate(), Y, Z, T.negate());
    }

    /** Scalar multiplication s*P via double-and-add (MSB to LSB). s must be >= 0. */
    public EdwardsPoint scalarMul(BigInteger s) {
        if (s.signum() < 0) {
            throw new IllegalArgumentException("scalar must be non-negative");
        }
        // Fixed 4-bit window: precompute 0..15 multiples once, then process 4 scalar bits per step
        // (4 doublings + 1 addition) instead of one bit at a time. Same result; ~40% fewer additions.
        // The table depends only on `this`, so it is memoized (windowTable) — a point reused across many
        // scalarMuls (the constant BASE above all) builds its 15-addition table once, not per call.
        EdwardsPoint[] table = windowTable();
        int bits = s.bitLength();
        EdwardsPoint result = IDENTITY;
        for (int i = ((bits + 3) / 4) * 4 - 4; i >= 0; i -= 4) {
            result = result.dbl().dbl().dbl().dbl();
            int window = (s.testBit(i + 3) ? 8 : 0) | (s.testBit(i + 2) ? 4 : 0)
                    | (s.testBit(i + 1) ? 2 : 0) | (s.testBit(i) ? 1 : 0);
            if (window != 0) {
                result = result.add(table[window]);
            }
        }
        return result;
    }

    /** Memoized 4-bit window table {@code [0..15]·this} for {@link #scalarMul} (see {@link #windowTable}). */
    private EdwardsPoint[] windowTable() {
        EdwardsPoint[] t = windowTable;
        if (t == null) {
            t = new EdwardsPoint[16];
            t[0] = IDENTITY;
            for (int w = 1; w < 16; w++) {
                t[w] = t[w - 1].add(this);
            }
            windowTable = t;
        }
        return t;
    }

    /**
     * Multi-scalar multiplication {@code Σ scalars[i]·points[i]} via Straus' method: ONE shared
     * doubling ladder over all points (4-bit windows), instead of one independent {@link #scalarMul}
     * per point. For {@code n} points this does ~256 doublings total (shared) instead of ~256·n, the
     * dominant cost of every proof's {@code Σ wᵢ·Pᵢ}. Result is bit-identical to the naive sum
     * (validated by fuzzing in MultiScalarMulTest). Scalars are reduced mod L; not constant-time.
     */
    public static EdwardsPoint multiscalarMul(BigInteger[] scalars, EdwardsPoint[] points) {
        if (scalars == null || points == null || scalars.length != points.length) {
            throw new IllegalArgumentException("scalars/points length mismatch");
        }
        int n = points.length;
        if (n == 0) {
            return IDENTITY;
        }
        BigInteger[] s = new BigInteger[n];
        int maxBits = 0;
        for (int i = 0; i < n; i++) {
            s[i] = scalars[i].mod(L);
            int b = s[i].bitLength();
            if (b > maxBits) {
                maxBits = b;
            }
        }
        if (maxBits == 0) {
            return IDENTITY; // all scalars zero
        }
        // Per-point 4-bit window tables: table[i][w] = w·points[i], w = 0..15. Reuses each point's
        // memoized table (see windowTable): the fixed generators (H, G_0, BASE) and any deck point
        // appearing in several multi-scalar calls build their 15-addition table once per lifetime
        // instead of once per call.
        EdwardsPoint[][] table = new EdwardsPoint[n][];
        for (int i = 0; i < n; i++) {
            table[i] = points[i].windowTable();
        }
        int top = ((maxBits + 3) / 4) * 4 - 4;
        EdwardsPoint result = IDENTITY;
        for (int pos = top; pos >= 0; pos -= 4) {
            result = result.dbl().dbl().dbl().dbl(); // shared across all points
            for (int i = 0; i < n; i++) {
                BigInteger si = s[i];
                int window = (si.testBit(pos + 3) ? 8 : 0) | (si.testBit(pos + 2) ? 4 : 0)
                        | (si.testBit(pos + 1) ? 2 : 0) | (si.testBit(pos) ? 1 : 0);
                if (window != 0) {
                    result = result.add(table[i][window]);
                }
            }
        }
        return result;
    }

    /** Projective equality: X1*Z2 == X2*Z1 and Y1*Z2 == Y2*Z1. */
    public boolean equalsPoint(EdwardsPoint q) {
        return X.mul(q.Z).ctEq(q.X.mul(Z)) && Y.mul(q.Z).ctEq(q.Y.mul(Z));
    }

    /** Checks the curve equation -x^2 + y^2 = 1 + d*x^2*y^2 in affine form. */
    public boolean isOnCurve() {
        Fe25519 x = affineX();
        Fe25519 y = affineY();
        Fe25519 lhs = x.sqr().negate().add(y.sqr());
        Fe25519 rhs = Fe25519.ONE.add(D.mul(x.sqr()).mul(y.sqr()));
        return lhs.ctEq(rhs);
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof EdwardsPoint) && equalsPoint((EdwardsPoint) o);
    }

    @Override
    public int hashCode() {
        // Hash on the affine normal form so equal points hash equally.
        return affineX().hashCode() * 31 + affineY().hashCode();
    }
}
