/*
 * Motor de barajado — medida de coste REAL (sin suponer). Un paso de cascada = 52 cartas, K=128.
 * Mide prove+verify para saber si es viable en setup de mano o hay que optimizar (K, paralelizar,
 * multi-scalar). El "Time elapsed" de surefire es el dato.
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.math.BigInteger;
import java.security.SecureRandom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CutChooseShufflePerfTest {

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
    public void oneCascadeStep52Cards128Rounds() {
        int n = 52, rounds = 128;
        EdwardsPoint[] a = new EdwardsPoint[n];
        for (int i = 0; i < n; i++) {
            a[i] = EdwardsPoint.BASE.scalarMul(scalar());
        }
        int[] pi = DeckTransform.randomPermutation(n);
        BigInteger k = scalar();
        EdwardsPoint[] b = DeckTransform.apply(a, pi, k);

        long t0 = System.nanoTime();
        CutChooseShuffleProof.Proof proof = CutChooseShuffleProof.prove(a, b, pi, k, rounds);
        long t1 = System.nanoTime();
        boolean ok = CutChooseShuffleProof.verify(a, b, proof);
        long t2 = System.nanoTime();

        System.out.println("[PERF] 52 cartas, 128 rondas: prove=" + ((t1 - t0) / 1_000_000)
                + "ms verify=" + ((t2 - t1) / 1_000_000) + "ms");
        assertTrue(ok, "el barajado honesto de 52 cartas verifica");
    }
}
