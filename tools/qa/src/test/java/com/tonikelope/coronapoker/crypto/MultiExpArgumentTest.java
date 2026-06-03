/*
 * Bayer-Groth ladrillo: argumento de multi-exponenciacion (Sigma enlazado).
 * Clave adversaria: NO se puede probar un Q falso (el multi-exp queda atado al vector comprometido)
 * + manipulaciones rechazadas.
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.math.BigInteger;
import java.security.SecureRandom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MultiExpArgumentTest {

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

    private static EdwardsPoint[] randomBases(int n) {
        EdwardsPoint[] a = new EdwardsPoint[n];
        for (int i = 0; i < n; i++) {
            a[i] = EdwardsPoint.BASE.scalarMul(scalar());
        }
        return a;
    }

    @Test
    public void honestProofVerifies() {
        int n = 6;
        BigInteger[] x = vec(n);
        BigInteger r = scalar();
        byte[] cx = PedersenVectorCommit.commit(x, r);
        EdwardsPoint[] a = randomBases(n);
        EdwardsPoint q = MultiExpArgument.msm(x, a);
        MultiExpArgument.Proof p = MultiExpArgument.prove(x, r, cx, a, q);
        assertTrue(MultiExpArgument.verify(cx, a, q, p), "Q = sum x_i*A_i honesto -> verifica");
    }

    @Test
    public void cannotProveWrongQ() {
        int n = 6;
        BigInteger[] x = vec(n);
        BigInteger r = scalar();
        byte[] cx = PedersenVectorCommit.commit(x, r);
        EdwardsPoint[] a = randomBases(n);
        EdwardsPoint qWrong = MultiExpArgument.msm(vec(n), a); // multi-exp de OTRO vector
        // Aunque el prover lo intente con su x real, la prueba para un Q falso no verifica.
        MultiExpArgument.Proof p = MultiExpArgument.prove(x, r, cx, a, qWrong);
        assertFalse(MultiExpArgument.verify(cx, a, qWrong, p),
                "no se puede probar un Q que no es sum x_i*A_i del vector comprometido");
    }

    @Test
    public void proofForOneQDoesNotVerifyAnother() {
        int n = 5;
        BigInteger[] x = vec(n);
        BigInteger r = scalar();
        byte[] cx = PedersenVectorCommit.commit(x, r);
        EdwardsPoint[] a = randomBases(n);
        EdwardsPoint q = MultiExpArgument.msm(x, a);
        MultiExpArgument.Proof p = MultiExpArgument.prove(x, r, cx, a, q);
        EdwardsPoint qOther = q.add(EdwardsPoint.BASE);
        assertFalse(MultiExpArgument.verify(cx, a, qOther, p), "la prueba de Q no vale para otro Q");
    }

    @Test
    public void tamperedResponseRejected() {
        int n = 5;
        BigInteger[] x = vec(n);
        BigInteger r = scalar();
        byte[] cx = PedersenVectorCommit.commit(x, r);
        EdwardsPoint[] a = randomBases(n);
        EdwardsPoint q = MultiExpArgument.msm(x, a);
        MultiExpArgument.Proof p = MultiExpArgument.prove(x, r, cx, a, q);
        BigInteger[] z2 = p.z.clone();
        z2[0] = z2[0].add(BigInteger.ONE);
        assertFalse(MultiExpArgument.verify(cx, a, q, new MultiExpArgument.Proof(p.tc, p.tq, z2, p.zr)),
                "z manipulado -> rechazado");
    }

    @Test
    public void wrongCommitmentRejected() {
        int n = 5;
        BigInteger[] x = vec(n);
        BigInteger r = scalar();
        byte[] cx = PedersenVectorCommit.commit(x, r);
        EdwardsPoint[] a = randomBases(n);
        EdwardsPoint q = MultiExpArgument.msm(x, a);
        MultiExpArgument.Proof p = MultiExpArgument.prove(x, r, cx, a, q);
        byte[] cxOther = PedersenVectorCommit.commit(vec(n), scalar());
        assertFalse(MultiExpArgument.verify(cxOther, a, q, p), "commitment distinto -> rechazado");
    }
}
