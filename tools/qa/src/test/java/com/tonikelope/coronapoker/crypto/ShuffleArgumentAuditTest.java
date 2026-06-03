/*
 * Bayer-Groth: tests del ENDURECIMIENTO post-auditoria (apto para CoronaPoker).
 * Cubre los hallazgos de la auto-auditoria adversaria:
 *  - degenerados/identidad (k=0, carta identidad) -> rechazados (antes pasaban como mazo basura)
 *  - proof malformado: respuesta null o fuera de rango [0,L) -> rechazado limpio (antes NPE)
 *  - maleabilidad z+L -> rechazado (canonicidad)
 *  - composicion honesta de 2 pasos (anclaje a genesis preserva DL-independencia) -> verifica
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.math.BigInteger;
import java.security.SecureRandom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ShuffleArgumentAuditTest {

    private static final BigInteger L = EdwardsPoint.L;

    @BeforeAll
    public static void ensureRng() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
    }

    private static BigInteger scalar() {
        return RistrettoSRA.bytesToScalar(RistrettoSRA.generateLockScalar());
    }

    private static EdwardsPoint[] randomDeck(int n) {
        EdwardsPoint[] a = new EdwardsPoint[n];
        for (int i = 0; i < n; i++) {
            a[i] = EdwardsPoint.BASE.scalarMul(scalar());
        }
        return a;
    }

    private static int[] permutation(int n) {
        int[] p = new int[n];
        for (int i = 0; i < n; i++) {
            p[i] = (i * 7 + 5) % n; // gcd(7,n)=1 para n coprimo con 7 (usamos n=9 -> gcd(7,9)=1)
        }
        return p;
    }

    private static EdwardsPoint[] applyShuffle(EdwardsPoint[] a, int[] pi, BigInteger k) {
        EdwardsPoint[] b = new EdwardsPoint[a.length];
        for (int i = 0; i < a.length; i++) {
            b[i] = a[pi[i]].scalarMul(k.mod(L));
        }
        return b;
    }

    // --- Degenerados / identidad ---

    @Test
    public void zeroScalarAllIdentityDeckRejected() {
        // k=0 -> B todo identidad. Antes el proof lo atestiguaba (mazo basura). Ahora se rechaza.
        int n = 9;
        EdwardsPoint[] a = randomDeck(n);
        int[] pi = permutation(n);
        BigInteger k = BigInteger.ZERO;
        EdwardsPoint[] b = applyShuffle(a, pi, k); // todo identidad
        ShuffleArgument.Proof p = ShuffleArgument.prove(a, b, pi, k);
        assertFalse(ShuffleArgument.verify(a, b, p), "k=0 (mazo identidad) -> rechazado");
    }

    @Test
    public void identityCardInOutputRejected() {
        int n = 9;
        EdwardsPoint[] a = randomDeck(n);
        int[] pi = permutation(n);
        BigInteger k = scalar();
        EdwardsPoint[] b = applyShuffle(a, pi, k);
        b[3] = EdwardsPoint.IDENTITY; // una carta identidad colada en la salida
        ShuffleArgument.Proof p = ShuffleArgument.prove(a, b, pi, k);
        assertFalse(ShuffleArgument.verify(a, b, p), "carta identidad en B -> rechazado");
    }

    @Test
    public void identityCardInInputRejected() {
        int n = 9;
        EdwardsPoint[] a = randomDeck(n);
        a[2] = EdwardsPoint.IDENTITY;
        int[] pi = permutation(n);
        BigInteger k = scalar();
        EdwardsPoint[] b = applyShuffle(a, pi, k);
        ShuffleArgument.Proof p = ShuffleArgument.prove(a, b, pi, k);
        assertFalse(ShuffleArgument.verify(a, b, p), "carta identidad en A -> rechazado");
    }

    // --- Proof malformado: null / fuera de rango (antes NPE) ---

    @Test
    public void outOfRangeSchnorrResponseRejectedCleanly() {
        int n = 9;
        EdwardsPoint[] a = randomDeck(n);
        int[] pi = permutation(n);
        BigInteger k = scalar();
        EdwardsPoint[] b = applyShuffle(a, pi, k);
        ShuffleArgument.Proof p = ShuffleArgument.prove(a, b, pi, k);
        ShuffleArgument.Proof bad = new ShuffleArgument.Proof(p.cf, p.perm, p.q, p.wsum, p.scT, p.scZ.add(L));
        assertFalse(ShuffleArgument.verify(a, b, bad), "scZ+L (fuera de rango) -> rechazado, sin excepcion");
    }

    @Test
    public void nullWeightedSumResponseRejectedCleanly() {
        int n = 9;
        EdwardsPoint[] a = randomDeck(n);
        int[] pi = permutation(n);
        BigInteger k = scalar();
        EdwardsPoint[] b = applyShuffle(a, pi, k);
        ShuffleArgument.Proof p = ShuffleArgument.prove(a, b, pi, k);
        BigInteger[] zBad = p.wsum.z.clone();
        zBad[0] = null; // antes -> NPE; ahora -> rechazo limpio
        WeightedSumArgument.Proof wbad = new WeightedSumArgument.Proof(p.wsum.t, p.wsum.tq, zBad, p.wsum.zs);
        ShuffleArgument.Proof bad = new ShuffleArgument.Proof(p.cf, p.perm, p.q, wbad, p.scT, p.scZ);
        boolean rejected;
        try {
            rejected = !ShuffleArgument.verify(a, b, bad);
        } catch (RuntimeException ex) {
            rejected = false; // una excepcion seria un fallo (DoS), no un rechazo limpio
        }
        assertTrue(rejected, "respuesta null en wsum -> rechazo limpio (sin excepcion)");
    }

    @Test
    public void outOfRangeWeightedSumResponseRejected() {
        int n = 9;
        EdwardsPoint[] a = randomDeck(n);
        int[] pi = permutation(n);
        BigInteger k = scalar();
        EdwardsPoint[] b = applyShuffle(a, pi, k);
        ShuffleArgument.Proof p = ShuffleArgument.prove(a, b, pi, k);
        BigInteger[] zsBad = p.wsum.zs.clone();
        zsBad[1] = zsBad[1].add(L); // z+L
        WeightedSumArgument.Proof wbad = new WeightedSumArgument.Proof(p.wsum.t, p.wsum.tq, p.wsum.z, zsBad);
        ShuffleArgument.Proof bad = new ShuffleArgument.Proof(p.cf, p.perm, p.q, wbad, p.scT, p.scZ);
        assertFalse(ShuffleArgument.verify(a, b, bad), "zs+L (maleabilidad) -> rechazado");
    }

    // --- Composicion honesta de 2 pasos (anclaje preserva DL-independencia) ---

    @Test
    public void honestTwoStepCompositionVerifies() {
        // genesis DL-independiente -> paso1 honesto -> su salida (DL-independiente) -> paso2 honesto.
        // Cada paso se verifica contra la salida YA verificada del anterior (el anclaje del cascade).
        int n = 9;
        EdwardsPoint[] genesis = randomDeck(n);

        int[] pi1 = permutation(n);
        BigInteger k1 = scalar();
        EdwardsPoint[] deck1 = applyShuffle(genesis, pi1, k1);
        ShuffleArgument.Proof p1 = ShuffleArgument.prove(genesis, deck1, pi1, k1);
        assertTrue(ShuffleArgument.verify(genesis, deck1, p1), "paso 1 verifica");

        int[] pi2 = permutation(n);
        BigInteger k2 = scalar();
        EdwardsPoint[] deck2 = applyShuffle(deck1, pi2, k2);
        ShuffleArgument.Proof p2 = ShuffleArgument.prove(deck1, deck2, pi2, k2);
        assertTrue(ShuffleArgument.verify(deck1, deck2, p2),
                "paso 2 verifica contra la salida ya verificada del paso 1 (anclaje a genesis por induccion)");
    }
}
