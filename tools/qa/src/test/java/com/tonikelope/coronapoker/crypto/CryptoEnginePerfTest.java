/*
 * Perf de las dos palancas de optimizacion del motor (mapa de auditoria 20.99):
 *   #1 memoizacion de los limbs radix-2^51 en Fe25519 (operandos reutilizados: constantes D2/D, etc.)
 *   #2 memoizacion de la tabla de ventana de 4 bits en EdwardsPoint.scalarMul (BASE reutilizada).
 * Imprime tiempos (mediana, con warmup para el JIT) E incluye asserts de correccion: el resultado
 * cacheado DEBE coincidir con una referencia independiente (BigInteger para el campo, multiscalarMul
 * para el punto) y ser estable entre llamadas. La correccion de fondo la cubren ademas
 * Fe25519FastReduceTest / EdwardsPointTest / MultiScalarMulTest.
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.math.BigInteger;
import java.security.SecureRandom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CryptoEnginePerfTest {

    private static final BigInteger L = EdwardsPoint.L;
    private static final BigInteger P = Fe25519.P;

    @BeforeAll
    public static void ensureRng() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
    }

    private static BigInteger scalar() {
        return RistrettoSRA.bytesToScalar(RistrettoSRA.generateLockScalar());
    }

    private static long medianMs(long[] xs) {
        java.util.Arrays.sort(xs);
        return xs[xs.length / 2];
    }

    /** Palanca #2: BASE reutilizada -> su tabla de ventana se construye una vez y se reusa. */
    @Test
    public void baseScalarMulCost() {
        int iters = 2000;
        BigInteger[] s = new BigInteger[iters];
        for (int i = 0; i < iters; i++) {
            s[i] = scalar();
        }

        // Correccion: BASE.scalarMul == multiscalarMul([s],[BASE]) (camino independiente, tabla NO cacheada).
        for (int i = 0; i < 8; i++) {
            EdwardsPoint viaScalar = EdwardsPoint.BASE.scalarMul(s[i]);
            EdwardsPoint viaMsm = EdwardsPoint.multiscalarMul(
                    new BigInteger[]{s[i]}, new EdwardsPoint[]{EdwardsPoint.BASE});
            assertTrue(viaScalar.equalsPoint(viaMsm), "BASE.scalarMul coincide con multiscalarMul (iter " + i + ")");
        }
        // Estabilidad de la cache: dos llamadas con el mismo escalar dan el mismo punto.
        assertTrue(EdwardsPoint.BASE.scalarMul(s[0]).equalsPoint(EdwardsPoint.BASE.scalarMul(s[0])),
                "cache estable entre llamadas");

        // Warmup JIT.
        EdwardsPoint acc = EdwardsPoint.IDENTITY;
        for (int w = 0; w < 200; w++) {
            acc = EdwardsPoint.BASE.scalarMul(s[w % iters]);
        }

        int runs = 7;
        long[] ms = new long[runs];
        for (int r = 0; r < runs; r++) {
            long t0 = System.nanoTime();
            EdwardsPoint sink = EdwardsPoint.IDENTITY;
            for (int i = 0; i < iters; i++) {
                sink = EdwardsPoint.BASE.scalarMul(s[i]);
            }
            long t1 = System.nanoTime();
            assertTrue(sink != null);
            ms[r] = (t1 - t0) / 1_000_000;
        }
        System.out.println("[PERF] BASE.scalarMul x" + iters + " (mediana de " + runs + "): " + medianMs(ms) + "ms");
    }

    /** Palanca #1: operando constante reutilizado (D2) en el lazo caliente de EdwardsPoint.add. */
    @Test
    public void pointAddCost() {
        int iters = 200_000;
        EdwardsPoint p = EdwardsPoint.BASE.scalarMul(scalar());
        EdwardsPoint q = EdwardsPoint.BASE.scalarMul(scalar());

        // Warmup.
        EdwardsPoint acc = EdwardsPoint.IDENTITY;
        for (int w = 0; w < 20_000; w++) {
            acc = acc.add(p);
        }

        int runs = 7;
        long[] ms = new long[runs];
        for (int r = 0; r < runs; r++) {
            EdwardsPoint sink = q;
            long t0 = System.nanoTime();
            for (int i = 0; i < iters; i++) {
                sink = sink.add(p);
            }
            long t1 = System.nanoTime();
            assertTrue(sink != null);
            ms[r] = (t1 - t0) / 1_000_000;
        }
        System.out.println("[PERF] EdwardsPoint.add x" + iters + " (mediana de " + runs + "): " + medianMs(ms) + "ms");
    }

    /** Palanca #1: field-mul con operando reutilizado, contra referencia BigInteger. */
    @Test
    public void fieldMulCost() {
        int iters = 1_000_000;
        Fe25519 konst = Fe25519.of(new BigInteger(
                "37095705934669439343138083508754565189542113879843219016388785533085940283555"));
        Fe25519 x = Fe25519.of(scalar());

        // Correccion: x.mul(konst) == (x*konst mod P) por BigInteger.
        BigInteger ref = x.toBigInteger().multiply(konst.toBigInteger()).mod(P);
        assertEquals(ref, x.mul(konst).toBigInteger(), "field mul coincide con la referencia BigInteger");

        // Warmup.
        Fe25519 acc = Fe25519.ONE;
        for (int w = 0; w < 50_000; w++) {
            acc = acc.mul(konst);
        }

        int runs = 7;
        long[] ms = new long[runs];
        for (int r = 0; r < runs; r++) {
            Fe25519 sink = x;
            long t0 = System.nanoTime();
            for (int i = 0; i < iters; i++) {
                sink = sink.mul(konst);
            }
            long t1 = System.nanoTime();
            assertTrue(sink != null);
            ms[r] = (t1 - t0) / 1_000_000;
        }
        System.out.println("[PERF] Fe25519.mul(konst) x" + iters + " (mediana de " + runs + "): " + medianMs(ms) + "ms");
    }
}
