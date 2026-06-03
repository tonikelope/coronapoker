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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * Pedersen commitment over the Ristretto255 prime-order group: {@code C = m·B + r·H}, where B is
 * the base point and H is a nothing-up-my-sleeve second generator whose discrete log relative to B
 * is unknown. Perfectly hiding (r uniform), computationally binding under the discrete-log
 * assumption.
 *
 * <p>First building block of the verifiable-shuffle engine (see
 * {@code docs/sra-shuffle-argument-engine.md}). Pure crypto, no protocol coupling. Commitments are
 * the canonical Ristretto encoding of the group element, so equality is byte equality of the
 * encoding (resolves cofactor / representative ambiguities the same way the rest of the SRA layer
 * does). All scalars are reduced mod the group order L.
 */
public final class PedersenCommit {

    /** Second generator H = hashToGroup(SHA-512("CoronaPoker/Pedersen/H/v1")); DL wrt B unknown. */
    public static final EdwardsPoint H = deriveH();

    private static final String H_DOMAIN = "CoronaPoker/Pedersen/H/v1";

    private PedersenCommit() {
    }

    private static EdwardsPoint deriveH() {
        try {
            byte[] seed = MessageDigest.getInstance("SHA-512")
                    .digest(H_DOMAIN.getBytes(StandardCharsets.UTF_8));
            return Ristretto255.hashToGroup(seed);
        } catch (Exception e) {
            throw new IllegalStateException("Pedersen H generator init failed", e);
        }
    }

    /** Commit to {@code m} with blinding {@code r}: canonical encoding of {@code m·B + r·H}. */
    public static byte[] commit(BigInteger m, BigInteger r) {
        EdwardsPoint c = EdwardsPoint.BASE.scalarMul(m.mod(EdwardsPoint.L))
                .add(H.scalarMul(r.mod(EdwardsPoint.L)));
        return Ristretto255.encode(c);
    }

    /** True iff {@code commitment} opens to {@code (m, r)}. */
    public static boolean verify(byte[] commitment, BigInteger m, BigInteger r) {
        return commitment != null && Arrays.equals(commitment, commit(m, r));
    }

    /**
     * Homomorphic addition: {@code Comm(m1,r1) ⊕ Comm(m2,r2) = Comm(m1+m2, r1+r2)}. Operates on
     * encodings; returns null if either input is not a canonical group element.
     */
    public static byte[] add(byte[] c1, byte[] c2) {
        EdwardsPoint p1 = Ristretto255.decode(c1);
        EdwardsPoint p2 = Ristretto255.decode(c2);
        if (p1 == null || p2 == null) {
            return null;
        }
        return Ristretto255.encode(p1.add(p2));
    }

    /**
     * Homomorphic scalar scaling: {@code e · Comm(m,r) = Comm(e·m, e·r)}. Returns null if the input
     * is not a canonical group element.
     */
    public static byte[] scale(byte[] c, BigInteger e) {
        EdwardsPoint p = Ristretto255.decode(c);
        if (p == null) {
            return null;
        }
        return Ristretto255.encode(p.scalarMul(e.mod(EdwardsPoint.L)));
    }
}
