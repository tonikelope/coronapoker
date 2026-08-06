/*
 * Diferencial de la escalera wNAF de EdwardsPoint.scalarMul contra un doble-y-suma ingenuo (la
 * definicion de la multiplicacion escalar, trivialmente correcta): mismo elemento de grupo para todo
 * escalar y todo punto. Un fallo de la recodificacion NAF, de la tabla de impares o del signo lo caza.
 */
package com.tonikelope.coronapoker.crypto;

import java.math.BigInteger;
import java.util.Random;
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
}
