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

        // warmup
        ShuffleArgument.Proof warm = ShuffleArgument.prove(a, b, pi, k);
        assertTrue(ShuffleArgument.verify(a, b, warm), "warmup verifica");

        int steps = 3; // cascada tipica: host + 2 clientes
        long tProve = 0, tVerify = 0;
        for (int s = 0; s < steps; s++) {
            long t0 = System.nanoTime();
            ShuffleArgument.Proof p = ShuffleArgument.prove(a, b, pi, k);
            long t1 = System.nanoTime();
            boolean ok = ShuffleArgument.verify(a, b, p);
            long t2 = System.nanoTime();
            assertTrue(ok, "paso " + s + " verifica");
            tProve += (t1 - t0);
            tVerify += (t2 - t1);
        }
        System.out.println("[PERF] shuffle n=52, " + steps + " pasos: prove total="
                + (tProve / 1_000_000) + "ms (" + (tProve / 1_000_000 / steps) + "ms/paso), verify total="
                + (tVerify / 1_000_000) + "ms (" + (tVerify / 1_000_000 / steps) + "ms/paso)");
    }
}
