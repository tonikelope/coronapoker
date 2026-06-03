/*
 * Fuzz de la reduccion rapida (2^255 ≡ 19) de Fe25519.mul/sqr: se cruza contra BigInteger (el
 * oraculo correcto) en 200.000 valores aleatorios + casos limite. Un solo fallo de carry/reduccion
 * en cualquier valor lo caza. Garantiza que la optimizacion da el MISMO resultado que antes.
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
