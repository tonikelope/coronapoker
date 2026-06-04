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
 * Ristretto255 encode/decode (RFC 9496 §4.3) over the edwards25519 point group.
 *
 * Ristretto255 is a prime-order group abstraction over edwards25519: canonical
 * 32-byte encodings, no cofactor, equal points encode equally. This replaces the
 * Montgomery x-only representation of the current SRA and removes the cofactor /
 * small-subgroup caveats entirely.
 *
 * Constants are taken from RFC 9496 §4.1 and self-validated in Ristretto255Test
 * (square relations), so a transcription error cannot slip through. Correctness-
 * first (Fe25519 / BigInteger backend); not constant-time.
 */
public final class Ristretto255 {

    /** d = -121665/121666 (RFC 9496 §4.1). */
    public static final Fe25519 D = EdwardsPoint.D;

    /** sqrt(-1) mod p. */
    public static final Fe25519 SQRT_M1 = Fe25519.of(Fe25519.SQRT_M1);

    /** 1/sqrt(a-d) with a=-1, i.e. 1/sqrt(-1-d). */
    public static final Fe25519 INVSQRT_A_MINUS_D = Fe25519.of(new BigInteger(
            "54469307008909316920995813868745141605393597292927456921205312896311721017578"));

    /** sqrt(a*d - 1) with a=-1, i.e. sqrt(-d-1) (used by Elligator hash-to-group). */
    public static final Fe25519 SQRT_AD_MINUS_ONE = Fe25519.of(new BigInteger(
            "25063068953384623474111414158702152701244531502492656460079210482610430750235"));

    /** 1 - d^2 (used by Elligator hash-to-group). */
    public static final Fe25519 ONE_MINUS_D_SQ = Fe25519.of(new BigInteger(
            "1159843021668779879193775521855586647937357759715417654439879720876111806838"));

    /** (d - 1)^2 (used by Elligator hash-to-group). */
    public static final Fe25519 D_MINUS_ONE_SQ = Fe25519.of(new BigInteger(
            "40440834346308536858101042469323190826248399146238708352240133220865137265952"));

    private Ristretto255() {
    }

    /**
     * Encodes a ristretto255 group element (given as an edwards25519 point) to its
     * canonical 32-byte representation (RFC 9496 §4.3.2).
     */
    public static byte[] encode(EdwardsPoint p) {
        byte[] cached = p.ristrettoCache();
        if (cached != null) {
            return cached.clone(); // fresh array per call, like the computed path
        }
        Fe25519 x0 = p.extX();
        Fe25519 y0 = p.extY();
        Fe25519 z0 = p.extZ();
        Fe25519 t0 = p.extT();

        Fe25519 u1 = z0.add(y0).mul(z0.sub(y0));
        Fe25519 u2 = x0.mul(y0);

        Fe25519 u1u2sq = u1.mul(u2.sqr());
        Fe25519 invsqrt = Fe25519.sqrtRatioM1(Fe25519.ONE, u1u2sq).r; // always square here

        Fe25519 den1 = invsqrt.mul(u1);
        Fe25519 den2 = invsqrt.mul(u2);
        Fe25519 zInv = den1.mul(den2).mul(t0);

        Fe25519 ix0 = x0.mul(SQRT_M1);
        Fe25519 iy0 = y0.mul(SQRT_M1);
        Fe25519 enchanted = den1.mul(INVSQRT_A_MINUS_D);

        boolean rotate = t0.mul(zInv).isNegative();

        Fe25519 x = rotate ? iy0 : x0;
        Fe25519 y = rotate ? ix0 : y0;
        Fe25519 z = z0;
        Fe25519 denInv = rotate ? enchanted : den2;

        if (x.mul(zInv).isNegative()) {
            y = y.negate();
        }

        Fe25519 s = denInv.mul(z.sub(y)).abs();
        byte[] enc = s.toBytes();
        p.ristrettoCache(enc.clone());
        return enc;
    }

    /**
     * Decodes a canonical 32-byte ristretto255 encoding to an edwards25519 point,
     * or returns null if the input is not a valid canonical encoding (RFC 9496
     * §4.3.1). This is the security gate that replaces arePointsOnCurve: it refuses
     * non-canonical field encodings, negative field elements, non-square inputs and
     * the s = -1 (y = 0) case.
     */
    public static EdwardsPoint decode(byte[] in) {
        Fe25519 s = Fe25519.fromBytesCanonical(in);
        if (s == null) {
            return null; // non-canonical field encoding
        }
        if (s.isNegative()) {
            return null; // negative field element
        }

        Fe25519 ss = s.sqr();
        Fe25519 u1 = Fe25519.ONE.sub(ss);     // 1 + a*ss, a = -1
        Fe25519 u2 = Fe25519.ONE.add(ss);     // 1 - a*ss
        Fe25519 u2sq = u2.sqr();

        Fe25519 v = D.negate().mul(u1.sqr()).sub(u2sq); // -(d*u1^2) - u2^2

        Fe25519.SqrtRatioResult sr = Fe25519.sqrtRatioM1(Fe25519.ONE, v.mul(u2sq));
        boolean wasSquare = sr.wasSquare;
        Fe25519 invsqrt = sr.r;

        Fe25519 denX = invsqrt.mul(u2);
        Fe25519 denY = invsqrt.mul(denX).mul(v);

        Fe25519 x = s.add(s).mul(denX).abs(); // CT_ABS(2 * s * den_x)
        Fe25519 y = u1.mul(denY);
        Fe25519 t = x.mul(y);

        if (!wasSquare || t.isNegative() || y.isZero()) {
            return null;
        }
        EdwardsPoint p = EdwardsPoint.fromAffine(x, y);
        // Canonical input accepted -> the round-trip is the identity (RFC 9496), so the input IS
        // this point's canonical encoding: seed the cache and the wire point re-encodes for free.
        p.ristrettoCache(in.clone());
        return p;
    }

    /**
     * Native Ristretto255 group-element equality of two edwards25519 representatives:
     * {@code X1·Y2 == Y1·X2} (invariant under the identity / {@code (0,-1)} coset translations) OR
     * {@code Y1·Y2 == X1·X2} (under the {@code (±i,0)} ones) — together, exactly "same E[4] coset",
     * i.e. the same relation as comparing canonical encodings, in four field multiplies instead of
     * two sqrt-ratio exponentiations. Validated against encode-equality by fuzz, torsion included
     * (RistrettoEqualityTest).
     */
    public static boolean equalPoints(EdwardsPoint a, EdwardsPoint b) {
        if (a.extX().mul(b.extY()).ctEq(a.extY().mul(b.extX()))) {
            return true;
        }
        return a.extY().mul(b.extY()).ctEq(a.extX().mul(b.extX()));
    }

    /**
     * True iff the point is the Ristretto255 identity element, i.e. lies in the E[4] coset of
     * {@code (0,1)}: exactly {@code X == 0} (the {@code (0,±1)} members) or {@code Y == 0} (the
     * {@code (±i,0)} ones). Two zero checks instead of a full encode.
     */
    public static boolean isIdentity(EdwardsPoint p) {
        return p.extX().isZero() || p.extY().isZero();
    }

    /**
     * The ristretto255 one-way MAP from a field element to a group element
     * (RFC 9496 §4.3.4). Used by {@link #hashToGroup} to build the genesis deck:
     * the resulting points have unknown mutual discrete logs (unlike s*B), which
     * is required so a locked deck cannot be de-permuted by matching card ratios.
     */
    static EdwardsPoint map(Fe25519 t) {
        Fe25519 r = SQRT_M1.mul(t.sqr());
        Fe25519 u = r.add(Fe25519.ONE).mul(ONE_MINUS_D_SQ);
        Fe25519 v = Fe25519.ONE.negate().sub(r.mul(D)).mul(r.add(D)); // (-1 - r*d)(r + d)

        Fe25519.SqrtRatioResult sr = Fe25519.sqrtRatioM1(u, v);
        boolean wasSquare = sr.wasSquare;
        Fe25519 s = sr.r;
        Fe25519 sPrime = s.mul(t).abs().negate(); // -CT_ABS(s*t)
        if (!wasSquare) {
            s = sPrime;
        }
        Fe25519 c = wasSquare ? Fe25519.ONE.negate() : r;

        Fe25519 n = c.mul(r.sub(Fe25519.ONE)).mul(D_MINUS_ONE_SQ).sub(v);
        Fe25519 w0 = s.add(s).mul(v);          // 2*s*v
        Fe25519 w1 = n.mul(SQRT_AD_MINUS_ONE);
        Fe25519 w2 = Fe25519.ONE.sub(s.sqr());
        Fe25519 w3 = Fe25519.ONE.add(s.sqr());

        return new EdwardsPoint(w0.mul(w3), w2.mul(w1), w1.mul(w3), w0.mul(w2));
    }

    /**
     * Maps 64 uniform bytes to a ristretto255 group element (RFC 9496): each
     * 32-byte half decodes to a field element (high bit ignored) and is mapped;
     * the two points are added. Feed it a wide hash (e.g. SHA-512) of a seed.
     */
    public static EdwardsPoint hashToGroup(byte[] uniform64) {
        if (uniform64 == null || uniform64.length != 64) {
            throw new IllegalArgumentException("hashToGroup requires 64 bytes");
        }
        Fe25519 t0 = Fe25519.fromBytes(Arrays.copyOfRange(uniform64, 0, 32));
        Fe25519 t1 = Fe25519.fromBytes(Arrays.copyOfRange(uniform64, 32, 64));
        return map(t0).add(map(t1));
    }

    /** Convenience: canonical 32-byte encoding of {@link #hashToGroup}. */
    public static byte[] hashToGroupEncoded(byte[] uniform64) {
        return encode(hashToGroup(uniform64));
    }
}
