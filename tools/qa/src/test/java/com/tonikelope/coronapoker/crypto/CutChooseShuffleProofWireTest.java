/*
 * Wire del proof de barajado (rama wire-1): serializar/deserializar la prueba para mandarla por
 * red. Round-trip que sigue verificando, malformado -> null sin crashear, bytes manipulados ->
 * verify falla, y el tamaño real (coste de red del cut-and-choose).
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.math.BigInteger;
import java.security.SecureRandom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CutChooseShuffleProofWireTest {

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

    private static CutChooseShuffleProof.Proof honest(EdwardsPoint[][] ab, int rounds) {
        EdwardsPoint[] a = randomDeck(8);
        int[] pi = DeckTransform.randomPermutation(8);
        BigInteger k = scalar();
        EdwardsPoint[] b = DeckTransform.apply(a, pi, k);
        ab[0] = a;
        ab[1] = b;
        return CutChooseShuffleProof.prove(a, b, pi, k, rounds);
    }

    @Test
    public void roundTripVerifies() {
        EdwardsPoint[][] ab = new EdwardsPoint[2][];
        CutChooseShuffleProof.Proof proof = honest(ab, 32);

        byte[] wire = proof.toBytes();
        CutChooseShuffleProof.Proof back = CutChooseShuffleProof.Proof.fromBytes(wire);
        assertNotNull(back, "fromBytes de un proof valido no es null");
        assertTrue(CutChooseShuffleProof.verify(ab[0], ab[1], back),
                "el proof deserializado sigue verificando");
        // serializacion determinista
        assertArrayEquals(wire, back.toBytes(), "toBytes determinista (round-trip estable)");
    }

    @Test
    public void malformedReturnsNullNeverThrows() {
        assertNull(CutChooseShuffleProof.Proof.fromBytes(null));
        assertNull(CutChooseShuffleProof.Proof.fromBytes(new byte[0]));
        assertNull(CutChooseShuffleProof.Proof.fromBytes(new byte[3]));
        // header absurdo (rounds enorme) -> null sin OOM
        byte[] absurd = new byte[8];
        absurd[0] = 0x7F; absurd[1] = (byte) 0xFF; absurd[2] = (byte) 0xFF; absurd[3] = (byte) 0xFF; // rounds gigante
        absurd[7] = 8; // deckSize 8
        assertNull(CutChooseShuffleProof.Proof.fromBytes(absurd));
        // longitud incoherente con el header
        byte[] shortBody = new byte[8 + 10];
        shortBody[3] = 1;  // rounds 1
        shortBody[7] = 8;  // deckSize 8 -> espera 8*32+8*2+32 bytes, no 10
        assertNull(CutChooseShuffleProof.Proof.fromBytes(shortBody));
    }

    @Test
    public void tamperedBytesRejectedByVerify() {
        EdwardsPoint[][] ab = new EdwardsPoint[2][];
        CutChooseShuffleProof.Proof proof = honest(ab, 32);
        byte[] wire = proof.toBytes();
        wire[100] ^= 0x01; // voltea un bit en el cuerpo (un punto intermedio o una mitad revelada)
        CutChooseShuffleProof.Proof back = CutChooseShuffleProof.Proof.fromBytes(wire);
        // puede deserializar estructuralmente, pero la verificacion debe fallar (o ser null)
        boolean rejected = (back == null) || !CutChooseShuffleProof.verify(ab[0], ab[1], back);
        assertTrue(rejected, "un bit manipulado en el wire -> verify falla (o no deserializa)");
    }

    @Test
    public void wireSizeForRealisticProof() {
        EdwardsPoint[] a = randomDeck(52);
        int[] pi = DeckTransform.randomPermutation(52);
        BigInteger k = scalar();
        EdwardsPoint[] b = DeckTransform.apply(a, pi, k);
        CutChooseShuffleProof.Proof proof = CutChooseShuffleProof.prove(a, b, pi, k, 128);
        byte[] wire = proof.toBytes();
        System.out.println("[WIRE] proof 52 cartas / 128 rondas = " + (wire.length / 1024) + " KB");
        assertNotNull(CutChooseShuffleProof.Proof.fromBytes(wire));
        assertTrue(CutChooseShuffleProof.verify(a, b, CutChooseShuffleProof.Proof.fromBytes(wire)),
                "el proof realista round-trip y verifica");
    }
}
