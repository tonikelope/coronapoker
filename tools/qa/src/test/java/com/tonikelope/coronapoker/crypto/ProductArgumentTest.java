/*
 * Bayer-Groth ladrillo: argumento de producto Pi(a_i)=b (b publico) via grand-product.
 * Nucleo combinatorio del shuffle. Clave adversaria: producto publico falso -> rechazado;
 * un factor cambiado rompe la cadena.
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.math.BigInteger;
import java.security.SecureRandom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProductArgumentTest {

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

    private static BigInteger product(BigInteger[] a) {
        BigInteger p = BigInteger.ONE;
        for (BigInteger x : a) {
            p = p.multiply(x).mod(L);
        }
        return p;
    }

    private static byte[][] commitAll(BigInteger[] a, BigInteger[] ra) {
        byte[][] ca = new byte[a.length][];
        for (int i = 0; i < a.length; i++) {
            ca[i] = MultiplicationProof.commitScalar(a[i], ra[i]);
        }
        return ca;
    }

    @Test
    public void honestProductVerifies() {
        int n = 7;
        BigInteger[] a = new BigInteger[n], ra = new BigInteger[n];
        for (int i = 0; i < n; i++) {
            a[i] = scalar();
            ra[i] = scalar();
        }
        byte[][] ca = commitAll(a, ra);
        BigInteger b = product(a);
        ProductArgument.Proof p = ProductArgument.prove(a, ra, ca, b);
        assertTrue(ProductArgument.verify(ca, b, p), "Pi(a_i)=b honesto -> verifica");
    }

    @Test
    public void singleFactorVerifies() {
        BigInteger[] a = {scalar()}, ra = {scalar()};
        byte[][] ca = commitAll(a, ra);
        ProductArgument.Proof p = ProductArgument.prove(a, ra, ca, a[0].mod(L));
        assertTrue(ProductArgument.verify(ca, a[0].mod(L), p), "n=1: producto = a_0 -> verifica");
    }

    @Test
    public void wrongPublicProductRejected() {
        int n = 6;
        BigInteger[] a = new BigInteger[n], ra = new BigInteger[n];
        for (int i = 0; i < n; i++) {
            a[i] = scalar();
            ra[i] = scalar();
        }
        byte[][] ca = commitAll(a, ra);
        BigInteger bWrong = product(a).add(BigInteger.ONE).mod(L);
        ProductArgument.Proof p = ProductArgument.prove(a, ra, ca, bWrong);
        assertFalse(ProductArgument.verify(ca, bWrong, p), "Pi(a_i)!=b -> rechazado (apertura final falla)");
    }

    @Test
    public void verifyAgainstDifferentTargetRejected() {
        int n = 5;
        BigInteger[] a = new BigInteger[n], ra = new BigInteger[n];
        for (int i = 0; i < n; i++) {
            a[i] = scalar();
            ra[i] = scalar();
        }
        byte[][] ca = commitAll(a, ra);
        BigInteger b = product(a);
        ProductArgument.Proof p = ProductArgument.prove(a, ra, ca, b);
        assertFalse(ProductArgument.verify(ca, b.add(BigInteger.ONE).mod(L), p),
                "prueba de b no vale para otro target");
    }

    @Test
    public void tamperedFactorCommitmentRejected() {
        int n = 5;
        BigInteger[] a = new BigInteger[n], ra = new BigInteger[n];
        for (int i = 0; i < n; i++) {
            a[i] = scalar();
            ra[i] = scalar();
        }
        byte[][] ca = commitAll(a, ra);
        BigInteger b = product(a);
        ProductArgument.Proof p = ProductArgument.prove(a, ra, ca, b);
        byte[][] caTampered = ca.clone();
        caTampered[2] = MultiplicationProof.commitScalar(scalar(), scalar()); // factor distinto
        assertFalse(ProductArgument.verify(caTampered, b, p), "factor cambiado -> rompe la cadena");
    }
}
