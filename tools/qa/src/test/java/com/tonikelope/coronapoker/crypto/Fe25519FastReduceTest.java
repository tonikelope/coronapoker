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
