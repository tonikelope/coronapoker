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

import com.tonikelope.coronapoker.Helpers;
import java.math.BigInteger;
import java.security.MessageDigest;

/**
 * Non-interactive proof of equality of discrete logarithms (Chaum-Pedersen) over
 * the Ristretto255 group, with the Fiat-Shamir transform.
 *
 * Proves knowledge of a scalar k such that h1 = k*g1 AND h2 = k*g2, without
 * revealing k. In the cascade this binds each de-locking step to the committed
 * deck: a peer commits K = k*B, and when it strips its lock from a point X
 * (producing X' = k^-1 * X) it proves log_B(K) = log_{X'}(X) = k, i.e. X = k*X'.
 * Chaining these from the committed MEGAPACKET kills the blinded-oracle attack —
 * a blinded input r*X has no valid proof because no peer committed the factor r.
 *
 * Proof wire format: c (32 bytes LE) || s (32 bytes LE) = 64 bytes.
 */
public final class Dleq {

    private static final byte[] DOMAIN = "SRA_DLEQ_V1\0".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private static final BigInteger L = EdwardsPoint.L;
    public static final int PROOF_BYTES = 64;

    private Dleq() {
    }

    /**
     * Proves that h1 = k*g1 and h2 = k*g2 for the secret k.
     *
     * @return a 64-byte proof (c || s)
     */
    public static byte[] prove(BigInteger k, EdwardsPoint g1, EdwardsPoint h1,
                               EdwardsPoint g2, EdwardsPoint h2) {
        BigInteger r = randomScalar();
        EdwardsPoint t1 = g1.scalarMul(r);
        EdwardsPoint t2 = g2.scalarMul(r);
        BigInteger c = challenge(g1, h1, g2, h2, t1, t2);
        BigInteger s = r.add(c.multiply(k)).mod(L);

        byte[] proof = new byte[PROOF_BYTES];
        System.arraycopy(RistrettoSRA.scalarToBytes(c), 0, proof, 0, 32);
        System.arraycopy(RistrettoSRA.scalarToBytes(s), 0, proof, 32, 32);
        return proof;
    }

    /**
     * Verifies a proof that h1 = k*g1 and h2 = k*g2 for some common k.
     *
     * @return true iff the proof is valid for exactly this statement
     */
    public static boolean verify(EdwardsPoint g1, EdwardsPoint h1, EdwardsPoint g2,
                                 EdwardsPoint h2, byte[] proof) {
        if (proof == null || proof.length != PROOF_BYTES) {
            return false;
        }
        byte[] cb = new byte[32];
        byte[] sb = new byte[32];
        System.arraycopy(proof, 0, cb, 0, 32);
        System.arraycopy(proof, 32, sb, 0, 32);
        BigInteger c = RistrettoSRA.bytesToScalar(cb);
        BigInteger s = RistrettoSRA.bytesToScalar(sb);
        if (c.compareTo(L) >= 0 || s.compareTo(L) >= 0) {
            return false; // non-canonical scalar
        }

        // R1 = s*g1 - c*h1 ; R2 = s*g2 - c*h2 — one shared-ladder multi-scalar each (the negation
        // moves to the point, which is free; s*g + c*(-h) is the exact same group element).
        EdwardsPoint r1 = EdwardsPoint.multiscalarMul(
                new BigInteger[]{s, c}, new EdwardsPoint[]{g1, h1.negate()});
        EdwardsPoint r2 = EdwardsPoint.multiscalarMul(
                new BigInteger[]{s, c}, new EdwardsPoint[]{g2, h2.negate()});
        BigInteger cPrime = challenge(g1, h1, g2, h2, r1, r2);
        return cPrime.equals(c);
    }

    private static BigInteger challenge(EdwardsPoint g1, EdwardsPoint h1, EdwardsPoint g2,
                                        EdwardsPoint h2, EdwardsPoint t1, EdwardsPoint t2) {
        try {
            MessageDigest sha512 = MessageDigest.getInstance("SHA-512");
            sha512.update(DOMAIN);
            sha512.update(Ristretto255.encode(g1));
            sha512.update(Ristretto255.encode(h1));
            sha512.update(Ristretto255.encode(g2));
            sha512.update(Ristretto255.encode(h2));
            sha512.update(Ristretto255.encode(t1));
            sha512.update(Ristretto255.encode(t2));
            byte[] digest = sha512.digest();
            return new BigInteger(1, digest).mod(L);
        } catch (Exception e) {
            throw new RuntimeException("DLEQ challenge hash failed", e);
        }
    }

    private static BigInteger randomScalar() {
        while (true) {
            byte[] raw = new byte[32];
            Helpers.CSPRNG_GENERATOR.nextBytes(raw);
            raw[31] &= (byte) 0x1f;
            BigInteger r = RistrettoSRA.bytesToScalar(raw);
            if (r.signum() != 0 && r.compareTo(L) < 0) {
                return r;
            }
        }
    }
}
