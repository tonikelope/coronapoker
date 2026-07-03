/*
 * Differential guard for the Pedersen fixed-base comb optimization: PedersenVectorCommit.commit
 * now computes the single-value commitment r·H + v·G_0 with precomputed comb tables over the
 * constant generators H and G_0, instead of the Straus multi-scalar ladder. Curve addition is
 * abelian, so the comb yields the SAME group element and therefore the SAME canonical encoding.
 * This asserts byte-for-byte equality against EdwardsPoint.multiscalarMul over edge cases and a
 * deterministic 256-bit fuzz. If the comb indexing ever drifts, this fails immediately.
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class PedersenCombTest {

    @BeforeAll
    public static void ensureRng() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
    }

    // Reference: r·H + v·G_0 via the shared-ladder Straus multi-scalar (the pre-optimization path).
    private static byte[] straus(BigInteger v, BigInteger r) {
        return Ristretto255.encode(EdwardsPoint.multiscalarMul(
                new BigInteger[]{r, v},
                new EdwardsPoint[]{PedersenVectorCommit.H, PedersenVectorCommit.generator(0)}));
    }

    private static void check(BigInteger v, BigInteger r) {
        assertArrayEquals(straus(v, r), PedersenVectorCommit.commit(new BigInteger[]{v}, r),
                "comb r·H+v·G0 must equal Straus multiscalarMul byte-for-byte (v=" + v + ", r=" + r + ")");
    }

    @Test
    public void combMatchesStrausEdgeCases() {
        BigInteger L = EdwardsPoint.L;
        BigInteger[] xs = {
            BigInteger.ZERO, BigInteger.ONE, BigInteger.TWO,
            L.subtract(BigInteger.ONE), L, L.add(BigInteger.ONE),
            BigInteger.ONE.shiftLeft(252), BigInteger.ONE.shiftLeft(255),
            new BigInteger("123456789012345678901234567890")
        };
        for (BigInteger v : xs) {
            for (BigInteger r : xs) {
                check(v, r);
            }
        }
    }

    @Test
    public void combMatchesStrausFuzz() {
        Random rnd = new Random(0xC0FFEEL); // fixed seed -> deterministic
        for (int i = 0; i < 2000; i++) {
            check(new BigInteger(256, rnd), new BigInteger(256, rnd));
        }
    }
}
