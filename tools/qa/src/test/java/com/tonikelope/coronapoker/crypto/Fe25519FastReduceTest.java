/*
 * Fuzz de la aritmetica rapida de Fe25519 (mul/sqr con reduccion 2^255 ≡ 19, y add/sub/negate con
 * reduccion condicional): se cruza contra BigInteger (el oraculo correcto) en cientos de miles de
 * valores aleatorios + casos limite. Un solo fallo de carry/reduccion en cualquier valor lo caza.
 * Garantiza que la optimizacion da el MISMO resultado que antes.
 */
package com.tonikelope.coronapoker.crypto;

import java.math.BigInteger;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Fe25519FastReduceTest {

    private static BigInteger toBig(Fe25519 f) {
        byte[] le = f.toBytes();
        byte[] be = new byte[le.length];
        for (int i = 0; i < le.length; i++) {
            be[i] = le[le.length - 1 - i];
        }
        return new BigInteger(1, be);
    }

    private static void checkMul(BigInteger a, BigInteger b) {
        BigInteger expected = a.mod(Fe25519.P).multiply(b.mod(Fe25519.P)).mod(Fe25519.P);
        assertEquals(expected, toBig(Fe25519.of(a).mul(Fe25519.of(b))),
                "mul(" + a + ", " + b + ")");
    }

    private static void checkSqr(BigInteger a) {
        BigInteger expected = a.mod(Fe25519.P).multiply(a.mod(Fe25519.P)).mod(Fe25519.P);
        assertEquals(expected, toBig(Fe25519.of(a).sqr()), "sqr(" + a + ")");
    }

    @Test
    public void mulFuzzMatchesBigInteger() {
        Random r = new Random(0xC0FFEEL);
        for (int i = 0; i < 200_000; i++) {
            checkMul(new BigInteger(256, r), new BigInteger(256, r));
        }
    }

    @Test
    public void sqrFuzzMatchesBigInteger() {
        Random r = new Random(0xBEEFL);
        for (int i = 0; i < 100_000; i++) {
            checkSqr(new BigInteger(256, r));
        }
    }

    @Test
    public void addSubNegateFuzzMatchesBigInteger() {
        // add/sub/negate usan resta/suma condicional sobre operandos canonicos en [0,P) en vez de
        // mod(): mismo resultado que la referencia BigInteger, y el resultado queda canonico
        // (round-trip toBytes -> valor en [0,P)).
        BigInteger P = Fe25519.P;
        Random r = new Random(0xFE25519L);
        for (int i = 0; i < 200_000; i++) {
            BigInteger a = new BigInteger(256, r);
            BigInteger b = new BigInteger(256, r);
            Fe25519 fa = Fe25519.of(a);
            Fe25519 fb = Fe25519.of(b);
            assertEquals(a.mod(P).add(b.mod(P)).mod(P), toBig(fa.add(fb)), "add(" + a + ", " + b + ")");
            assertEquals(a.mod(P).subtract(b.mod(P)).mod(P), toBig(fa.sub(fb)), "sub(" + a + ", " + b + ")");
            assertEquals(a.mod(P).negate().mod(P), toBig(fa.negate()), "negate(" + a + ")");
        }
    }

    @Test
    public void addSubNegateEdgeCases() {
        BigInteger P = Fe25519.P;
        BigInteger[] vals = {
            BigInteger.ZERO, BigInteger.ONE,
            P.subtract(BigInteger.ONE), P.subtract(BigInteger.TWO),
            P.shiftRight(1), BigInteger.valueOf(19)
        };
        for (BigInteger a : vals) {
            for (BigInteger b : vals) {
                Fe25519 fa = Fe25519.of(a);
                Fe25519 fb = Fe25519.of(b);
                assertEquals(a.add(b).mod(P), toBig(fa.add(fb)), "add(" + a + ", " + b + ")");
                assertEquals(a.subtract(b).mod(P), toBig(fa.sub(fb)), "sub(" + a + ", " + b + ")");
            }
            assertEquals(a.negate().mod(P), toBig(Fe25519.of(a).negate()), "negate(" + a + ")");
        }
    }

    @Test
    public void conditionalSubtractDeterministicVectors() {
        // Productos cuyo valor reducido cae exactamente en [P, 2^255) antes de la resta condicional
        // final: si esa rama faltara, el resultado quedaria no-canonico (P+1 en vez de 1) y el
        // oraculo BigInteger lo caza. El fuzz aleatorio no llega aqui (probabilidad ~19/2^255).
        BigInteger P = Fe25519.P;
        checkMul(BigInteger.TWO, P.add(BigInteger.ONE).shiftRight(1));            // 2 * (P+1)/2 = P+1 ≡ 1
        checkMul(BigInteger.valueOf(3), P.add(BigInteger.TWO).divide(BigInteger.valueOf(3))); // = P+2 ≡ 2
        assertEquals(BigInteger.ZERO, P.add(BigInteger.TWO).mod(BigInteger.valueOf(3)),
                "P+2 divisible entre 3 (sanity del vector)");
    }

    @Test
    public void carryNormalizeDenormalizedWrapCorner() {
        // Corner adversario de la cadena de carries: tras el wrap ×19, l0 puede quedar con el bit 51
        // puesto a la vez que l1 queda impar (ripple de limbs saturados exactos) — el reensamblado OR
        // antiguo perdia un carry ahi. Sumas de columna craftadas para caer exactamente en ese caso:
        // h0..h3 = 2^51-1, h4 = T*2^51 + (2^51-1) con 19T = 2^53-10, que fuerza l0 = 2^51+8 y l1 = 3
        // antes del segundo pase ligero. Se cruza contra el oraculo BigInteger.
        BigInteger M = BigInteger.ONE.shiftLeft(51).subtract(BigInteger.ONE); // 2^51 - 1
        BigInteger t = BigInteger.ONE.shiftLeft(53).subtract(BigInteger.TEN);
        assertEquals(BigInteger.ZERO, t.mod(BigInteger.valueOf(19)), "2^53-10 divisible entre 19 (sanity)");
        BigInteger h4 = t.divide(BigInteger.valueOf(19)).shiftLeft(51).add(M);

        BigInteger[] h = {M, M, M, M, h4};
        long[] lohi = new long[10];
        for (int i = 0; i < 5; i++) {
            lohi[2 * i] = h[i].longValue();                 // lo (64 bits bajos)
            lohi[2 * i + 1] = h[i].shiftRight(64).longValue(); // hi
        }
        long[] limbs = Fe25519.carryNormalize(lohi[0], lohi[1], lohi[2], lohi[3], lohi[4],
                lohi[5], lohi[6], lohi[7], lohi[8], lohi[9]);

        BigInteger expected = BigInteger.ZERO;
        for (int i = 0; i < 5; i++) {
            expected = expected.add(h[i].shiftLeft(51 * i));
        }
        expected = expected.mod(Fe25519.P);

        BigInteger got = BigInteger.ZERO;
        for (int i = 0; i < 5; i++) {
            long li = limbs[i];
            assertTrue(li >= 0 && li < (1L << 51), "limb " + i + " canonico (< 2^51)");
            got = got.add(BigInteger.valueOf(li).shiftLeft(51 * i));
        }
        assertEquals(expected, got, "carryNormalize en el corner del wrap denormalizado");
    }

    @Test
    public void edgeCases() {
        BigInteger P = Fe25519.P;
        BigInteger[] vals = {
            BigInteger.ZERO, BigInteger.ONE, BigInteger.TWO,
            P.subtract(BigInteger.ONE),                  // p-1
            P.subtract(BigInteger.TWO),                  // p-2
            BigInteger.ONE.shiftLeft(255).subtract(BigInteger.ONE), // 2^255-1 (> p)
            BigInteger.ONE.shiftLeft(254),
            BigInteger.valueOf(19), BigInteger.valueOf(38)
        };
        for (BigInteger a : vals) {
            checkSqr(a);
            for (BigInteger b : vals) {
                checkMul(a, b);
            }
        }
    }
}
