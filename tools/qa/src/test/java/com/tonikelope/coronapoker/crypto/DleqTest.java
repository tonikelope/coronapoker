/*
 * Phase 2 (verifiable dealing) — DLEQ (Chaum-Pedersen) proof tests.
 *
 * Validates the proof of equality of discrete logs that will bind each cascade
 * de-locking step to the committed deck: a valid (k*g1, k*g2) proof verifies; a
 * proof made with the wrong k, for a different statement, for a blinded/altered
 * target, or with tampered bytes must NOT verify. Also exercises the exact SRA
 * shape: commit K = k*B and prove X = k*X' (so the unlock used the committed key).
 */
package com.tonikelope.coronapoker.crypto;

import java.math.BigInteger;
import java.security.SecureRandom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DleqTest {

    private static final BigInteger L = EdwardsPoint.L;

    @BeforeAll
    public static void ensureRng() {
        if (com.tonikelope.coronapoker.Helpers.CSPRNG_GENERATOR == null) {
            com.tonikelope.coronapoker.Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
    }

    private static BigInteger scalar() {
        return new BigInteger(252, new SecureRandom()).mod(L).max(BigInteger.ONE);
    }

    @Test
    public void validProofVerifies() {
        for (int i = 0; i < 20; i++) {
            BigInteger k = scalar();
            EdwardsPoint g1 = EdwardsPoint.BASE;
            EdwardsPoint g2 = EdwardsPoint.BASE.scalarMul(scalar()); // an independent generator
            EdwardsPoint h1 = g1.scalarMul(k);
            EdwardsPoint h2 = g2.scalarMul(k);
            byte[] proof = Dleq.prove(k, g1, h1, g2, h2);
            assertTrue(Dleq.verify(g1, h1, g2, h2, proof), "valid DLEQ proof must verify");
        }
    }

    @Test
    public void wrongKeyDoesNotVerify() {
        BigInteger k = scalar();
        BigInteger kPrime = k.add(BigInteger.ONE).mod(L);
        EdwardsPoint g1 = EdwardsPoint.BASE;
        EdwardsPoint g2 = EdwardsPoint.BASE.scalarMul(scalar());
        EdwardsPoint h1 = g1.scalarMul(k);
        EdwardsPoint h2 = g2.scalarMul(kPrime); // inconsistent: different exponent on g2
        byte[] proof = Dleq.prove(k, g1, h1, g2, h2);
        assertFalse(Dleq.verify(g1, h1, g2, h2, proof),
                "a proof must not verify when the two exponents differ");
    }

    @Test
    public void blindedOrAlteredTargetDoesNotVerify() {
        BigInteger k = scalar();
        EdwardsPoint g1 = EdwardsPoint.BASE;
        EdwardsPoint g2 = EdwardsPoint.BASE.scalarMul(scalar());
        EdwardsPoint h1 = g1.scalarMul(k);
        EdwardsPoint h2 = g2.scalarMul(k);
        byte[] proof = Dleq.prove(k, g1, h1, g2, h2);

        // Blind the target by a factor r: the same proof must not verify.
        BigInteger r = scalar();
        EdwardsPoint h2Blinded = h2.scalarMul(r);
        assertFalse(Dleq.verify(g1, h1, g2, h2Blinded, proof),
                "proof must not verify against a blinded target");

        // Altered first target.
        EdwardsPoint h1Altered = h1.add(EdwardsPoint.BASE);
        assertFalse(Dleq.verify(g1, h1Altered, g2, h2, proof),
                "proof must not verify against an altered h1");
    }

    @Test
    public void tamperedProofDoesNotVerify() {
        BigInteger k = scalar();
        EdwardsPoint g1 = EdwardsPoint.BASE;
        EdwardsPoint g2 = EdwardsPoint.BASE.scalarMul(scalar());
        EdwardsPoint h1 = g1.scalarMul(k);
        EdwardsPoint h2 = g2.scalarMul(k);
        byte[] proof = Dleq.prove(k, g1, h1, g2, h2);

        for (int pos : new int[]{0, 16, 32, 48, 63}) {
            byte[] bad = proof.clone();
            bad[pos] ^= 0x01;
            assertFalse(Dleq.verify(g1, h1, g2, h2, bad),
                    "a one-bit-tampered proof must not verify (pos " + pos + ")");
        }
    }

    @Test
    public void differentGeneratorsDoNotVerify() {
        // A proof for one statement must not verify under different generators.
        BigInteger k = scalar();
        EdwardsPoint g1 = EdwardsPoint.BASE;
        EdwardsPoint g2 = EdwardsPoint.BASE.scalarMul(scalar());
        byte[] proof = Dleq.prove(k, g1, g1.scalarMul(k), g2, g2.scalarMul(k));

        EdwardsPoint g2Other = EdwardsPoint.BASE.scalarMul(scalar());
        assertFalse(Dleq.verify(g1, g1.scalarMul(k), g2Other, g2Other.scalarMul(k), proof),
                "proof must be bound to the exact generators it was made for");
    }

    @Test
    public void sraUnlockBindingShape() {
        // The real cascade shape: peer commits K = k*B; it received X (locked by k,
        // among others) and produced X' = k^-1 * X, so X = k*X'. It proves
        // log_B(K) = log_{X'}(X) = k. Anyone can verify the unlock used the committed key.
        BigInteger k = RistrettoSRA.bytesToScalar(RistrettoSRA.generateLockScalar());
        BigInteger kInv = k.modInverse(L);
        EdwardsPoint base = EdwardsPoint.BASE;
        EdwardsPoint commitK = base.scalarMul(k); // K = k*B, committed

        // X is some locked card point; X' is the unlocked-by-this-peer result.
        EdwardsPoint x = EdwardsPoint.BASE.scalarMul(scalar()); // stand-in for a locked point
        EdwardsPoint xPrime = x.scalarMul(kInv);                // X' = k^-1 * X  => X = k*X'

        byte[] proof = Dleq.prove(k, base, commitK, xPrime, x);
        assertTrue(Dleq.verify(base, commitK, xPrime, x, proof),
                "honest unlock with the committed key must verify");

        // A peer claiming a DIFFERENT committed key cannot pass.
        EdwardsPoint wrongCommit = base.scalarMul(k.add(BigInteger.ONE).mod(L));
        assertFalse(Dleq.verify(base, wrongCommit, xPrime, x, proof),
                "unlock must not verify against a key other than the committed one");
    }
}
