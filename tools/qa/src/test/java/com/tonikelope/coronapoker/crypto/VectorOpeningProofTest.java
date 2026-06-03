/*
 * Bayer-Groth ladrillo: PoK (Schnorr) de la apertura del commitment vectorial.
 * Suite adversaria: completeness, y rechazo de T/z/zr manipulados, commitment cambiado, longitud
 * mal. ZK sanity: el proof no depende de poder re-abrir (las respuestas van enmascaradas).
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.math.BigInteger;
import java.security.SecureRandom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VectorOpeningProofTest {

    @BeforeAll
    public static void ensureRng() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
    }

    private static BigInteger scalar() {
        return RistrettoSRA.bytesToScalar(RistrettoSRA.generateLockScalar());
    }

    private static BigInteger[] vec(int n) {
        BigInteger[] a = new BigInteger[n];
        for (int i = 0; i < n; i++) {
            a[i] = scalar();
        }
        return a;
    }

    @Test
    public void honestProofVerifies() {
        BigInteger[] a = vec(7);
        BigInteger r = scalar();
        byte[] c = PedersenVectorCommit.commit(a, r);
        VectorOpeningProof.Proof p = VectorOpeningProof.prove(a, r, c);
        assertTrue(VectorOpeningProof.verify(c, 7, p), "PoK honesto de la apertura verifica");
    }

    @Test
    public void tamperedMaskCommitmentRejected() {
        BigInteger[] a = vec(5);
        BigInteger r = scalar();
        byte[] c = PedersenVectorCommit.commit(a, r);
        VectorOpeningProof.Proof p = VectorOpeningProof.prove(a, r, c);
        VectorOpeningProof.Proof bad = new VectorOpeningProof.Proof(
                PedersenVectorCommit.commit(vec(5), scalar()), p.z, p.zr);
        assertFalse(VectorOpeningProof.verify(c, 5, bad), "T manipulado -> rechazado");
    }

    @Test
    public void tamperedResponseRejected() {
        BigInteger[] a = vec(5);
        BigInteger r = scalar();
        byte[] c = PedersenVectorCommit.commit(a, r);
        VectorOpeningProof.Proof p = VectorOpeningProof.prove(a, r, c);
        BigInteger[] z2 = p.z.clone();
        z2[1] = z2[1].add(BigInteger.ONE);
        assertFalse(VectorOpeningProof.verify(c, 5, new VectorOpeningProof.Proof(p.t, z2, p.zr)),
                "z manipulado -> rechazado");
        assertFalse(VectorOpeningProof.verify(c, 5, new VectorOpeningProof.Proof(p.t, p.z, p.zr.add(BigInteger.ONE))),
                "zr manipulado -> rechazado");
    }

    @Test
    public void proofForOneCommitmentDoesNotVerifyAnother() {
        BigInteger[] a = vec(6);
        BigInteger r = scalar();
        byte[] c = PedersenVectorCommit.commit(a, r);
        VectorOpeningProof.Proof p = VectorOpeningProof.prove(a, r, c);
        byte[] cOther = PedersenVectorCommit.commit(vec(6), scalar());
        assertFalse(VectorOpeningProof.verify(cOther, 6, p), "PoK de C no vale para otro commitment");
    }

    @Test
    public void wrongLengthRejected() {
        BigInteger[] a = vec(4);
        BigInteger r = scalar();
        byte[] c = PedersenVectorCommit.commit(a, r);
        VectorOpeningProof.Proof p = VectorOpeningProof.prove(a, r, c);
        assertFalse(VectorOpeningProof.verify(c, 5, p), "longitud declarada distinta -> rechazado");
    }
}
