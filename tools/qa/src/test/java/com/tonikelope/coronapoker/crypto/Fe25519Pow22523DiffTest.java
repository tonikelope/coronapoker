/*
 * Diferencial de la cadena de adicion pow22523 (this^((p-5)/8) = this^(2^252-3)) contra el oraculo
 * BigInteger.modPow: la exponenciacion de sqrtRatioM1 pasa del modPow generico al kernel rapido
 * (sqr/mul en limbs) y debe dar el MISMO resultado bit a bit en todo elemento de campo. Un solo
 * fallo de la cadena en cualquier valor lo caza. Garantiza que la optimizacion no cambia el resultado.
 */
package com.tonikelope.coronapoker.crypto;

import java.math.BigInteger;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class Fe25519Pow22523DiffTest {

    /** (p-5)/8 = 2^252 - 3, el exponente que sqrtRatioM1 aplica en cada encode/decode. */
    private static final BigInteger EXP =
            Fe25519.P.subtract(BigInteger.valueOf(5)).divide(BigInteger.valueOf(8));

    private static void check(Fe25519 x) {
        assertArrayEquals(x.pow(EXP).toBytes(), x.pow22523().toBytes());
    }

    @Test
    public void pow22523MatchesModPow() {
        Random r = new Random(0x22523L);
        for (int i = 0; i < 100_000; i++) {
            check(Fe25519.of(new BigInteger(256, r)));
        }
    }

    @Test
    public void pow22523EdgeCases() {
        check(Fe25519.ZERO);
        check(Fe25519.ONE);
        check(Fe25519.of(Fe25519.P.subtract(BigInteger.ONE))); // -1 mod p
        check(Fe25519.of(BigInteger.TWO));
        check(Fe25519.of(Fe25519.SQRT_M1));
    }
}
