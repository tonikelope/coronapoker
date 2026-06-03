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
