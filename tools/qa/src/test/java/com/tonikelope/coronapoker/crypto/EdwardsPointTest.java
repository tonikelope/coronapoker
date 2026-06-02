/*
 * Phase 1 (Ristretto255 engine) — edwards25519 point arithmetic tests.
 *
 * Property-based validation of the group law before Ristretto encode/decode is
 * built on top: base point is on the curve and has order L, double == add-self,
 * identity is neutral, addition is associative, and — critically for SRA — scalar
 * multiplication is commutative ((a*b)*B == a*(b*B)). The official RFC 9496
 * encoding vectors are exercised in the Ristretto encode/decode tests.
 */
package com.tonikelope.coronapoker.crypto;

import java.math.BigInteger;
import java.security.SecureRandom;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EdwardsPointTest {

    private static final BigInteger L = EdwardsPoint.L;

    @Test
    public void baseAndIdentityAreOnCurve() {
        assertTrue(EdwardsPoint.BASE.isOnCurve(), "base point must satisfy the curve equation");
        assertTrue(EdwardsPoint.IDENTITY.isOnCurve(), "identity must satisfy the curve equation");
    }

    @Test
    public void baseYIsFourFifths() {
        // Standard ed25519 base point has y = 4/5 mod p.
        Fe25519 expected = Fe25519.of(4).mul(Fe25519.of(5).inv());
        assertTrue(EdwardsPoint.BASE.affineY().ctEq(expected), "base y must be 4/5");
    }

    @Test
    public void identityIsNeutral() {
        assertTrue(EdwardsPoint.BASE.add(EdwardsPoint.IDENTITY).equalsPoint(EdwardsPoint.BASE));
        assertTrue(EdwardsPoint.IDENTITY.add(EdwardsPoint.BASE).equalsPoint(EdwardsPoint.BASE));
        assertTrue(EdwardsPoint.IDENTITY.dbl().equalsPoint(EdwardsPoint.IDENTITY));
    }

    @Test
    public void doubleEqualsAddSelf() {
        EdwardsPoint p = EdwardsPoint.BASE;
        for (int k = 0; k < 8; k++) {
            assertTrue(p.dbl().equalsPoint(p.add(p)), "2P must equal P+P");
            p = p.add(EdwardsPoint.BASE);
        }
    }

    @Test
    public void additionIsAssociative() {
        EdwardsPoint b = EdwardsPoint.BASE;
        EdwardsPoint b2 = b.dbl();
        EdwardsPoint b3 = b2.add(b);
        // (B + 2B) + 3B == B + (2B + 3B)
        assertTrue(b.add(b2).add(b3).equalsPoint(b.add(b2.add(b3))));
    }

    @Test
    public void basePointHasOrderL() {
        // L*B == identity (B generates the prime-order subgroup).
        assertTrue(EdwardsPoint.BASE.scalarMul(L).equalsPoint(EdwardsPoint.IDENTITY),
                "L*B must be the identity");
        // (L+1)*B == B.
        assertTrue(EdwardsPoint.BASE.scalarMul(L.add(BigInteger.ONE)).equalsPoint(EdwardsPoint.BASE),
                "(L+1)*B must equal B");
    }

    @Test
    public void basePointIsNotLowOrder() {
        // B is not a small-torsion point: 8*B != identity.
        assertFalse(EdwardsPoint.BASE.scalarMul(BigInteger.valueOf(8)).equalsPoint(EdwardsPoint.IDENTITY),
                "8*B must not be the identity");
    }

    @Test
    public void scalarMulIsAdditiveAndConsistent() {
        // (a + b)*B == a*B + b*B for small scalars.
        for (long a = 0; a < 12; a++) {
            for (long b = 0; b < 12; b++) {
                EdwardsPoint lhs = EdwardsPoint.BASE.scalarMul(BigInteger.valueOf(a + b));
                EdwardsPoint rhs = EdwardsPoint.BASE.scalarMul(BigInteger.valueOf(a))
                        .add(EdwardsPoint.BASE.scalarMul(BigInteger.valueOf(b)));
                assertTrue(lhs.equalsPoint(rhs), "(a+b)*B == a*B + b*B for a=" + a + " b=" + b);
            }
        }
    }

    @Test
    public void scalarMulIsCommutative() {
        // THE SRA property: (a*b)*B == a*(b*B) == b*(a*B), mod L.
        // This is what makes the commutative lock/unlock cascade work.
        SecureRandom rnd = new SecureRandom();
        for (int k = 0; k < 20; k++) {
            BigInteger a = new BigInteger(252, rnd).mod(L);
            BigInteger b = new BigInteger(252, rnd).mod(L);
            if (a.signum() == 0 || b.signum() == 0) {
                continue;
            }
            EdwardsPoint ab = EdwardsPoint.BASE.scalarMul(a.multiply(b).mod(L));
            EdwardsPoint aThenB = EdwardsPoint.BASE.scalarMul(a).scalarMul(b);
            EdwardsPoint bThenA = EdwardsPoint.BASE.scalarMul(b).scalarMul(a);
            assertTrue(ab.equalsPoint(aThenB), "(a*b)*B == a*(b*B)");
            assertTrue(ab.equalsPoint(bThenA), "(a*b)*B == b*(a*B)");
        }
    }

    @Test
    public void lockUnlockRoundTrip() {
        // Models the SRA lock/unlock: k * (k^-1 * P) == P, with arithmetic mod L
        // on a prime-order-subgroup point (k*B).
        SecureRandom rnd = new SecureRandom();
        EdwardsPoint card = EdwardsPoint.BASE.scalarMul(BigInteger.valueOf(1234567)); // in <B>
        for (int t = 0; t < 20; t++) {
            BigInteger k = new BigInteger(252, rnd).mod(L);
            if (k.signum() == 0) {
                continue;
            }
            BigInteger kInv = k.modInverse(L);
            EdwardsPoint locked = card.scalarMul(k);
            EdwardsPoint unlocked = locked.scalarMul(kInv);
            assertTrue(unlocked.equalsPoint(card), "k^-1 * (k * card) == card");
        }
    }

    @Test
    public void scalarMulResultIsOnCurve() {
        SecureRandom rnd = new SecureRandom();
        for (int k = 0; k < 10; k++) {
            BigInteger s = new BigInteger(252, rnd).mod(L).max(BigInteger.ONE);
            assertTrue(EdwardsPoint.BASE.scalarMul(s).isOnCurve(), "s*B must stay on the curve");
        }
    }
}
