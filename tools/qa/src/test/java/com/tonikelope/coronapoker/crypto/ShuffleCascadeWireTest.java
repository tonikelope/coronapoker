/*
 * Bayer-Groth: helpers de wire de ShuffleCascade (decks planos + proofs serializados).
 * Lo que usaran los handlers de red: prove/verify por bytes y cascada completa por bytes.
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ShuffleCascadeWireTest {

    @BeforeAll
    public static void ensureRng() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
    }

    private static BigInteger scalar() {
        return RistrettoSRA.bytesToScalar(RistrettoSRA.generateLockScalar());
    }

    private static byte[] encodeDeck(EdwardsPoint[] deck) {
        byte[] out = new byte[deck.length * 32];
        for (int i = 0; i < deck.length; i++) {
            System.arraycopy(Ristretto255.encode(deck[i]), 0, out, i * 32, 32);
        }
        return out;
    }

    private static EdwardsPoint[] genesisDeck(int n) {
        EdwardsPoint[] a = new EdwardsPoint[n];
        for (int i = 0; i < n; i++) {
            a[i] = EdwardsPoint.BASE.scalarMul(scalar());
        }
        return a;
    }

    @Test
    public void wireStepRoundTripVerifies() {
        int n = 9;
        EdwardsPoint[] in = genesisDeck(n);
        int[] perm = DeckTransform.randomPermutation(n);
        byte[] k = RistrettoSRA.generateLockScalar();
        EdwardsPoint[] out = DeckTransform.apply(in, perm, RistrettoSRA.bytesToScalar(k));

        byte[] proof = ShuffleCascade.proveStepWire(encodeDeck(in), encodeDeck(out), perm, k);
        assertNotNull(proof, "proveStepWire no nulo");
        assertTrue(ShuffleCascade.verifyStepWire(encodeDeck(in), encodeDeck(out), proof),
                "verifyStepWire honesto -> true");
    }

    @Test
    public void wireStepWrongDeckRejected() {
        int n = 9;
        EdwardsPoint[] in = genesisDeck(n);
        int[] perm = DeckTransform.randomPermutation(n);
        byte[] k = RistrettoSRA.generateLockScalar();
        EdwardsPoint[] out = DeckTransform.apply(in, perm, RistrettoSRA.bytesToScalar(k));
        byte[] proof = ShuffleCascade.proveStepWire(encodeDeck(in), encodeDeck(out), perm, k);

        EdwardsPoint[] outTampered = out.clone();
        outTampered[3] = outTampered[3].add(EdwardsPoint.BASE);
        assertFalse(ShuffleCascade.verifyStepWire(encodeDeck(in), encodeDeck(outTampered), proof),
                "deck de salida cambiado -> verifyStepWire false");
    }

    @Test
    public void wireStepMalformedProofRejected() {
        int n = 8;
        EdwardsPoint[] in = genesisDeck(n);
        assertFalse(ShuffleCascade.verifyStepWire(encodeDeck(in), encodeDeck(in), new byte[]{1, 2, 3}),
                "proof basura -> false, sin excepcion");
        assertFalse(ShuffleCascade.verifyStepWire(encodeDeck(in), encodeDeck(in), null),
                "proof null -> false");
    }

    @Test
    public void wireFullCascadeVerifies() {
        int n = 9;
        EdwardsPoint[] genesis = genesisDeck(n);
        List<byte[]> deckBytes = new ArrayList<>();
        List<byte[]> proofBytes = new ArrayList<>();
        deckBytes.add(encodeDeck(genesis));

        EdwardsPoint[] cur = genesis;
        for (int m = 0; m < 3; m++) {
            int[] perm = DeckTransform.randomPermutation(n);
            byte[] k = RistrettoSRA.generateLockScalar();
            EdwardsPoint[] next = DeckTransform.apply(cur, perm, RistrettoSRA.bytesToScalar(k));
            byte[] proof = ShuffleCascade.proveStepWire(encodeDeck(cur), encodeDeck(next), perm, k);
            assertNotNull(proof, "proof paso " + m);
            deckBytes.add(encodeDeck(next));
            proofBytes.add(proof);
            cur = next;
        }
        assertTrue(ShuffleCascade.verifyChainWire(encodeDeck(genesis), deckBytes, proofBytes),
                "cascada completa por bytes, anclada -> verifica");
    }

    @Test
    public void wireFullCascadeWrongAnchorRejected() {
        int n = 9;
        EdwardsPoint[] genesis = genesisDeck(n);
        List<byte[]> deckBytes = new ArrayList<>();
        List<byte[]> proofBytes = new ArrayList<>();
        deckBytes.add(encodeDeck(genesis));
        EdwardsPoint[] cur = genesis;
        for (int m = 0; m < 2; m++) {
            int[] perm = DeckTransform.randomPermutation(n);
            byte[] k = RistrettoSRA.generateLockScalar();
            EdwardsPoint[] next = DeckTransform.apply(cur, perm, RistrettoSRA.bytesToScalar(k));
            proofBytes.add(ShuffleCascade.proveStepWire(encodeDeck(cur), encodeDeck(next), perm, k));
            deckBytes.add(encodeDeck(next));
            cur = next;
        }
        byte[] wrongGenesis = encodeDeck(genesisDeck(n));
        assertFalse(ShuffleCascade.verifyChainWire(wrongGenesis, deckBytes, proofBytes),
                "anclaje a genesis equivocado -> false");
    }
}
