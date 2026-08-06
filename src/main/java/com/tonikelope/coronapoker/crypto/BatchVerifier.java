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
import java.util.ArrayList;
import java.util.List;

/**
 * Batch verifier for a set of multi-scalar-multiplication equalities, each of the form
 * {@code multiscalarMul(scalars_j, points_j) == expected_j}. Instead of checking every equation with
 * its own ~252-doubling ladder, it draws one independent Fiat–Shamir weight {@code rho_j} PER equation,
 * each bound to every equation's points and scalars, and checks the random linear combination
 * {@code Σ_j rho_j·(lhs_j − expected_j) == O} in a SINGLE multi-scalar multiplication (one shared
 * ladder). Verify-side only: it never touches a proof or the wire format, so it is fully backward
 * compatible — the prover is unchanged.
 *
 * <p><b>Soundness.</b> If some equation j has {@code d_j = lhs_j − expected_j != O}, then for the
 * independent uniform weights the combination {@code Σ rho_j·d_j} equals {@code O} with probability
 * exactly {@code 1/L}: fix all weights but {@code rho_j}; since {@code d_j} has order {@code L}, the map
 * {@code rho_j ↦ rho_j·d_j} is a bijection, so exactly one {@code rho_j} value hits the required point.
 * The weights are a hash of ALL the equations' points and scalars, so a malicious prover cannot craft a
 * proof whose induced weights land in the kernel: changing any element reshuffles every weight
 * unpredictably. {@code 1/L ≈ 2^-252} is negligible, and unlike powers-of-a-single-challenge there is
 * no degenerate weight (a zero {@code rho_j} still leaves the other equations checked by their own
 * weight; the argument above uses only {@code rho_j} of the failing equation).
 *
 * <p><b>Completeness.</b> If every {@code d_j == O}, the combination is {@code O} for every choice of
 * weights, so a valid set of equations always passes — no false rejects.
 *
 * <p>A null/malformed equation poisons the batch ({@link #allHold} returns false): a missing point can
 * never satisfy an equation, and this mirrors the pre-batch behaviour of rejecting on a failed decode.
 * Package-private; exercised directly by {@code BatchVerifierTest}.
 */
final class BatchVerifier {

    private static final BigInteger L = EdwardsPoint.L;

    private final Transcript tr;
    private final List<BigInteger[]> scalars = new ArrayList<>();
    private final List<EdwardsPoint[]> points = new ArrayList<>();
    private final List<EdwardsPoint> expected = new ArrayList<>();
    private boolean poisoned = false;

    BatchVerifier(String domain) {
        this.tr = new Transcript(domain);
    }

    /**
     * Adds the equation {@code multiscalarMul(eqScalars, eqPoints) == expectedPoint} and absorbs every
     * element so the batch weights bind to it. A null array, a length mismatch, or any null element
     * poisons the batch so {@link #allHold} returns false.
     */
    void addEquation(BigInteger[] eqScalars, EdwardsPoint[] eqPoints, EdwardsPoint expectedPoint) {
        if (poisoned) {
            return;
        }
        if (eqScalars == null || eqPoints == null || eqScalars.length != eqPoints.length || expectedPoint == null) {
            poisoned = true;
            return;
        }
        tr.absorb("eq", frame(scalars.size()));
        tr.absorb("k", frame(eqPoints.length));
        for (int i = 0; i < eqPoints.length; i++) {
            if (eqScalars[i] == null || eqPoints[i] == null) {
                poisoned = true;
                return;
            }
            tr.absorbScalar("s", eqScalars[i]);
            tr.absorbPoint("P", eqPoints[i]);
        }
        tr.absorbPoint("M", expectedPoint);
        scalars.add(eqScalars);
        points.add(eqPoints);
        expected.add(expectedPoint);
    }

    /**
     * True iff every added equation holds, checked as {@code Σ rho_j·(lhs_j − expected_j) == O} in one
     * multi-scalar multiplication. False if the batch was poisoned by a null/malformed equation. An
     * empty batch holds vacuously.
     */
    boolean allHold() {
        if (poisoned) {
            return false;
        }
        int m = scalars.size();
        if (m == 0) {
            return true;
        }
        List<BigInteger> allS = new ArrayList<>();
        List<EdwardsPoint> allP = new ArrayList<>();
        for (int j = 0; j < m; j++) {
            BigInteger rho = tr.challengeScalar("rho" + j); // independent weight, bound to every equation
            BigInteger[] sj = scalars.get(j);
            EdwardsPoint[] pj = points.get(j);
            for (int i = 0; i < pj.length; i++) {
                allS.add(sj[i].multiply(rho).mod(L)); // rho_j · lhs_j
                allP.add(pj[i]);
            }
            allS.add(rho);                            // − rho_j · expected_j (negation on the point)
            allP.add(expected.get(j).negate());
        }
        EdwardsPoint combined = EdwardsPoint.multiscalarMul(
                allS.toArray(new BigInteger[0]), allP.toArray(new EdwardsPoint[0]));
        return Ristretto255.isIdentity(combined);
    }

    /** Four-byte big-endian frame for a count, so absorbed positions/lengths are unambiguous even
     *  past 2^16 equations (a latent bound an auditor flagged; unreachable here at <= 53 per deck). */
    private static byte[] frame(int v) {
        return new byte[]{(byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v};
    }
}
