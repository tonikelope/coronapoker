/*
 * wire-2: valida que la operacion REAL de un paso de cascada del cliente
 * (RistrettoSRA.applyCommutativeLock + CryptoSRA.shuffleDeck) es exactamente apply(deck,perm,k)
 * y que se prueba/verifica via los helpers byte-orientados. Es la red de seguridad del cableado
 * del handler DECK_CASCADE_REQ.
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.CryptoSRA;
import com.tonikelope.coronapoker.Helpers;
import java.security.SecureRandom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CascadeStepWireTest {

    @BeforeAll
    public static void ensureRng() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
    }

    /** Replica EXACTA del paso de cascada del cliente y comprueba que el proof cuadra. */
    @Test
    public void realCascadeStepProvesAndVerifies() {
        byte[] deckIn = RistrettoSRA.getGenesisDeck(); // 52 * 32, puntos validos
        byte[] k = RistrettoSRA.generateLockScalar();
        byte[] locked = RistrettoSRA.applyCommutativeLock(deckIn, k);
        byte[] seed = new byte[48];
        Helpers.CSPRNG_GENERATOR.nextBytes(seed);
        byte[] deckOut = CryptoSRA.shuffleDeck(locked, seed);
        int[] perm = CryptoSRA.shufflePermutation(52, seed);

        byte[] proof = VerifiableCascade.proveStepWire(deckIn, deckOut, perm, k, 32);
        assertNotNull(proof, "el paso de cascada real se puede probar");
        assertTrue(VerifiableCascade.verifyStepWire(deckIn, deckOut, proof),
                "applyCommutativeLock + shuffleDeck == apply(deck,perm,k) -> el proof verifica");
    }

    @Test
    public void tamperedDeckOutRejected() {
        byte[] deckIn = RistrettoSRA.getGenesisDeck();
        byte[] k = RistrettoSRA.generateLockScalar();
        byte[] locked = RistrettoSRA.applyCommutativeLock(deckIn, k);
        byte[] seed = new byte[48];
        Helpers.CSPRNG_GENERATOR.nextBytes(seed);
        byte[] deckOut = CryptoSRA.shuffleDeck(locked, seed);
        int[] perm = CryptoSRA.shufflePermutation(52, seed);
        byte[] proof = VerifiableCascade.proveStepWire(deckIn, deckOut, perm, k, 32);

        // El host (tramposo) presenta un deckOut distinto del que se probo.
        byte[] otherOut = RistrettoSRA.applyCommutativeLock(deckOut, RistrettoSRA.generateLockScalar());
        assertFalse(VerifiableCascade.verifyStepWire(deckIn, otherOut, proof),
                "deckOut distinto del probado -> rechazado");
    }

    @Test
    public void malformedProofRejected() {
        byte[] deckIn = RistrettoSRA.getGenesisDeck();
        byte[] deckOut = RistrettoSRA.applyCommutativeLock(deckIn, RistrettoSRA.generateLockScalar());
        assertFalse(VerifiableCascade.verifyStepWire(deckIn, deckOut, new byte[]{1, 2, 3}),
                "proof basura -> rechazado, sin crashear");
        assertFalse(VerifiableCascade.verifyStepWire(deckIn, deckOut, null), "proof null -> rechazado");
    }

    @Test
    public void badDeckBytesReturnNull() {
        byte[] k = RistrettoSRA.generateLockScalar();
        int[] perm = new int[52];
        for (int i = 0; i < 52; i++) {
            perm[i] = i;
        }
        assertNull(VerifiableCascade.proveStepWire(new byte[]{1, 2, 3}, new byte[]{4, 5, 6}, perm, k, 8),
                "decks no-puntos -> proveStepWire null (no throw)");
        assertNull(VerifiableCascade.decodeDeck(new byte[]{0, 0, 0}), "decodeDeck de basura -> null");
    }
}
