/*
 * Bayer-Groth: ENSAMBLAJE del shuffle verificable B[i]=k*A[pi[i]] (permutacion + escalar comun ocultos).
 * La mitad que faltaba del mental poker. Suite adversaria CORONA:
 *  - barajado honesto verifica
 *  - ATAQUE SMUGGLE (carta duplicada en B, no es permutacion) -> RECHAZADO, con pi real Y mintiendo pi
 *  - escalar no-uniforme (k distinto por posicion) -> rechazado
 *  - manipulaciones -> rechazadas
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.math.BigInteger;
import java.security.SecureRandom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ShuffleArgumentTest {

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

    private static EdwardsPoint[] randomDeck(int n) {
        EdwardsPoint[] a = new EdwardsPoint[n];
        for (int i = 0; i < n; i++) {
            a[i] = EdwardsPoint.BASE.scalarMul(scalar());
        }
        return a;
    }

    /** Derangement determinista (rotacion + swap), sin depender del RNG. */
    private static int[] permutation(int n) {
        int[] p = new int[n];
        for (int i = 0; i < n; i++) {
            p[i] = (i + 3) % n;
        }
        int tmp = p[0];
        p[0] = p[n - 1];
        p[n - 1] = tmp;
        return p;
    }

    private static EdwardsPoint[] applyShuffle(EdwardsPoint[] a, int[] pi, BigInteger k) {
        EdwardsPoint[] b = new EdwardsPoint[a.length];
        for (int i = 0; i < a.length; i++) {
            b[i] = a[pi[i]].scalarMul(k.mod(L));
        }
        return b;
    }

    @Test
    public void honestShuffleVerifies() {
        int n = 9;
        EdwardsPoint[] a = randomDeck(n);
        int[] pi = permutation(n);
        BigInteger k = scalar();
        EdwardsPoint[] b = applyShuffle(a, pi, k);
        ShuffleArgument.Proof p = ShuffleArgument.prove(a, b, pi, k);
        assertTrue(ShuffleArgument.verify(a, b, p), "barajado honesto B=k*pi(A) -> verifica");
    }

    @Test
    public void identityShuffleVerifies() {
        int n = 6;
        EdwardsPoint[] a = randomDeck(n);
        int[] pi = new int[n];
        for (int i = 0; i < n; i++) {
            pi[i] = i;
        }
        BigInteger k = scalar();
        EdwardsPoint[] b = applyShuffle(a, pi, k);
        ShuffleArgument.Proof p = ShuffleArgument.prove(a, b, pi, k);
        assertTrue(ShuffleArgument.verify(a, b, p), "identidad relockeada -> verifica");
    }

    @Test
    public void smuggleAttackWithRealMappingRejected() {
        // EL ATAQUE: el host coloca la misma carta de entrada en dos posiciones de salida (duplicado),
        // dejando una entrada sin repartir. El mapeo real NO es biyeccion.
        int n = 8;
        EdwardsPoint[] a = randomDeck(n);
        BigInteger k = scalar();
        int[] badPi = permutation(n);
        badPi[1] = badPi[0]; // posicion 1 repite la carta de la posicion 0; una entrada queda huerfana
        EdwardsPoint[] b = applyShuffle(a, badPi, k); // B tiene B[0]==B[1] (carta duplicada)
        ShuffleArgument.Proof p = ShuffleArgument.prove(a, b, badPi, k);
        assertFalse(ShuffleArgument.verify(a, b, p),
                "[ATAQUE] carta duplicada (no permutacion) -> RECHAZADO por el argumento de permutacion");
    }

    @Test
    public void smuggleAttackLyingAboutPermutationRejected() {
        // El atacante construye un B con duplicado pero MIENTE en la prueba afirmando una permutacion
        // valida (identidad) para pasar el check de permutacion. Debe caer en el check de escalar comun.
        int n = 8;
        EdwardsPoint[] a = randomDeck(n);
        BigInteger k = scalar();
        EdwardsPoint[] b = new EdwardsPoint[n];
        for (int i = 0; i < n; i++) {
            b[i] = a[i].scalarMul(k.mod(L));
        }
        b[1] = b[0]; // duplicado: B[1] = k*A[0] en vez de k*A[1]
        int[] idPi = new int[n];
        for (int i = 0; i < n; i++) {
            idPi[i] = i;
        }
        ShuffleArgument.Proof p = ShuffleArgument.prove(a, b, idPi, k); // miente: pi=identidad
        assertFalse(ShuffleArgument.verify(a, b, p),
                "[ATAQUE] duplicado mintiendo pi=identidad -> RECHAZADO por el check de escalar comun (Q!=k*P_A)");
    }

    @Test
    public void nonUniformScalarRejected() {
        // Cada posicion relockeada con un escalar distinto (no es un unico k uniforme).
        int n = 7;
        EdwardsPoint[] a = randomDeck(n);
        int[] pi = permutation(n);
        EdwardsPoint[] b = new EdwardsPoint[n];
        for (int i = 0; i < n; i++) {
            b[i] = a[pi[i]].scalarMul(scalar()); // k_i distinto por posicion
        }
        BigInteger kClaim = scalar();
        ShuffleArgument.Proof p = ShuffleArgument.prove(a, b, pi, kClaim);
        assertFalse(ShuffleArgument.verify(a, b, p), "escalar no-uniforme -> rechazado");
    }

    @Test
    public void tamperedQRejected() {
        int n = 6;
        EdwardsPoint[] a = randomDeck(n);
        int[] pi = permutation(n);
        BigInteger k = scalar();
        EdwardsPoint[] b = applyShuffle(a, pi, k);
        ShuffleArgument.Proof p = ShuffleArgument.prove(a, b, pi, k);
        ShuffleArgument.Proof bad = new ShuffleArgument.Proof(
                p.cf, p.perm, Ristretto255.encode(Ristretto255.decode(p.q).add(EdwardsPoint.BASE)),
                p.wsum, p.scT, p.scZ);
        assertFalse(ShuffleArgument.verify(a, b, bad), "Q manipulado -> rechazado");
    }

    @Test
    public void tamperedScalarResponseRejected() {
        int n = 6;
        EdwardsPoint[] a = randomDeck(n);
        int[] pi = permutation(n);
        BigInteger k = scalar();
        EdwardsPoint[] b = applyShuffle(a, pi, k);
        ShuffleArgument.Proof p = ShuffleArgument.prove(a, b, pi, k);
        ShuffleArgument.Proof bad = new ShuffleArgument.Proof(
                p.cf, p.perm, p.q, p.wsum, p.scT, p.scZ.add(BigInteger.ONE));
        assertFalse(ShuffleArgument.verify(a, b, bad), "z del Schnorr manipulado -> rechazado");
    }
}
