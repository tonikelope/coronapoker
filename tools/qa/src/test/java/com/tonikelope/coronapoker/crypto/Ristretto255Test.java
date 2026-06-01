/*
 * Phase 1 (Ristretto255 engine) — encode/decode tests (RFC 9496 §4.3).
 *
 * Validation strategy (the official appendix hex is fragile to transcribe, so we
 * minimise reliance on it):
 *   - Constants are self-validated against d via square relations (RFC 9496 §4.1).
 *   - encode is anchored to the OFFICIAL vectors for identity, B and 2B — if the
 *     formula were wrong these would not match (we never adjust the vector to pass).
 *   - encode/decode are exercised as mutual inverses over many multiples k*B
 *     (round-trip), which is the functional property the protocol relies on.
 *   - The security-critical decode rejections (non-canonical, negative, non-square)
 *     are built programmatically rather than pasted from the (corruptible) appendix.
 */
package com.tonikelope.coronapoker.crypto;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Ristretto255Test {

    // Official RFC 9496 Appendix A.1 encodings (verified 64 hex chars each).
    private static final String B1 = "e2f2ae0a6abc4e71a884a961c500515f58e30b6aa582dd8db6a65945e08d2d76";
    private static final String B2 = "6a493210f7499cd17fecb510ae0cea23a110e8d5b901f8acadd3095c73a3b919";

    private static byte[] hex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        }
        return out;
    }

    @Test
    public void constantsSelfValidate() {
        // D matches the RFC 9496 §4.1 decimal value (and -121665/121666).
        assertTrue(Ristretto255.D.ctEq(Fe25519.of(new BigInteger(
                "37095705934669439343138083508754565189542113879843219016388785533085940283555"))),
                "D must equal the RFC value");

        // SQRT_M1^2 == -1.
        assertTrue(Ristretto255.SQRT_M1.sqr().ctEq(Fe25519.ONE.negate()));

        // INVSQRT_A_MINUS_D^2 * (a-d) == 1, with a=-1 -> (a-d) = -1-d.
        Fe25519 aMinusD = Fe25519.ONE.negate().sub(Ristretto255.D);
        assertTrue(Ristretto255.INVSQRT_A_MINUS_D.sqr().mul(aMinusD).ctEq(Fe25519.ONE),
                "INVSQRT_A_MINUS_D^2 * (a-d) must be 1");

        // SQRT_AD_MINUS_ONE^2 == a*d - 1 == -d - 1.
        assertTrue(Ristretto255.SQRT_AD_MINUS_ONE.sqr().ctEq(Ristretto255.D.negate().sub(Fe25519.ONE)),
                "SQRT_AD_MINUS_ONE^2 must be -d-1");

        // ONE_MINUS_D_SQ == 1 - d^2.
        assertTrue(Ristretto255.ONE_MINUS_D_SQ.ctEq(Fe25519.ONE.sub(Ristretto255.D.sqr())));

        // D_MINUS_ONE_SQ == (d-1)^2.
        assertTrue(Ristretto255.D_MINUS_ONE_SQ.ctEq(Ristretto255.D.sub(Fe25519.ONE).sqr()));
    }

    @Test
    public void encodeIdentityIsAllZeros() {
        assertArrayEquals(new byte[32], Ristretto255.encode(EdwardsPoint.IDENTITY),
                "encode(identity) must be 32 zero bytes");
    }

    @Test
    public void encodeBaseMatchesOfficialVector() {
        assertArrayEquals(hex(B1), Ristretto255.encode(EdwardsPoint.BASE),
                "encode(B) must equal the RFC 9496 vector");
    }

    @Test
    public void encodeTwoBaseMatchesOfficialVector() {
        assertArrayEquals(hex(B2), Ristretto255.encode(EdwardsPoint.BASE.dbl()),
                "encode(2B) must equal the RFC 9496 vector");
    }

    @Test
    public void decodeIdentityRoundTrips() {
        EdwardsPoint p = Ristretto255.decode(new byte[32]);
        assertNotNull(p, "decode(zeros) must succeed");
        assertTrue(p.equalsPoint(EdwardsPoint.IDENTITY), "decode(zeros) must be the identity");
    }

    @Test
    public void encodeDecodeRoundTripOverMultiples() {
        for (int k = 0; k <= 64; k++) {
            EdwardsPoint p = EdwardsPoint.BASE.scalarMul(BigInteger.valueOf(k));
            byte[] enc = Ristretto255.encode(p);
            EdwardsPoint dec = Ristretto255.decode(enc);
            assertNotNull(dec, "decode(encode(" + k + "*B)) must succeed");
            // Ristretto identifies points up to the cofactor torsion, so decode may
            // return a different Edwards representative of the SAME group element.
            // The correct equality is by canonical encoding (encode is injective on
            // group elements): encode(decode(enc)) == enc.
            assertArrayEquals(enc, Ristretto255.encode(dec),
                    "decode(encode(" + k + "*B)) must re-encode to the same element");
        }
    }

    @Test
    public void encodeIsIndependentOfExtendedRepresentation() {
        // Ristretto: equal group elements encode equally, regardless of the extended
        // representation. P and P+identity are the same element with a different (Z).
        EdwardsPoint p = EdwardsPoint.BASE.scalarMul(BigInteger.valueOf(7));
        EdwardsPoint sameViaAdd = p.add(EdwardsPoint.IDENTITY);
        assertArrayEquals(Ristretto255.encode(p), Ristretto255.encode(sameViaAdd),
                "the same group element must encode identically from any representation");
    }

    @Test
    public void rejectsNonCanonicalFieldEncodings() {
        // s = p (raw 256-bit LE value == p) -> non-canonical -> reject.
        assertNull(Ristretto255.decode(leBytes(Fe25519.P)), "s = p must be rejected (non-canonical)");
        // all 0xff (value 2^256-1 >= p) -> reject.
        byte[] allFf = new byte[32];
        java.util.Arrays.fill(allFf, (byte) 0xff);
        assertNull(Ristretto255.decode(allFf), "all-ones must be rejected (non-canonical)");
        // A valid encoding with bit 255 set is non-canonical (value >= p) -> reject.
        byte[] bWithHighBit = Ristretto255.encode(EdwardsPoint.BASE).clone();
        bWithHighBit[31] |= (byte) 0x80;
        assertNull(Ristretto255.decode(bWithHighBit), "bit-255-set must be rejected (non-canonical)");
    }

    @Test
    public void rejectsNegativeFieldElement() {
        // encode always yields an even (non-negative) s. Its negation p - s is odd
        // (p is odd) -> IS_NEGATIVE -> decode must reject.
        byte[] encB = Ristretto255.encode(EdwardsPoint.BASE);
        BigInteger sB = Fe25519.fromBytes(encB).toBigInteger();
        assertTrue(sB.testBit(0) == false, "encode must produce an even s");
        Fe25519 sNeg = Fe25519.of(Fe25519.P.subtract(sB)); // odd, canonical
        assertTrue(sNeg.isNegative(), "p - s must be odd (negative)");
        assertNull(Ristretto255.decode(sNeg.toBytes()), "negative field element must be rejected");
    }

    @Test
    public void rejectsNonSquareInputs() {
        // Roughly half of the even canonical field elements are not valid ristretto
        // encodings (non-square branch). Confirm decode refuses a healthy number of them.
        int rejected = 0;
        int valid = 0;
        for (int k = 1; k <= 200; k++) {
            byte[] enc = Fe25519.of(2L * k).toBytes(); // even -> not rejected for negativity
            EdwardsPoint p = Ristretto255.decode(enc);
            if (p == null) {
                rejected++;
            } else {
                valid++;
                // Anything that decodes must round-trip its canonical encoding.
                assertArrayEquals(enc, Ristretto255.encode(p),
                        "decoded point must re-encode to its canonical input");
            }
        }
        assertTrue(rejected > 0, "expected some non-square even inputs to be rejected");
        assertTrue(valid > 0, "expected some even inputs to decode to valid points");
    }

    @Test
    public void sraStyleLockUnlockAndCommutativityByEncoding() {
        // Exercises the EXACT pattern the SRA cascade will use: operate on canonical
        // encodings, decoding to a representative, scalar-multiplying, and re-encoding.
        // Validates that this is well-defined on Ristretto group elements (independent
        // of the cofactor representative decode happens to pick).
        java.security.SecureRandom rnd = new java.security.SecureRandom();
        byte[] genesis = Ristretto255.encode(EdwardsPoint.BASE.scalarMul(BigInteger.valueOf(98765)));

        for (int t = 0; t < 25; t++) {
            BigInteger k = new BigInteger(252, rnd).mod(EdwardsPoint.L);
            if (k.signum() == 0) {
                continue;
            }
            BigInteger kInv = k.modInverse(EdwardsPoint.L);

            byte[] locked = Ristretto255.encode(Ristretto255.decode(genesis).scalarMul(k));
            byte[] unlocked = Ristretto255.encode(Ristretto255.decode(locked).scalarMul(kInv));
            assertArrayEquals(genesis, unlocked,
                    "lock then unlock (via encodings) must recover the genesis encoding");
        }

        // Commutativity at the encoding level: k1 then k2 == k2 then k1.
        BigInteger k1 = new BigInteger(252, rnd).mod(EdwardsPoint.L).max(BigInteger.ONE);
        BigInteger k2 = new BigInteger(252, rnd).mod(EdwardsPoint.L).max(BigInteger.ONE);
        byte[] ab = Ristretto255.encode(
                Ristretto255.decode(Ristretto255.encode(Ristretto255.decode(genesis).scalarMul(k1))).scalarMul(k2));
        byte[] ba = Ristretto255.encode(
                Ristretto255.decode(Ristretto255.encode(Ristretto255.decode(genesis).scalarMul(k2))).scalarMul(k1));
        assertArrayEquals(ab, ba, "lock order must not matter (commutative) at the encoding level");
    }

    private static byte[] leBytes(BigInteger value) {
        byte[] be = value.toByteArray();
        byte[] le = new byte[32];
        for (int i = 0; i < be.length && i < 32; i++) {
            le[i] = be[be.length - 1 - i];
        }
        return le;
    }
}
