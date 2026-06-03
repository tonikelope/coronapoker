/*
 * Motor de barajado — ladrillo 3: DeckTransform (permutación + escalar común sobre un deck).
 *
 * Pinea la matemática sobre la que se apoya el cut-and-choose: apply, invert/round-trip,
 * isPermutation, decksEqual, randomPermutation, y la IDENTIDAD DE FACTORIZACIÓN que permite
 * partir cualquier shuffle A->B por un intermedio C (el corazón del cut-and-choose).
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.math.BigInteger;
import java.security.SecureRandom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DeckTransformTest {

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
        EdwardsPoint[] d = new EdwardsPoint[n];
        for (int i = 0; i < n; i++) {
            d[i] = EdwardsPoint.BASE.scalarMul(scalar());
        }
        return d;
    }

    @Test
    public void identityPermAndUnitScalarIsNoOp() {
        EdwardsPoint[] deck = randomDeck(8);
        int[] id = new int[8];
        for (int i = 0; i < 8; i++) {
            id[i] = i;
        }
        assertTrue(DeckTransform.decksEqual(deck, DeckTransform.apply(deck, id, BigInteger.ONE)),
                "permutación identidad + k=1 = sin cambios");
    }

    @Test
    public void invertRoundTripsTheTransform() {
        EdwardsPoint[] deck = randomDeck(10);
        int[] perm = DeckTransform.randomPermutation(10);
        BigInteger k = scalar();
        EdwardsPoint[] shuffled = DeckTransform.apply(deck, perm, k);
        EdwardsPoint[] back = DeckTransform.apply(shuffled, DeckTransform.invert(perm), k.modInverse(EdwardsPoint.L));
        assertTrue(DeckTransform.decksEqual(deck, back),
                "apply(.,perm,k) seguido de apply(.,invert(perm),k^-1) recupera el deck");
    }

    @Test
    public void factorisationIdentityHoldsForCutAndChoose() {
        // B = apply(A, π, k). Para CUALQUIER (π1,k1) frescos, con C = apply(A,π1,k1),
        // π2 = compose(invert(π1), π), k2 = k·k1^-1: debe cumplirse B = apply(C, π2, k2).
        EdwardsPoint[] A = randomDeck(12);
        int[] pi = DeckTransform.randomPermutation(12);
        BigInteger k = scalar();
        EdwardsPoint[] B = DeckTransform.apply(A, pi, k);

        for (int round = 0; round < 5; round++) {
            int[] pi1 = DeckTransform.randomPermutation(12);
            BigInteger k1 = scalar();
            EdwardsPoint[] C = DeckTransform.apply(A, pi1, k1);

            int[] pi2 = DeckTransform.compose(DeckTransform.invert(pi1), pi);
            BigInteger k2 = k.multiply(k1.modInverse(EdwardsPoint.L)).mod(EdwardsPoint.L);

            assertTrue(DeckTransform.decksEqual(B, DeckTransform.apply(C, pi2, k2)),
                    "identidad de factorización: B = apply(apply(A,π1,k1), compose(invert(π1),π), k·k1^-1)");
            // y la otra mitad (A->C) verifica por construcción
            assertTrue(DeckTransform.decksEqual(C, DeckTransform.apply(A, pi1, k1)));
        }
    }

    @Test
    public void composeMatchesSequentialApply() {
        // apply(apply(A, g, kg), f, kf) == apply(A, compose(g, f)... ) cuidado con el orden:
        // out2[i] = kf·C[f[i]] = kf·kg·A[g[f[i]]] = (kf·kg)·A[ compose(g,f)[i] ].
        EdwardsPoint[] A = randomDeck(9);
        int[] f = DeckTransform.randomPermutation(9);
        int[] g = DeckTransform.randomPermutation(9);
        BigInteger kf = scalar(), kg = scalar();
        EdwardsPoint[] viaTwo = DeckTransform.apply(DeckTransform.apply(A, g, kg), f, kf);
        EdwardsPoint[] viaOne = DeckTransform.apply(A, DeckTransform.compose(g, f), kf.multiply(kg).mod(EdwardsPoint.L));
        assertTrue(DeckTransform.decksEqual(viaTwo, viaOne), "compose modela dos applies encadenados");
    }

    @Test
    public void isPermutationRejectsBadInputs() {
        assertTrue(DeckTransform.isPermutation(new int[]{0, 1, 2, 3}, 4));
        assertTrue(DeckTransform.isPermutation(new int[]{3, 0, 2, 1}, 4));
        assertFalse(DeckTransform.isPermutation(new int[]{0, 1, 1, 3}, 4), "duplicado");
        assertFalse(DeckTransform.isPermutation(new int[]{0, 1, 2, 4}, 4), "fuera de rango");
        assertFalse(DeckTransform.isPermutation(new int[]{0, 1, 2}, 4), "longitud distinta");
        assertFalse(DeckTransform.isPermutation(null, 4), "null");
    }

    @Test
    public void randomPermutationIsAlwaysValid() {
        for (int n = 1; n <= 30; n++) {
            assertTrue(DeckTransform.isPermutation(DeckTransform.randomPermutation(n), n),
                    "randomPermutation(" + n + ") es una permutación válida");
        }
    }

    @Test
    public void decksEqualDetectsDifference() {
        EdwardsPoint[] a = randomDeck(5);
        EdwardsPoint[] b = a.clone();
        assertTrue(DeckTransform.decksEqual(a, b));
        b[2] = b[2].add(EdwardsPoint.BASE); // altera un punto
        assertFalse(DeckTransform.decksEqual(a, b), "un punto distinto -> decks no iguales");
        assertFalse(DeckTransform.decksEqual(a, randomDeck(4)), "longitud distinta -> no iguales");
    }
}
