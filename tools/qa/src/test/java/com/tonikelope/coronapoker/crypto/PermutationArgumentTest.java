/*
 * Bayer-Groth ladrillo: argumento de permutacion (d' comprometido es permutacion de d publico).
 * Corazon combinatorio del shuffle. Clave adversaria: multiconjunto distinto / duplicado -> rechazado.
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PermutationArgumentTest {

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

    /** Build commitments for a vector with fresh blindings; returns {commitments, blindings}. */
    private static byte[][] commitVec(BigInteger[] v, BigInteger[] sOut) {
        byte[][] c = new byte[v.length][];
        for (int i = 0; i < v.length; i++) {
            sOut[i] = scalar();
            c[i] = MultiplicationProof.commitScalar(v[i], sOut[i]);
        }
        return c;
    }

    private static int[] shuffledIndices(int n) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            idx.add(i);
        }
        // deterministico pero no identidad (rotacion + swap) para no depender de RNG en el test
        Collections.rotate(idx, 3);
        Collections.swap(idx, 0, n - 1);
        int[] r = new int[n];
        for (int i = 0; i < n; i++) {
            r[i] = idx.get(i);
        }
        return r;
    }

    @Test
    public void honestPermutationVerifies() {
        int n = 8;
        BigInteger[] d = new BigInteger[n];
        for (int i = 0; i < n; i++) {
            d[i] = scalar();
        }
        int[] perm = shuffledIndices(n);
        BigInteger[] dprime = new BigInteger[n];
        for (int i = 0; i < n; i++) {
            dprime[i] = d[perm[i]];
        }
        BigInteger[] s = new BigInteger[n];
        byte[][] cdprime = commitVec(dprime, s);
        PermutationArgument.Proof p = PermutationArgument.prove(dprime, s, cdprime, d);
        assertTrue(PermutationArgument.verify(cdprime, d, p), "d' permutacion de d -> verifica");
    }

    @Test
    public void identityPermutationVerifies() {
        int n = 5;
        BigInteger[] d = new BigInteger[n];
        for (int i = 0; i < n; i++) {
            d[i] = scalar();
        }
        BigInteger[] dprime = d.clone();
        BigInteger[] s = new BigInteger[n];
        byte[][] cdprime = commitVec(dprime, s);
        PermutationArgument.Proof p = PermutationArgument.prove(dprime, s, cdprime, d);
        assertTrue(PermutationArgument.verify(cdprime, d, p), "identidad es permutacion -> verifica");
    }

    @Test
    public void nonPermutationRejected() {
        int n = 8;
        BigInteger[] d = new BigInteger[n];
        for (int i = 0; i < n; i++) {
            d[i] = scalar();
        }
        // d' = d salvo un elemento cambiado -> multiconjunto distinto
        BigInteger[] dprime = d.clone();
        dprime[4] = scalar();
        BigInteger[] s = new BigInteger[n];
        byte[][] cdprime = commitVec(dprime, s);
        PermutationArgument.Proof p = PermutationArgument.prove(dprime, s, cdprime, d);
        assertFalse(PermutationArgument.verify(cdprime, d, p), "multiconjunto distinto -> rechazado");
    }

    @Test
    public void duplicateInsteadOfPermutationRejected() {
        int n = 6;
        BigInteger[] d = new BigInteger[n];
        for (int i = 0; i < n; i++) {
            d[i] = scalar();
        }
        // d' repite d[0] en la posicion de d[1] (duplicado: el ataque smuggle es justo esto)
        BigInteger[] dprime = d.clone();
        dprime[1] = d[0];
        BigInteger[] s = new BigInteger[n];
        byte[][] cdprime = commitVec(dprime, s);
        PermutationArgument.Proof p = PermutationArgument.prove(dprime, s, cdprime, d);
        assertFalse(PermutationArgument.verify(cdprime, d, p), "duplicado -> rechazado");
    }

    @Test
    public void tamperedCommitmentRejected() {
        int n = 6;
        BigInteger[] d = new BigInteger[n];
        for (int i = 0; i < n; i++) {
            d[i] = scalar();
        }
        int[] perm = shuffledIndices(n);
        BigInteger[] dprime = new BigInteger[n];
        for (int i = 0; i < n; i++) {
            dprime[i] = d[perm[i]];
        }
        BigInteger[] s = new BigInteger[n];
        byte[][] cdprime = commitVec(dprime, s);
        PermutationArgument.Proof p = PermutationArgument.prove(dprime, s, cdprime, d);
        byte[][] tampered = cdprime.clone();
        tampered[2] = MultiplicationProof.commitScalar(scalar(), scalar());
        assertFalse(PermutationArgument.verify(tampered, d, p), "commitment manipulado -> rechazado");
    }
}
