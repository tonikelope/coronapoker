/*
 * Ejercita la thread-safety que las auditorias RAZONARON pero que ningun test ejecutaba: (1) la
 * instancia SHA-512 por hilo (ThreadLocal) de Dleq.challenge, y (2) la publicacion volatil de la tabla
 * memoizada perezosa (windowTable) que construye EdwardsPoint.scalarMul la primera vez sobre un punto.
 * N hilos golpean a la vez (barrera, sin sleeps); cada resultado debe ser correcto. Una publicacion
 * rota o una instancia compartida darian un resultado erroneo o una excepcion. No falla en falso.
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CryptoConcurrencyTest {

    private static final BigInteger L = EdwardsPoint.L;
    private static final int THREADS = 8;

    @BeforeAll
    public static void ensureRng() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
    }

    private static BigInteger randomScalar(Random r) {
        while (true) {
            byte[] raw = new byte[32];
            r.nextBytes(raw);
            raw[31] &= (byte) 0x1f;
            BigInteger s = RistrettoSRA.bytesToScalar(raw);
            if (s.signum() != 0 && s.compareTo(L) < 0) {
                return s;
            }
        }
    }

    /**
     * N hilos multiplican a la vez el MISMO punto recien creado (su tabla aun sin construir): fuerza la
     * carrera de construccion perezosa de windowTable. Todos deben devolver el mismo s*P que un gemelo.
     */
    @Test
    public void scalarMulSharedPointConcurrent() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            Random r = new Random(0xC0C0A11L);
            for (int iter = 0; iter < 150; iter++) {
                final BigInteger k = randomScalar(r);
                final BigInteger s = randomScalar(r);
                final EdwardsPoint fresh = EdwardsPoint.BASE.scalarMul(k);        // tabla propia SIN construir
                final EdwardsPoint expected = EdwardsPoint.BASE.scalarMul(k).scalarMul(s); // gemelo aparte
                final CyclicBarrier barrier = new CyclicBarrier(THREADS);
                List<Future<EdwardsPoint>> futs = new ArrayList<>();
                for (int t = 0; t < THREADS; t++) {
                    futs.add(pool.submit((Callable<EdwardsPoint>) () -> {
                        barrier.await();
                        return fresh.scalarMul(s);
                    }));
                }
                for (Future<EdwardsPoint> f : futs) {
                    assertTrue(f.get(60, TimeUnit.SECONDS).equalsPoint(expected),
                            "scalarMul concurrente incorrecto en iter " + iter);
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * N hilos hacen prove+verify de DLEQ a la vez, martilleando la SHA-512 ThreadLocal de Dleq.challenge.
     * Cada prueba valida debe verificar true y una manipulada false; una instancia compartida o corrupta
     * entre hilos daria un challenge erroneo -> verify false -> falla la asercion.
     */
    @Test
    public void dleqChallengeConcurrent() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            final EdwardsPoint g1 = EdwardsPoint.BASE;
            final EdwardsPoint g2 = EdwardsPoint.BASE.scalarMul(BigInteger.valueOf(9999991));
            Random r = new Random(0x0D1E0D1EL);
            for (int iter = 0; iter < 120; iter++) {
                final BigInteger k = randomScalar(r);
                final EdwardsPoint h1 = g1.scalarMul(k);
                final EdwardsPoint h2 = g2.scalarMul(k);
                final CyclicBarrier barrier = new CyclicBarrier(THREADS);
                List<Future<Boolean>> futs = new ArrayList<>();
                for (int t = 0; t < THREADS; t++) {
                    futs.add(pool.submit((Callable<Boolean>) () -> {
                        barrier.await();
                        byte[] proof = Dleq.prove(k, g1, h1, g2, h2);
                        boolean okValid = Dleq.verify(g1, h1, g2, h2, proof);
                        byte[] tampered = proof.clone();
                        tampered[0] ^= 0x01;
                        boolean okTampered = Dleq.verify(g1, h1, g2, h2, tampered);
                        return okValid && !okTampered;
                    }));
                }
                for (Future<Boolean> f : futs) {
                    assertTrue(f.get(60, TimeUnit.SECONDS), "DLEQ concurrente incorrecto en iter " + iter);
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
