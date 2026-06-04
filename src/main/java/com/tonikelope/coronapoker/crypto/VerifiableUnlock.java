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
 * Verifiable de-locking for the SRA dealing cascade — the logic that closes the
 * blinded-oracle attack (see docs/SECURITY.md).
 *
 * A peer that strips its lock from a point publishes, alongside the result, a DLEQ
 * proof that it used its COMMITTED key. Verifying the chain from the committed deck
 * point (MEGAPACKET[slot]) up to any residual proves the residual descends from the
 * committed deck — so the host cannot feed a blinded r*P (the chain would not start
 * at the committed bytes, and no peer committed the factor r).
 *
 * This is engine-level, protocol-agnostic glue (no Crupier coupling): the cascade
 * orchestration in Crupier will call these and put the chain on the wire.
 */
public final class VerifiableUnlock {

    private VerifiableUnlock() {
    }

    /** One de-locking step: the residual after this peer's unlock, plus its DLEQ proof. */
    public static final class Step {
        /** Residual after this peer stripped its lock (32-byte encoding). */
        public final byte[] residual;
        /** DLEQ proof that residualBefore = k * residual for the committed K = k*B. */
        public final byte[] proof;

        public Step(byte[] residual, byte[] proof) {
            this.residual = residual;
            this.proof = proof;
        }
    }

    /**
     * Peer side: strip our lock from {@code residualBefore} and prove we used our
     * committed key. Returns null if the input point is off-group (zero-trust reject).
     *
     * @param residualBefore current 32-byte residual handed to us by the host
     * @param lockScalar     our per-hand lock scalar k (we derive k^-1 and K = k*B)
     */
    public static Step unlockWithProof(byte[] residualBefore, byte[] lockScalar) {
        EdwardsPoint x = Ristretto255.decode(residualBefore);
        if (x == null) {
            return null; // off-group / non-canonical input
        }
        BigInteger k = RistrettoSRA.bytesToScalar(lockScalar);
        BigInteger kInv = k.modInverse(EdwardsPoint.L);
        EdwardsPoint xPrime = x.scalarMul(kInv);       // X' = k^-1 * X
        EdwardsPoint commitK = EdwardsPoint.BASE.scalarMul(k); // K = k*B
        // Prove log_B(K) = log_{X'}(X) = k, i.e. X = k * X'.
        byte[] proof = Dleq.prove(k, EdwardsPoint.BASE, commitK, xPrime, x);
        return new Step(Ristretto255.encode(xPrime), proof);
    }

    /**
     * Verify a single de-locking step: that {@code residualBefore = k * residualAfter}
     * for the peer whose committed key is {@code committedK}. Used by a peer before it
     * applies its own unlock (to check the incoming chain) and by any observer.
     */
    public static boolean verifyStep(byte[] residualBefore, byte[] residualAfter,
                                     byte[] committedK, byte[] proof) {
        EdwardsPoint before = Ristretto255.decode(residualBefore);
        EdwardsPoint after = Ristretto255.decode(residualAfter);
        EdwardsPoint k = Ristretto255.decode(committedK);
        if (before == null || after == null || k == null) {
            return false;
        }
        return Dleq.verify(EdwardsPoint.BASE, k, after, before, proof);
    }

    /**
     * Verify a whole de-locking chain for one slot.
     *
     * @param committedSlot the committed MEGAPACKET[slot] bytes (chain MUST start here)
     * @param residuals     residuals[0]..residuals[n]; residuals[0] is the chain start
     * @param committedKs   committedKs[m] is the committed key of the peer who produced
     *                      residuals[m+1] from residuals[m]
     * @param proofs        proofs[m] is the DLEQ for step m
     * @return true iff the chain starts at the committed slot and every step verifies
     */
    public static boolean verifyChain(byte[] committedSlot, byte[][] residuals,
                                      byte[][] committedKs, byte[][] proofs) {
        if (residuals == null || residuals.length < 1) {
            return false;
        }
        // Chain MUST start at the committed deck point — this is what kills blinding.
        if (!Arrays.equals(committedSlot, residuals[0])) {
            return false;
        }
        int steps = residuals.length - 1;
        if (committedKs == null || proofs == null
                || committedKs.length != steps || proofs.length != steps) {
            return false;
        }
        for (int m = 0; m < steps; m++) {
            if (!verifyStep(residuals[m], residuals[m + 1], committedKs[m], proofs[m])) {
                return false;
            }
        }
        return true;
    }
}
