/*
 * Wire del cierre completo dual-lock (DualLockWire): genesis recomputado + cadenas por bytes.
 * Lo que correra cada peer antes del unlock. Honesto por bytes verifica; manipulado/malformado -> false.
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DualLockWireTest {

    private static final BigInteger L = EdwardsPoint.L;
    private static final int DECK = 16;
    private static final int POCKET = 4;
    private static final int COMMUNITY = DECK - POCKET;

    @BeforeAll
    public static void ensureRng() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
    }

    private static BigInteger scalar() {
        return RistrettoSRA.bytesToScalar(RistrettoSRA.generateLockScalar());
    }

    // Devuelve {genesisBytes, cascadeDeckBytes(list), cascadeProofBytes(list), megapacketBytes,
    //           rotationStateBytes(list), rotationProofBytes(list)}
    private static Object[] honestWire(int cascadeSteps, int rotationPeers) {
        EdwardsPoint[] genesis = new EdwardsPoint[DECK];
        for (int i = 0; i < DECK; i++) {
            genesis[i] = EdwardsPoint.BASE.scalarMul(scalar());
        }
        List<byte[]> cascadeDeckBytes = new ArrayList<>();
        List<byte[]> cascadeProofBytes = new ArrayList<>();
        EdwardsPoint[] cur = genesis;
        for (int m = 0; m < cascadeSteps; m++) {
            int[] perm = DeckTransform.randomPermutation(DECK);
            BigInteger k = scalar();
            EdwardsPoint[] next = DeckTransform.apply(cur, perm, k);
            cascadeProofBytes.add(ProofCodec.encodeShuffle(ShuffleArgument.prove(cur, next, perm, k)));
            cascadeDeckBytes.add(DualLockWire.encodeDeck(next));
            cur = next;
        }
        EdwardsPoint[] preRot = cur;
        EdwardsPoint[] community = Arrays.copyOfRange(preRot, POCKET, DECK);

        List<byte[]> rotationStateBytes = new ArrayList<>();
        List<byte[]> rotationProofBytes = new ArrayList<>();
        EdwardsPoint[] curC = community;
        for (int j = 0; j < rotationPeers; j++) {
            BigInteger s = scalar();
            EdwardsPoint[] next = new EdwardsPoint[COMMUNITY];
            for (int i = 0; i < COMMUNITY; i++) {
                next[i] = curC[i].scalarMul(s.mod(L));
            }
            rotationProofBytes.add(DualLockWire.encodeRotationProof(RotationProof.prove(s, curC, next)));
            rotationStateBytes.add(DualLockWire.encodeDeck(next));
            curC = next;
        }
        EdwardsPoint[] megapacket = new EdwardsPoint[DECK];
        System.arraycopy(preRot, 0, megapacket, 0, POCKET);
        System.arraycopy(curC, 0, megapacket, POCKET, COMMUNITY);

        return new Object[]{DualLockWire.encodeDeck(genesis), cascadeDeckBytes, cascadeProofBytes,
            DualLockWire.encodeDeck(megapacket), rotationStateBytes, rotationProofBytes};
    }

    @SuppressWarnings("unchecked")
    private static boolean verify(Object[] w) {
        return DualLockWire.verifyFullChainWire((byte[]) w[0], (List<byte[]>) w[1], (List<byte[]>) w[2],
                POCKET, (byte[]) w[3], (List<byte[]>) w[4], (List<byte[]>) w[5]);
    }

    @Test
    public void honestWireVerifies() {
        assertTrue(verify(honestWire(3, 3)), "cadena dual-lock completa por bytes -> verifica");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void tamperedMegapacketRejected() {
        Object[] w = honestWire(2, 2);
        byte[] mega = (byte[]) w[3];
        mega[POCKET * 32] ^= 0x01; // tocar el primer punto community del megapacket
        assertFalse(verify(w), "megapacket manipulado -> false");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void wrongGenesisRejected() {
        Object[] w = honestWire(2, 2);
        EdwardsPoint[] other = new EdwardsPoint[DECK];
        for (int i = 0; i < DECK; i++) {
            other[i] = EdwardsPoint.BASE.scalarMul(scalar());
        }
        w[0] = DualLockWire.encodeDeck(other);
        assertFalse(verify(w), "genesis equivocado (anclaje) -> false");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void malformedProofRejectedCleanly() {
        Object[] w = honestWire(2, 2);
        ((List<byte[]>) w[5]).set(0, new byte[]{1, 2, 3}); // rotation proof basura
        assertFalse(verify(w), "rotation proof malformado -> false, sin excepcion");
        Object[] w2 = honestWire(2, 2);
        ((List<byte[]>) w2[2]).set(0, new byte[]{9, 9}); // cascade proof basura
        assertFalse(verify(w2), "cascade proof malformado -> false, sin excepcion");
    }

    @Test
    public void nullsRejected() {
        assertFalse(DualLockWire.verifyFullChainWire(null, null, null, POCKET, null, null, null),
                "nulls -> false");
    }

    // --- verifyRotationStepWire: autenticacion por-paso de la prueba de rotacion REMOTA en la ingestion.
    // El host la usa para NO aceptar de un peer una prueba basura bien formada que rotaria las piezas OK
    // pero haria fallar su full-chain self-check mientras difunde el bundle igual (falso "host deshonesto").

    // Un paso de rotacion honesto: after[i] = s · before[i]; proof = RotationProof.prove(s, before, after).
    // Devuelve {beforeBytes, afterBytes, proofBytes}.
    private static byte[][] honestRotationStep() {
        EdwardsPoint[] before = new EdwardsPoint[COMMUNITY];
        for (int i = 0; i < COMMUNITY; i++) {
            before[i] = EdwardsPoint.BASE.scalarMul(scalar());
        }
        BigInteger s = scalar();
        EdwardsPoint[] after = new EdwardsPoint[COMMUNITY];
        for (int i = 0; i < COMMUNITY; i++) {
            after[i] = before[i].scalarMul(s.mod(L));
        }
        byte[] proof = DualLockWire.encodeRotationProof(RotationProof.prove(s, before, after));
        return new byte[][]{DualLockWire.encodeDeck(before), DualLockWire.encodeDeck(after), proof};
    }

    @Test
    public void rotationStepHonestVerifies() {
        byte[][] st = honestRotationStep();
        assertTrue(DualLockWire.verifyRotationStepWire(st[0], st[1], st[2]),
                "paso de rotacion honesto -> verifica");
    }

    @Test
    public void rotationStepGarbageProofRejected() {
        // El vector del MEDIUM: piezas rotadas OK, pero prueba basura bien formada (64 bytes).
        byte[][] st = honestRotationStep();
        byte[] garbage = new byte[64];
        Helpers.CSPRNG_GENERATOR.nextBytes(garbage);
        assertFalse(DualLockWire.verifyRotationStepWire(st[0], st[1], garbage),
                "prueba basura de 64 bytes -> false (no se acepta en la ingestion)");
    }

    @Test
    public void rotationStepTamperedAfterRejected() {
        byte[][] st = honestRotationStep();
        st[1][0] ^= 0x01; // tocar el estado 'after' -> la prueba honesta ya no cuadra
        assertFalse(DualLockWire.verifyRotationStepWire(st[0], st[1], st[2]),
                "'after' manipulado -> false");
    }

    @Test
    public void rotationStepMalformedRejectedCleanly() {
        byte[][] st = honestRotationStep();
        assertFalse(DualLockWire.verifyRotationStepWire(st[0], st[1], new byte[]{1, 2, 3}),
                "prueba de longitud invalida -> false, sin excepcion");
        assertFalse(DualLockWire.verifyRotationStepWire(null, st[1], st[2]), "before null -> false");
        assertFalse(DualLockWire.verifyRotationStepWire(st[0], null, st[2]), "after null -> false");
        assertFalse(DualLockWire.verifyRotationStepWire(st[0], st[1], null), "proof null -> false");
    }
}
