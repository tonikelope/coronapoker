/*
 * Bayer-Groth: codec de wire de la prueba de shuffle (ProofCodec).
 * Round-trip determinista + decode total (malformado/truncado/garbage -> null, nunca excepcion).
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProofCodecTest {

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

    private static ShuffleArgument.Proof sampleProof(int n) {
        EdwardsPoint[] a = new EdwardsPoint[n];
        for (int i = 0; i < n; i++) {
            a[i] = EdwardsPoint.BASE.scalarMul(scalar());
        }
        int[] pi = DeckTransform.randomPermutation(n);
        BigInteger k = scalar();
        EdwardsPoint[] b = DeckTransform.apply(a, pi, k);
        return ShuffleArgument.prove(a, b, pi, k);
    }

    @Test
    public void roundTripProofStillVerifies() {
        int n = 9;
        EdwardsPoint[] a = new EdwardsPoint[n];
        for (int i = 0; i < n; i++) {
            a[i] = EdwardsPoint.BASE.scalarMul(scalar());
        }
        int[] pi = DeckTransform.randomPermutation(n);
        BigInteger k = scalar();
        EdwardsPoint[] b = DeckTransform.apply(a, pi, k);
        ShuffleArgument.Proof p = ShuffleArgument.prove(a, b, pi, k);

        byte[] enc = ProofCodec.encodeShuffle(p);
        assertNotNull(enc, "encode no nulo");
        ShuffleArgument.Proof back = ProofCodec.decodeShuffle(enc);
        assertNotNull(back, "decode no nulo");
        assertTrue(ShuffleArgument.verify(a, b, back), "el proof decodificado sigue verificando");
    }

    @Test
    public void encodingIsDeterministic() {
        ShuffleArgument.Proof p = sampleProof(8);
        byte[] enc1 = ProofCodec.encodeShuffle(p);
        byte[] enc2 = ProofCodec.encodeShuffle(ProofCodec.decodeShuffle(enc1));
        assertArrayEquals(enc1, enc2, "encode(decode(encode(p))) == encode(p)");
    }

    @Test
    public void nullEncodeAndDecodeAreNull() {
        assertNull(ProofCodec.encodeShuffle(null), "encode(null) -> null");
        assertNull(ProofCodec.decodeShuffle(null), "decode(null) -> null");
        assertNull(ProofCodec.decodeShuffle(new byte[0]), "decode(vacio) -> null");
        assertNull(ProofCodec.decodeShuffle(new byte[]{1, 2, 3}), "decode(basura corta) -> null");
    }

    @Test
    public void truncatedRejected() {
        byte[] enc = ProofCodec.encodeShuffle(sampleProof(9));
        byte[] truncated = Arrays.copyOf(enc, enc.length - 1);
        assertNull(ProofCodec.decodeShuffle(truncated), "truncado -> null (underflow)");
    }

    @Test
    public void trailingGarbageRejected() {
        byte[] enc = ProofCodec.encodeShuffle(sampleProof(9));
        byte[] extended = Arrays.copyOf(enc, enc.length + 1);
        assertNull(ProofCodec.decodeShuffle(extended), "garbage al final -> null");
    }

    @Test
    public void hugeCountRejected() {
        // Un conteo de array absurdo al principio (primer int = 0x7FFFFFFF) no debe OOM ni lanzar.
        byte[] evil = new byte[8];
        evil[0] = 0x7F;
        evil[1] = (byte) 0xFF;
        evil[2] = (byte) 0xFF;
        evil[3] = (byte) 0xFF;
        assertNull(ProofCodec.decodeShuffle(evil), "conteo gigante -> null (cap), sin OOM");
    }

    @Test
    public void tamperedBytesDoNotVerify() {
        int n = 9;
        EdwardsPoint[] a = new EdwardsPoint[n];
        for (int i = 0; i < n; i++) {
            a[i] = EdwardsPoint.BASE.scalarMul(scalar());
        }
        int[] pi = DeckTransform.randomPermutation(n);
        BigInteger k = scalar();
        EdwardsPoint[] b = DeckTransform.apply(a, pi, k);
        byte[] enc = ProofCodec.encodeShuffle(ShuffleArgument.prove(a, b, pi, k));

        // Voltear un byte cerca del final (zona de respuestas/scZ): o decodifica a algo que NO verifica,
        // o no decodifica. En ningun caso debe verificar.
        byte[] tampered = enc.clone();
        tampered[tampered.length - 2] ^= 0x01;
        ShuffleArgument.Proof back = ProofCodec.decodeShuffle(tampered);
        boolean accepted = back != null && ShuffleArgument.verify(a, b, back);
        assertFalse(accepted, "bytes manipulados -> no verifican");
    }
}
