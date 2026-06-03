/*
 * Motor de barajado — ladrillo 4: CutChooseShuffleProof (prove/verify no interactivo).
 *
 * Suite adversaria: barajado honesto verifica; un PROVER TRAMPOSO que intenta probar un deck que
 * NO es barajado (carta duplicada) es rechazado; y cualquier manipulacion (input, output,
 * intermedio, mitad revelada) tambien. La clave didactica: el mecanismo de soundness de
 * cut-and-choose es tan simple que el test del tramposo EJERCITA la soundness real entera.
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

public class CutChooseShuffleProofTest {

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
    public void honestShuffleVerifies() {
        EdwardsPoint[] a = randomDeck(6);
        int[] pi = DeckTransform.randomPermutation(6);
        BigInteger k = scalar();
        EdwardsPoint[] b = DeckTransform.apply(a, pi, k);
        CutChooseShuffleProof.Proof proof = CutChooseShuffleProof.prove(a, b, pi, k, 32);
        assertTrue(CutChooseShuffleProof.verify(a, b, proof), "barajado honesto -> verifica");
    }

    @Test
    public void honestShuffleVerifiesAtProductionRounds() {
        EdwardsPoint[] a = randomDeck(4);
        int[] pi = DeckTransform.randomPermutation(4);
        BigInteger k = scalar();
        EdwardsPoint[] b = DeckTransform.apply(a, pi, k);
        CutChooseShuffleProof.Proof proof = CutChooseShuffleProof.prove(a, b, pi, k); // 128 rondas
        assertTrue(CutChooseShuffleProof.verify(a, b, proof), "verifica con el parametro de produccion (2^-128)");
    }

    @Test
    public void cheatingProverOnNonShuffleIsCaught() {
        // B NO es un barajado de A (carta duplicada). El tramposo construye cada intermedio como
        // C_j = apply(B, sigma_j, m_j), de modo que la mitad C->B es valida; para la mitad A->C no
        // tiene respuesta (C_j es barajado de B, no de A) y mete basura. El verify lo caza salvo que
        // las 48 monedas salgan todas en C->B (prob 2^-48, despreciable).
        int n = 6, rounds = 48;
        EdwardsPoint[] a = randomDeck(n);
        int[] pi = DeckTransform.randomPermutation(n);
        BigInteger k = scalar();
        EdwardsPoint[] b = DeckTransform.apply(a, pi, k);
        b[0] = EdwardsPoint.BASE.scalarMul(scalar()); // CORRUPCION: B deja de ser barajado de A

        byte[][] hashes = new byte[rounds][];
        int[][] sigma = new int[rounds][];
        BigInteger[] m = new BigInteger[rounds];
        for (int j = 0; j < rounds; j++) {
            sigma[j] = DeckTransform.randomPermutation(n);
            m[j] = scalar();
            hashes[j] = CutChooseShuffleProof.hashDeck(DeckTransform.apply(b, sigma[j], m[j])); // C_j = barajado de B
        }
        boolean[] bits = CutChooseShuffleProof.challengeBits(null, a, b, hashes, rounds);

        int[][] revealedPerm = new int[rounds][];
        BigInteger[] revealedScalar = new BigInteger[rounds];
        int[] identity = new int[n];
        for (int i = 0; i < n; i++) {
            identity[i] = i;
        }
        for (int j = 0; j < rounds; j++) {
            if (bits[j]) {
                // mitad C->B: el verify recalcula C=apply(B,invert(perm),s^-1); con perm=invert(sigma),
                // s=m^-1 sale C_j=apply(B,sigma,m) -> su hash cuadra con el comprometido.
                revealedPerm[j] = DeckTransform.invert(sigma[j]);
                revealedScalar[j] = m[j].modInverse(EdwardsPoint.L);
            } else {
                // mitad A->C: el tramposo no puede; mete basura -> hash no cuadra
                revealedPerm[j] = identity;
                revealedScalar[j] = BigInteger.ONE;
            }
        }
        CutChooseShuffleProof.Proof forged =
                new CutChooseShuffleProof.Proof(rounds, n, hashes, revealedPerm, revealedScalar);

        assertFalse(CutChooseShuffleProof.verify(a, b, forged),
                "prover TRAMPOSO sobre un no-barajado -> RECHAZADO (soundness cut-and-choose)");
    }

    @Test
    public void tamperedInputDeckRejected() {
        EdwardsPoint[] a = randomDeck(6);
        int[] pi = DeckTransform.randomPermutation(6);
        BigInteger k = scalar();
        EdwardsPoint[] b = DeckTransform.apply(a, pi, k);
        CutChooseShuffleProof.Proof proof = CutChooseShuffleProof.prove(a, b, pi, k, 32);
        EdwardsPoint[] aTampered = a.clone();
        aTampered[2] = aTampered[2].add(EdwardsPoint.BASE);
        assertFalse(CutChooseShuffleProof.verify(aTampered, b, proof), "input cambiado -> rechazado");
    }

    @Test
    public void tamperedOutputDeckRejected() {
        EdwardsPoint[] a = randomDeck(6);
        int[] pi = DeckTransform.randomPermutation(6);
        BigInteger k = scalar();
        EdwardsPoint[] b = DeckTransform.apply(a, pi, k);
        CutChooseShuffleProof.Proof proof = CutChooseShuffleProof.prove(a, b, pi, k, 32);
        EdwardsPoint[] bTampered = b.clone();
        bTampered[1] = bTampered[1].add(EdwardsPoint.BASE);
        assertFalse(CutChooseShuffleProof.verify(a, bTampered, proof), "output cambiado -> rechazado");
    }

    @Test
    public void tamperedIntermediateRejected() {
        EdwardsPoint[] a = randomDeck(6);
        int[] pi = DeckTransform.randomPermutation(6);
        BigInteger k = scalar();
        EdwardsPoint[] b = DeckTransform.apply(a, pi, k);
        CutChooseShuffleProof.Proof proof = CutChooseShuffleProof.prove(a, b, pi, k, 32);
        // manipular el hash comprometido de un intermedio
        proof.intermediateHash[0] = new byte[32];
        assertFalse(CutChooseShuffleProof.verify(a, b, proof), "hash de intermedio manipulado -> rechazado");
    }

    @Test
    public void tamperedRevealedHalfRejected() {
        EdwardsPoint[] a = randomDeck(6);
        int[] pi = DeckTransform.randomPermutation(6);
        BigInteger k = scalar();
        EdwardsPoint[] b = DeckTransform.apply(a, pi, k);
        CutChooseShuffleProof.Proof proof = CutChooseShuffleProof.prove(a, b, pi, k, 32);
        proof.revealedScalar[5] = proof.revealedScalar[5].add(BigInteger.ONE).mod(EdwardsPoint.L);
        assertFalse(CutChooseShuffleProof.verify(a, b, proof), "escalar revelado manipulado -> rechazado");
    }

    @Test
    public void proveRefusesFalseWitness() {
        EdwardsPoint[] a = randomDeck(6);
        int[] pi = DeckTransform.randomPermutation(6);
        BigInteger k = scalar();
        EdwardsPoint[] notB = randomDeck(6); // no es apply(a, pi, k)
        assertThrows(IllegalArgumentException.class,
                () -> CutChooseShuffleProof.prove(a, notB, pi, k, 16),
                "prove() se niega a 'probar' una afirmacion falsa");
    }
}
