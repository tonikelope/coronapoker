/*
 * Bayer-Groth: orquestador de cascada (anclaje a genesis + cadena) que IMPONE la precondicion DL.
 * Esto convierte la precondicion critica de la auditoria en codigo probado:
 *  - cascada honesta multi-paso verifica
 *  - smuggle en CUALQUIER paso -> rechazado (cae el paso, cae la cadena)
 *  - anclaje roto (decks[0] != genesis) -> rechazado
 *  - mazo manipulado / conteo de proofs mal -> rechazado
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.math.BigInteger;
import java.security.SecureRandom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ShuffleCascadeTest {

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

    private static EdwardsPoint[] genesisDeck(int n) {
        EdwardsPoint[] a = new EdwardsPoint[n];
        for (int i = 0; i < n; i++) {
            a[i] = EdwardsPoint.BASE.scalarMul(scalar());
        }
        return a;
    }

    /** Build an honest m-step cascade; returns {decks (m+1), proofs (m)}. */
    private static Object[] honestCascade(EdwardsPoint[] genesis, int steps) {
        EdwardsPoint[][] decks = new EdwardsPoint[steps + 1][];
        ShuffleArgument.Proof[] proofs = new ShuffleArgument.Proof[steps];
        decks[0] = genesis;
        for (int m = 0; m < steps; m++) {
            int[] perm = DeckTransform.randomPermutation(genesis.length);
            BigInteger k = scalar();
            decks[m + 1] = DeckTransform.apply(decks[m], perm, k);
            proofs[m] = ShuffleCascade.proveStep(decks[m], decks[m + 1], perm, k);
        }
        return new Object[]{decks, proofs};
    }

    @Test
    public void honestThreeStepCascadeVerifies() {
        EdwardsPoint[] genesis = genesisDeck(9);
        Object[] c = honestCascade(genesis, 3);
        assertTrue(ShuffleCascade.verifyChain(genesis, (EdwardsPoint[][]) c[0], (ShuffleArgument.Proof[]) c[1]),
                "cascada honesta de 3 pasos anclada a genesis -> verifica");
    }

    @Test
    public void singleStepCascadeVerifies() {
        EdwardsPoint[] genesis = genesisDeck(8);
        Object[] c = honestCascade(genesis, 1);
        assertTrue(ShuffleCascade.verifyChain(genesis, (EdwardsPoint[][]) c[0], (ShuffleArgument.Proof[]) c[1]),
                "cascada de 1 paso -> verifica");
    }

    @Test
    public void anchorMismatchRejected() {
        EdwardsPoint[] genesis = genesisDeck(9);
        Object[] c = honestCascade(genesis, 2);
        EdwardsPoint[] otherGenesis = genesisDeck(9); // genesis distinto
        assertFalse(ShuffleCascade.verifyChain(otherGenesis, (EdwardsPoint[][]) c[0], (ShuffleArgument.Proof[]) c[1]),
                "decks[0] != genesis -> rechazado (anclaje)");
    }

    @Test
    public void smuggleAtMiddleStepRejected() {
        // El host hace pasos honestos salvo el del medio, donde mete un duplicado (smuggle) y miente
        // afirmando pi=identidad. El paso cae -> la cadena entera cae.
        int n = 9;
        EdwardsPoint[] genesis = genesisDeck(n);
        EdwardsPoint[][] decks = new EdwardsPoint[4][];
        ShuffleArgument.Proof[] proofs = new ShuffleArgument.Proof[3];
        decks[0] = genesis;

        int[] perm0 = DeckTransform.randomPermutation(n);
        BigInteger k0 = scalar();
        decks[1] = DeckTransform.apply(decks[0], perm0, k0);
        proofs[0] = ShuffleCascade.proveStep(decks[0], decks[1], perm0, k0);

        // paso 1: SMUGGLE — relock honesto salvo que duplica la carta 0 en la posicion 1
        BigInteger k1 = scalar();
        EdwardsPoint[] smug = new EdwardsPoint[n];
        for (int i = 0; i < n; i++) {
            smug[i] = decks[1][i].scalarMul(k1.mod(L));
        }
        smug[1] = decks[1][0].scalarMul(k1.mod(L)); // duplicado de la carta 0
        decks[2] = smug;
        int[] idPerm = new int[n];
        for (int i = 0; i < n; i++) {
            idPerm[i] = i;
        }
        proofs[1] = ShuffleCascade.proveStep(decks[1], decks[2], idPerm, k1); // miente

        int[] perm2 = DeckTransform.randomPermutation(n);
        BigInteger k2 = scalar();
        decks[3] = DeckTransform.apply(decks[2], perm2, k2);
        proofs[2] = ShuffleCascade.proveStep(decks[2], decks[3], perm2, k2);

        assertFalse(ShuffleCascade.verifyChain(genesis, decks, proofs),
                "[ATAQUE] smuggle en el paso intermedio -> la cadena entera se rechaza");
    }

    @Test
    public void tamperedDeckRejected() {
        EdwardsPoint[] genesis = genesisDeck(9);
        Object[] c = honestCascade(genesis, 3);
        EdwardsPoint[][] decks = (EdwardsPoint[][]) c[0];
        // manipular una carta del segundo mazo tras generar las pruebas
        decks[2][4] = decks[2][4].add(EdwardsPoint.BASE);
        assertFalse(ShuffleCascade.verifyChain(genesis, decks, (ShuffleArgument.Proof[]) c[1]),
                "mazo intermedio manipulado -> rechazado");
    }

    @Test
    public void wrongProofCountRejected() {
        EdwardsPoint[] genesis = genesisDeck(8);
        Object[] c = honestCascade(genesis, 2);
        ShuffleArgument.Proof[] proofs = (ShuffleArgument.Proof[]) c[1];
        ShuffleArgument.Proof[] tooFew = new ShuffleArgument.Proof[]{proofs[0]}; // falta uno
        assertFalse(ShuffleCascade.verifyChain(genesis, (EdwardsPoint[][]) c[0], tooFew),
                "conteo de proofs != pasos -> rechazado");
    }
}
