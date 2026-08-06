/*
 * Diferencial de la escalera wNAF de EdwardsPoint.scalarMul contra un doble-y-suma ingenuo (la
 * definicion de la multiplicacion escalar, trivialmente correcta): mismo elemento de grupo para todo
 * escalar y todo punto. Un fallo de la recodificacion NAF, de la tabla de impares o del signo lo caza.
 */
package com.tonikelope.coronapoker.crypto;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ScalarMulWnafDiffTest {

    private static final BigInteger L = EdwardsPoint.L;

    /** s*P por doble-y-suma bit a bit (MSB->LSB); oraculo obviamente correcto. s >= 0. */
    private static EdwardsPoint naive(EdwardsPoint p, BigInteger s) {
        EdwardsPoint r = EdwardsPoint.IDENTITY;
        for (int i = s.bitLength() - 1; i >= 0; i--) {
            r = r.dbl();
            if (s.testBit(i)) {
                r = r.add(p);
            }
        }
        return r;
    }

    private static void check(EdwardsPoint p, BigInteger s) {
        assertTrue(p.scalarMul(s).equalsPoint(naive(p, s)), "scalarMul mismatch, s=" + s);
    }

    @Test
    public void wnafMatchesNaiveFuzz() {
        Random r = new Random(0x5CA1A5L);
        for (int i = 0; i < 10_000; i++) {
            EdwardsPoint p = naive(EdwardsPoint.BASE, new BigInteger(252, r)); // punto de grupo valido
            check(p, new BigInteger(256, r)); // escalar aleatorio, puede exceder L
        }
    }

    @Test
    public void wnafEdgeCases() {
        EdwardsPoint[] pts = {
            EdwardsPoint.IDENTITY, EdwardsPoint.BASE, naive(EdwardsPoint.BASE, BigInteger.valueOf(7))
        };
        BigInteger[] scalars = {
            BigInteger.ZERO, BigInteger.ONE, BigInteger.TWO, BigInteger.valueOf(15), BigInteger.valueOf(16),
            BigInteger.valueOf(17), BigInteger.valueOf(31), BigInteger.valueOf(32), BigInteger.valueOf(33),
            L.subtract(BigInteger.ONE), L, L.add(BigInteger.ONE)
        };
        for (EdwardsPoint p : pts) {
            for (BigInteger s : scalars) {
                check(p, s);
            }
        }
        // Ademas de la igualdad proyectiva, encode canonico bit-identico para BASE*s.
        for (BigInteger s : scalars) {
            assertArrayEquals(Ristretto255.encode(EdwardsPoint.BASE.scalarMul(s)),
                    Ristretto255.encode(naive(EdwardsPoint.BASE, s)), "encode mismatch, s=" + s);
        }
    }

    /**
     * N hilos multiplican a la vez el MISMO punto recien creado (su nafTable aun sin construir): fuerza
     * la carrera de construccion perezosa de la tabla de impares wNAF (volatile). Todos deben devolver el
     * mismo s*P que el oraculo doble-y-suma (que no toca nafTable). Barrera sin sleeps, timeout defensivo.
     */
    @Test
    public void scalarMulNafTableConcurrent() throws Exception {
        final int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            Random r = new Random(0xC0C0A11L);
            for (int iter = 0; iter < 150; iter++) {
                final BigInteger k = new BigInteger(252, r).add(BigInteger.ONE);
                final BigInteger s = new BigInteger(252, r).add(BigInteger.ONE);
                final EdwardsPoint fresh = naive(EdwardsPoint.BASE, k); // nafTable propia SIN construir
                final EdwardsPoint expected = naive(fresh, s);          // oraculo: no llama a scalarMul
                final CyclicBarrier barrier = new CyclicBarrier(threads);
                List<Future<EdwardsPoint>> futs = new ArrayList<>();
                for (int t = 0; t < threads; t++) {
                    futs.add(pool.submit((Callable<EdwardsPoint>) () -> {
                        barrier.await();
                        return fresh.scalarMul(s);
                    }));
                }
                for (Future<EdwardsPoint> f : futs) {
                    assertTrue(f.get(60, TimeUnit.SECONDS).equalsPoint(expected),
                            "scalarMul wNAF concurrente incorrecto en iter " + iter);
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
