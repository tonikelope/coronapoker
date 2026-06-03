/*
 * Coste real del shuffle Bayer-Groth para un mazo de 52 cartas (el motivo del pivote: que NO pegue
 * el CPU como el cut-and-choose). No es un test de correccion: imprime tiempos prove/verify.
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.math.BigInteger;
import java.security.SecureRandom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ShuffleArgumentPerfTest {

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
    public void deck52ThreeStepsCost() {
        int n = 52;
        EdwardsPoint[] a = new EdwardsPoint[n];
        for (int i = 0; i < n; i++) {
            a[i] = EdwardsPoint.BASE.scalarMul(scalar());
        }
        int[] pi = new int[n];
        for (int i = 0; i < n; i++) {
            pi[i] = (i * 7 + 11) % n;
        }
        // garantizar permutacion: (i*7+11) mod 52 es biyeccion porque gcd(7,52)=1
        BigInteger k = scalar();
        EdwardsPoint[] b = new EdwardsPoint[n];
        for (int i = 0; i < n; i++) {
            b[i] = a[pi[i]].scalarMul(k.mod(L));
        }

        // Warmup generoso para que el JIT compile el hot path antes de medir (si no, el ruido domina).
        ShuffleArgument.Proof warm = null;
        for (int w = 0; w < 5; w++) {
            warm = ShuffleArgument.prove(a, b, pi, k);
            assertTrue(ShuffleArgument.verify(a, b, warm), "warmup verifica");
        }

        int iters = 11;
        long[] proveMs = new long[iters];
        long[] verifyMs = new long[iters];
        for (int s = 0; s < iters; s++) {
            long t0 = System.nanoTime();
            ShuffleArgument.Proof p = ShuffleArgument.prove(a, b, pi, k);
            long t1 = System.nanoTime();
            boolean ok = ShuffleArgument.verify(a, b, p);
            long t2 = System.nanoTime();
            assertTrue(ok, "iter " + s + " verifica");
            proveMs[s] = (t1 - t0) / 1_000_000;
            verifyMs[s] = (t2 - t1) / 1_000_000;
        }
        java.util.Arrays.sort(proveMs);
        java.util.Arrays.sort(verifyMs);
        System.out.println("[PERF] shuffle n=52 (mediana de " + iters + ", warmup x5): prove="
                + proveMs[iters / 2] + "ms/paso, verify=" + verifyMs[iters / 2] + "ms/paso");
    }
}
