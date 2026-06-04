/*
 * Bayer-Groth ladrillo 1: commitment vectorial de Pedersen C = r*H + sum a_i*G_i.
 * Suite adversaria: completeness, binding (vector/blinding distinto -> commit distinto; apertura
 * falsa no abre), hiding (el blinding importa), homomorfismo add/scale, generadores independientes.
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

public class PedersenVectorCommitTest {

    @BeforeAll
    public static void ensureRng() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
    }

    private static BigInteger scalar() {
        return RistrettoSRA.bytesToScalar(RistrettoSRA.generateLockScalar());
    }

    private static BigInteger[] vec(int n) {
        BigInteger[] a = new BigInteger[n];
        for (int i = 0; i < n; i++) {
            a[i] = scalar();
        }
        return a;
    }

    @Test
    public void commitOpensWithItsOwnOpening() {
        // Abrir un commitment ES recomputarlo con (a, r) y comparar encodings (determinismo).
        BigInteger[] a = vec(8);
        BigInteger r = scalar();
        byte[] c = PedersenVectorCommit.commit(a, r);
        assertNotNull(c);
        assertArrayEquals(c, PedersenVectorCommit.commit(a, r), "el commitment vectorial abre con (a, r)");
    }

    @Test
    public void wrongOpeningFails() {
        BigInteger[] a = vec(6);
        BigInteger r = scalar();
        byte[] c = PedersenVectorCommit.commit(a, r);
        BigInteger[] a2 = a.clone();
        a2[2] = a2[2].add(BigInteger.ONE);
        assertFalse(Arrays.equals(c, PedersenVectorCommit.commit(a2, r)), "un elemento del vector cambiado no abre");
        assertFalse(Arrays.equals(c, PedersenVectorCommit.commit(a, r.add(BigInteger.ONE))), "blinding cambiado no abre");
    }

    @Test
    public void bindingDifferentVectors() {
        BigInteger r = scalar();
        byte[] c1 = PedersenVectorCommit.commit(new BigInteger[]{BigInteger.valueOf(1), BigInteger.valueOf(2)}, r);
        byte[] c2 = PedersenVectorCommit.commit(new BigInteger[]{BigInteger.valueOf(1), BigInteger.valueOf(3)}, r);
        assertFalse(Arrays.equals(c1, c2), "mismo blinding, vector distinto -> commit distinto");
    }

    @Test
    public void hidingBlindingMatters() {
        BigInteger[] a = vec(4);
        assertFalse(Arrays.equals(PedersenVectorCommit.commit(a, scalar()), PedersenVectorCommit.commit(a, scalar())),
                "mismo vector, blinding distinto -> commit distinto (no filtra el vector)");
    }

    @Test
    public void homomorphicAddition() {
        // La propiedad que los verificadores plegados explotan a nivel de punto:
        // decode(Comm(a,ra)) + decode(Comm(b,rb)) = decode(Comm(a+b, ra+rb)).
        BigInteger[] a = vec(5), b = vec(5);
        BigInteger ra = scalar(), rb = scalar();
        EdwardsPoint ca = Ristretto255.decode(PedersenVectorCommit.commit(a, ra));
        EdwardsPoint cb = Ristretto255.decode(PedersenVectorCommit.commit(b, rb));
        BigInteger[] ab = new BigInteger[5];
        for (int i = 0; i < 5; i++) {
            ab[i] = a[i].add(b[i]);
        }
        assertArrayEquals(PedersenVectorCommit.commit(ab, ra.add(rb)), Ristretto255.encode(ca.add(cb)),
                "Comm(a,ra) (+) Comm(b,rb) = Comm(a+b, ra+rb)");
    }

    @Test
    public void homomorphicScaling() {
        BigInteger[] a = vec(5);
        BigInteger r = scalar(), e = scalar();
        EdwardsPoint c = Ristretto255.decode(PedersenVectorCommit.commit(a, r));
        BigInteger[] ea = new BigInteger[5];
        for (int i = 0; i < 5; i++) {
            ea[i] = a[i].multiply(e);
        }
        assertArrayEquals(PedersenVectorCommit.commit(ea, r.multiply(e)),
                Ristretto255.encode(c.scalarMul(e.mod(EdwardsPoint.L))),
                "e * Comm(a,r) = Comm(e*a, e*r)");
    }

    @Test
    public void generatorsAreDistinctAndValid() {
        byte[] h = Ristretto255.encode(PedersenVectorCommit.H);
        byte[] g0 = Ristretto255.encode(PedersenVectorCommit.generator(0));
        byte[] g1 = Ristretto255.encode(PedersenVectorCommit.generator(1));
        assertNotNull(Ristretto255.decode(g0));
        assertFalse(Arrays.equals(h, g0), "H != G0");
        assertFalse(Arrays.equals(g0, g1), "G0 != G1");
        assertFalse(Arrays.equals(g0, Ristretto255.encode(EdwardsPoint.BASE)), "G0 != base");
    }

    @Test
    public void scalarsReducedModL() {
        BigInteger[] a = vec(3);
        BigInteger r = scalar();
        byte[] c = PedersenVectorCommit.commit(a, r);
        BigInteger[] aShift = a.clone();
        aShift[0] = aShift[0].add(EdwardsPoint.L);
        assertArrayEquals(c, PedersenVectorCommit.commit(aShift, r.add(EdwardsPoint.L)),
                "a_i+L, r+L -> mismo commit (aritmetica mod L)");
    }
}
