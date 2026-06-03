/*
 * Optimizacion MSM (Straus): EdwardsPoint.multiscalarMul DEBE dar resultado bit-identico a la suma
 * ingenua sum points[i]*scalars[i]. Fuzz masivo + edge cases. Cripto: cero tolerancia a divergencia.
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.math.BigInteger;
import java.security.SecureRandom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MultiScalarMulTest {

    private static final BigInteger L = EdwardsPoint.L;
    private static SecureRandom RND;

    @BeforeAll
    public static void ensureRng() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
        RND = new SecureRandom();
    }

    private static BigInteger scalar() {
        return RistrettoSRA.bytesToScalar(RistrettoSRA.generateLockScalar());
    }

    private static EdwardsPoint randomPoint() {
        return EdwardsPoint.BASE.scalarMul(scalar());
    }

    /** Reference: naive sum of independent scalar multiplications. */
    private static EdwardsPoint naive(BigInteger[] s, EdwardsPoint[] p) {
        EdwardsPoint acc = EdwardsPoint.IDENTITY;
        for (int i = 0; i < p.length; i++) {
            acc = acc.add(p[i].scalarMul(s[i].mod(L)));
        }
        return acc;
    }

    private static void assertSame(BigInteger[] s, EdwardsPoint[] p, String msg) {
        assertArrayEquals(Ristretto255.encode(naive(s, p)),
                Ristretto255.encode(EdwardsPoint.multiscalarMul(s, p)), msg);
    }

    @Test
    public void fuzzMatchesNaive() {
        for (int t = 0; t < 150; t++) {
            int n = 1 + RND.nextInt(24);
            BigInteger[] s = new BigInteger[n];
            EdwardsPoint[] p = new EdwardsPoint[n];
            for (int i = 0; i < n; i++) {
                s[i] = scalar();
                p[i] = randomPoint();
            }
            assertSame(s, p, "fuzz n=" + n + " caso " + t);
        }
    }

    @Test
    public void deck52MatchesNaive() {
        int n = 52;
        BigInteger[] s = new BigInteger[n];
        EdwardsPoint[] p = new EdwardsPoint[n];
        for (int i = 0; i < n; i++) {
            s[i] = scalar();
            p[i] = randomPoint();
        }
        assertSame(s, p, "mazo 52");
    }

    @Test
    public void zerosAndEdges() {
        // todos cero -> identidad
        BigInteger[] z = {BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO};
        EdwardsPoint[] p3 = {randomPoint(), randomPoint(), randomPoint()};
        assertArrayEquals(Ristretto255.encode(EdwardsPoint.IDENTITY),
                Ristretto255.encode(EdwardsPoint.multiscalarMul(z, p3)), "todos cero -> identidad");

        // algunos cero mezclados
        BigInteger[] s = {scalar(), BigInteger.ZERO, scalar(), BigInteger.ONE, BigInteger.ZERO};
        EdwardsPoint[] p = {randomPoint(), randomPoint(), randomPoint(), randomPoint(), randomPoint()};
        assertSame(s, p, "algunos cero");

        // n=1
        BigInteger[] s1 = {scalar()};
        EdwardsPoint[] p1 = {randomPoint()};
        assertSame(s1, p1, "n=1");

        // n=0 -> identidad
        assertArrayEquals(Ristretto255.encode(EdwardsPoint.IDENTITY),
                Ristretto255.encode(EdwardsPoint.multiscalarMul(new BigInteger[0], new EdwardsPoint[0])),
                "n=0 -> identidad");

        // escalar 1 y escalar L-1 (extremos)
        BigInteger[] sx = {BigInteger.ONE, L.subtract(BigInteger.ONE)};
        EdwardsPoint[] px = {randomPoint(), randomPoint()};
        assertSame(sx, px, "extremos 1 y L-1");
    }

    @Test
    public void unreducedScalarsReducedConsistently() {
        // entrada sin reducir: multiscalarMul reduce mod L igual que naive
        BigInteger[] s = {scalar().add(L), scalar().add(L.multiply(BigInteger.TWO))};
        EdwardsPoint[] p = {randomPoint(), randomPoint()};
        assertSame(s, p, "escalares sin reducir");
    }

    @Test
    public void repeatedPointVerifies() {
        // mismo punto repetido (degenerado pero valido)
        EdwardsPoint q = randomPoint();
        BigInteger[] s = {scalar(), scalar(), scalar()};
        EdwardsPoint[] p = {q, q, q};
        assertSame(s, p, "punto repetido");
        assertEquals(true, true);
    }
}
