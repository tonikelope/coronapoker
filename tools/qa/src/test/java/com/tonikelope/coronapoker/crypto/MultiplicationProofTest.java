/*
 * Bayer-Groth ladrillo: prueba de multiplicacion c = a*b sobre valores comprometidos.
 * Atomo del argumento de producto. Clave adversaria: el tramposo que afirma c != a*b CAE (gate 3).
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.math.BigInteger;
import java.security.SecureRandom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MultiplicationProofTest {

    private static final BigInteger L = EdwardsPoint.L;

    @BeforeAll
    public static void ensureRng() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
    }

    private static BigInteger scalar() {
        return RistrettoSRA.bytesToScalar(RistrettoSRA.generateLockScalar());
    }

    @Test
    public void honestProductVerifies() {
        BigInteger a = scalar(), ra = scalar();
        BigInteger b = scalar(), rb = scalar();
        BigInteger rc = scalar();
        BigInteger c = a.multiply(b).mod(L);
        byte[] ca = MultiplicationProof.commitScalar(a, ra);
        byte[] cb = MultiplicationProof.commitScalar(b, rb);
        byte[] cc = MultiplicationProof.commitScalar(c, rc);
        MultiplicationProof.Proof p = MultiplicationProof.prove(a, ra, b, rb, c, rc, cb);
        assertTrue(MultiplicationProof.verify(ca, cb, cc, p), "c = a*b honesto -> verifica");
    }

    @Test
    public void cheatingProductRejected() {
        BigInteger a = scalar(), ra = scalar();
        BigInteger b = scalar(), rb = scalar();
        BigInteger rc = scalar();
        BigInteger cWrong = a.multiply(b).add(BigInteger.ONE).mod(L); // c != a*b
        byte[] ca = MultiplicationProof.commitScalar(a, ra);
        byte[] cb = MultiplicationProof.commitScalar(b, rb);
        byte[] cc = MultiplicationProof.commitScalar(cWrong, rc);
        MultiplicationProof.Proof p = MultiplicationProof.prove(a, ra, b, rb, cWrong, rc, cb);
        assertFalse(MultiplicationProof.verify(ca, cb, cc, p), "c != a*b -> el gate de producto lo rechaza");
    }

    @Test
    public void zeroFactorHonest() {
        BigInteger a = BigInteger.ZERO, ra = scalar();
        BigInteger b = scalar(), rb = scalar();
        BigInteger rc = scalar();
        BigInteger c = BigInteger.ZERO; // 0 * b = 0
        byte[] ca = MultiplicationProof.commitScalar(a, ra);
        byte[] cb = MultiplicationProof.commitScalar(b, rb);
        byte[] cc = MultiplicationProof.commitScalar(c, rc);
        MultiplicationProof.Proof p = MultiplicationProof.prove(a, ra, b, rb, c, rc, cb);
        assertTrue(MultiplicationProof.verify(ca, cb, cc, p), "0*b = 0 honesto -> verifica");
    }

    @Test
    public void tamperedResponseRejected() {
        BigInteger a = scalar(), ra = scalar();
        BigInteger b = scalar(), rb = scalar();
        BigInteger rc = scalar();
        BigInteger c = a.multiply(b).mod(L);
        byte[] ca = MultiplicationProof.commitScalar(a, ra);
        byte[] cb = MultiplicationProof.commitScalar(b, rb);
        byte[] cc = MultiplicationProof.commitScalar(c, rc);
        MultiplicationProof.Proof p = MultiplicationProof.prove(a, ra, b, rb, c, rc, cb);
        MultiplicationProof.Proof t1 = new MultiplicationProof.Proof(p.m1, p.m2, p.m3,
                p.z1.add(BigInteger.ONE), p.z2, p.z3, p.z4, p.z5);
        assertFalse(MultiplicationProof.verify(ca, cb, cc, t1), "z1 manipulado -> rechazado");
        MultiplicationProof.Proof t5 = new MultiplicationProof.Proof(p.m1, p.m2, p.m3,
                p.z1, p.z2, p.z3, p.z4, p.z5.add(BigInteger.ONE));
        assertFalse(MultiplicationProof.verify(ca, cb, cc, t5), "z5 manipulado -> rechazado");
    }

    @Test
    public void proofForOneTripleDoesNotVerifyAnother() {
        BigInteger a = scalar(), ra = scalar();
        BigInteger b = scalar(), rb = scalar();
        BigInteger rc = scalar();
        BigInteger c = a.multiply(b).mod(L);
        byte[] cb = MultiplicationProof.commitScalar(b, rb);
        byte[] cc = MultiplicationProof.commitScalar(c, rc);
        MultiplicationProof.Proof p = MultiplicationProof.prove(a, ra, b, rb, c, rc, cb);
        byte[] caOther = MultiplicationProof.commitScalar(scalar(), scalar());
        assertFalse(MultiplicationProof.verify(caOther, cb, cc, p), "prueba de un triple no vale para otro C_a");
    }
}
