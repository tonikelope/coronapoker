/*
 * Bayer-Groth ladrillo: argumento de suma ponderada Q = sum f_i*B_i (f comprometido individualmente).
 * Pieza que ata la permutacion a los puntos cifrados. Clave adversaria: NO se puede probar un Q falso.
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.math.BigInteger;
import java.security.SecureRandom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WeightedSumArgumentTest {

    @BeforeAll
    public static void ensureRng() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
    }

    private static BigInteger scalar() {
        return RistrettoSRA.bytesToScalar(RistrettoSRA.generateLockScalar());
    }

    private static EdwardsPoint[] randomBases(int n) {
        EdwardsPoint[] b = new EdwardsPoint[n];
        for (int i = 0; i < n; i++) {
            b[i] = EdwardsPoint.BASE.scalarMul(scalar());
        }
        return b;
    }

    private static byte[][] commitWeights(BigInteger[] f, BigInteger[] sOut) {
        byte[][] cf = new byte[f.length][];
        for (int i = 0; i < f.length; i++) {
            sOut[i] = scalar();
            cf[i] = MultiplicationProof.commitScalar(f[i], sOut[i]);
        }
        return cf;
    }

    @Test
    public void honestWeightedSumVerifies() {
        int n = 8;
        BigInteger[] f = new BigInteger[n];
        for (int i = 0; i < n; i++) {
            f[i] = scalar();
        }
        BigInteger[] s = new BigInteger[n];
        byte[][] cf = commitWeights(f, s);
        EdwardsPoint[] b = randomBases(n);
        EdwardsPoint q = WeightedSumArgument.msm(f, b);
        WeightedSumArgument.Proof p = WeightedSumArgument.prove(f, s, cf, b, q);
        assertTrue(WeightedSumArgument.verify(cf, b, q, p), "Q = sum f_i*B_i honesto -> verifica");
    }

    @Test
    public void cannotProveWrongQ() {
        int n = 7;
        BigInteger[] f = new BigInteger[n];
        for (int i = 0; i < n; i++) {
            f[i] = scalar();
        }
        BigInteger[] s = new BigInteger[n];
        byte[][] cf = commitWeights(f, s);
        EdwardsPoint[] b = randomBases(n);
        EdwardsPoint qWrong = WeightedSumArgument.msm(f, b).add(EdwardsPoint.BASE);
        WeightedSumArgument.Proof p = WeightedSumArgument.prove(f, s, cf, b, qWrong);
        assertFalse(WeightedSumArgument.verify(cf, b, qWrong, p),
                "no se puede probar un Q que no es la suma ponderada de los pesos comprometidos");
    }

    @Test
    public void proofForOneQDoesNotVerifyAnother() {
        int n = 6;
        BigInteger[] f = new BigInteger[n];
        for (int i = 0; i < n; i++) {
            f[i] = scalar();
        }
        BigInteger[] s = new BigInteger[n];
        byte[][] cf = commitWeights(f, s);
        EdwardsPoint[] b = randomBases(n);
        EdwardsPoint q = WeightedSumArgument.msm(f, b);
        WeightedSumArgument.Proof p = WeightedSumArgument.prove(f, s, cf, b, q);
        assertFalse(WeightedSumArgument.verify(cf, b, q.add(EdwardsPoint.BASE), p), "la prueba de Q no vale para otro Q");
    }

    @Test
    public void tamperedResponseRejected() {
        int n = 6;
        BigInteger[] f = new BigInteger[n];
        for (int i = 0; i < n; i++) {
            f[i] = scalar();
        }
        BigInteger[] s = new BigInteger[n];
        byte[][] cf = commitWeights(f, s);
        EdwardsPoint[] b = randomBases(n);
        EdwardsPoint q = WeightedSumArgument.msm(f, b);
        WeightedSumArgument.Proof p = WeightedSumArgument.prove(f, s, cf, b, q);
        BigInteger[] z2 = p.z.clone();
        z2[3] = z2[3].add(BigInteger.ONE);
        assertFalse(WeightedSumArgument.verify(cf, b, q, new WeightedSumArgument.Proof(p.t, p.tq, z2, p.zs)),
                "z manipulado -> rechazado");
    }

    @Test
    public void tamperedWeightCommitmentRejected() {
        int n = 6;
        BigInteger[] f = new BigInteger[n];
        for (int i = 0; i < n; i++) {
            f[i] = scalar();
        }
        BigInteger[] s = new BigInteger[n];
        byte[][] cf = commitWeights(f, s);
        EdwardsPoint[] b = randomBases(n);
        EdwardsPoint q = WeightedSumArgument.msm(f, b);
        WeightedSumArgument.Proof p = WeightedSumArgument.prove(f, s, cf, b, q);
        byte[][] cfTampered = cf.clone();
        cfTampered[2] = MultiplicationProof.commitScalar(scalar(), scalar());
        assertFalse(WeightedSumArgument.verify(cfTampered, b, q, p), "peso comprometido cambiado -> rechazado");
    }
}
