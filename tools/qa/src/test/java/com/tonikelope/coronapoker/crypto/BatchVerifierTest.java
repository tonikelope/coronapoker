/*
 * Blindaje del nucleo de soundness del batch-verify (BatchVerifier): comprueba Sum rho_j*(lhs_j -
 * expected_j) == O con pesos rho_j independientes ligados por Fiat-Shamir a todas las ecuaciones.
 * (1) COMPLETITUD: si todas las ecuaciones se cumplen, allHold es true para cualquier peso.
 * (2) SOUNDNESS: si UNA cualquiera no se cumple (expected desviado, escalar o punto tocado), allHold
 *     es false (falla salvo prob 1/L ~ 2^-252, imposible con seeds fijos). (3) poison en null. (4) vacio.
 * Es la red que un smoke de juego honesto NO puede dar: un juego honesto solo ejercita el caso true.
 */
package com.tonikelope.coronapoker.crypto;

import java.math.BigInteger;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BatchVerifierTest {

    private static final BigInteger L = EdwardsPoint.L;

    private static BigInteger scal(Random r) {
        return new BigInteger(252, r).add(BigInteger.ONE); // en [1, 2^252] < L, nunca 0
    }

    private static EdwardsPoint pt(Random r) {
        return EdwardsPoint.BASE.scalarMul(scal(r));
    }

    private static EdwardsPoint expectedOf(BigInteger[] s, EdwardsPoint[] p) {
        return EdwardsPoint.multiscalarMul(s, p);
    }

    /** COMPLETITUD: m ecuaciones todas ciertas -> allHold true, sobre muchos m y muchos intentos. */
    @Test
    public void completenessAllHold() {
        Random r = new Random(0xBA7C4L);
        for (int trial = 0; trial < 200; trial++) {
            int m = 1 + r.nextInt(6);
            BatchVerifier bv = new BatchVerifier("test/complete");
            for (int j = 0; j < m; j++) {
                int k = 1 + r.nextInt(4);
                BigInteger[] s = new BigInteger[k];
                EdwardsPoint[] p = new EdwardsPoint[k];
                for (int i = 0; i < k; i++) {
                    s[i] = scal(r);
                    p[i] = pt(r);
                }
                bv.addEquation(s, p, expectedOf(s, p));
            }
            assertTrue(bv.allHold(), "completitud fallo en trial " + trial);
        }
    }

    /** SOUNDNESS: entre m ecuaciones ciertas, romper UNA (expected desviado por BASE != O) -> false. */
    @Test
    public void soundnessBrokenExpectedRejected() {
        Random r = new Random(0x50D5AL);
        for (int trial = 0; trial < 300; trial++) {
            int m = 2 + r.nextInt(5);
            int bad = r.nextInt(m);
            BatchVerifier bv = new BatchVerifier("test/sound-exp");
            for (int j = 0; j < m; j++) {
                int k = 1 + r.nextInt(4);
                BigInteger[] s = new BigInteger[k];
                EdwardsPoint[] p = new EdwardsPoint[k];
                for (int i = 0; i < k; i++) {
                    s[i] = scal(r);
                    p[i] = pt(r);
                }
                EdwardsPoint exp = expectedOf(s, p);
                if (j == bad) {
                    exp = exp.add(EdwardsPoint.BASE); // d_bad = -BASE != O
                }
                bv.addEquation(s, p, exp);
            }
            assertFalse(bv.allHold(), "soundness (expected) fallo en trial " + trial + " bad=" + bad);
        }
    }

    /** SOUNDNESS: romper UNA ecuacion tocando un escalar (+1) -> allHold false. */
    @Test
    public void soundnessBrokenScalarRejected() {
        Random r = new Random(0x5CA1B0L);
        for (int trial = 0; trial < 300; trial++) {
            int m = 2 + r.nextInt(5);
            int bad = r.nextInt(m);
            BatchVerifier bv = new BatchVerifier("test/sound-scal");
            for (int j = 0; j < m; j++) {
                int k = 2 + r.nextInt(3);
                BigInteger[] s = new BigInteger[k];
                EdwardsPoint[] p = new EdwardsPoint[k];
                for (int i = 0; i < k; i++) {
                    s[i] = scal(r);
                    p[i] = pt(r);
                }
                EdwardsPoint exp = expectedOf(s, p); // expected para el s ORIGINAL
                if (j == bad) {
                    s[0] = s[0].add(BigInteger.ONE).mod(L); // ahora MSM(s,p) != exp (p[0] != O)
                }
                bv.addEquation(s, p, exp);
            }
            assertFalse(bv.allHold(), "soundness (escalar) fallo en trial " + trial + " bad=" + bad);
        }
    }

    /** poison: null en array/elemento/expected o longitudes que no casan -> allHold false. */
    @Test
    public void poisonOnMalformed() {
        BatchVerifier a = new BatchVerifier("test/p1");
        a.addEquation(new BigInteger[]{BigInteger.ONE}, new EdwardsPoint[]{null}, EdwardsPoint.BASE);
        assertFalse(a.allHold());

        BatchVerifier b = new BatchVerifier("test/p2");
        b.addEquation(null, null, EdwardsPoint.BASE);
        assertFalse(b.allHold());

        BatchVerifier c = new BatchVerifier("test/p3");
        c.addEquation(new BigInteger[]{BigInteger.ONE, BigInteger.TWO},
                new EdwardsPoint[]{EdwardsPoint.BASE}, EdwardsPoint.BASE); // longitud descompensada
        assertFalse(c.allHold());

        BatchVerifier d = new BatchVerifier("test/p4");
        d.addEquation(new BigInteger[]{BigInteger.ONE}, new EdwardsPoint[]{EdwardsPoint.BASE}, null);
        assertFalse(d.allHold());

        BatchVerifier e = new BatchVerifier("test/p5"); // una buena + una poison -> false
        BigInteger[] s = {scal(new Random(1)), scal(new Random(2))};
        EdwardsPoint[] p = {pt(new Random(3)), pt(new Random(4))};
        e.addEquation(s, p, expectedOf(s, p));
        e.addEquation(new BigInteger[]{BigInteger.ONE}, new EdwardsPoint[]{null}, EdwardsPoint.BASE);
        assertFalse(e.allHold());
    }

    /** Un batch vacio se cumple de forma vacua. */
    @Test
    public void emptyVacuous() {
        assertTrue(new BatchVerifier("test/empty").allHold());
    }
}
