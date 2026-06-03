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
 * Multiplication proof (Σ-protocol): given Pedersen commitments {@code C_a}, {@code C_b}, {@code C_c}
 * (single-value, {@code Comm([v], r) = r·H + v·G_0}), proves in zero knowledge that {@code c = a·b}
 * mod L — without revealing {@code a}, {@code b}, {@code c} or their blindings. This is the atom of
 * the product argument (a grand-product chains one of these per multiplication step), which in turn
 * powers the permutation/shuffle argument (see {@code docs/sra-bayer-groth-shuffle.md}).
 *
 * <p>Protocol. Prover masks {@code x1..x5}; sends {@code M1 = x1·G_0 + x2·H}, {@code M2 = x3·G_0 + x4·H},
 * {@code M3 = x1·C_b + x5·H}. Challenge {@code e = H(C_a, C_b, C_c, M1, M2, M3)}. Responses
 * {@code z1 = x1 + e·a}, {@code z2 = x2 + e·r_a}, {@code z3 = x3 + e·b}, {@code z4 = x4 + e·r_b},
 * {@code z5 = x5 + e·(r_c − a·r_b)}. Verifier accepts iff
 * <pre>
 *   (1) z1·G_0 + z2·H == M1 ⊕ e·C_a          (knowledge of a, r_a)
 *   (2) z3·G_0 + z4·H == M2 ⊕ e·C_b          (knowledge of b, r_b)
 *   (3) z1·C_b + z5·H == M3 ⊕ e·C_c          (the product gate)
 * </pre>
 * Check (3) expands to {@code M3 + e·(a·b·G_0 + r_c·H)}, which equals {@code M3 ⊕ e·C_c} iff
 * {@code a·b == c}. Complete, special-sound, honest-verifier ZK.
 *
 * <p>All point arithmetic is expressed through {@link PedersenVectorCommit} on length-1 vectors:
 * {@code v·G_0 + r·H = commit([v], r)}, {@code r·H = commit([0], r)}, {@code x1·C_b = scale(C_b, x1)}.
 */
public final class MultiplicationProof {

    private static final String FS_DOMAIN = "SRA/MultiplicationProof/v1";
    private static final BigInteger L = EdwardsPoint.L;

    private MultiplicationProof() {
    }

    public static final class Proof {
        final byte[] m1;
        final byte[] m2;
        final byte[] m3;
        final BigInteger z1;
        final BigInteger z2;
        final BigInteger z3;
        final BigInteger z4;
        final BigInteger z5;

        Proof(byte[] m1, byte[] m2, byte[] m3,
                BigInteger z1, BigInteger z2, BigInteger z3, BigInteger z4, BigInteger z5) {
            this.m1 = m1;
            this.m2 = m2;
            this.m3 = m3;
            this.z1 = z1;
            this.z2 = z2;
            this.z3 = z3;
            this.z4 = z4;
            this.z5 = z5;
        }
    }

    /** Single-value commitment {@code Comm([v], r) = r·H + v·G_0}. */
    public static byte[] commitScalar(BigInteger v, BigInteger r) {
        return PedersenVectorCommit.commit(new BigInteger[]{v}, r);
    }

    private static byte[] scalarG0PlusRH(BigInteger v, BigInteger r) {
        return commitScalar(v, r);
    }

    private static BigInteger challenge(byte[] ca, byte[] cb, byte[] cc, byte[] m1, byte[] m2, byte[] m3) {
        Transcript tr = new Transcript(FS_DOMAIN);
        tr.absorb("Ca", ca);
        tr.absorb("Cb", cb);
        tr.absorb("Cc", cc);
        tr.absorb("M1", m1);
        tr.absorb("M2", m2);
        tr.absorb("M3", m3);
        return tr.challengeScalar("e");
    }

    /**
     * Prove {@code c = a·b} for {@code C_a = Comm([a], ra)}, {@code C_b = Comm([b], rb)},
     * {@code C_c = Comm([c], rc)}. The caller supplies the openings and the (already published) {@code C_b}.
     * An honest prover passes {@code c == a·b mod L}; the resulting proof verifies only in that case
     * (a cheating prover that passes {@code c != a·b} produces a proof the verifier rejects — check (3)).
     */
    public static Proof prove(BigInteger a, BigInteger ra, BigInteger b, BigInteger rb, BigInteger c, BigInteger rc, byte[] cb) {
        BigInteger x1 = scalar();
        BigInteger x2 = scalar();
        BigInteger x3 = scalar();
        BigInteger x4 = scalar();
        BigInteger x5 = scalar();

        byte[] m1 = scalarG0PlusRH(x1, x2);
        byte[] m2 = scalarG0PlusRH(x3, x4);
        // M3 = x1·C_b + x5·H
        byte[] m3 = PedersenVectorCommit.add(PedersenVectorCommit.scale(cb, x1), commitScalar(BigInteger.ZERO, x5));

        byte[] ca = scalarG0PlusRH(a, ra);
        byte[] cc = scalarG0PlusRH(c.mod(L), rc);
        BigInteger e = challenge(ca, cb, cc, m1, m2, m3);

        BigInteger z1 = x1.add(e.multiply(a)).mod(L);
        BigInteger z2 = x2.add(e.multiply(ra)).mod(L);
        BigInteger z3 = x3.add(e.multiply(b)).mod(L);
        BigInteger z4 = x4.add(e.multiply(rb)).mod(L);
        BigInteger z5 = x5.add(e.multiply(rc.subtract(a.multiply(rb)))).mod(L);
        return new Proof(m1, m2, m3, z1, z2, z3, z4, z5);
    }

    /** Verify that {@code C_c} commits to the product of the values in {@code C_a} and {@code C_b}. */
    public static boolean verify(byte[] ca, byte[] cb, byte[] cc, Proof p) {
        if (p == null || ca == null || cb == null || cc == null
                || p.m1 == null || p.m2 == null || p.m3 == null
                || !inRange(p.z1) || !inRange(p.z2) || !inRange(p.z3) || !inRange(p.z4) || !inRange(p.z5)) {
            return false;
        }
        // Decode each commitment ONCE and work in projective points, encoding only the two sides of
        // each gate for the (canonical-encoding) comparison — avoids the redundant decode/encode round
        // trips of the byte-oriented add/scale on this O(n) hot path. Behaviour is identical.
        EdwardsPoint cap = Ristretto255.decode(ca);
        EdwardsPoint cbp = Ristretto255.decode(cb);
        EdwardsPoint ccp = Ristretto255.decode(cc);
        EdwardsPoint m1p = Ristretto255.decode(p.m1);
        EdwardsPoint m2p = Ristretto255.decode(p.m2);
        EdwardsPoint m3p = Ristretto255.decode(p.m3);
        if (cap == null || cbp == null || ccp == null || m1p == null || m2p == null || m3p == null) {
            return false;
        }
        EdwardsPoint g0 = PedersenVectorCommit.generator(0);
        EdwardsPoint h = PedersenVectorCommit.H;
        BigInteger e = challenge(ca, cb, cc, p.m1, p.m2, p.m3);

        // (1) z1·G_0 + z2·H == M1 ⊕ e·C_a
        EdwardsPoint lhs1 = EdwardsPoint.multiscalarMul(new BigInteger[]{p.z1, p.z2}, new EdwardsPoint[]{g0, h});
        EdwardsPoint rhs1 = m1p.add(cap.scalarMul(e));
        if (!encEq(lhs1, rhs1)) {
            return false;
        }
        // (2) z3·G_0 + z4·H == M2 ⊕ e·C_b
        EdwardsPoint lhs2 = EdwardsPoint.multiscalarMul(new BigInteger[]{p.z3, p.z4}, new EdwardsPoint[]{g0, h});
        EdwardsPoint rhs2 = m2p.add(cbp.scalarMul(e));
        if (!encEq(lhs2, rhs2)) {
            return false;
        }
        // (3) z1·C_b + z5·H == M3 ⊕ e·C_c
        EdwardsPoint lhs3 = EdwardsPoint.multiscalarMul(new BigInteger[]{p.z1, p.z5}, new EdwardsPoint[]{cbp, h});
        EdwardsPoint rhs3 = m3p.add(ccp.scalarMul(e));
        return encEq(lhs3, rhs3);
    }

    /** Canonical-encoding equality (same relation the byte-oriented path used: Ristretto point equality). */
    private static boolean encEq(EdwardsPoint a, EdwardsPoint b) {
        return Arrays.equals(Ristretto255.encode(a), Ristretto255.encode(b));
    }

    private static BigInteger scalar() {
        return RistrettoSRA.bytesToScalar(RistrettoSRA.generateLockScalar());
    }

    /** Canonical scalar response: present and reduced into {@code [0, L)}. */
    private static boolean inRange(BigInteger s) {
        return s != null && s.signum() >= 0 && s.compareTo(L) < 0;
    }
}
