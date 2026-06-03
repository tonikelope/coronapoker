/*
 * Sanidad del CABLEADO: el RotationProof generado con s = s1*s2 (uPocket*kCommunity) DEBE verificar
 * contra la rotacion real (dos applyCommutativeLock encadenados). Si la convencion de escalar/endianness
 * no coincide, el host loggearia "full-chain verify = false" en falso. Esto lo caza antes del smoke.
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.math.BigInteger;
import java.security.SecureRandom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RotationProofWiringTest {

    private static final BigInteger L = EdwardsPoint.L;

    @BeforeAll
    public static void ensureRng() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
    }

    /** Replica EXACTA de Crupier.genRotationProof (host/bot): s = s1*s2, prove(in, out). */
    private static byte[] genRotationProofLikeHost(byte[] before, byte[] after, byte[] s1, byte[] s2) {
        BigInteger s = RistrettoSRA.bytesToScalar(s1).multiply(RistrettoSRA.bytesToScalar(s2)).mod(L);
        EdwardsPoint[] in = ShuffleCascade.decodeDeck(before);
        EdwardsPoint[] out = ShuffleCascade.decodeDeck(after);
        if (in == null || out == null) {
            return null;
        }
        return DualLockWire.encodeRotationProof(RotationProof.prove(s, in, out));
    }

    @Test
    public void hostRotationProofVerifiesAgainstRealLock() {
        // region community: 46 puntos (mazo 52, 3 jugadores -> 6 pocket)
        int n = 46;
        byte[] region = new byte[n * 32];
        for (int i = 0; i < n; i++) {
            EdwardsPoint pt = EdwardsPoint.BASE.scalarMul(RistrettoSRA.bytesToScalar(RistrettoSRA.generateLockScalar()));
            System.arraycopy(Ristretto255.encode(pt), 0, region, i * 32, 32);
        }
        // rotacion real: uPocket (s1) luego kCommunity (s2), como en Crupier
        byte[] s1 = RistrettoSRA.generateLockScalar(); // uPocket
        byte[] s2 = RistrettoSRA.generateLockScalar(); // kCommunity
        byte[] step1 = RistrettoSRA.applyCommutativeLock(region, s1);
        byte[] after = RistrettoSRA.applyCommutativeLock(step1, s2);
        assertNotNull(after, "applyCommutativeLock no nulo");

        byte[] proofBytes = genRotationProofLikeHost(region, after, s1, s2);
        assertNotNull(proofBytes, "proof generado");

        RotationProof.Proof proof = DualLockWire.decodeRotationProof(proofBytes);
        assertNotNull(proof, "proof decodificado");
        assertTrue(RotationProof.verify(ShuffleCascade.decodeDeck(region), ShuffleCascade.decodeDeck(after), proof),
                "el RotationProof del host (s=s1*s2) verifica contra la rotacion real (dos applyCommutativeLock)");
    }

    @Test
    public void chainedTwoPeerRotationVerifies() {
        // dos peers encadenados, como el bucle de rotacion del host
        int n = 40;
        byte[] state0 = new byte[n * 32];
        for (int i = 0; i < n; i++) {
            EdwardsPoint pt = EdwardsPoint.BASE.scalarMul(RistrettoSRA.bytesToScalar(RistrettoSRA.generateLockScalar()));
            System.arraycopy(Ristretto255.encode(pt), 0, state0, i * 32, 32);
        }
        // peer 0
        byte[] a1 = RistrettoSRA.generateLockScalar(), b1 = RistrettoSRA.generateLockScalar();
        byte[] state1 = RistrettoSRA.applyCommutativeLock(RistrettoSRA.applyCommutativeLock(state0, a1), b1);
        byte[] proof0 = genRotationProofLikeHost(state0, state1, a1, b1);
        // peer 1
        byte[] a2 = RistrettoSRA.generateLockScalar(), b2 = RistrettoSRA.generateLockScalar();
        byte[] state2 = RistrettoSRA.applyCommutativeLock(RistrettoSRA.applyCommutativeLock(state1, a2), b2);
        byte[] proof1 = genRotationProofLikeHost(state1, state2, a2, b2);

        assertTrue(RotationProof.verify(ShuffleCascade.decodeDeck(state0), ShuffleCascade.decodeDeck(state1),
                DualLockWire.decodeRotationProof(proof0)), "paso 0 verifica");
        assertTrue(RotationProof.verify(ShuffleCascade.decodeDeck(state1), ShuffleCascade.decodeDeck(state2),
                DualLockWire.decodeRotationProof(proof1)), "paso 1 verifica");
    }
}
