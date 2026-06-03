/*
 * Capstone del motor de barajado: VerifiableCascade. Demuestra END-TO-END que el ataque queda
 * cerrado — una cascada honesta verifica, pero si algún paso (el host tramposo) cuela un
 * NO-barajado (carta duplicada = el smuggle), ese paso no se puede probar y la cadena se rechaza.
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.math.BigInteger;
import java.security.SecureRandom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VerifiableCascadeTest {

    @BeforeAll
    public static void ensureRng() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
    }

    private static final int N = 8;       // cartas del deck (pequeño para velocidad)
    private static final int PEERS = 3;   // pasos de cascada
    private static final int ROUNDS = 32; // soundness del test (produccion = 128)

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

    /** Construye una cascada honesta: decks[0]=genesis, decks[m+1]=apply(decks[m],perm_m,k_m). */
    private static EdwardsPoint[][] buildHonestDecks(EdwardsPoint[] genesis, int[][] perms, BigInteger[] ks) {
        EdwardsPoint[][] decks = new EdwardsPoint[PEERS + 1][];
        decks[0] = genesis;
        for (int m = 0; m < PEERS; m++) {
            perms[m] = DeckTransform.randomPermutation(N);
            ks[m] = scalar();
            decks[m + 1] = DeckTransform.apply(decks[m], perms[m], ks[m]);
        }
        return decks;
    }

    @Test
    public void honestCascadeVerifies() {
        EdwardsPoint[] genesis = randomDeck(N);
        int[][] perms = new int[PEERS][];
        BigInteger[] ks = new BigInteger[PEERS];
        EdwardsPoint[][] decks = buildHonestDecks(genesis, perms, ks);

        CutChooseShuffleProof.Proof[] proofs = new CutChooseShuffleProof.Proof[PEERS];
        for (int m = 0; m < PEERS; m++) {
            proofs[m] = VerifiableCascade.proveStep(decks[m], decks[m + 1], perms[m], ks[m], ROUNDS);
        }
        assertTrue(VerifiableCascade.verifyChain(genesis, decks, proofs),
                "cascada honesta anclada al genesis -> verifica");
    }

    @Test
    public void smuggleStepCannotBeProven() {
        // El smuggle = un paso cuyo output NO es un barajado del input (duplica una carta).
        EdwardsPoint[] in = randomDeck(N);
        EdwardsPoint[] badOut = DeckTransform.apply(in, DeckTransform.randomPermutation(N), scalar());
        badOut[0] = badOut[1]; // DUPLICADO: deja de ser una permutacion de 'in'
        // El host no tiene (perm,k) testigo -> prove() se niega.
        assertThrows(IllegalArgumentException.class,
                () -> VerifiableCascade.proveStep(in, badOut, DeckTransform.randomPermutation(N), scalar(), ROUNDS),
                "un paso no-barajado (smuggle) no se puede probar");
    }

    @Test
    public void forgedSmuggleStepRejectedByChain() {
        // El tramposo construye una prueba FORJADA para un paso no-barajado y la mete en la cadena.
        EdwardsPoint[] genesis = randomDeck(N);
        int[][] perms = new int[PEERS][];
        BigInteger[] ks = new BigInteger[PEERS];
        EdwardsPoint[][] decks = buildHonestDecks(genesis, perms, ks);

        // Corrompe el output del paso 1 (smuggle): duplica una carta.
        decks[2] = decks[1].clone();
        decks[2][0] = decks[2][1];

        int rounds = 48;
        CutChooseShuffleProof.Proof[] proofs = new CutChooseShuffleProof.Proof[PEERS];
        for (int m = 0; m < PEERS; m++) {
            if (m == 1) {
                proofs[m] = forgeProof(decks[m], decks[m + 1], rounds); // prueba tramposa
            } else {
                // los otros pasos honestos (recomputados sobre decks corrompidos: paso 2 desde decks[2])
                int[] p = DeckTransform.randomPermutation(N);
                BigInteger k = scalar();
                decks[m + 1] = DeckTransform.apply(decks[m], p, k);
                proofs[m] = VerifiableCascade.proveStep(decks[m], decks[m + 1], p, k, rounds);
            }
        }
        assertFalse(VerifiableCascade.verifyChain(genesis, decks, proofs),
                "cadena con un paso smuggle (prueba forjada) -> RECHAZADA");
    }

    /** Prueba forjada para un paso A->B que NO es barajado: C_j=apply(B,sigma,m); basura en A->C. */
    private static CutChooseShuffleProof.Proof forgeProof(EdwardsPoint[] a, EdwardsPoint[] b, int rounds) {
        int n = a.length;
        byte[][][] intermediates = new byte[rounds][][];
        int[][] sigma = new int[rounds][];
        BigInteger[] mm = new BigInteger[rounds];
        for (int j = 0; j < rounds; j++) {
            sigma[j] = DeckTransform.randomPermutation(n);
            mm[j] = scalar();
            EdwardsPoint[] c = DeckTransform.apply(b, sigma[j], mm[j]);
            intermediates[j] = new byte[n][];
            for (int i = 0; i < n; i++) {
                intermediates[j][i] = Ristretto255.encode(c[i]);
            }
        }
        boolean[] bits = CutChooseShuffleProof.challengeBits(null, a, b, intermediates, rounds);
        int[][] perm = new int[rounds][];
        BigInteger[] sc = new BigInteger[rounds];
        int[] id = new int[n];
        for (int i = 0; i < n; i++) {
            id[i] = i;
        }
        for (int j = 0; j < rounds; j++) {
            if (bits[j]) {
                perm[j] = DeckTransform.invert(sigma[j]);
                sc[j] = mm[j].modInverse(EdwardsPoint.L);
            } else {
                perm[j] = id;
                sc[j] = BigInteger.ONE;
            }
        }
        return new CutChooseShuffleProof.Proof(rounds, n, intermediates, perm, sc);
    }

    @Test
    public void anchorMismatchRejected() {
        EdwardsPoint[] genesis = randomDeck(N);
        int[][] perms = new int[PEERS][];
        BigInteger[] ks = new BigInteger[PEERS];
        EdwardsPoint[][] decks = buildHonestDecks(genesis, perms, ks);
        CutChooseShuffleProof.Proof[] proofs = new CutChooseShuffleProof.Proof[PEERS];
        for (int m = 0; m < PEERS; m++) {
            proofs[m] = VerifiableCascade.proveStep(decks[m], decks[m + 1], perms[m], ks[m], ROUNDS);
        }
        // El host presenta una cascada que NO arranca en el genesis publico.
        assertFalse(VerifiableCascade.verifyChain(randomDeck(N), decks, proofs),
                "decks[0] != genesis publico -> rechazado (anclaje)");
    }

    @Test
    public void tamperedIntermediateDeckRejected() {
        EdwardsPoint[] genesis = randomDeck(N);
        int[][] perms = new int[PEERS][];
        BigInteger[] ks = new BigInteger[PEERS];
        EdwardsPoint[][] decks = buildHonestDecks(genesis, perms, ks);
        CutChooseShuffleProof.Proof[] proofs = new CutChooseShuffleProof.Proof[PEERS];
        for (int m = 0; m < PEERS; m++) {
            proofs[m] = VerifiableCascade.proveStep(decks[m], decks[m + 1], perms[m], ks[m], ROUNDS);
        }
        decks[2] = decks[2].clone();
        decks[2][3] = decks[2][3].add(EdwardsPoint.BASE); // altera un deck intermedio
        assertFalse(VerifiableCascade.verifyChain(genesis, decks, proofs),
                "deck intermedio manipulado -> la prueba de su paso ya no cuadra -> rechazado");
    }
}
